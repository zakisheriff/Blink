//
//  DeviceListView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI

struct DeviceListView: View {
    @ObservedObject var discoveryService: DiscoveryService
    var onDeviceSelected: (Device) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nearby Devices")
                .font(.headline)
                .foregroundColor(.primary)
            
            if discoveryService.discoveredDevices.isEmpty {
                emptyStateView
            } else {
                deviceList
            }
        }
        .liquidGlassStyle()
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            
            Text("Searching for devices...")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
    
    private var deviceList: some View {
        VStack(spacing: 8) {
            ForEach(discoveryService.discoveredDevices) { device in
                DeviceRow(device: device)
                    .hoverEffect()
                    .onTapGesture {
                        onDeviceSelected(device)
                    }
            }
        }
    }
}

struct DeviceRow: View {
    let device: Device
    
    var body: some View {
        HStack(spacing: 12) {
            // Device icon
            ZStack {
                Circle()
                    .fill(Color.liquidGlassAccent)
                    .frame(width: 40, height: 40)
                
                Image(systemName: deviceIcon)
                    .font(.system(size: 20))
                    .foregroundColor(.white)
            }
            
            // Device info
            VStack(alignment: .leading, spacing: 4) {
                Text(device.name)
                    .font(.body)
                    .fontWeight(.medium)
                
                Text(device.ipAddress)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            // Status indicator
            Circle()
                .fill(device.isConnected ? Color.green : Color.orange)
                .frame(width: 8, height: 8)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.liquidGlassBackground)
        )
    }
    
    private var deviceIcon: String {
        switch device.deviceType {
        case .android:
            return "smartphone"
        case .mac:
            return "laptopcomputer"
        case .unknown:
            return "questionmark.circle"
        }
    }
}
