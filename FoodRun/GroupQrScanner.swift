import UIKit
import AVFoundation
import FoodRunShared

final class GroupQrScanner: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let completion: (String?) -> Void
    private let capture = AVCaptureSession()
    private let captureQueue = DispatchQueue(label: "com.karim.foodrun.camera", qos: .userInitiated)
    private var preview: AVCaptureVideoPreviewLayer?
    private var completed = false

    init(completion: @escaping (String?) -> Void) {
        self.completion = completion
        super.init(nibName: nil, bundle: nil)
        // Cancellation is explicit so one callback always owns dismissal.
        isModalInPresentation = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(FoodTheme.ink)
        let close = UIButton(type: .system)
        close.setTitle(GroupText.shared.back, for: .normal)
        close.tintColor = UIColor(FoodTheme.white)
        close.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        close.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(close)
        NSLayoutConstraint.activate([
            close.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: FoodSpacing.s20),
            close.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: FoodSpacing.s12),
            close.heightAnchor.constraint(greaterThanOrEqualToConstant: FoodSpacing.s44),
            close.widthAnchor.constraint(greaterThanOrEqualToConstant: FoodSpacing.s74)
        ])
        AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
            DispatchQueue.main.async {
                guard let self, !self.completed else { return }
                if allowed { self.configure() } else { self.finish(nil) }
            }
        }
    }

    private func configure() {
        captureQueue.async { [weak self] in
            guard let self else { return }
            guard let camera = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: camera), self.capture.canAddInput(input) else {
                DispatchQueue.main.async { self.finish(nil) }
                return
            }
            self.capture.beginConfiguration()
            self.capture.addInput(input)
            let output = AVCaptureMetadataOutput()
            guard self.capture.canAddOutput(output) else {
                self.capture.commitConfiguration()
                DispatchQueue.main.async { self.finish(nil) }
                return
            }
            self.capture.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            self.capture.commitConfiguration()
            DispatchQueue.main.async {
                guard !self.completed else { return }
                let preview = AVCaptureVideoPreviewLayer(session: self.capture)
                preview.videoGravity = .resizeAspectFill
                preview.frame = self.view.bounds
                self.preview = preview
                self.view.layer.insertSublayer(preview, at: 0)
                self.captureQueue.async { self.capture.startRunning() }
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    @objc private func cancel() { finish(nil) }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        if let value = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue { finish(value) }
    }

    private func stopCamera() { captureQueue.async { [capture] in capture.stopRunning() } }

    private func finish(_ text: String?) {
        guard !completed else { return }
        completed = true
        stopCamera()
        dismiss(animated: true) { self.completion(text) }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        stopCamera()
        if !completed { completed = true; completion(nil) }
    }
}
