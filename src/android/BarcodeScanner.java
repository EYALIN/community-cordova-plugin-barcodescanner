/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */
package com.anthropic.community.barcodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BarcodeScanner extends CordovaPlugin {
    private static final String TAG = "BarcodeScanner";
    private static final int REQUEST_CODE_SCAN = 49374;
    private static final int PERMISSION_REQUEST_CAMERA = 1;

    private static final String ACTION_SCAN = "scan";
    private static final String ACTION_DECODE_FILE = "decodeFile";
    private static final String ACTION_GET_PENDING_SHARE = "getPendingShare";

    // APP-224: pending result of an image shared into the app via ACTION_SEND/ACTION_SEND_MULTIPLE,
    // written by ShareReceiverActivity and consumed exactly once by getPendingShare(). SharedPreferences
    // (not a static field) because the share activity and the WebView activity can run in separate
    // plugin instances / after a process restart.
    public static final String PENDING_SHARE_PREFS = "barcodescanner_pending_share";
    public static final String PENDING_SHARE_KEY = "pending_result";

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

        if (ACTION_DECODE_FILE.equals(action)) {
            final String path = args.optString(0, null);
            final CallbackContext decodeCallback = callbackContext;
            cordova.getThreadPool().execute(() -> decodeFile(path, decodeCallback));
            return true;
        }

        if (ACTION_GET_PENDING_SHARE.equals(action)) {
            JSONObject pending = readAndClearPendingShare(cordova.getActivity());
            if (pending != null) {
                callbackContext.success(pending);
            } else {
                // Cordova's CallbackContext.success(JSONObject) calls message.toString() with no
                // null guard, so a literal null throws a NullPointerException inside the plugin
                // bridge (PluginManager then swallows it uncaught and never invokes the JS
                // callback at all - the exec() promise hangs forever instead of resolving null,
                // which hung every app startup that awaits getPendingShare()). Use the no-arg
                // success() so "nothing pending" actually resolves on the JS side.
                callbackContext.success();
            }
            return true;
        }

        return false;
    }

    /**
     * APP-224: decode every barcode found in an arbitrary image file (from the share/upload path)
     * using ML Kit's still-image API, instead of the live-camera `scan()` flow above. Supports any
     * format BitmapFactory can load (JPEG/PNG/WebP/HEIC on API 28+); multiple codes in one image are
     * all returned. Runs on the Cordova thread pool (never the UI thread) and blocks that worker
     * thread on a latch because the plugin's execute() contract expects a single callback result -
     * ML Kit's Task is otherwise async-only.
     */
    private void decodeFile(String path, CallbackContext cb) {
        if (path == null || path.isEmpty()) {
            cb.error("decodeFile: missing file path");
            return;
        }
        try {
            Uri uri = path.startsWith("content://") || path.startsWith("file://")
                    ? Uri.parse(path) : Uri.fromFile(new java.io.File(path));
            InputImage image = InputImage.fromFilePath(cordova.getActivity(), uri);

            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder().build();
            final JSONArray results = new JSONArray();
            final Exception[] failure = new Exception[1];
            final CountDownLatch latch = new CountDownLatch(1);

            Task<java.util.List<Barcode>> task = BarcodeScanning.getClient(options).process(image);
            task.addOnSuccessListener(barcodes -> {
                for (Barcode barcode : barcodes) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("text", barcode.getRawValue() != null ? barcode.getRawValue() : "");
                        result.put("format", formatName(barcode.getFormat()));
                        result.put("type", valueTypeName(barcode.getValueType()));
                        result.put("cancelled", false);
                        results.put(result);
                    } catch (JSONException e) {
                        Log.e(TAG, "decodeFile: error building result", e);
                    }
                }
                latch.countDown();
            }).addOnFailureListener(e -> {
                failure[0] = e;
                latch.countDown();
            });

            latch.await(15, TimeUnit.SECONDS);

            if (failure[0] != null) {
                cb.error("decodeFile failed: " + failure[0].getMessage());
                return;
            }
            cb.success(results);
        } catch (IOException e) {
            cb.error("decodeFile: unable to load image: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cb.error("decodeFile: interrupted");
        } catch (Exception e) {
            cb.error("decodeFile: " + e.getMessage());
        }
    }

    static String formatName(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE: return "QR_CODE";
            case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_CODE_93: return "CODE_93";
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODABAR: return "CODABAR";
            case Barcode.FORMAT_ITF: return "ITF";
            case Barcode.FORMAT_PDF417: return "PDF_417";
            case Barcode.FORMAT_AZTEC: return "AZTEC";
            default: return "UNKNOWN";
        }
    }

    static String valueTypeName(int valueType) {
        switch (valueType) {
            case Barcode.TYPE_URL: return "URL";
            case Barcode.TYPE_EMAIL: return "EMAIL";
            case Barcode.TYPE_PHONE: return "PHONE";
            case Barcode.TYPE_SMS: return "SMS";
            case Barcode.TYPE_WIFI: return "WIFI";
            case Barcode.TYPE_GEO: return "GEO";
            case Barcode.TYPE_CONTACT_INFO: return "CONTACT";
            case Barcode.TYPE_CALENDAR_EVENT: return "CALENDAR";
            case Barcode.TYPE_DRIVER_LICENSE: return "DRIVER_LICENSE";
            case Barcode.TYPE_ISBN: return "ISBN";
            case Barcode.TYPE_PRODUCT: return "PRODUCT";
            default: return "TEXT";
        }
    }

    /**
     * Reads (and clears) the result cached by ShareReceiverActivity, if any. Package-visible static
     * so ShareReceiverActivity can write it without needing a plugin instance.
     */
    static JSONObject readAndClearPendingShare(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PENDING_SHARE_PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(PENDING_SHARE_KEY, null);
        if (json == null) {
            return null;
        }
        prefs.edit().remove(PENDING_SHARE_KEY).apply();
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
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

            // APP-225: batch scan mode re-launches this one-shot activity per code, so the running
            // count/label are passed fresh on every launch, same pattern as `prompt` above. When
            // doneButtonLabel is absent (every pre-existing, non-batch call site) the Done button
            // stays hidden and the activity is byte-identical to before.
            if (scanOptions.has("doneButtonLabel") && !scanOptions.isNull("doneButtonLabel")) {
                intent.putExtra("doneButtonLabel", scanOptions.optString("doneButtonLabel"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing options", e);
        }

        cordova.startActivityForResult(this, intent, REQUEST_CODE_SCAN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            // Android can kill our process while the scanner activity is in the foreground. The
            // plugin is then recreated and this callback arrives with no callbackContext, so every
            // success()/error() below would NPE. Nothing is waiting on the JS side either, since
            // that page was destroyed with the process — drop the result instead of crashing.
            if (callbackContext == null) {
                return;
            }
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
                    // APP-225: distinguishes "tapped Done" from "tapped X / pressed back" - both end
                    // a batch run the same way on the JS side, but this keeps that attribution honest
                    // instead of logging every batch-ending Done tap as reason: 'user_back'.
                    result.put("done", intent != null && intent.getBooleanExtra("DONE", false));
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
