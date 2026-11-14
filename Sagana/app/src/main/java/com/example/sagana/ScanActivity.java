package com.example.sagana;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Staff scan the receiver's handover QR code with ML Kit Barcode Scanning.
 * The model is bundled in the app, so this runs fully on the phone:
 * no API key, no network, no cost. Returns the code to DeskFragment.
 */
public class ScanActivity extends AppCompatActivity {

    public static final String EXTRA_CODE = "com.example.sagana.SCANNED_CODE";

    private LifecycleCameraController controller;
    private BarcodeScanner scanner;
    private ScanOverlayView overlay;
    private TextView hint;
    private boolean torchOn;
    private boolean done;
    private long lastWrongToast;

    private final ActivityResultLauncher<String> askCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else findViewById(R.id.noCamera).setVisibility(View.VISIBLE);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scan);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        Ui.padForSystemBars(findViewById(R.id.scanBars));

        overlay = findViewById(R.id.overlay);
        hint = findViewById(R.id.textScanHint);
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonTypeInstead).setOnClickListener(v -> finish());
        findViewById(R.id.buttonNoCameraBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonAskAgain).setOnClickListener(v -> askCamera.launch(Manifest.permission.CAMERA));
        findViewById(R.id.buttonTorch).setOnClickListener(v -> {
            if (controller == null) return;
            torchOn = !torchOn;
            controller.enableTorch(torchOn);
            v.setAlpha(torchOn ? 1f : 0.7f);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            askCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        findViewById(R.id.noCamera).setVisibility(View.GONE);
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build());
        Executor main = ContextCompat.getMainExecutor(this);
        PreviewView preview = findViewById(R.id.preview);
        controller = new LifecycleCameraController(this);
        controller.setImageAnalysisAnalyzer(main, new MlKitAnalyzer(
                Collections.singletonList(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                main,
                result -> {
                    List<Barcode> codes = result.getValue(scanner);
                    if (codes == null || codes.isEmpty() || done) return;
                    onRead(codes.get(0).getRawValue());
                }));
        controller.bindToLifecycle(this);
        preview.setController(controller);
    }

    private void onRead(String raw) {
        String code = QrCodes.extract(raw);
        if (code == null) {
            long now = System.currentTimeMillis();
            if (now - lastWrongToast > 2500) {
                lastWrongToast = now;
                hint.setText(R.string.scan_not_sagana);
            }
            return;
        }
        done = true;
        overlay.setFound(true);
        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        hint.setText(getString(R.string.scan_found, code));
        overlay.postDelayed(() -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CODE, code));
            finish();
        }, 450);
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) scanner.close();
        super.onDestroy();
    }
}
