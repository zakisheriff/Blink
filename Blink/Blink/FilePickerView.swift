//
//  FilePickerView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI
import UniformTypeIdentifiers

struct FilePickerView: View {
    @Binding var selectedFiles: [TransferItem]
    @State private var isTargeted = false
    
    var body: some View {
        VStack(spacing: 16) {
            // Drop zone
            dropZone
            
            // Selected files list
            if !selectedFiles.isEmpty {
                selectedFilesList
            }
            
            // Action buttons
            HStack(spacing: 12) {
                Button(action: selectFiles) {
                    Label("Choose Files", systemImage: "doc.badge.plus")
                }
                .buttonStyle(LiquidGlassButtonStyle())
                
                Button(action: selectFolder) {
                    Label("Choose Folder", systemImage: "folder.badge.plus")
                }
                .buttonStyle(LiquidGlassButtonStyle())
                
                if !selectedFiles.isEmpty {
                    Button(action: clearSelection) {
                        Label("Clear", systemImage: "trash")
                    }
                    .buttonStyle(LiquidGlassButtonStyle(isDestructive: true))
                }
            }
        }
        .liquidGlassStyle()
    }
    
    private var dropZone: some View {
        VStack(spacing: 16) {
            Image(systemName: "arrow.down.doc")
                .font(.system(size: 48))
                .foregroundColor(isTargeted ? .liquidGlassAccent : .secondary)
            
            Text("Drop files or folders here")
                .font(.headline)
                .foregroundColor(.primary)
            
            Text("or use the buttons below")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 200)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(
                    isTargeted ? Color.liquidGlassAccent : Color.liquidGlassBorder,
                    style: StrokeStyle(lineWidth: 2, dash: [10, 5])
                )
        )
        .onDrop(of: [.fileURL], isTargeted: $isTargeted) { providers in
            handleDrop(providers: providers)
            return true
        }
    }
    
    private var selectedFilesList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Selected Files (\(selectedFiles.count))")
                .font(.subheadline)
                .fontWeight(.medium)
            
            ScrollView {
                VStack(spacing: 6) {
                    ForEach(selectedFiles) { item in
                        HStack {
                            Image(systemName: item.isDirectory ? "folder.fill" : "doc.fill")
                                .foregroundColor(.liquidGlassAccent)
                            
                            Text(item.name)
                                .font(.caption)
                                .lineLimit(1)
                            
                            Spacer()
                            
                            Text(item.size.formatFileSize())
                                .font(.caption)
                                .foregroundColor(.secondary)
                            
                            Button(action: {
                                selectedFiles.removeAll { $0.id == item.id }
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.liquidGlassBackground)
                        )
                    }
                }
            }
            .frame(maxHeight: 150)
        }
    }
    
    private func selectFiles() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        
        if panel.runModal() == .OK {
            let items = panel.urls.map { TransferItem(url: $0) }
            selectedFiles.append(contentsOf: items)
        }
    }
    
    private func selectFolder() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        
        if panel.runModal() == .OK, let url = panel.url {
            let item = TransferItem(url: url)
            selectedFiles.append(item)
        }
    }
    
    private func handleDrop(providers: [NSItemProvider]) {
        for provider in providers {
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, error in
                if let data = item as? Data,
                   let url = URL(dataRepresentation: data, relativeTo: nil) {
                    DispatchQueue.main.async {
                        let transferItem = TransferItem(url: url)
                        if !selectedFiles.contains(where: { $0.url == url }) {
                            selectedFiles.append(transferItem)
                        }
                    }
                }
            }
        }
    }
    
    private func clearSelection() {
        selectedFiles.removeAll()
    }
}

// MARK: - Liquid Glass Button Style
struct LiquidGlassButtonStyle: ButtonStyle {
    var isDestructive: Bool = false
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                ZStack {
                    VisualEffectBlur(material: .hudWindow, blendingMode: .behindWindow)
                    
                    if isDestructive {
                        Color.red.opacity(0.2)
                    } else {
                        Color.liquidGlassAccent.opacity(configuration.isPressed ? 0.3 : 0.2)
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isDestructive ? Color.red.opacity(0.5) : Color.liquidGlassBorder, lineWidth: 1)
            )
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.liquidGlass, value: configuration.isPressed)
    }
}
