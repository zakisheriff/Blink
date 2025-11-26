//
//  SocketManager.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation
import Network
import Combine

// MARK: - Socket Manager
class SocketManager: ObservableObject {
    private var udpListener: NWListener?
    private var tcpListener: NWListener?
    private var connections: [UUID: NWConnection] = [:]
    private let queue = DispatchQueue(label: "com.blink.socket", qos: .userInitiated)
    
    @Published var isListening = false
    @Published var error: String?
    
    // Callbacks
    var onDiscoveryMessage: ((DiscoveryMessage, String) -> Void)?
    var onConnectionReceived: ((NWConnection) -> Void)?
    var onDataReceived: ((Data, UUID) -> Void)?
    
    // MARK: - UDP Broadcast
    func startUDPListener(port: UInt16 = NetworkConstants.discoveryPort) {
        let parameters = NWParameters.udp
        parameters.allowLocalEndpointReuse = true
        
        do {
            udpListener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
            
            udpListener?.newConnectionHandler = { [weak self] connection in
                self?.handleUDPConnection(connection)
            }
            
            udpListener?.stateUpdateHandler = { [weak self] state in
                DispatchQueue.main.async {
                    switch state {
                    case .ready:
                        self?.isListening = true
                        print("UDP listener ready on port \(port)")
                    case .failed(let error):
                        self?.error = "UDP listener failed: \(error.localizedDescription)"
                        self?.isListening = false
                    default:
                        break
                    }
                }
            }
            
            udpListener?.start(queue: queue)
        } catch {
            DispatchQueue.main.async {
                self.error = "Failed to start UDP listener: \(error.localizedDescription)"
            }
        }
    }
    
    private func handleUDPConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        
        connection.receiveMessage { [weak self] data, context, isComplete, error in
            if let data = data, !data.isEmpty {
                self?.processUDPMessage(data, from: connection)
            }
            
            // Continue receiving
            if connection.state == .ready {
                self?.handleUDPConnection(connection)
            }
        }
    }
    
    private func processUDPMessage(_ data: Data, from connection: NWConnection) {
        guard let message = try? JSONDecoder().decode(DiscoveryMessage.self, from: data) else {
            return
        }
        
        // Extract IP address from connection
        if case .hostPort(let host, _) = connection.endpoint {
            let ipAddress = "\(host)"
            onDiscoveryMessage?(message, ipAddress)
        }
    }
    
    func sendUDPBroadcast(message: DiscoveryMessage, to address: String, port: UInt16 = NetworkConstants.discoveryPort) {
        guard let data = try? JSONEncoder().encode(message) else { return }
        
        let host = NWEndpoint.Host(address)
        let port = NWEndpoint.Port(rawValue: port)!
        let endpoint = NWEndpoint.hostPort(host: host, port: port)
        
        let connection = NWConnection(to: endpoint, using: .udp)
        connection.start(queue: queue)
        
        connection.send(content: data, completion: .contentProcessed { error in
            if let error = error {
                print("UDP send error: \(error.localizedDescription)")
            }
            connection.cancel()
        })
    }
    
    // MARK: - TCP Server
    func startTCPServer(port: UInt16 = NetworkConstants.transferPort) {
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        
        do {
            tcpListener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
            
            tcpListener?.newConnectionHandler = { [weak self] connection in
                print("New TCP connection received")
                self?.handleTCPConnection(connection)
            }
            
            tcpListener?.stateUpdateHandler = { [weak self] state in
                DispatchQueue.main.async {
                    switch state {
                    case .ready:
                        print("TCP server ready on port \(port)")
                    case .failed(let error):
                        self?.error = "TCP server failed: \(error.localizedDescription)"
                    default:
                        break
                    }
                }
            }
            
            tcpListener?.start(queue: queue)
        } catch {
            DispatchQueue.main.async {
                self.error = "Failed to start TCP server: \(error.localizedDescription)"
            }
        }
    }
    
    private func handleTCPConnection(_ connection: NWConnection) {
        let connectionId = UUID()
        queue.async { [weak self] in
            self?.connections[connectionId] = connection
        }
        
        connection.start(queue: queue)
        onConnectionReceived?(connection)
        
        receiveData(on: connection, id: connectionId)
    }
    
    private func receiveData(on connection: NWConnection, id: UUID) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: NetworkConstants.bufferSize) { [weak self] data, context, isComplete, error in
            if let data = data, !data.isEmpty {
                self?.onDataReceived?(data, id)
            }
            
            if let error = error {
                print("TCP receive error: \(error.localizedDescription)")
                self?.queue.async {
                    self?.connections.removeValue(forKey: id)
                }
                return
            }
            
            if isComplete {
                self?.queue.async {
                    self?.connections.removeValue(forKey: id)
                }
                return
            }
            
            // Continue receiving
            self?.receiveData(on: connection, id: id)
        }
    }
    
    // MARK: - TCP Client
    func connectToDevice(ipAddress: String, port: UInt16, completion: @escaping (NWConnection?, Error?) -> Void) {
        let host = NWEndpoint.Host(ipAddress)
        let port = NWEndpoint.Port(rawValue: port)!
        let endpoint = NWEndpoint.hostPort(host: host, port: port)
        
        let connection = NWConnection(to: endpoint, using: .tcp)
        let connectionId = UUID()
        
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.queue.async {
                    self?.connections[connectionId] = connection
                }
                completion(connection, nil)
            case .failed(let error):
                completion(nil, error)
            default:
                break
            }
        }
        
        connection.start(queue: queue)
        receiveData(on: connection, id: connectionId)
    }
    
    func sendData(_ data: Data, on connection: NWConnection, completion: @escaping (Error?) -> Void) {
        connection.send(content: data, completion: .contentProcessed { error in
            completion(error)
        })
    }
    
    // MARK: - Cleanup
    func stop() {
        udpListener?.cancel()
        tcpListener?.cancel()
        
        queue.async { [weak self] in
            guard let self = self else { return }
            for connection in self.connections.values {
                connection.cancel()
            }
            self.connections.removeAll()
        }
        
        DispatchQueue.main.async {
            self.isListening = false
        }
    }
    
    deinit {
        stop()
    }
}
