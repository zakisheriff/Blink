//
//  Models.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation
import SwiftUI
import Combine

// MARK: - Device Model
struct Device: Identifiable, Codable, Hashable {
    let id: UUID
    var name: String
    var ipAddress: String
    var port: Int
    var deviceType: DeviceType
    var lastSeen: Date
    var isConnected: Bool
    
    enum DeviceType: String, Codable {
        case android
        case mac
        case unknown
    }
    
    init(id: UUID = UUID(), name: String, ipAddress: String, port: Int, deviceType: DeviceType = .android) {
        self.id = id
        self.name = name
        self.ipAddress = ipAddress
        self.port = port
        self.deviceType = deviceType
        self.lastSeen = Date()
        self.isConnected = false
    }
}

// MARK: - Transfer Item
struct TransferItem: Identifiable, Hashable {
    let id: UUID
    let url: URL
    let name: String
    let size: Int64
    let isDirectory: Bool
    
    init(id: UUID = UUID(), url: URL) {
        self.id = id
        self.url = url
        self.name = url.lastPathComponent
        
        // Get file size
        if let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
           let fileSize = attributes[.size] as? Int64 {
            self.size = fileSize
        } else {
            self.size = 0
        }
        
        // Check if directory
        var isDir: ObjCBool = false
        FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir)
        self.isDirectory = isDir.boolValue
    }
}

// MARK: - Transfer Progress
class TransferProgress: ObservableObject, Identifiable {
    let id: UUID
    let item: TransferItem
    let device: Device
    
    @Published var bytesTransferred: Int64 = 0
    @Published var totalBytes: Int64
    @Published var speed: Double = 0 // bytes per second
    @Published var status: TransferStatus = .pending
    @Published var error: String?
    
    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(bytesTransferred) / Double(totalBytes)
    }
    
    var eta: TimeInterval? {
        guard speed > 0, bytesTransferred < totalBytes else { return nil }
        let remaining = Double(totalBytes - bytesTransferred)
        return remaining / speed
    }
    
    enum TransferStatus: String {
        case pending
        case transferring
        case completed
        case failed
        case cancelled
    }
    
    init(id: UUID = UUID(), item: TransferItem, device: Device) {
        self.id = id
        self.item = item
        self.device = device
        self.totalBytes = item.size
    }
}

// MARK: - Transfer Session
class TransferSession: ObservableObject, Identifiable {
    let id: UUID
    let device: Device
    @Published var transfers: [TransferProgress] = []
    @Published var isActive: Bool = false
    
    init(id: UUID = UUID(), device: Device) {
        self.id = id
        self.device = device
    }
    
    func addTransfer(_ transfer: TransferProgress) {
        transfers.append(transfer)
    }
    
    var totalProgress: Double {
        guard !transfers.isEmpty else { return 0 }
        let total = transfers.reduce(0.0) { $0 + $1.progress }
        return total / Double(transfers.count)
    }
}

// MARK: - QR Code Payload
struct QRCodePayload: Codable {
    let deviceId: String
    let deviceName: String
    let ipAddress: String
    let port: Int
    let timestamp: TimeInterval
    
    func toJSON() -> String? {
        guard let data = try? JSONEncoder().encode(self),
              let json = String(data: data, encoding: .utf8) else {
            return nil
        }
        return json
    }
    
    static func fromJSON(_ json: String) -> QRCodePayload? {
        guard let data = json.data(using: .utf8),
              let payload = try? JSONDecoder().decode(QRCodePayload.self, from: data) else {
            return nil
        }
        return payload
    }
}
