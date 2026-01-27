/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * TypeScript Definitions
 */

/**
 * Scan configuration options
 */
export interface BarcodeScannerOptions {
    /** Show flashlight toggle button. Default: true */
    showTorchButton?: boolean;

    /** Show camera flip button. Default: false */
    showFlipCameraButton?: boolean;

    /** Custom prompt message to display on scanner screen */
    prompt?: string;

    /** Play beep sound on successful scan. Default: true */
    beepOnSuccess?: boolean;

    /** Vibrate on successful scan. Default: true */
    vibrateOnSuccess?: boolean;

    /** Size of the scan detection area (0.1 to 1.0). Default: 0.6 */
    detectorSize?: number;

    /** Comma-separated list of barcode formats to scan.
     * Example: "QR_CODE,EAN_13,CODE_128"
     * Leave empty to scan all supported formats.
     */
    formats?: string;

    /** Use front camera instead of back camera. Default: false */
    preferFrontCamera?: boolean;

    /** Launch with the torch switched on. Default: false */
    torchOn?: boolean;

    /** How long to display the result after a successful scan (ms). Default: 500 */
    resultDisplayDuration?: number;
}

/**
 * Result returned from a successful barcode scan
 */
export interface BarcodeScanResult {
    /** The scanned barcode content/data */
    text: string;

    /** The barcode format (e.g., "QR_CODE", "EAN_13", "CODE_128") */
    format: BarcodeFormat;

    /** The type of data encoded in the barcode (e.g., "URL", "EMAIL", "TEXT") */
    type: BarcodeValueType;

    /** Whether the user cancelled the scan */
    cancelled: boolean;
}

/**
 * Supported barcode formats (ML Kit)
 */
export type BarcodeFormat =
    | "QR_CODE"
    | "DATA_MATRIX"
    | "UPC_A"
    | "UPC_E"
    | "EAN_8"
    | "EAN_13"
    | "CODE_39"
    | "CODE_93"
    | "CODE_128"
    | "CODABAR"
    | "ITF"
    | "PDF_417"
    | "AZTEC"
    | "UNKNOWN";

/**
 * Types of data that can be encoded in barcodes
 */
export type BarcodeValueType =
    | "TEXT"
    | "URL"
    | "EMAIL"
    | "PHONE"
    | "SMS"
    | "WIFI"
    | "GEO"
    | "CONTACT"
    | "CALENDAR"
    | "DRIVER_LICENSE"
    | "ISBN"
    | "PRODUCT";

/**
 * Format constants object
 */
export interface FormatConstants {
    QR_CODE: string;
    DATA_MATRIX: string;
    UPC_A: string;
    UPC_E: string;
    EAN_8: string;
    EAN_13: string;
    CODE_39: string;
    CODE_93: string;
    CODE_128: string;
    CODABAR: string;
    ITF: string;
    PDF_417: string;
    AZTEC: string;
    ALL: string;
}

/**
 * Value type constants object
 */
export interface ValueTypeConstants {
    TEXT: string;
    URL: string;
    EMAIL: string;
    PHONE: string;
    SMS: string;
    WIFI: string;
    GEO: string;
    CONTACT: string;
    CALENDAR: string;
    DRIVER_LICENSE: string;
    ISBN: string;
    PRODUCT: string;
}

/**
 * BarcodeScanner Manager interface
 */
export default interface BarcodeScannerManager {
    /** Available barcode format constants */
    format: FormatConstants;

    /** Available value type constants */
    valueType: ValueTypeConstants;

    /**
     * Scan a barcode using the device camera
     *
     * @param successCallback Called with the scan result
     * @param errorCallback Called if an error occurs
     * @param options Optional scan configuration
     *
     * @example
     * BarcodeScannerPlugin.scan(
     *   (result) => {
     *     if (!result.cancelled) {
     *       console.log("Scanned:", result.text);
     *       console.log("Format:", result.format);
     *       console.log("Type:", result.type);
     *     }
     *   },
     *   (error) => console.error("Scan failed:", error),
     *   {
     *     showTorchButton: true,
     *     prompt: "Scan a QR code",
     *     formats: "QR_CODE,EAN_13"
     *   }
     * );
     */
    scan(
        successCallback: (result: BarcodeScanResult) => void,
        errorCallback: (error: string) => void,
        options?: BarcodeScannerOptions
    ): void;

    /**
     * Check if a scan is currently in progress
     * @returns true if scanning is active
     */
    isScanning(): boolean;
}
