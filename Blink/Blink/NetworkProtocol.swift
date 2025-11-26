//
//  NetworkProtocol.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation

// MARK: - Network Protocol Constants
enum NetworkConstants {
    static let discoveryPort: UInt16 = 8888
    static let transferPort: UInt16 = 8889
    static let broadcastInterval: TimeInterval = 2.0
    static let deviceTimeout: TimeInterval = 10.0
    static let bufferSize: Int = 65536 // 64KB chunks
    static let protocolVersion: String = "1.0"
}

// MARK: - Message Types
enum MessageType: String, Codable {
    case discovery = "DISCOVERY"
    case discoveryResponse = "DISCOVERY_RESPONSE"
    case pairRequest = "PAIR_REQUEST"
    case pairResponse = "PAIR_RESPONSE"
    case transferRequest = "TRANSFER_REQUEST"
    case transferResponse = "TRANSFER_RESPONSE"
    case transferData = "TRANSFER_DATA"
    case transferComplete = "TRANSFER_COMPLETE"
    case transferError = "TRANSFER_ERROR"
    case progress = "PROGRESS"
    case ping = "PING"
    case pong = "PONG"
}

// MARK: - Protocol Message
struct ProtocolMessage: Codable {
    let type: MessageType
    let timestamp: TimeInterval
    let payload: Data?
    
    init(type: MessageType, payload: Data? = nil) {
        self.type = type
        self.timestamp = Date().timeIntervalSince1970
        self.payload = payload
    }
    
    func toData() -> Data? {
        return try? JSONEncoder().encode(self)
    }
    
    static func fromData(_ data: Data) -> ProtocolMessage? {
        return try? JSONDecoder().decode(ProtocolMessage.self, from: data)
    }
}

// MARK: - Discovery Message
struct DiscoveryMessage: Codable {
    let deviceId: String
    let deviceName: String
    let deviceType: String
    let port: Int
    let protocolVersion: String
    let code: String?
    let type: String // "presence", "query", "response"
    
    init(deviceId: String, deviceName: String, deviceType: String = "macOS", port: Int, code: String? = nil, type: String = "presence") {
        self.deviceId = deviceId
        self.deviceName = deviceName
        self.deviceType = deviceType
        self.port = port
        self.protocolVersion = NetworkConstants.protocolVersion
        self.code = code
        self.type = type
    }
}

// MARK: - Pair Request
struct PairRequest: Codable {
    let deviceId: String
    let deviceName: String
    let timestamp: TimeInterval
}

// MARK: - Pair Response
struct PairResponse: Codable {
    let accepted: Bool
    let deviceId: String
    let deviceName: String
    let sessionToken: String?
}

// MARK: - Transfer Request
struct TransferRequest: Codable {
    let transferId: String
    let fileName: String
    let fileSize: Int64
    let isDirectory: Bool
    let checksum: String?
}

// MARK: - Transfer Response
struct TransferResponse: Codable {
    let transferId: String
    let accepted: Bool
    let reason: String?
}

// MARK: - Transfer Data Packet
struct TransferDataPacket {
    let transferId: String
    let sequenceNumber: Int64
    let data: Data
    let isLastPacket: Bool
    
    func toData() -> Data {
        var result = Data()
        
        // Transfer ID (36 bytes for UUID string)
        if let idData = transferId.data(using: .utf8) {
            var idBytes = [UInt8](repeating: 0, count: 36)
            let copyCount = min(idData.count, 36)
            idData.copyBytes(to: &idBytes, count: copyCount)
            result.append(contentsOf: idBytes)
        }
        
        // Sequence number (8 bytes)
        var seq = sequenceNumber.bigEndian
        result.append(Data(bytes: &seq, count: 8))
        
        // Is last packet flag (1 byte)
        result.append(isLastPacket ? 1 : 0)
        
        // Data length (4 bytes)
        var length = UInt32(data.count).bigEndian
        result.append(Data(bytes: &length, count: 4))
        
        // Actual data
        result.append(data)
        
        return result
    }
    
    static func fromData(_ data: Data) -> TransferDataPacket? {
        guard data.count >= 49 else { return nil } // Minimum size
        
        var offset = 0
        
        // Transfer ID
        let idData = data.subdata(in: offset..<(offset + 36))
        guard let transferId = String(data: idData, encoding: .utf8)?.trimmingCharacters(in: .controlCharacters) else {
            return nil
        }
        offset += 36
        
        // Sequence number
        let seqData = data.subdata(in: offset..<(offset + 8))
        let sequenceNumber = seqData.withUnsafeBytes { $0.load(as: Int64.self) }.bigEndian
        offset += 8
        
        // Is last packet
        let isLastPacket = data[offset] == 1
        offset += 1
        
        // Data length
        let lengthData = data.subdata(in: offset..<(offset + 4))
        let length = lengthData.withUnsafeBytes { $0.load(as: UInt32.self) }.bigEndian
        offset += 4
        
        // Actual data
        guard data.count >= offset + Int(length) else { return nil }
        let packetData = data.subdata(in: offset..<(offset + Int(length)))
        
        return TransferDataPacket(
            transferId: transferId,
            sequenceNumber: sequenceNumber,
            data: packetData,
            isLastPacket: isLastPacket
        )
    }
}

// MARK: - Progress Update
struct ProgressUpdate: Codable {
    let transferId: String
    let bytesTransferred: Int64
    let totalBytes: Int64
    let speed: Double
}

// MARK: - Transfer Complete
struct TransferComplete: Codable {
    let transferId: String
    let success: Bool
    let checksum: String?
    let error: String?
}
