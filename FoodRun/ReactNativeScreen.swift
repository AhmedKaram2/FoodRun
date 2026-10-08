import SwiftUI
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider

final class IntrviooReactDelegate: RCTDefaultReactNativeFactoryDelegate {
    override func sourceURL(for bridge: RCTBridge) -> URL? { bundleURL() }
    override func bundleURL() -> URL? {
        if let bundle = Bundle.main.url(forResource:"main",withExtension:"jsbundle") { return bundle }
        #if DEBUG
        return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot:"index")
        #else
        return nil
        #endif
    }
}
struct ReactNativeScreen: UIViewRepresentable {
    final class Coordinator {
        let delegate: IntrviooReactDelegate
        let factory: RCTReactNativeFactory
        init() { delegate = IntrviooReactDelegate();delegate.dependencyProvider = RCTAppDependencyProvider();factory = RCTReactNativeFactory(delegate:delegate) }
    }
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> UIView { context.coordinator.factory.rootViewFactory.view(withModuleName:"FoodRun",initialProperties:[:]) }
    func updateUIView(_ uiView: UIView,context: Context) {}
}
