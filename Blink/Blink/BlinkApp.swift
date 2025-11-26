//
//  BlinkApp.swift
//  Blink
//
//  Created by Zaki Sheriff on 2025-11-26.
//

import SwiftUI

@main
struct BlinkApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .frame(minWidth: 900, minHeight: 600)
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
    }
}
