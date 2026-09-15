import SwiftUI

@main
struct FoodRunApp: App {
    @State private var store = WheelStore()
    @StateObject private var groups = GroupStore()

    var body: some Scene {
        WindowGroup {
            GroupScreen(store: groups, wheelStore: store)
                .preferredColorScheme(.light)
                .tint(FoodTheme.orange)
        }
    }
}
