# Community Cordova BarcodeScanner Plugin

A modern Cordova plugin to scan barcodes and QR codes using **Google ML Kit**.

## Features

- **Powered by Google ML Kit** - Fast, accurate, and modern barcode scanning
- **Beautiful UI** - Clean scanning interface with corner indicators and overlay
- **Customizable** - Adjust scan area size, prompts, feedback, and more
- **Smart Detection** - Detects barcode value types (URL, Email, Phone, WiFi, etc.)
- **Haptic Feedback** - Beep and vibration on successful scan

## Installation

```bash
cordova plugin add community-cordova-plugin-barcodescanner
```

Or from GitHub:

```bash
cordova plugin add https://github.com/EYALIN/community-cordova-plugin-barcodescanner.git
```

## Requirements

- **Cordova** >= 10.0.0
- **cordova-android** >= 10.0.0
- **cordova-ios** >= 6.0.0
- **Android** SDK 21+ (Android 5.0+)
- **iOS** 13.0+

## Supported Platforms

- Android
- iOS
- Browser (limited support)

## Usage

### Basic Scan

```javascript
cordova.plugins.barcodeScanner.scan(
    function(result) {
        if (!result.cancelled) {
            console.log("Scanned: " + result.text);
            console.log("Format: " + result.format);
            console.log("Type: " + result.type);
        }
    },
    function(error) {
        console.error("Scan failed: " + error);
    }
);
```

### With Options

```javascript
cordova.plugins.barcodeScanner.scan(
    function(result) {
        if (!result.cancelled) {
            alert("Scanned: " + result.text);
        }
    },
    function(error) {
        console.error(error);
    },
    {
        showTorchButton: true,
        prompt: "Point camera at a barcode",
        beepOnSuccess: true,
        vibrateOnSuccess: true,
        detectorSize: 0.7,
        formats: "QR_CODE,EAN_13,CODE_128"
    }
);
```

### Scan Result

The success callback receives a result object:

```typescript
{
    text: string;       // The scanned barcode content
    format: string;     // Barcode format (e.g., "QR_CODE", "EAN_13")
    type: string;       // Value type (e.g., "URL", "EMAIL", "TEXT")
    cancelled: boolean; // true if user cancelled the scan
}
```

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `showTorchButton` | boolean | `true` | Show flashlight toggle button |
| `showFlipCameraButton` | boolean | `false` | Show camera flip button |
| `prompt` | string | `"Point the camera at a barcode"` | Custom prompt message |
| `beepOnSuccess` | boolean | `true` | Play beep sound on successful scan |
| `vibrateOnSuccess` | boolean | `true` | Vibrate on successful scan |
| `detectorSize` | number | `0.6` | Scan area size (0.1 to 1.0) |
| `formats` | string | all | Comma-separated barcode formats |

## Supported Barcode Formats

| Format | Description |
|--------|-------------|
| `QR_CODE` | QR Code |
| `DATA_MATRIX` | Data Matrix |
| `UPC_A` | UPC-A |
| `UPC_E` | UPC-E |
| `EAN_8` | EAN-8 |
| `EAN_13` | EAN-13 |
| `CODE_39` | Code 39 |
| `CODE_93` | Code 93 |
| `CODE_128` | Code 128 |
| `CODABAR` | Codabar |
| `ITF` | ITF (Interleaved 2 of 5) |
| `PDF_417` | PDF417 |
| `AZTEC` | Aztec |

## Detected Value Types

ML Kit automatically detects the type of data in the barcode:

| Type | Description |
|------|-------------|
| `TEXT` | Plain text |
| `URL` | Web URL |
| `EMAIL` | Email address |
| `PHONE` | Phone number |
| `SMS` | SMS message |
| `WIFI` | WiFi network credentials |
| `GEO` | Geographic coordinates |
| `CONTACT` | Contact information (vCard) |
| `CALENDAR` | Calendar event |
| `DRIVER_LICENSE` | Driver's license data |
| `ISBN` | Book ISBN |
| `PRODUCT` | Product code |

## Platform Configuration

### iOS

Add camera usage description to your `config.xml`:

```xml
<preference name="CAMERA_USAGE_DESCRIPTION" value="This app needs camera access to scan barcodes" />
```

### Android

The plugin automatically handles camera permissions. No additional configuration required.

## TypeScript

Full TypeScript definitions are included:

```typescript
cordova.plugins.barcodeScanner.scan(
    (result: BarcodeScanner.BarcodeScanResult) => {
        if (!result.cancelled) {
            console.log(result.text);
            console.log(result.format);  // "QR_CODE", "EAN_13", etc.
            console.log(result.type);    // "URL", "EMAIL", etc.
        }
    },
    (error: string) => {
        console.error(error);
    },
    {
        showTorchButton: true,
        formats: "QR_CODE,EAN_13"
    }
);
```

## Migration from v1.x (ZXing)

If you're upgrading from the ZXing-based version:

### API Changes

| Old Option | New Option | Notes |
|------------|------------|-------|
| `preferFrontCamera` | - | Removed |
| `torchOn` | - | Removed (use torch button) |
| `saveHistory` | - | Removed |
| `resultDisplayDuration` | - | Removed |
| `orientation` | - | Removed (always portrait) |
| `disableSuccessBeep` | `beepOnSuccess: false` | Inverted logic |

### New Features

- `type` field in result (URL, EMAIL, etc.)
- `detectorSize` option for scan area
- `vibrateOnSuccess` option
- Modern ML Kit scanning engine

## License

MIT License

## Credits

Powered by [Google ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning).

Community maintained by [EYALIN](https://github.com/EYALIN).

## Support

If you find this plugin helpful, consider [sponsoring](https://github.com/sponsors/EYALIN).
