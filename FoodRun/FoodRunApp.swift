import SwiftUI
import FirebaseCore
import FirebaseMessaging

final class FoodRunAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        return true
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
        NotificationCenter.default.post(name: .init("FoodRunAPNsReady"), object: nil)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NotificationCenter.default.post(name: .init("FoodRunAPNsFailed"), object: nil)
    }
}


@main
struct FoodRunApp: App {
    @UIApplicationDelegateAdaptor(FoodRunAppDelegate.self) private var appDelegate
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
