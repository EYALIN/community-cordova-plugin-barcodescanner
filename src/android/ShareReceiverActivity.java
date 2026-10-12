/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 *
 * APP-224: "Share to Barcode Reader" entry point. Declared in plugin.xml with
 * ACTION_SEND / ACTION_SEND_MULTIPLE intent filters for image/* and application/pdf. For
 * ACTION_SEND_MULTIPLE only the first shared item is decoded (per the card's own scope). A PDF is
 * rasterized to its first page via PdfRenderer before decoding; an image is decoded directly.
 * Every supported image type (JPEG/PNG/WebP/HEIC) works because decoding goes through ML Kit's
 * InputImage.fromFilePath, which uses BitmapFactory/ImageDecoder under the hood rather than a
 * format-specific path.
 *
 * The decoded result (or an error placeholder) is written to the same SharedPreferences slot
 * BarcodeScanner#getPendingShare() reads, then the app's own launcher activity is started so the
 * WebView picks the result up on next ionViewWillEnter / resume.
 */
package com.anthropic.community.barcodescanner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShareReceiverActivity extends Activity {
    private static final String TAG = "ShareReceiverActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        Uri sharedUri = null;

        if (Intent.ACTION_SEND.equals(action)) {
            sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            // Card scope: only the first shared item is decoded.
            List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null && !uris.isEmpty()) {
                sharedUri = uris.get(0);
            }
        }

        // APP-224: Tasks.await() throws IllegalStateException on the main thread, so every share
        // used to fail with "Could not decode the shared image". Decode on a worker thread; the
        // activity stays alive (no UI, translucent) until the pending payload is written.
        final Uri finalUri = sharedUri;
        final String mimeType = intent != null ? intent.getType() : null;
        new Thread(() -> {
            JSONObject resultPayload = finalUri == null
                    ? errorPayload("No shared image found")
                    : decodeShared(finalUri, mimeType);
            writePendingShare(resultPayload);
            runOnUiThread(() -> {
                launchApp();
                finish();
            });
        }, "share-decode").start();
    }

    private JSONObject decodeShared(Uri uri, String mimeType) {
        File tempPdfPage = null;
        try {
            Uri decodeUri = uri;
            if (mimeType != null && mimeType.equals("application/pdf")) {
                tempPdfPage = rasterizeFirstPdfPage(uri);
                if (tempPdfPage == null) {
                    return errorPayload("Unable to read shared PDF");
                }
                decodeUri = Uri.fromFile(tempPdfPage);
            }

            InputImage image = InputImage.fromFilePath(this, decodeUri);
            List<Barcode> barcodes = Tasks.await(
                    BarcodeScanner.getSharedClient().process(image), 15, TimeUnit.SECONDS);

            JSONArray codes = new JSONArray();
            for (Barcode barcode : barcodes) {
                JSONObject result = new JSONObject();
                result.put("text", barcode.getRawValue() != null ? barcode.getRawValue() : "");
                result.put("format", BarcodeScanner.formatName(barcode.getFormat()));
                result.put("type", BarcodeScanner.valueTypeName(barcode.getValueType()));
                result.put("cancelled", false);
                codes.put(result);
            }

            JSONObject payload = new JSONObject();
            payload.put("codes", codes);
            payload.put("error", JSONObject.NULL);
            return payload;
        } catch (Exception e) {
            // APP-224 review round 1: widened from the specific checked exceptions to catch-all.
            // InputImage.fromFilePath/the content resolver can also throw SecurityException (a
            // revoked/one-shot URI permission grant) or IllegalArgumentException (a malformed/
            // untrusted content:// or file:// URI shared from another app) - neither is a checked
            // exception, so either previously crashed this activity instead of resolving to
            // errorPayload() like every other decode failure.
            Log.e(TAG, "decodeShared failed", e);
            return errorPayload("Could not decode the shared image: " + e.getMessage());
        } finally {
            if (tempPdfPage != null) {
                //noinspection ResultOfMethodCallIgnored
                tempPdfPage.delete();
            }
        }
    }

    private File rasterizeFirstPdfPage(Uri pdfUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        try {
            pfd = getContentResolver().openFileDescriptor(pdfUri, "r");
            if (pfd == null) {
                return null;
            }
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() == 0) {
                return null;
            }
            page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            File out = new File(getCacheDir(), "share_pdf_page.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return out;
        } catch (Exception e) {
            Log.e(TAG, "rasterizeFirstPdfPage failed", e);
            return null;
        } finally {
            if (page != null) {
                page.close();
            }
            if (renderer != null) {
                renderer.close();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }
    }

    private JSONObject errorPayload(String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("codes", new JSONArray());
            payload.put("error", message);
            return payload;
        } catch (JSONException e) {
            return null;
        }
    }

    private void writePendingShare(JSONObject payload) {
        if (payload == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(
                BarcodeScanner.PENDING_SHARE_PREFS, android.content.Context.MODE_PRIVATE);
        prefs.edit().putString(BarcodeScanner.PENDING_SHARE_KEY, payload.toString()).apply();
    }

    private void launchApp() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                // APP-448: bring the running instance forward (a platform resume the JS layer
                // listens for) instead of CLEAR_TOP, which could rebuild the activity and WebView.
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(launch);
            }
        } catch (Exception e) {
            Log.e(TAG, "launchApp failed", e);
        }
    }
}
