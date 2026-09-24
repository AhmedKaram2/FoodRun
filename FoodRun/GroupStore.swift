import SwiftUI
import FoodRunShared

final class GroupStore: ObservableObject {
    let controller: GroupController
    @Published var state: GroupState
    private let observer = WeakGroupObserver()

    init(platform: GroupPlatform = GroupIosPlatform()) {
        controller = GroupController(platform: platform)
        state = controller.state
        observer.store = self
        controller.observe(observer: observer)
        if let native = platform as? GroupIosPlatform {
            native.onNotification = { [weak self] id, action in self?.controller.openNotification(id: id, action: action) }
        }
    }
    func dispatch(_ action: GroupAction, _ value: String = "") { controller.dispatch(action: action, value: value) }
    deinit { controller.removeObserver(observer: observer); controller.close() }
}

private final class WeakGroupObserver: GroupObserver {
    weak var store: GroupStore?
    func changed(state: GroupState) {
        precondition(Thread.isMainThread, "Group UI state must be delivered on the main thread")
        if store?.state != state { store?.state = state }
    }
}
