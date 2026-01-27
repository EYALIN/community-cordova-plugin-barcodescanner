/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

import UIKit
import AVFoundation
import MLKitBarcodeScanning
import MLKitVision

@objc public protocol BarcodeScannerDelegate: AnyObject {
    func barcodeScannerDidCancel()
    func barcodeScannerDidFailWithError(_ error: String)
    func barcodeScannerDidScanBarcode(_ value: String, format: String, type: String)
}

@objc public class BarcodeScannerViewController: UIViewController {

    // MARK: - Public Properties (accessible from Obj-C)
    @objc public weak var delegate: BarcodeScannerDelegate?
    @objc public var showTorchButton: Bool = true
    @objc public var promptText: String = "Point the camera at a barcode"
    @objc public var beepOnSuccess: Bool = true
    @objc public var vibrateOnSuccess: Bool = true
    @objc public var formats: String = ""

    // MARK: - Private Properties
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var barcodeScanner: BarcodeScanner?
    private var isScanning = true

    // UI Elements
    private let overlayView = UIView()
    private let scanAreaView = UIView()
    private let promptLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    private let torchButton = UIButton(type: .system)

    // MARK: - Lifecycle

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupBarcodeScanner()
        setupCamera()
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)

        if captureSession?.isRunning == false {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.startRunning()
            }
        }
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)

        if captureSession?.isRunning == true {
            captureSession?.stopRunning()
        }
    }

    public override var prefersStatusBarHidden: Bool {
        return true
    }

    // MARK: - Setup

    private func setupUI() {
        view.backgroundColor = .black

        // Scan area
        let scanSize: CGFloat = min(view.bounds.width, view.bounds.height) * 0.7
        scanAreaView.frame = CGRect(
            x: (view.bounds.width - scanSize) / 2,
            y: (view.bounds.height - scanSize) / 2,
            width: scanSize,
            height: scanSize
        )
        scanAreaView.backgroundColor = .clear
        scanAreaView.layer.borderColor = UIColor.white.withAlphaComponent(0.3).cgColor
        scanAreaView.layer.borderWidth = 2
        scanAreaView.layer.cornerRadius = 16

        // Corner indicators
        addCornerIndicators(to: scanAreaView)

        // Close button
        closeButton.frame = CGRect(x: 20, y: 50, width: 44, height: 44)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)

        // Torch button
        torchButton.frame = CGRect(x: view.bounds.width - 64, y: 50, width: 44, height: 44)
        torchButton.setImage(UIImage(systemName: "flashlight.off.fill"), for: .normal)
        torchButton.tintColor = .white
        torchButton.alpha = 0.6
        torchButton.addTarget(self, action: #selector(torchButtonTapped), for: .touchUpInside)
        torchButton.isHidden = !showTorchButton

        // Prompt label
        promptLabel.text = promptText
        promptLabel.textColor = .white
        promptLabel.textAlignment = .center
        promptLabel.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        promptLabel.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        promptLabel.layer.cornerRadius = 8
        promptLabel.clipsToBounds = true

        let labelWidth: CGFloat = view.bounds.width - 48
        let labelHeight: CGFloat = 44
        promptLabel.frame = CGRect(
            x: (view.bounds.width - labelWidth) / 2,
            y: scanAreaView.frame.maxY + 24,
            width: labelWidth,
            height: labelHeight
        )

        // Add overlay views
        createOverlayMask()

        view.addSubview(overlayView)
        view.addSubview(scanAreaView)
        view.addSubview(promptLabel)
        view.addSubview(closeButton)
        view.addSubview(torchButton)
    }

    private func addCornerIndicators(to containerView: UIView) {
        let cornerLength: CGFloat = 40
        let cornerWidth: CGFloat = 4
        let cornerColor = UIColor.white

        let corners: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
            // Top-left horizontal
            (0, 0, cornerLength, cornerWidth),
            // Top-left vertical
            (0, 0, cornerWidth, cornerLength),
            // Top-right horizontal
            (containerView.bounds.width - cornerLength, 0, cornerLength, cornerWidth),
            // Top-right vertical
            (containerView.bounds.width - cornerWidth, 0, cornerWidth, cornerLength),
            // Bottom-left horizontal
            (0, containerView.bounds.height - cornerWidth, cornerLength, cornerWidth),
            // Bottom-left vertical
            (0, containerView.bounds.height - cornerLength, cornerWidth, cornerLength),
            // Bottom-right horizontal
            (containerView.bounds.width - cornerLength, containerView.bounds.height - cornerWidth, cornerLength, cornerWidth),
            // Bottom-right vertical
            (containerView.bounds.width - cornerWidth, containerView.bounds.height - cornerLength, cornerWidth, cornerLength)
        ]

        for corner in corners {
            let cornerView = UIView(frame: CGRect(x: corner.0, y: corner.1, width: corner.2, height: corner.3))
            cornerView.backgroundColor = cornerColor
            containerView.addSubview(cornerView)
        }
    }

    private func createOverlayMask() {
        overlayView.frame = view.bounds
        overlayView.backgroundColor = UIColor.black.withAlphaComponent(0.5)

        let maskLayer = CAShapeLayer()
        let path = UIBezierPath(rect: view.bounds)

        let holePath = UIBezierPath(roundedRect: scanAreaView.frame, cornerRadius: 16)
        path.append(holePath)

        maskLayer.path = path.cgPath
        maskLayer.fillRule = .evenOdd
        overlayView.layer.mask = maskLayer
    }

    private func setupBarcodeScanner() {
        var barcodeFormats: BarcodeFormat = .all

        if !formats.isEmpty {
            barcodeFormats = parseFormats(formats)
        }

        let options = BarcodeScannerOptions(formats: barcodeFormats)
        barcodeScanner = BarcodeScanner.barcodeScanner(options: options)
    }

    private func parseFormats(_ formatsString: String) -> BarcodeFormat {
        var result: BarcodeFormat = []
        let formatArray = formatsString.uppercased().components(separatedBy: ",")

        for format in formatArray {
            let trimmed = format.trimmingCharacters(in: .whitespaces)
            switch trimmed {
            case "QR_CODE":
                result.insert(.qrCode)
            case "DATA_MATRIX":
                result.insert(.dataMatrix)
            case "UPC_A":
                result.insert(.UPCA)
            case "UPC_E":
                result.insert(.UPCE)
            case "EAN_8":
                result.insert(.EAN8)
            case "EAN_13":
                result.insert(.EAN13)
            case "CODE_39":
                result.insert(.code39)
            case "CODE_93":
                result.insert(.code93)
            case "CODE_128":
                result.insert(.code128)
            case "CODABAR":
                result.insert(.codaBar)
            case "ITF":
                result.insert(.ITF)
            case "PDF_417", "PDF417":
                result.insert(.PDF417)
            case "AZTEC":
                result.insert(.aztec)
            default:
                break
            }
        }

        return result.isEmpty ? .all : result
    }

    private func setupCamera() {
        captureSession = AVCaptureSession()
        captureSession?.sessionPreset = .high

        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
            delegate?.barcodeScannerDidFailWithError("No camera available")
            return
        }

        do {
            let videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)

            if captureSession?.canAddInput(videoInput) == true {
                captureSession?.addInput(videoInput)
            } else {
                delegate?.barcodeScannerDidFailWithError("Failed to add camera input")
                return
            }

            let videoOutput = AVCaptureVideoDataOutput()
            videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "barcodeScannerQueue"))
            videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]

            if captureSession?.canAddOutput(videoOutput) == true {
                captureSession?.addOutput(videoOutput)
            } else {
                delegate?.barcodeScannerDidFailWithError("Failed to add video output")
                return
            }

            previewLayer = AVCaptureVideoPreviewLayer(session: captureSession!)
            previewLayer?.frame = view.layer.bounds
            previewLayer?.videoGravity = .resizeAspectFill
            view.layer.insertSublayer(previewLayer!, at: 0)

            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.startRunning()
            }

        } catch {
            delegate?.barcodeScannerDidFailWithError("Failed to setup camera: \(error.localizedDescription)")
        }
    }

    // MARK: - Actions

    @objc private func closeButtonTapped() {
        captureSession?.stopRunning()
        delegate?.barcodeScannerDidCancel()
    }

    @objc private func torchButtonTapped() {
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            if device.torchMode == .on {
                device.torchMode = .off
                torchButton.alpha = 0.6
                torchButton.setImage(UIImage(systemName: "flashlight.off.fill"), for: .normal)
            } else {
                try device.setTorchModeOn(level: 1.0)
                torchButton.alpha = 1.0
                torchButton.setImage(UIImage(systemName: "flashlight.on.fill"), for: .normal)
            }
            device.unlockForConfiguration()
        } catch {
            print("Torch error: \(error)")
        }
    }

    // MARK: - Barcode Processing

    private func processBarcode(_ barcode: Barcode) {
        guard isScanning else { return }
        isScanning = false

        DispatchQueue.main.async { [weak self] in
            self?.captureSession?.stopRunning()

            if self?.beepOnSuccess == true {
                self?.playBeep()
            }
            if self?.vibrateOnSuccess == true {
                self?.vibrate()
            }

            let format = self?.getFormatName(barcode.format) ?? "UNKNOWN"
            let type = self?.getTypeName(barcode.valueType) ?? "TEXT"

            self?.delegate?.barcodeScannerDidScanBarcode(barcode.rawValue ?? "", format: format, type: type)
        }
    }

    private func getFormatName(_ format: BarcodeFormat) -> String {
        switch format {
        case .qrCode:
            return "QR_CODE"
        case .dataMatrix:
            return "DATA_MATRIX"
        case .UPCA:
            return "UPC_A"
        case .UPCE:
            return "UPC_E"
        case .EAN8:
            return "EAN_8"
        case .EAN13:
            return "EAN_13"
        case .code39:
            return "CODE_39"
        case .code93:
            return "CODE_93"
        case .code128:
            return "CODE_128"
        case .codaBar:
            return "CODABAR"
        case .ITF:
            return "ITF"
        case .PDF417:
            return "PDF_417"
        case .aztec:
            return "AZTEC"
        default:
            return "UNKNOWN"
        }
    }

    private func getTypeName(_ type: BarcodeValueType) -> String {
        switch type {
        case .URL:
            return "URL"
        case .email:
            return "EMAIL"
        case .phone:
            return "PHONE"
        case .SMS:
            return "SMS"
        case .wiFi:
            return "WIFI"
        case .geographicCoordinates:
            return "GEO"
        case .contactInfo:
            return "CONTACT"
        case .calendarEvent:
            return "CALENDAR"
        case .driversLicense:
            return "DRIVER_LICENSE"
        case .ISBN:
            return "ISBN"
        case .product:
            return "PRODUCT"
        case .text:
            return "TEXT"
        default:
            return "TEXT"
        }
    }

    private func playBeep() {
        AudioServicesPlaySystemSound(1057)
    }

    private func vibrate() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate

extension BarcodeScannerViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard isScanning else { return }

        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let image = VisionImage(buffer: sampleBuffer)
        image.orientation = imageOrientation()

        barcodeScanner?.process(image) { [weak self] barcodes, error in
            guard error == nil, let barcodes = barcodes, !barcodes.isEmpty else { return }

            if let barcode = barcodes.first {
                self?.processBarcode(barcode)
            }
        }
    }

    private func imageOrientation() -> UIImage.Orientation {
        let deviceOrientation = UIDevice.current.orientation
        switch deviceOrientation {
        case .portrait:
            return .right
        case .landscapeLeft:
            return .up
        case .landscapeRight:
            return .down
        case .portraitUpsideDown:
            return .left
        default:
            return .right
        }
    }
}
