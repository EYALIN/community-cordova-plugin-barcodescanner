/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */
package com.anthropic.community.barcodescanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScannerActivity extends AppCompatActivity {
    private static final String TAG = "BarcodeScannerActivity";

    private PreviewView previewView;
    private View scanArea;
    private View overlayTop, overlayBottom, overlayLeft, overlayRight;
    private ImageButton torchButton;
    private ImageButton closeButton;
    private TextView promptText;

    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;

    private boolean isTorchOn = false;
    private boolean isScanning = true;

    // Options
    private boolean showTorchButton = true;
    private boolean showFlipCameraButton = false;
    private String prompt = "Point the camera at a barcode";
    private boolean beepOnSuccess = true;
    private boolean vibrateOnSuccess = true;
    private double detectorSize = 0.6;
    private String formats = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get options from intent
        Intent intent = getIntent();
        showTorchButton = intent.getBooleanExtra("showTorchButton", true);
        showFlipCameraButton = intent.getBooleanExtra("showFlipCameraButton", false);
        prompt = intent.getStringExtra("prompt");
        if (prompt == null) prompt = "Point the camera at a barcode";
        beepOnSuccess = intent.getBooleanExtra("beepOnSuccess", true);
        vibrateOnSuccess = intent.getBooleanExtra("vibrateOnSuccess", true);
        detectorSize = intent.getDoubleExtra("detectorSize", 0.6);
        formats = intent.getStringExtra("formats");
        if (formats == null) formats = "";

        // Setup UI
        setupUI();

        // Initialize barcode scanner
        setupBarcodeScanner();

        // Start camera
        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void setupUI() {
        setContentView(getResources().getIdentifier("activity_barcode_scanner", "layout", getPackageName()));

        previewView = findViewById(getResources().getIdentifier("preview_view", "id", getPackageName()));
        scanArea = findViewById(getResources().getIdentifier("scan_area", "id", getPackageName()));
        overlayTop = findViewById(getResources().getIdentifier("overlay_top", "id", getPackageName()));
        overlayBottom = findViewById(getResources().getIdentifier("overlay_bottom", "id", getPackageName()));
        overlayLeft = findViewById(getResources().getIdentifier("overlay_left", "id", getPackageName()));
        overlayRight = findViewById(getResources().getIdentifier("overlay_right", "id", getPackageName()));
        torchButton = findViewById(getResources().getIdentifier("torch_button", "id", getPackageName()));
        closeButton = findViewById(getResources().getIdentifier("close_button", "id", getPackageName()));
        promptText = findViewById(getResources().getIdentifier("prompt_text", "id", getPackageName()));

        // Set prompt text
        promptText.setText(prompt);

        // Setup torch button
        if (showTorchButton) {
            torchButton.setVisibility(View.VISIBLE);
            torchButton.setOnClickListener(v -> toggleTorch());
        } else {
            torchButton.setVisibility(View.GONE);
        }

        // Setup close button
        closeButton.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        // Setup scan area size after layout
        previewView.post(() -> {
            int width = previewView.getWidth();
            int height = previewView.getHeight();
            int scanSize = (int) (Math.min(width, height) * detectorSize);

            // Update scan area size
            scanArea.getLayoutParams().width = scanSize;
            scanArea.getLayoutParams().height = scanSize;
            scanArea.requestLayout();
        });
    }

    private void setupBarcodeScanner() {
        BarcodeScannerOptions.Builder optionsBuilder = new BarcodeScannerOptions.Builder();

        if (formats != null && !formats.isEmpty()) {
            int formatFlags = parseFormats(formats);
            if (formatFlags != 0) {
                optionsBuilder.setBarcodeFormats(formatFlags);
            } else {
                optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS);
            }
        } else {
            optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS);
        }

        barcodeScanner = BarcodeScanning.getClient(optionsBuilder.build());
    }

    private int parseFormats(String formatsStr) {
        int result = 0;
        String[] formatArray = formatsStr.toUpperCase().split(",");

        for (String format : formatArray) {
            format = format.trim();
            switch (format) {
                case "QR_CODE":
                    result |= Barcode.FORMAT_QR_CODE;
                    break;
                case "DATA_MATRIX":
                    result |= Barcode.FORMAT_DATA_MATRIX;
                    break;
                case "UPC_A":
                    result |= Barcode.FORMAT_UPC_A;
                    break;
                case "UPC_E":
                    result |= Barcode.FORMAT_UPC_E;
                    break;
                case "EAN_8":
                    result |= Barcode.FORMAT_EAN_8;
                    break;
                case "EAN_13":
                    result |= Barcode.FORMAT_EAN_13;
                    break;
                case "CODE_39":
                    result |= Barcode.FORMAT_CODE_39;
                    break;
                case "CODE_93":
                    result |= Barcode.FORMAT_CODE_93;
                    break;
                case "CODE_128":
                    result |= Barcode.FORMAT_CODE_128;
                    break;
                case "CODABAR":
                    result |= Barcode.FORMAT_CODABAR;
                    break;
                case "ITF":
                    result |= Barcode.FORMAT_ITF;
                    break;
                case "PDF_417":
                case "PDF417":
                    result |= Barcode.FORMAT_PDF417;
                    break;
                case "AZTEC":
                    result |= Barcode.FORMAT_AZTEC;
                    break;
            }
        }
        return result;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                returnError("Failed to start camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            returnError("Failed to bind camera: " + e.getMessage());
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(ImageProxy imageProxy) {
        if (!isScanning) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && isScanning) {
                        isScanning = false;
                        Barcode barcode = barcodes.get(0);
                        onBarcodeDetected(barcode);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodeDetected(Barcode barcode) {
        runOnUiThread(() -> {
            // Feedback
            if (beepOnSuccess) {
                playBeep();
            }
            if (vibrateOnSuccess) {
                vibrate();
            }

            // Return result
            Intent resultIntent = new Intent();
            resultIntent.putExtra("SCAN_RESULT", barcode.getRawValue());
            resultIntent.putExtra("SCAN_RESULT_FORMAT", getFormatName(barcode.getFormat()));
            resultIntent.putExtra("SCAN_RESULT_TYPE", getTypeName(barcode.getValueType()));
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    private String getFormatName(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case Barcode.FORMAT_DATA_MATRIX:
                return "DATA_MATRIX";
            case Barcode.FORMAT_UPC_A:
                return "UPC_A";
            case Barcode.FORMAT_UPC_E:
                return "UPC_E";
            case Barcode.FORMAT_EAN_8:
                return "EAN_8";
            case Barcode.FORMAT_EAN_13:
                return "EAN_13";
            case Barcode.FORMAT_CODE_39:
                return "CODE_39";
            case Barcode.FORMAT_CODE_93:
                return "CODE_93";
            case Barcode.FORMAT_CODE_128:
                return "CODE_128";
            case Barcode.FORMAT_CODABAR:
                return "CODABAR";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_PDF417:
                return "PDF_417";
            case Barcode.FORMAT_AZTEC:
                return "AZTEC";
            default:
                return "UNKNOWN";
        }
    }

    private String getTypeName(int type) {
        switch (type) {
            case Barcode.TYPE_URL:
                return "URL";
            case Barcode.TYPE_EMAIL:
                return "EMAIL";
            case Barcode.TYPE_PHONE:
                return "PHONE";
            case Barcode.TYPE_SMS:
                return "SMS";
            case Barcode.TYPE_WIFI:
                return "WIFI";
            case Barcode.TYPE_GEO:
                return "GEO";
            case Barcode.TYPE_CONTACT_INFO:
                return "CONTACT";
            case Barcode.TYPE_CALENDAR_EVENT:
                return "CALENDAR";
            case Barcode.TYPE_DRIVER_LICENSE:
                return "DRIVER_LICENSE";
            case Barcode.TYPE_ISBN:
                return "ISBN";
            case Barcode.TYPE_PRODUCT:
                return "PRODUCT";
            case Barcode.TYPE_TEXT:
            default:
                return "TEXT";
        }
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            // Update button appearance
            torchButton.setAlpha(isTorchOn ? 1.0f : 0.6f);
        }
    }

    private void playBeep() {
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play beep", e);
        }
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to vibrate", e);
        }
    }

    private void returnError(String message) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCAN_ERROR", message);
        setResult(Activity.RESULT_FIRST_USER, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }
}
