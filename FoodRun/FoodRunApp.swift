import SwiftUI

@main
struct FoodRunApp: App {
    @State private var store = WheelStore()

    var body: some Scene {
        WindowGroup {
            ContentView(store: store)
                .preferredColorScheme(.light)
                .tint(FoodTheme.orange)
        }
    }
}
