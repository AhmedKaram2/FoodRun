import SwiftUI
import React
import GoogleSignIn
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
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ReactNativeScreen().ignoresSafeArea()
                .onOpenURL { url in
                    if !GIDSignIn.sharedInstance.handle(url) { RCTLinkingManager.application(UIApplication.shared,open:url,options:[:]) }
                }
                .onChange(of:scenePhase) { _,phase in
                    if phase == .active { FoodRunReactRuntime.shared.groups.foreground() } else { FoodRunReactRuntime.shared.groups.background() }
                }
                .preferredColorScheme(.light)
                .tint(FoodTheme.orange)
        }
    }
}
