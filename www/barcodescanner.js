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
    }
};

module.exports = BarcodeScannerPlugin;
