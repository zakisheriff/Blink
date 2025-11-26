//
//  QRCodeView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI
import CoreImage.CIFilterBuiltins

struct QRCodeView: View {
    let payload: QRCodePayload
    @State private var qrImage: NSImage?
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Scan to Connect")
                .font(.headline)
            
            if let qrImage = qrImage {
                Image(nsImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 200, height: 200)
                    .padding()
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                ProgressView()
                    .frame(width: 200, height: 200)
            }
            
            VStack(alignment: .leading, spacing: 8) {
                InfoRow(label: "Device", value: payload.deviceName)
                InfoRow(label: "IP Address", value: payload.ipAddress)
                InfoRow(label: "Port", value: "\(payload.port)")
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.liquidGlassBackground)
            )
            
            Button(action: copyConnectionInfo) {
                Label("Copy Connection Info", systemImage: "doc.on.doc")
            }
            .buttonStyle(LiquidGlassButtonStyle())
        }
        .liquidGlassStyle()
        .onAppear {
            generateQRCode()
        }
    }
    
    private func generateQRCode() {
        guard let jsonString = payload.toJSON() else { return }
        
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        
        let data = Data(jsonString.utf8)
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("H", forKey: "inputCorrectionLevel")
        
        if let outputImage = filter.outputImage {
            let transform = CGAffineTransform(scaleX: 10, y: 10)
            let scaledImage = outputImage.transformed(by: transform)
            
            if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
                qrImage = NSImage(cgImage: cgImage, size: NSSize(width: 200, height: 200))
            }
        }
    }
    
    private func copyConnectionInfo() {
        let info = """
        Device: \(payload.deviceName)
        IP: \(payload.ipAddress)
        Port: \(payload.port)
        """
        
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(info, forType: .string)
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            
            Spacer()
            
            Text(value)
                .font(.caption)
                .fontWeight(.medium)
        }
    }
}
