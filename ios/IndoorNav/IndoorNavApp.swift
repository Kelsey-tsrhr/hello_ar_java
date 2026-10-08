import SwiftUI

@main
struct IndoorNavApp: App {
    @StateObject private var navigation = NavigationViewModel()

    var body: some Scene {
        WindowGroup {
            NavigationScreen(navigation: navigation)
                .preferredColorScheme(.dark)
        }
    }
}
