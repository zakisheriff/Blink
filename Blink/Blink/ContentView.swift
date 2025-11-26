//
//  ContentView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI

struct ContentView: View {
    @StateObject private var socketManager = SocketManager()
    @StateObject private var discoveryService: DiscoveryService
    @StateObject private var transferManager: TransferManager
    
    @State private var selectedFiles: [TransferItem] = []
    @State private var selectedDevice: Device?
    @State private var showQRCode = false
    @State private var showCodeConnection = false
    
    init() {
        let socket = SocketManager()
        _socketManager = StateObject(wrappedValue: socket)
        _discoveryService = StateObject(wrappedValue: DiscoveryService(socketManager: socket))
        _transferManager = StateObject(wrappedValue: TransferManager(socketManager: socket))
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(nsColor: .windowBackgroundColor),
                    Color(nsColor: .windowBackgroundColor).opacity(0.8)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    header
                    
                    // Main content
                    HStack(alignment: .top, spacing: 24) {
                        // Left column - Devices
                        VStack(spacing: 24) {
                            DeviceListView(discoveryService: discoveryService) { device in
                                selectedDevice = device
                            }
                            .frame(width: 300)
                            
                            if showQRCode {
                                qrCodeSection
                                    .frame(width: 300)
                            }
                        }
                        
                        // Right column - File picker and transfers
                        VStack(spacing: 24) {
                            FilePickerView(selectedFiles: $selectedFiles)
                            
                            if !selectedFiles.isEmpty && selectedDevice != nil {
                                sendButton
                            }
                            
                            TransferListView(transferManager: transferManager)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(24)
            }
        }
        .onAppear {
            startServices()
        }
        .onDisappear {
            stopServices()
        }
    }
    
    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Blink")
                    .font(.system(size: 32, weight: .bold))
                
                Text("Ultra-fast file transfer")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            HStack(spacing: 12) {
                // Discovery status
                HStack(spacing: 6) {
                    Circle()
                        .fill(discoveryService.isDiscovering ? Color.green : Color.gray)
                        .frame(width: 8, height: 8)
                    
                    Text(discoveryService.isDiscovering ? "Discovering" : "Offline")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                // Connect with Code button
                Button(action: { showCodeConnection.toggle() }) {
                    Label("Connect with Code", systemImage: "number.square")
                }
                .buttonStyle(LiquidGlassButtonStyle())
                .sheet(isPresented: $showCodeConnection) {
                    CodeConnectionView(discoveryService: discoveryService, isPresented: $showCodeConnection)
                }
                
                // QR Code button
                Button(action: { showQRCode.toggle() }) {
                    Label(showQRCode ? "Hide QR" : "Show QR", systemImage: "qrcode")
                }
                .buttonStyle(LiquidGlassButtonStyle())
            }
        }
        .liquidGlassStyle()
    }
    
    private var qrCodeSection: some View {
        QRCodeView(payload: QRCodePayload(
            deviceId: UserDefaults.standard.string(forKey: "com.blink.deviceId") ?? UUID().uuidString,
            deviceName: Host.current().localizedName ?? "Mac",
            ipAddress: getLocalIPAddress(),
            port: Int(NetworkConstants.transferPort),
            timestamp: Date().timeIntervalSince1970
        ))
        .transition(.scale.combined(with: .opacity))
        .animation(.liquidGlass, value: showQRCode)
    }
    
    private var sendButton: some View {
        Button(action: sendFiles) {
            HStack {
                Image(systemName: "paperplane.fill")
                Text("Send to \(selectedDevice?.name ?? "Device")")
            }
            .frame(maxWidth: .infinity)
            .padding()
        }
        .buttonStyle(LiquidGlassButtonStyle())
        .disabled(selectedDevice == nil || selectedFiles.isEmpty)
    }
    
    private func startServices() {
        discoveryService.startDiscovery()
        socketManager.startTCPServer()
    }
    
    private func stopServices() {
        discoveryService.stopDiscovery()
        socketManager.stop()
    }
    
    private func sendFiles() {
        guard let device = selectedDevice else { return }
        
        Task {
            do {
                try await transferManager.sendFiles(selectedFiles, to: device)
                selectedFiles.removeAll()
            } catch {
                print("Transfer failed: \(error.localizedDescription)")
            }
        }
    }
    
    private func getLocalIPAddress() -> String {
        var address = "127.0.0.1"
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        
        guard getifaddrs(&ifaddr) == 0 else { return address }
        guard let firstAddr = ifaddr else { return address }
        
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
                
                // Return the first non-loopback IPv4 address found
                address = String(cString: hostname)
                break
            }
        }
        
        freeifaddrs(ifaddr)
        return address
    }
}

#Preview {
    ContentView()
        .frame(width: 1000, height: 700)
}
