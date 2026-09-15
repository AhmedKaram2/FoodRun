import Foundation
import FoodRunShared

final class GroupDiscovery: NSObject, NetServiceBrowserDelegate, NetServiceDelegate {
    private var callback: GroupReplyCallback?
    private var browser: NetServiceBrowser?
    private var services: [NetService] = []
    private var timeout: DispatchWorkItem?

    func discover(_ callback: GroupReplyCallback) {
        finish("", "A new hub search was started.")
        self.callback = callback
        let browser = NetServiceBrowser()
        self.browser = browser
        browser.delegate = self
        browser.searchForServices(ofType: "_foodrun._tcp.", inDomain: "local.")
        let work = DispatchWorkItem { [weak self] in
            self?.finish("", "No hub found. Keep the computer awake or paste its pairing link.")
        }
        timeout = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 8, execute: work)
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        guard self.browser === browser, services.count < 32 else { return }
        services.append(service)
        service.delegate = self
        service.resolve(withTimeout: 5)
    }

    func netServiceDidResolveAddress(_ sender: NetService) {
        guard callback != nil, services.contains(where: { $0 === sender }),
              let host = sender.hostName, sender.port > 0 else { return }
        finish("https://\(host):\(sender.port)", "")
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        guard self.browser === browser else { return }
        finish("", "Local-network access is unavailable. Enable it in Settings, then retry.")
    }

    private func finish(_ body: String, _ error: String) {
        let completion = callback
        callback = nil
        stop()
        completion?.complete(body: body, error: error)
    }

    private func stop() {
        timeout?.cancel()
        timeout = nil
        browser?.delegate = nil
        browser?.stop()
        browser = nil
        services.forEach { $0.delegate = nil; $0.stop() }
        services = []
    }

    deinit { stop() }
}
