/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */
package com.anthropic.community.barcodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BarcodeScanner extends CordovaPlugin {
    private static final String TAG = "BarcodeScanner";
    private static final int REQUEST_CODE_SCAN = 49374;
    private static final int PERMISSION_REQUEST_CAMERA = 1;

    private static final String ACTION_SCAN = "scan";

    private CallbackContext callbackContext;
    private JSONObject scanOptions;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (ACTION_SCAN.equals(action)) {
            this.scanOptions = args.optJSONObject(0);
            if (this.scanOptions == null) {
                this.scanOptions = new JSONObject();
            }

            if (hasPermission()) {
                startScan();
            } else {
                requestPermission();
            }
            return true;
        }

        return false;
    }

    private boolean hasPermission() {
        return PermissionHelper.hasPermission(this, Manifest.permission.CAMERA);
    }

    private void requestPermission() {
        PermissionHelper.requestPermission(this, PERMISSION_REQUEST_CAMERA, Manifest.permission.CAMERA);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                callbackContext.error("Camera permission denied");
            }
        }
    }

    private void startScan() {
        Intent intent = new Intent(cordova.getActivity(), BarcodeScannerActivity.class);

        try {
            // Pass options to the scanner activity
            intent.putExtra("showTorchButton", scanOptions.optBoolean("showTorchButton", true));
            intent.putExtra("showFlipCameraButton", scanOptions.optBoolean("showFlipCameraButton", false));
            intent.putExtra("prompt", scanOptions.optString("prompt", "Point the camera at a barcode"));
            intent.putExtra("beepOnSuccess", scanOptions.optBoolean("beepOnSuccess", true));
            intent.putExtra("vibrateOnSuccess", scanOptions.optBoolean("vibrateOnSuccess", true));
            intent.putExtra("detectorSize", scanOptions.optDouble("detectorSize", 0.6));
            intent.putExtra("formats", scanOptions.optString("formats", ""));

            // UI customization
            intent.putExtra("scanAreaColor", scanOptions.optString("scanAreaColor", "#FFFFFF"));
            intent.putExtra("scanAreaOpacity", scanOptions.optDouble("scanAreaOpacity", 0.3));
            intent.putExtra("overlayColor", scanOptions.optString("overlayColor", "#000000"));
            intent.putExtra("overlayOpacity", scanOptions.optDouble("overlayOpacity", 0.5));

        } catch (Exception e) {
            Log.e(TAG, "Error parsing options", e);
        }

        cordova.startActivityForResult(this, intent, REQUEST_CODE_SCAN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("text", intent.getStringExtra("SCAN_RESULT"));
                    result.put("format", intent.getStringExtra("SCAN_RESULT_FORMAT"));
                    result.put("type", intent.getStringExtra("SCAN_RESULT_TYPE"));
                    result.put("cancelled", false);
                    callbackContext.success(result);
                } catch (JSONException e) {
                    callbackContext.error("Error creating result: " + e.getMessage());
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("text", "");
                    result.put("format", "");
                    result.put("cancelled", true);
                    callbackContext.success(result);
                } catch (JSONException e) {
                    callbackContext.error("Error creating result: " + e.getMessage());
                }
            } else {
                String error = intent != null ? intent.getStringExtra("SCAN_ERROR") : "Unknown error";
                callbackContext.error(error != null ? error : "Scan failed");
            }
        }
    }
}
