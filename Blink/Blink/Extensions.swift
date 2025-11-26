//
//  Extensions.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import Foundation
import SwiftUI

// MARK: - File Size Formatting
extension Int64 {
    func formatFileSize() -> String {
        let bytes = Double(self)
        
        if bytes < 1024 {
            return "\(self) B"
        } else if bytes < 1024 * 1024 {
            return String(format: "%.1f KB", bytes / 1024)
        } else if bytes < 1024 * 1024 * 1024 {
            return String(format: "%.1f MB", bytes / (1024 * 1024))
        } else {
            return String(format: "%.2f GB", bytes / (1024 * 1024 * 1024))
        }
    }
}

// MARK: - Speed Formatting
extension Double {
    func formatSpeed() -> String {
        if self < 1024 {
            return String(format: "%.0f B/s", self)
        } else if self < 1024 * 1024 {
            return String(format: "%.1f KB/s", self / 1024)
        } else if self < 1024 * 1024 * 1024 {
            return String(format: "%.1f MB/s", self / (1024 * 1024))
        } else {
            return String(format: "%.2f GB/s", self / (1024 * 1024 * 1024))
        }
    }
}

// MARK: - Time Interval Formatting
extension TimeInterval {
    func formatETA() -> String {
        let seconds = Int(self)
        
        if seconds < 60 {
            return "\(seconds)s"
        } else if seconds < 3600 {
            let minutes = seconds / 60
            let secs = seconds % 60
            return "\(minutes)m \(secs)s"
        } else {
            let hours = seconds / 3600
            let minutes = (seconds % 3600) / 60
            return "\(hours)h \(minutes)m"
        }
    }
}

// MARK: - Liquid Glass Colors
extension Color {
    static let liquidGlassBackground = Color(nsColor: .windowBackgroundColor).opacity(0.3)
    static let liquidGlassAccent = Color.accentColor.opacity(0.8)
    static let liquidGlassBorder = Color.white.opacity(0.2)
    
    // Gradient colors for liquid glass effect
    static let liquidGlassGradientStart = Color.white.opacity(0.1)
    static let liquidGlassGradientEnd = Color.white.opacity(0.05)
}

// MARK: - Liquid Glass View Modifier
struct LiquidGlassStyle: ViewModifier {
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 16
    
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(
                ZStack {
                    // Base blur
                    VisualEffectBlur(material: .hudWindow, blendingMode: .behindWindow)
                    
                    // Gradient overlay
                    LinearGradient(
                        colors: [
                            Color.liquidGlassGradientStart,
                            Color.liquidGlassGradientEnd
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.liquidGlassBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 5)
    }
}

extension View {
    func liquidGlassStyle(cornerRadius: CGFloat = 16, padding: CGFloat = 16) -> some View {
        modifier(LiquidGlassStyle(cornerRadius: cornerRadius, padding: padding))
    }
}

// MARK: - Visual Effect Blur (macOS)
struct VisualEffectBlur: NSViewRepresentable {
    var material: NSVisualEffectView.Material
    var blendingMode: NSVisualEffectView.BlendingMode
    
    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = .active
        return view
    }
    
    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
    }
}

// MARK: - Smooth Animation
extension Animation {
    static let liquidGlass = Animation.spring(response: 0.4, dampingFraction: 0.8)
}

// MARK: - Hover Effect Modifier
struct HoverEffectModifier: ViewModifier {
    @State private var isHovered = false
    
    func body(content: Content) -> some View {
        content
            .scaleEffect(isHovered ? 1.02 : 1.0)
            .animation(.liquidGlass, value: isHovered)
            .onHover { hovering in
                isHovered = hovering
            }
    }
}

extension View {
    func hoverEffect() -> some View {
        modifier(HoverEffectModifier())
    }
}
