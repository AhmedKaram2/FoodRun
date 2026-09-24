import Foundation
import CryptoKit
import Security
import FoodRunShared

final class PinnedHubSession: NSObject, URLSessionDataDelegate {
    static let maximumBytes = 4 * 1_024 * 1_024
    let hub: HubPairing
    private var responseData = Data()
    private var responseError = ""
    private var completion: ((String, String) -> Void)?
    lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 20
        config.httpCookieStorage = nil
        config.urlCache = nil
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    init(hub: HubPairing) { self.hub = hub }

    func endpoint(_ path: String, websocket: Bool = false) -> URL? {
        guard var components = URLComponents(string: hub.url), components.scheme == "https",
              let host = components.host, !host.isEmpty, components.user == nil, components.password == nil,
              components.port == nil || (1...65_535).contains(components.port!),
              components.query == nil, components.fragment == nil,
              components.path.isEmpty || components.path == "/",
              hub.fingerprint.isEmpty || hub.fingerprint.count == 64,
              hub.fingerprint.allSatisfy({ $0.isASCII && $0.isHexDigit }) else { return nil }
        components.scheme = websocket ? "wss" : "https"
        components.path = path
        return components.url
    }

    func command(_ body: String, path: String = "/command", completion: @escaping (String, String) -> Void) {
        guard let url = endpoint(path), body.utf8.count <= Self.maximumBytes else {
            completion("", "Check the hub pairing link and request size.")
            return
        }
        self.completion = completion
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = Data(body.utf8)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        session.dataTask(with: request).resume()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let response = response as? HTTPURLResponse, response.statusCode == 200,
              response.expectedContentLength <= Self.maximumBytes else {
            responseError = "The hub response could not be read. Retry shortly."
            completionHandler(.cancel)
            return
        }
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard responseData.count + data.count <= Self.maximumBytes else {
            responseError = "The hub response is too large. Ask the organizer to reduce the order."
            dataTask.cancel()
            return
        }
        responseData.append(data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let callback = completion else { return }
        completion = nil
        let text = String(data: responseData, encoding: .utf8)
        let message = !responseError.isEmpty ? responseError :
            (error != nil ? "Hub unavailable. Check Wi-Fi, computer, and pairing fingerprint." :
                (text == nil ? "The hub sent unreadable text. Retry shortly." : ""))
        DispatchQueue.main.async { callback(message.isEmpty ? text ?? "" : "", message) }
        session.finishTasksAndInvalidate()
    }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if hub.fingerprint.isEmpty {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              Self.normalizedHost(challenge.protectionSpace.host) == Self.normalizedHost(URL(string: hub.url)?.host ?? ""),
              challenge.protectionSpace.port == (URL(string: hub.url)?.port ?? 443),
              let trust = challenge.protectionSpace.serverTrust,
              let certificate = (SecTrustCopyCertificateChain(trust) as? [SecCertificate])?.first,
              Self.accepts(certificate: certificate, trust: trust, fingerprint: hub.fingerprint) else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    private static func normalizedHost(_ host: String) -> String {
        host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
    }

    static func accepts(certificate: SecCertificate, trust: SecTrust, fingerprint: String) -> Bool {
        let actual = SHA256.hash(data: SecCertificateCopyData(certificate) as Data)
            .map { String(format: "%02x", $0) }.joined()
        guard actual == fingerprint.lowercased() else { return false }
        // A verified QR pins this self-signed hub as the sole trust anchor.
        SecTrustSetPolicies(trust, SecPolicyCreateBasicX509())
        SecTrustSetAnchorCertificates(trust, [certificate] as CFArray)
        SecTrustSetAnchorCertificatesOnly(trust, true)
        SecTrustSetNetworkFetchAllowed(trust, false)
        return SecTrustEvaluateWithError(trust, nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        // Never redirect a room token to another endpoint, including the same host.
        completionHandler(nil)
    }
}
