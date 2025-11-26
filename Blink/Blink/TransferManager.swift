//
//  TransferManager.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation
import Network
import Combine
import CryptoKit

// MARK: - Transfer Manager
@MainActor
class TransferManager: ObservableObject {
    private let socketManager: SocketManager
    
    @Published var activeSessions: [TransferSession] = []
    @Published var activeTransfers: [TransferProgress] = []
    
    private var connections: [UUID: NWConnection] = [:]
    private var transferBuffers: [String: Data] = [:]
    private var transferSequences: [String: Int64] = [:]
    
    init(socketManager: SocketManager) {
        self.socketManager = socketManager
        setupCallbacks()
    }
    
    private func setupCallbacks() {
        socketManager.onDataReceived = { [weak self] data, connectionId in
            Task { @MainActor in
                self?.handleReceivedData(data, from: connectionId)
            }
        }
    }
    
    // MARK: - Send Files
    func sendFiles(_ items: [TransferItem], to device: Device) async throws {
        // Get or create session
        let session = getOrCreateSession(for: device)
        
        // Connect to device
        try await connectToDevice(device)
        
        // Create transfers
        for item in items {
            let transfer = TransferProgress(item: item, device: device)
            activeTransfers.append(transfer)
            session.addTransfer(transfer)
            
            // Start transfer
            Task {
                await self.performTransfer(transfer, to: device)
            }
        }
    }
    
    private func connectToDevice(_ device: Device) async throws {
        return try await withCheckedThrowingContinuation { continuation in
            socketManager.connectToDevice(ipAddress: device.ipAddress, port: UInt16(device.port)) { connection, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let connection = connection {
                    self.connections[device.id] = connection
                    continuation.resume()
                } else {
                    continuation.resume(throwing: NSError(domain: "TransferManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to connect"]))
                }
            }
        }
    }
    
    private func performTransfer(_ transfer: TransferProgress, to device: Device) async {
        guard let connection = connections[device.id] else {
            transfer.status = .failed
            transfer.error = "No connection to device"
            return
        }
        
        transfer.status = .transferring
        
        do {
            // Send transfer request
            try await sendTransferRequest(transfer, on: connection)
            
            // Wait for acceptance
            // In a full implementation, we'd wait for response
            try await Task.sleep(nanoseconds: 100_000_000) // 100ms
            
            // Send file data
            try await sendFileData(transfer, on: connection)
            
            transfer.status = .completed
        } catch {
            transfer.status = .failed
            transfer.error = error.localizedDescription
        }
    }
    
    private func sendTransferRequest(_ transfer: TransferProgress, on connection: NWConnection) async throws {
        let request = TransferRequest(
            transferId: transfer.id.uuidString,
            fileName: transfer.item.name,
            fileSize: transfer.item.size,
            isDirectory: transfer.item.isDirectory,
            checksum: nil
        )
        
        guard let requestData = try? JSONEncoder().encode(request) else {
            throw NSError(domain: "TransferManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode request"])
        }
        
        let message = ProtocolMessage(type: .transferRequest, payload: requestData)
        guard let messageData = message.toData() else {
            throw NSError(domain: "TransferManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode message"])
        }
        
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            socketManager.sendData(messageData, on: connection) { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }
    
    private func sendFileData(_ transfer: TransferProgress, on connection: NWConnection) async throws {
        let fileURL = transfer.item.url
        
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            throw NSError(domain: "TransferManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "File not found"])
        }
        
        let fileHandle = try FileHandle(forReadingFrom: fileURL)
        defer { try? fileHandle.close() }
        
        var sequenceNumber: Int64 = 0
        let bufferSize = NetworkConstants.bufferSize
        var totalSent: Int64 = 0
        let startTime = Date()
        
        while true {
            let data = fileHandle.readData(ofLength: bufferSize)
            
            if data.isEmpty {
                break
            }
            
            let isLast = data.count < bufferSize
            
            let packet = TransferDataPacket(
                transferId: transfer.id.uuidString,
                sequenceNumber: sequenceNumber,
                data: data,
                isLastPacket: isLast
            )
            
            let packetData = packet.toData()
            
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                socketManager.sendData(packetData, on: connection) { error in
                    if let error = error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume()
                    }
                }
            }
            
            totalSent += Int64(data.count)
            sequenceNumber += 1
            
            // Update progress
            transfer.bytesTransferred = totalSent
            
            // Calculate speed
            let elapsed = Date().timeIntervalSince(startTime)
            if elapsed > 0 {
                transfer.speed = Double(totalSent) / elapsed
            }
            
            if isLast {
                break
            }
        }
        
        // Send completion message
        let complete = TransferComplete(
            transferId: transfer.id.uuidString,
            success: true,
            checksum: nil,
            error: nil
        )
        
        if let completeData = try? JSONEncoder().encode(complete) {
            let message = ProtocolMessage(type: .transferComplete, payload: completeData)
            if let messageData = message.toData() {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    socketManager.sendData(messageData, on: connection) { error in
                        if let error = error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume()
                        }
                    }
                }
            }
        }
    }
    
    // MARK: - Receive Files
    private func handleReceivedData(_ data: Data, from connectionId: UUID) {
        // Try to parse as protocol message first
        if let message = ProtocolMessage.fromData(data) {
            handleProtocolMessage(message, from: connectionId)
        } else if let packet = TransferDataPacket.fromData(data) {
            handleDataPacket(packet)
        }
    }
    
    private func handleProtocolMessage(_ message: ProtocolMessage, from connectionId: UUID) {
        switch message.type {
        case .transferRequest:
            handleTransferRequest(message, from: connectionId)
        case .transferComplete:
            handleTransferComplete(message)
        default:
            break
        }
    }
    
    private func handleTransferRequest(_ message: ProtocolMessage, from connectionId: UUID) {
        guard let payload = message.payload,
              let request = try? JSONDecoder().decode(TransferRequest.self, from: payload) else {
            return
        }
        
        // Auto-accept for now (in real app, show user prompt)
        let response = TransferResponse(
            transferId: request.transferId,
            accepted: true,
            reason: nil
        )
        
        if let responseData = try? JSONEncoder().encode(response),
           let responseMessage = ProtocolMessage(type: .transferResponse, payload: responseData).toData(),
           let connection = connections.values.first(where: { _ in true }) {
            socketManager.sendData(responseMessage, on: connection) { _ in }
        }
        
        // Initialize transfer buffer
        transferBuffers[request.transferId] = Data()
        transferSequences[request.transferId] = 0
    }
    
    private func handleDataPacket(_ packet: TransferDataPacket) {
        // Append data to buffer
        if transferBuffers[packet.transferId] == nil {
            transferBuffers[packet.transferId] = Data()
        }
        
        transferBuffers[packet.transferId]?.append(packet.data)
        
        if packet.isLastPacket {
            // Save file
            saveReceivedFile(transferId: packet.transferId)
        }
    }
    
    private func saveReceivedFile(transferId: String) {
        guard let data = transferBuffers[transferId] else { return }
        
        // Save to Downloads folder
        let downloadsURL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        let fileURL = downloadsURL.appendingPathComponent("received_\(transferId)")
        
        do {
            try data.write(to: fileURL)
            print("File saved to: \(fileURL.path)")
        } catch {
            print("Failed to save file: \(error.localizedDescription)")
        }
        
        // Cleanup
        transferBuffers.removeValue(forKey: transferId)
        transferSequences.removeValue(forKey: transferId)
    }
    
    private func handleTransferComplete(_ message: ProtocolMessage) {
        guard let payload = message.payload,
              let complete = try? JSONDecoder().decode(TransferComplete.self, from: payload) else {
            return
        }
        
        print("Transfer completed: \(complete.transferId), success: \(complete.success)")
    }
    
    // MARK: - Session Management
    private func getOrCreateSession(for device: Device) -> TransferSession {
        if let session = activeSessions.first(where: { $0.device.id == device.id }) {
            return session
        }
        
        let session = TransferSession(device: device)
        activeSessions.append(session)
        return session
    }
    
    func cancelTransfer(_ transfer: TransferProgress) {
        transfer.status = .cancelled
    }
}
