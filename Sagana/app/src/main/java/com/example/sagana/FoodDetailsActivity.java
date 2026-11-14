package com.example.sagana;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Feature 3: food details with the pickup countdown and the walking route
 * from Geoapify (Routing API for distance/time/path, Static Maps API for the picture).
 */
public class FoodDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "com.example.sagana.LISTING_ID";

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());

    private TextView textCountdown;
    private ProgressBar progressWindow;
    private DatabaseHelper.Listing listing;

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
        Ui.padForSystemBars(findViewById(R.id.foodDetailsRoot));

        listing = DatabaseHelper.get(this).getListing(getIntent().getLongExtra(EXTRA_LISTING_ID, -1));
        if (listing == null) {
            Toast.makeText(this, R.string.toast_listing_gone, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        textCountdown = findViewById(R.id.textCountdown);
        progressWindow = findViewById(R.id.progressWindow);

        DatabaseHelper.User me = Session.user(this);
        double[] home = me != null && (me.lat != 0 || me.lng != 0) ? new double[]{me.lat, me.lng}
                : Geo.fallback(me != null ? me.barangay : "Villamonte");
        double straightKm = DatabaseHelper.km(home[0], home[1], listing.lat, listing.lng);

        ((TextView) findViewById(R.id.textTitle)).setText(listing.name);
        ((TextView) findViewById(R.id.textSubtitle)).setText(
                getString(R.string.food_subtitle, listing.qty, listing.category, Geo.km(straightKm)));
        ((TextView) findViewById(R.id.textDonor)).setText(listing.donor);
        ((TextView) findViewById(R.id.textDonorType)).setText(listing.donorType == null ? "" : listing.donorType);
        ((RatingBar) findViewById(R.id.ratingDonor)).setRating((float) listing.rating);
        ((TextView) findViewById(R.id.textDonorRating)).setText(getString(R.string.donor_rating_f, listing.rating));
        ((TextView) findViewById(R.id.textNotes)).setText(listing.notes == null || listing.notes.isEmpty()
                ? getString(R.string.no_notes) : listing.notes);
        ((TextView) findViewById(R.id.textHandover)).setText(getString(R.string.handover_note_f, listing.desk));
        ((TextView) findViewById(R.id.textPhotoHint)).setText(
                getString(R.string.photo_hint_details, listing.name.toLowerCase(Locale.US)));
        ((TextView) findViewById(R.id.textDeskShort)).setText("Brgy. " + Geo.barangayOf(listing.desk));

        if (Ui.loadPhoto(findViewById(R.id.imagePhoto), listing.photo)) {
            findViewById(R.id.photoCard).setVisibility(View.VISIBLE);
            findViewById(R.id.photoPlaceholder).setVisibility(View.GONE);
        }

        // first show a straight-line estimate, then replace it with Geoapify's real walking route
        showWalk(straightKm, Geo.walkMinutes(straightKm), false);
        loadRoute(home);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonReserve).setOnClickListener(v -> reserve());
        View.OnClickListener directions = v -> Nav.push(this,
                MapRouteActivity.intent(this, listing.desk, listing.lat, listing.lng));
        findViewById(R.id.buttonDirections).setOnClickListener(directions);
        findViewById(R.id.imageRoute).setOnClickListener(directions);

        updateCountdown();
    }

    private void loadRoute(double[] home) {
        GeoapifyClient.walkingRoute(home[0], home[1], listing.lat, listing.lng, new GeoapifyClient.Callback<GeoapifyClient.Route>() {
            @Override
            public void onResult(GeoapifyClient.Route r) {
                if (isFinishing()) return;
                showWalk(r.km, (int) Math.round(r.minutes), true);
                loadMap(GeoapifyClient.routeMapUrl(home[0], home[1], listing.lat, listing.lng, r.path));
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing()) return;
                // routing failed: still try the map picture with just the two pins
                if (GeoapifyClient.hasKey()) loadMap(GeoapifyClient.routeMapUrl(home[0], home[1], listing.lat, listing.lng, null));
                else mapFailed();
            }
        });
    }

    private void showWalk(double km, int minutes, boolean fromApi) {
        ((TextView) findViewById(R.id.textWalkDistance)).setText(Geo.km(km));
        ((TextView) findViewById(R.id.textWalkTime)).setText(getString(R.string.walk_minutes, minutes));
        ((TextView) findViewById(R.id.textRouteSource)).setText(fromApi ? R.string.route_from_api : R.string.route_estimate);
    }

    private void loadMap(String url) {
        ImageView map = findViewById(R.id.imageRoute);
        Glide.with(this).load(url).timeout(20000).transition(DrawableTransitionOptions.withCrossFade())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object m, @NonNull Target<Drawable> t, boolean f) {
                        mapFailed();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable d, @NonNull Object m, Target<Drawable> t, @NonNull DataSource s, boolean f) {
                        findViewById(R.id.progressRoute).setVisibility(View.GONE);
                        return false;
                    }
                }).into(map);
    }

    private void mapFailed() {
        findViewById(R.id.progressRoute).setVisibility(View.GONE);
        findViewById(R.id.textRouteError).setVisibility(View.VISIBLE);
    }

    private void reserve() {
        if (!listing.isOpen()) {
            Toast.makeText(this, R.string.toast_listing_closed, Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseHelper.User me = Session.user(this);
        if (me != null && me.isStaff()) {
            Toast.makeText(this, R.string.toast_staff_cannot_reserve, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.EXTRA_RESERVE_ID, listing.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (listing != null) countdownHandler.post(countdownRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void updateCountdown() {
        long remaining = listing.pickupEnd - System.currentTimeMillis();
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
        long window = Math.max(1, listing.pickupEnd - listing.pickupStart);
        progressWindow.setProgress((int) Math.min(100, remaining * 100 / window));
    }
}
