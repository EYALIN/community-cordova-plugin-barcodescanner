/*
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

var PLUGIN_NAME = 'BarcodeScanner';

var scanInProgress = false;

var BarcodeScannerPlugin = {
    /**
     * Barcode format constants for ML Kit
     */
    format: {
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
    },

    /**
     * Barcode value type constants
     */
    valueType: {
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
    },

    /**
     * Scan a barcode using the device camera
     * @param {Function} successCallback Called with result object
     * @param {Function} errorCallback Called with error message string
     * @param {Object} [options] Scan configuration options
     */
    scan: function(successCallback, errorCallback, options) {
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
                scanOptions.formats = options.formats.replace(/\s+/g, "");
            }
            // APP-225: batch scan's "Done (N)" label. Without forwarding it here the native side
            // never receives it and the Done button stays hidden.
            if (typeof options.doneButtonLabel === "string" && options.doneButtonLabel) {
                scanOptions.doneButtonLabel = options.doneButtonLabel;
            }
        }

        scanInProgress = true;

        cordova.exec(
            function(result) {
                scanInProgress = false;
                successCallback(result);
            },
            function(error) {
                scanInProgress = false;
                errorCallback(error);
            },
            PLUGIN_NAME,
            "scan",
            [scanOptions]
        );
    },

    /**
     * Check if a scan is currently in progress
     * @returns {boolean}
     */
    isScanning: function() {
        return scanInProgress;
    },

    /**
     * APP-224: decode every barcode found in an already-captured image file (upload / share flow),
     * using native ML Kit (Android) / Vision (iOS) instead of a live camera session.
     * @param {string} path File path (or content:// / file:// URI on Android) of the image to decode
     * @param {Function} successCallback Called with an array of result objects ({text, format, type, cancelled:false})
     * @param {Function} errorCallback Called with an error message string
     */
    decodeFile: function(path, successCallback, errorCallback) {
        if (typeof successCallback !== "function") {
            console.error("BarcodeScanner.decodeFile: success callback must be a function");
            return;
        }
        if (errorCallback == null) {
            errorCallback = function() {};
        }
        cordova.exec(successCallback, errorCallback, PLUGIN_NAME, "decodeFile", [path]);
    },

    /**
     * APP-224: returns (and clears) the result of an image shared into the app via the OS share
     * sheet ("Share to Barcode Reader"), or null if there is none pending. Android only for now —
     * the iOS share extension is tracked separately.
     * @param {Function} successCallback Called with { codes: [...], error: string|null } or null
     * @param {Function} errorCallback Called with an error message string
     */
    getPendingShare: function(successCallback, errorCallback) {
        if (typeof successCallback !== "function") {
            console.error("BarcodeScanner.getPendingShare: success callback must be a function");
            return;
        }
        if (errorCallback == null) {
            errorCallback = function() {};
        }
        cordova.exec(successCallback, errorCallback, PLUGIN_NAME, "getPendingShare", []);
    }
};

module.exports = BarcodeScannerPlugin;
