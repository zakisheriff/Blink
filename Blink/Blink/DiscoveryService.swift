//
//  DiscoveryService.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation
import Network
import Combine

// MARK: - Discovery Service
class DiscoveryService: ObservableObject {
    private let socketManager: SocketManager
    private var broadcastTimer: Timer?
    private var cleanupTimer: Timer?
    
    @Published var discoveredDevices: [Device] = []
    @Published var isDiscovering = false
    
    private let deviceId: String
    private let deviceName: String
    
    init(socketManager: SocketManager) {
        self.socketManager = socketManager
        self.deviceId = Self.getDeviceId()
        self.deviceName = Host.current().localizedName ?? "Mac"
        
        setupCallbacks()
    }
    
    private func setupCallbacks() {
        socketManager.onDiscoveryMessage = { [weak self] message, ipAddress in
            self?.handleDiscoveryMessage(message, from: ipAddress)
        }
    }
    
    // MARK: - Device ID
    private static func getDeviceId() -> String {
        let key = "com.blink.deviceId"
        if let existingId = UserDefaults.standard.string(forKey: key) {
            return existingId
        }
        
        let newId = UUID().uuidString
        UserDefaults.standard.set(newId, forKey: key)
        return newId
    }
    
    // MARK: - Discovery
    func startDiscovery() {
        guard !isDiscovering else { return }
        
        isDiscovering = true
        socketManager.startUDPListener()
        
        // Start broadcasting
        broadcastTimer = Timer.scheduledTimer(withTimeInterval: NetworkConstants.broadcastInterval, repeats: true) { [weak self] _ in
            self?.broadcastPresence()
        }
        
        // Start cleanup timer
        cleanupTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.cleanupStaleDevices()
        }
        
        // Initial broadcast
        broadcastPresence()
    }
    
    func stopDiscovery() {
        isDiscovering = false
        broadcastTimer?.invalidate()
        cleanupTimer?.invalidate()
        broadcastTimer = nil
        cleanupTimer = nil
    }
    
    private func broadcastPresence() {
        let message = DiscoveryMessage(
            deviceId: deviceId,
            deviceName: deviceName,
            port: Int(NetworkConstants.transferPort)
        )
        
        // Broadcast to subnet
        // In a real implementation, we'd get the actual subnet
        // For now, broadcast to common local network ranges
        let baseIPs = getLocalNetworkBaseIPs()
        
        for baseIP in baseIPs {
            for i in 1...254 {
                let address = "\(baseIP).\(i)"
                socketManager.sendUDPBroadcast(message: message, to: address)
            }
        }
    }
    
    private func getLocalNetworkBaseIPs() -> [String] {
        var baseIPs: [String] = []
        
        // Get local IP addresses
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return ["192.168.1"] }
        guard let firstAddr = ifaddr else { return ["192.168.1"] }
        
        for ifptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ifptr.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family
            
            if addrFamily == UInt8(AF_INET) {
                let name = String(cString: interface.ifa_name)
                
                // Skip loopback
                if name == "lo0" { continue }
                
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                           &hostname, socklen_t(hostname.count),
                           nil, socklen_t(0), NI_NUMERICHOST)
                
                let address = String(cString: hostname)
                
                // Extract base IP (first 3 octets)
                let components = address.split(separator: ".")
                if components.count == 4 {
                    let baseIP = "\(components[0]).\(components[1]).\(components[2])"
                    if !baseIPs.contains(baseIP) {
                        baseIPs.append(baseIP)
                        print("Found active interface: \(name) (\(address))")
                    }
                }
            }
        }
        
        freeifaddrs(ifaddr)
        
        return baseIPs.isEmpty ? ["192.168.1"] : baseIPs
    }
    
    // MARK: - Code-Based Connection
    private var hostedCode: String?
    
    func startHosting(with code: String) {
        self.hostedCode = code
        socketManager.startUDPListener()
        print("Started hosting with code: \(code)")
    }
    
    func connect(with code: String) {
        socketManager.startUDPListener()
        
        let message = DiscoveryMessage(
            deviceId: deviceId,
            deviceName: deviceName,
            port: Int(NetworkConstants.transferPort),
            code: code,
            type: "query"
        )
        
        // Broadcast query
        let baseIPs = getLocalNetworkBaseIPs()
        for baseIP in baseIPs {
            for i in 1...254 {
                let address = "\(baseIP).\(i)"
                socketManager.sendUDPBroadcast(message: message, to: address)
            }
        }
    }
    
    private func handleDiscoveryMessage(_ message: DiscoveryMessage, from ipAddress: String) {
        // Don't add ourselves
        guard message.deviceId != deviceId else { return }
        
        // Handle Code Query
        if message.type == "query" {
            if let code = message.code, code == hostedCode {
                print("Received valid code query from \(message.deviceName)")
                // Send response
                let response = DiscoveryMessage(
                    deviceId: deviceId,
                    deviceName: deviceName,
                    port: Int(NetworkConstants.transferPort),
                    code: code,
                    type: "response"
                )
                socketManager.sendUDPBroadcast(message: response, to: ipAddress)
                
                // Also add the querier to our list
                addOrUpdateDevice(message, ipAddress: ipAddress)
            }
            return
        }
        
        // Handle Code Response or Presence
        if message.type == "response" || message.type == "presence" {
            addOrUpdateDevice(message, ipAddress: ipAddress)
        }
    }
    
    private func addOrUpdateDevice(_ message: DiscoveryMessage, ipAddress: String) {
        DispatchQueue.main.async {
            if let index = self.discoveredDevices.firstIndex(where: { $0.id.uuidString == message.deviceId }) {
                // Update last seen time
                self.discoveredDevices[index].lastSeen = Date()
                self.discoveredDevices[index].ipAddress = ipAddress // Update IP just in case
            } else {
                // Add new device
                let device = Device(
                    id: UUID(uuidString: message.deviceId) ?? UUID(),
                    name: message.deviceName,
                    ipAddress: ipAddress,
                    port: message.port,
                    deviceType: message.deviceType == "android" ? .android : .mac
                )
                self.discoveredDevices.append(device)
            }
        }
    }
    
    private func cleanupStaleDevices() {
        let timeout = NetworkConstants.deviceTimeout
        let now = Date()
        
        discoveredDevices.removeAll { device in
            now.timeIntervalSince(device.lastSeen) > timeout
        }
    }
    
    // MARK: - Manual Device Addition
    func addDevice(ipAddress: String, port: Int, name: String) {
        let device = Device(
            name: name,
            ipAddress: ipAddress,
            port: port,
            deviceType: .android
        )
        
        if !discoveredDevices.contains(where: { $0.ipAddress == ipAddress }) {
            discoveredDevices.append(device)
        }
    }
    
    deinit {
        stopDiscovery()
    }
}
