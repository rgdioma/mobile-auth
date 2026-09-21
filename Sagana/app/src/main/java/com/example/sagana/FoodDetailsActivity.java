package com.example.sagana;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.TimeUnit;

public class FoodDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "com.example.sagana.TITLE";
    public static final String EXTRA_QUANTITY = "com.example.sagana.QUANTITY";
    public static final String EXTRA_CATEGORY = "com.example.sagana.CATEGORY";
    public static final String EXTRA_DISTANCE = "com.example.sagana.DISTANCE";
    public static final String EXTRA_DONOR = "com.example.sagana.DONOR";
    public static final String EXTRA_PHOTO_HINT = "com.example.sagana.PHOTO_HINT";
    public static final String EXTRA_PHOTO_NAME = "com.example.sagana.PHOTO_NAME";
    public static final String EXTRA_REMAINING_MILLIS = "com.example.sagana.REMAINING_MILLIS";

    /** The full pickup window every listing starts with, used to fill the bar. */
    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(3);

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());

    private TextView textCountdown;
    private ProgressBar progressWindow;
    private long endTimeMillis;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_food_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.foodDetailsRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textCountdown = findViewById(R.id.textCountdown);
        progressWindow = findViewById(R.id.progressWindow);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String quantity = getIntent().getStringExtra(EXTRA_QUANTITY);
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        String distance = getIntent().getStringExtra(EXTRA_DISTANCE);
        String donor = getIntent().getStringExtra(EXTRA_DONOR);
        String photoHint = getIntent().getStringExtra(EXTRA_PHOTO_HINT);
        endTimeMillis = System.currentTimeMillis()
                + getIntent().getLongExtra(EXTRA_REMAINING_MILLIS, WINDOW_MILLIS);

        ((TextView) findViewById(R.id.textTitle)).setText(title);
        ((TextView) findViewById(R.id.textSubtitle)).setText(
                getString(R.string.food_subtitle, quantity, category, distance));
        ((TextView) findViewById(R.id.textDonor)).setText(donor);
        ((TextView) findViewById(R.id.textPhotoHint)).setText(
                getString(R.string.photo_hint_details, photoHint));

        int photoRes = FoodPhotos.resolve(this, getIntent().getStringExtra(EXTRA_PHOTO_NAME));
        if (photoRes != 0) {
            ((ImageView) findViewById(R.id.imagePhoto)).setImageResource(photoRes);
            findViewById(R.id.photoCard).setVisibility(View.VISIBLE);
            findViewById(R.id.photoPlaceholder).setVisibility(View.GONE);
        }

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonReserve).setOnClickListener(v -> {
            // TODO: Hand the reservation to the Pickups screen once bookings are stored.
            Toast.makeText(this, R.string.toast_reserved, Toast.LENGTH_SHORT).show();
            finish();
        });

        updateCountdown();
    }

    @Override
    protected void onStart() {
        super.onStart();
        countdownHandler.post(countdownRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void updateCountdown() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            textCountdown.setText(R.string.window_closed);
            progressWindow.setProgress(0);
            return;
        }

        long hours = TimeUnit.MILLISECONDS.toHours(remaining);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
        if (hours > 0) {
            textCountdown.setText(getString(R.string.format_countdown_long, (int) hours, (int) minutes, (int) seconds));
        } else {
            textCountdown.setText(getString(R.string.format_countdown_short, (int) minutes, (int) seconds));
        }

        int percent = (int) Math.min(100, remaining * 100 / WINDOW_MILLIS);
        progressWindow.setProgress(percent);
    }
}
