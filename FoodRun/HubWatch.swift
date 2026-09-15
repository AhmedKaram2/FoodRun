import Foundation
import FoodRunShared

/// All mutable subscription state is confined to the main queue.
final class HubWatch: GroupSubscription {
    private let transport: PinnedHubSession
    private let body: String
    private let callback: GroupReplyCallback
    private var task: URLSessionWebSocketTask?
    private var retryWork: DispatchWorkItem?
    private var cancelled = false
    private var attempt = 0

    init(hub: HubPairing, body: String, callback: GroupReplyCallback) {
        transport = PinnedHubSession(hub: hub)
        self.body = body
        self.callback = callback
        connect()
    }

    private func connect() {
        guard !cancelled, task == nil else { return }
        guard let url = transport.endpoint("/events", websocket: true) else {
            callback.complete(body: "", error: "Check the saved hub address and pairing fingerprint.")
            return
        }
        let socket = transport.session.webSocketTask(with: url)
        task = socket
        socket.maximumMessageSize = PinnedHubSession.maximumBytes
        socket.resume()
        socket.send(.string(body)) { [weak self, weak socket] error in
            DispatchQueue.main.async {
                guard let self, let socket, !self.cancelled, self.task === socket else { return }
                if error == nil { self.receive(socket) } else { self.retry(socket) }
            }
        }
    }

    private func receive(_ socket: URLSessionWebSocketTask) {
        socket.receive { [weak self, weak socket] result in
            DispatchQueue.main.async {
                guard let self, let socket, !self.cancelled, self.task === socket else { return }
                switch result {
                case .success(let message):
                    guard case .string(let text) = message, text.utf8.count <= PinnedHubSession.maximumBytes else {
                        self.retry(socket)
                        return
                    }
                    self.attempt = 0
                    self.callback.complete(body: text, error: "")
                    if self.task === socket, !self.cancelled { self.receive(socket) }
                case .failure: self.retry(socket)
                }
            }
        }
    }

    private func retry(_ socket: URLSessionWebSocketTask) {
        guard !cancelled, task === socket else { return }
        socket.cancel(with: .goingAway, reason: nil)
        task = nil
        attempt = min(attempt + 1, 6)
        callback.complete(body: "", error: "Connection paused. Reconnecting to your local hub…")
        guard !cancelled else { return }
        let delay = min(30, pow(2, Double(attempt - 1))) + Double.random(in: 0...0.25)
        let work = DispatchWorkItem { [weak self] in self?.connect() }
        retryWork?.cancel()
        retryWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    func cancel() {
        cancelled = true
        retryWork?.cancel()
        retryWork = nil
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        transport.session.invalidateAndCancel()
    }

    deinit { retryWork?.cancel(); transport.session.invalidateAndCancel() }
}
