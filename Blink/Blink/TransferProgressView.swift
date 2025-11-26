//
//  TransferProgressView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI

struct TransferProgressView: View {
    @ObservedObject var transfer: TransferProgress
    var onCancel: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // File info
            HStack(spacing: 12) {
                Image(systemName: transfer.item.isDirectory ? "folder.fill" : "doc.fill")
                    .font(.system(size: 32))
                    .foregroundColor(.liquidGlassAccent)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(transfer.item.name)
                        .font(.body)
                        .fontWeight(.medium)
                        .lineLimit(1)
                    
                    Text(transfer.item.size.formatFileSize())
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // Cancel button
                Button(action: onCancel) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
                .hoverEffect()
            }
            
            // Progress bar
            ProgressView(value: transfer.progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .liquidGlassAccent))
            
            // Stats
            HStack {
                // Speed
                if transfer.speed > 0 {
                    Label(transfer.speed.formatSpeed(), systemImage: "speedometer")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // ETA
                if let eta = transfer.eta {
                    Label(eta.formatETA(), systemImage: "clock")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                // Percentage
                Text("\(Int(transfer.progress * 100))%")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
            }
            
            // Status
            if let error = transfer.error {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.liquidGlassBackground)
        )
    }
}

struct TransferListView: View {
    @ObservedObject var transferManager: TransferManager
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Active Transfers")
                .font(.headline)
                .foregroundColor(.primary)
            
            if transferManager.activeTransfers.isEmpty {
                emptyStateView
            } else {
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(transferManager.activeTransfers) { transfer in
                            TransferProgressView(transfer: transfer) {
                                transferManager.cancelTransfer(transfer)
                            }
                        }
                    }
                }
            }
        }
        .liquidGlassStyle()
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: "arrow.up.arrow.down.circle")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            
            Text("No active transfers")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}
