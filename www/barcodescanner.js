/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

var exec = cordova.require("cordova/exec");

var scanInProgress = false;

/**
 * BarcodeScanner constructor
 * @constructor
 */
function BarcodeScanner() {
    /**
     * Barcode format constants for ML Kit
     * @type {Object}
     */
    this.format = {
        QR_CODE: "QR_CODE",
        DATA_MATRIX: "DATA_MATRIX",
        UPC_A: "UPC_A",
        UPC_E: "UPC_E",
        EAN_8: "EAN_8",
        EAN_13: "EAN_13",
        CODE_39: "CODE_39",
        CODE_93: "CODE_93",
        CODE_128: "CODE_128",
        CODABAR: "CODABAR",
        ITF: "ITF",
        PDF_417: "PDF_417",
        AZTEC: "AZTEC",
        ALL: ""
    };

    /**
     * Barcode value type constants
     * @type {Object}
     */
    this.valueType = {
        TEXT: "TEXT",
        URL: "URL",
        EMAIL: "EMAIL",
        PHONE: "PHONE",
        SMS: "SMS",
        WIFI: "WIFI",
        GEO: "GEO",
        CONTACT: "CONTACT",
        CALENDAR: "CALENDAR",
        DRIVER_LICENSE: "DRIVER_LICENSE",
        ISBN: "ISBN",
        PRODUCT: "PRODUCT"
    };
}

/**
 * Scan a barcode using the device camera
 *
 * @param {Function} successCallback Called with result object:
 *   {
 *     text: string,        // The scanned barcode content
 *     format: string,      // Barcode format (e.g., "QR_CODE", "EAN_13")
 *     type: string,        // Value type (e.g., "TEXT", "URL", "EMAIL")
 *     cancelled: boolean   // Whether the scan was cancelled
 *   }
 * @param {Function} errorCallback Called with error message string
 * @param {Object} [options] Scan configuration options:
 *   {
 *     showTorchButton: boolean,      // Show flashlight toggle (default: true)
 *     showFlipCameraButton: boolean, // Show camera flip button (default: false)
 *     prompt: string,                // Custom prompt text
 *     beepOnSuccess: boolean,        // Play beep on scan (default: true)
 *     vibrateOnSuccess: boolean,     // Vibrate on scan (default: true)
 *     detectorSize: number,          // Scan area size 0-1 (default: 0.6)
 *     formats: string                // Comma-separated formats (default: all)
 *   }
 */
BarcodeScanner.prototype.scan = function(successCallback, errorCallback, options) {
    // Validate callbacks
    if (typeof successCallback !== "function") {
        console.error("BarcodeScanner.scan: success callback must be a function");
        return;
    }

    if (errorCallback == null) {
        errorCallback = function() {};
    }

    if (typeof errorCallback !== "function") {
        console.error("BarcodeScanner.scan: error callback must be a function");
        return;
    }

    // Prevent multiple simultaneous scans
    if (scanInProgress) {
        errorCallback("Scan is already in progress");
        return;
    }

    // Normalize options
    var scanOptions = {};
    if (options && typeof options === "object") {
        // Copy valid options
        if (typeof options.showTorchButton === "boolean") {
            scanOptions.showTorchButton = options.showTorchButton;
        }
        if (typeof options.showFlipCameraButton === "boolean") {
            scanOptions.showFlipCameraButton = options.showFlipCameraButton;
        }
        if (typeof options.prompt === "string") {
            scanOptions.prompt = options.prompt;
        }
        if (typeof options.beepOnSuccess === "boolean") {
            scanOptions.beepOnSuccess = options.beepOnSuccess;
        }
        if (typeof options.vibrateOnSuccess === "boolean") {
            scanOptions.vibrateOnSuccess = options.vibrateOnSuccess;
        }
        if (typeof options.detectorSize === "number") {
            scanOptions.detectorSize = Math.max(0.1, Math.min(1.0, options.detectorSize));
        }
        if (typeof options.formats === "string") {
            // Clean up format string
            scanOptions.formats = options.formats.replace(/\s+/g, "");
        }
    }

    scanInProgress = true;

    exec(
        function(result) {
            scanInProgress = false;
            successCallback(result);
        },
        function(error) {
            scanInProgress = false;
            errorCallback(error);
        },
        "BarcodeScanner",
        "scan",
        [scanOptions]
    );
};

/**
 * Check if a scan is currently in progress
 * @returns {boolean}
 */
BarcodeScanner.prototype.isScanning = function() {
    return scanInProgress;
};

var barcodeScanner = new BarcodeScanner();
module.exports = barcodeScanner;
