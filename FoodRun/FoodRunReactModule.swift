import Foundation
import UIKit
import React
import FoodRunShared

final class FoodRunReactRuntime {
    static let shared = FoodRunReactRuntime()
    let platform = GroupIosPlatform()
    let groups: GroupController
    let wheel: FoodRunController
    let viewBridge: GroupReactBridge
    private init() {
        groups = GroupController(platform: platform)
        wheel = FoodRunController(storage: IosFoodRunStorage(defaults: .standard),timeZone: IosFoodRunTimeZone())
        viewBridge = GroupReactBridge(groups:groups,wheel:wheel)
        platform.onNotification = { [weak groups] id, action in groups?.openNotification(id:id,action:action) }
    }
}

@objc(FoodRun)
final class FoodRunReactModule: RCTEventEmitter, GroupObserver, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    private let runtime = FoodRunReactRuntime.shared
    private var listening = false
    private var observation: FoodRunObservation?
    private var photoResolve: RCTPromiseResolveBlock?
    private var photoReject: RCTPromiseRejectBlock?
    private var receiptPhoto = false
    override static func requiresMainQueueSetup() -> Bool { true }
    override var methodQueue: DispatchQueue! { DispatchQueue.main }
    override init() { super.init();runtime.groups.observe(observer:self);observation = runtime.wheel.observe { [weak self] _ in self?.emit() } }
    override func supportedEvents() -> [String]! { ["FoodRunState"] }
    override func startObserving() { listening = true;emit() }
    override func stopObserving() { listening = false }
    func changed(state: GroupState) { emit() }
    private func emit() { if listening { sendEvent(withName:"FoodRunState",body:runtime.viewBridge.snapshotJson()) } }
    @objc func getSnapshot(_ resolve: RCTPromiseResolveBlock,rejecter reject: RCTPromiseRejectBlock) { resolve(runtime.viewBridge.snapshotJson()) }
    @objc func dispatch(_ action: String,value: String) { runtime.viewBridge.dispatch(action:action,value:value) }
    @objc func update(_ key: String,value: String) { runtime.viewBridge.update(key:key,value:value) }
    @objc func tick() { runtime.viewBridge.tick() }
    @objc func quickAction(_ action: String,value: String) { runtime.viewBridge.quickAction(action:action,value:value) }
    @objc func dismissFeedback(_ id: Double) { runtime.groups.dismissFeedback(id:Int64(id)) }
    @objc func share(_ text: String) { runtime.platform.share(text:text,fileName:"") }
    @objc func copy(_ text: String) { runtime.platform.copyToClipboard(text:text) }
    @objc func photo(_ key: String,resolver resolve: @escaping RCTPromiseResolveBlock,rejecter reject: @escaping RCTPromiseRejectBlock) {
        guard ["PHOTO","ADMIN_PHOTO","RECEIPT_PHOTO"].contains(key),photoResolve == nil else { reject("PHOTO","Choose a profile or receipt photo.",nil);return }
        guard let window = UIApplication.shared.connectedScenes.compactMap({$0 as? UIWindowScene}).flatMap(\.windows).first(where: \.isKeyWindow),var presenter = window.rootViewController else { reject("PHOTO","Open the app before choosing a photo.",nil);return }
        while let shown = presenter.presentedViewController { presenter = shown }
        photoResolve = resolve;photoReject = reject;receiptPhoto = key == "RECEIPT_PHOTO"
        let picker = UIImagePickerController();picker.sourceType = .photoLibrary;picker.delegate = self;picker.allowsEditing = !receiptPhoto
        presenter.present(picker,animated:true)
    }
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { picker.dismiss(animated:true);photoResolve?("");photoResolve = nil;photoReject = nil }
    func imagePickerController(_ picker: UIImagePickerController,didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey:Any]) {
        picker.dismiss(animated:true)
        defer { photoResolve = nil;photoReject = nil }
        guard let image = (info[receiptPhoto ? .originalImage : .editedImage] ?? info[.originalImage]) as? UIImage else { photoReject?("PHOTO","This photo could not be opened.",nil);return }
        let limit:CGFloat = receiptPhoto ? 1200 : 320,scale = min(1,limit/max(image.size.width,image.size.height))
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let size = CGSize(width:max(1,image.size.width*scale),height:max(1,image.size.height*scale))
        let resized = UIGraphicsImageRenderer(size:size,format:format).image { _ in image.draw(in:CGRect(origin:.zero,size:size)) }
        var data = resized.jpegData(compressionQuality:0.78)
        if receiptPhoto && (data?.count ?? Int.max) > 440_000 { data = resized.jpegData(compressionQuality:0.5) }
        guard let data,data.count <= (receiptPhoto ? 440_000 : 128_000) else { photoReject?("PHOTO","Choose a smaller photo.",nil);return }
        photoResolve?("data:image/jpeg;base64,"+data.base64EncodedString())
    }
    override func invalidate() { runtime.groups.removeObserver(observer:self);observation?.cancel();observation = nil;super.invalidate() }
}
