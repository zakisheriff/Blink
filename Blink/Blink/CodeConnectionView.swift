//
//  CodeConnectionView.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI

struct CodeConnectionView: View {
    @ObservedObject var discoveryService: DiscoveryService
    @Binding var isPresented: Bool
    
    @State private var selectedTab = 0
    @State private var generatedCode = ""
    @State private var enteredCode = ""
    
    var body: some View {
        VStack(spacing: 20) {
            Picker("Mode", selection: $selectedTab) {
                Text("Host").tag(0)
                Text("Join").tag(1)
            }
            .pickerStyle(SegmentedPickerStyle())
            .padding()
            
            if selectedTab == 0 {
                // Host Mode
                VStack(spacing: 16) {
                    Text("Your Connection Code")
                        .font(.headline)
                    
                    Text(generatedCode)
                        .font(.system(size: 48, weight: .bold, design: .monospaced))
                        .padding()
                        .background(Color.secondary.opacity(0.1))
                        .cornerRadius(12)
                        .onTapGesture {
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(generatedCode, forType: .string)
                        }
                    
                    Text("Enter this code on the other device")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .onAppear {
                    generateCode()
                }
            } else {
                // Join Mode
                VStack(spacing: 16) {
                    Text("Enter Connection Code")
                        .font(.headline)
                    
                    TextField("000000", text: $enteredCode)
                        .font(.system(size: 32, design: .monospaced))
                        .multilineTextAlignment(.center)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .frame(width: 200)
                    
                    Button("Connect") {
                        discoveryService.connect(with: enteredCode)
                        isPresented = false
                    }
                    .buttonStyle(LiquidGlassButtonStyle())
                    .disabled(enteredCode.count != 6)
                }
            }
            
            Spacer()
        }
        .frame(width: 300, height: 250)
        .padding()
    }
    
    private func generateCode() {
        let code = String(format: "%06d", Int.random(in: 0...999999))
        generatedCode = code
        discoveryService.startHosting(with: code)
    }
}
