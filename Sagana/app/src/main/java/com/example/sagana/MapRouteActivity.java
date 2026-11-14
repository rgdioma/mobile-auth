package com.example.sagana;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

/**
 * Directions to a pickup desk, drawn on an interactive Geoapify map.
 *   1. Asks for location permission and reads the phone's current position.
 *   2. Asks the Geoapify Routing API for a walking (or driving) route to the desk.
 *   3. Draws the route line, a green "you" dot and the red desk pin, then fits them on screen.
 * If location is refused, unavailable, or far outside the city (e.g. an emulator parked in
 * California), the route starts from the member's saved barangay instead, and says so.
 */
public class MapRouteActivity extends AppCompatActivity {

    private static final String EXTRA_NAME = "route.name";
    private static final String EXTRA_LAT = "route.lat";
    private static final String EXTRA_LNG = "route.lng";
    private static final long LOCATION_TIMEOUT_MS = 12_000;
    /** A fix farther than this from the desk is treated as "not in the city". */
    private static final double MAX_START_KM = 60;

    public static Intent intent(Context c, String deskName, double lat, double lng) {
        return new Intent(c, MapRouteActivity.class)
                .putExtra(EXTRA_NAME, deskName)
                .putExtra(EXTRA_LAT, lat)
                .putExtra(EXTRA_LNG, lng);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MapView map;
    private GeoPoint destination;
    private GeoPoint start;
    private String startLabel;
    private String mode = GeoapifyClient.MODE_WALK;
    private Marker startMarker;
    private Polyline routeLine;
    private TextView stats, from;
    private View progress, useLocation;
    private LocationManager locationManager;
    private LocationListener listener;
    private int routeRequest;   // ignore answers to requests that were superseded

    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) findMe();
                else useSavedLocation(getString(R.string.route_from_saved_denied));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map_route);
        Ui.padForSystemBars(findViewById(R.id.routeBars));

        destination = GeoMap.point(getIntent().getDoubleExtra(EXTRA_LAT, 0), getIntent().getDoubleExtra(EXTRA_LNG, 0));
        String name = getIntent().getStringExtra(EXTRA_NAME);
        ((TextView) findViewById(R.id.textDestination)).setText(name);
        stats = findViewById(R.id.textRouteStats);
        from = findViewById(R.id.textRouteFrom);
        progress = findViewById(R.id.progressRoute);
        useLocation = findViewById(R.id.buttonUseLocation);

        map = findViewById(R.id.routeMap);
        GeoMap.setup(map, destination.getLatitude(), destination.getLongitude(), 15.0);
        // the summary card covers the bottom of the map, so put the +/- buttons on the right edge
        map.getZoomController().getDisplay().setPositions(false,
                CustomZoomButtonsDisplay.HorizontalPosition.RIGHT, CustomZoomButtonsDisplay.VerticalPosition.CENTER);
        Marker desk = new Marker(map);
        desk.setPosition(destination);
        desk.setIcon(GeoMap.destinationPin(this));
        desk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        desk.setTitle(name);
        desk.setSubDescription(getString(R.string.route_destination_hint));
        map.getOverlays().add(desk);       // tapping it shows its name in a bubble

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonFit).setOnClickListener(v -> fitRoute());
        useLocation.setOnClickListener(v -> askForLocation());
        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleMode);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            mode = id == R.id.buttonDrive ? GeoapifyClient.MODE_DRIVE : GeoapifyClient.MODE_WALK;
            if (start != null) requestRoute();
        });

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        stats.setText(R.string.route_finding_you);
        askForLocation();
    }

    // ------------------------------------------------------------------ where am I?
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askForLocation() {
        useLocation.setVisibility(View.GONE);
        if (hasLocationPermission()) findMe();
        else askLocation.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission()
    private void findMe() {
        if (!hasLocationPermission() || locationManager == null) {
            useSavedLocation(getString(R.string.route_from_saved_denied));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        stats.setText(R.string.route_finding_you);

        // 1. a recent last-known fix answers instantly
        Location best = null;
        for (String p : locationManager.getProviders(true)) {
            Location l = locationManager.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 2 * 60_000L) {
            onLocation(best);
            return;
        }

        // 2. otherwise ask for one fresh fix, with a timeout
        List<String> providers = new ArrayList<>();
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER);
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
        if (providers.isEmpty()) {
            if (best != null) onLocation(best);
            else useSavedLocation(getString(R.string.route_from_saved_off));
            return;
        }
        final Location fallback = best;
        listener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) { onLocation(location); }
            // older Android versions require these to be implemented
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(@NonNull String provider) { }
            @Override public void onProviderDisabled(@NonNull String provider) { }
        };
        for (String p : providers) locationManager.requestLocationUpdates(p, 0, 0, listener, Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (listener == null) return;
            stopListening();
            if (fallback != null) onLocation(fallback);
            else useSavedLocation(getString(R.string.route_from_saved_timeout));
        }, LOCATION_TIMEOUT_MS);
    }

    private void onLocation(Location l) {
        stopListening();
        if (isFinishing()) return;
        GeoPoint here = GeoMap.point(l.getLatitude(), l.getLongitude());
        double km = DatabaseHelper.km(here.getLatitude(), here.getLongitude(), destination.getLatitude(), destination.getLongitude());
        if (km > MAX_START_KM) {
            useSavedLocation(getString(R.string.route_from_saved_far, km));
            return;
        }
        setStart(here, getString(R.string.route_from_gps));
    }

    private void useSavedLocation(String why) {
        stopListening();
        DatabaseHelper.User me = Session.user(this);
        double[] ll = me != null && (me.lat != 0 || me.lng != 0) ? new double[]{me.lat, me.lng}
                : Geo.fallback(me != null ? me.barangay : "Villamonte");
        setStart(GeoMap.point(ll[0], ll[1]), why);
        useLocation.setVisibility(View.VISIBLE); // let them try their real location again
    }

    private void setStart(GeoPoint p, String label) {
        start = p;
        startLabel = label;
        if (startMarker == null) {
            startMarker = new Marker(map);
            startMarker.setIcon(GeoMap.youDot(this));
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            startMarker.setTitle(getString(R.string.map_you));
            map.getOverlays().add(startMarker);
        }
        startMarker.setPosition(p);
        requestRoute();
    }

    private void stopListening() {
        if (listener != null && locationManager != null) locationManager.removeUpdates(listener);
        listener = null;
    }

    // ------------------------------------------------------------------ the route
    private void requestRoute() {
        final int request = ++routeRequest;
        progress.setVisibility(View.VISIBLE);
        stats.setText(R.string.route_loading);
        from.setText(startLabel);
        GeoapifyClient.route(start.getLatitude(), start.getLongitude(),
                destination.getLatitude(), destination.getLongitude(), mode,
                new GeoapifyClient.Callback<GeoapifyClient.Route>() {
                    @Override
                    public void onResult(GeoapifyClient.Route r) {
                        if (request != routeRequest || isFinishing()) return;
                        progress.setVisibility(View.GONE);
                        drawRoute(r);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (request != routeRequest || isFinishing()) return;
                        progress.setVisibility(View.GONE);
                        if (routeLine != null) { map.getOverlays().remove(routeLine); routeLine = null; }
                        stats.setText(R.string.route_failed_title);
                        from.setText(getString(R.string.route_failed_body, e.getMessage() == null ? "" : e.getMessage()));
                        fit(java.util.Arrays.asList(start, destination));
                    }
                });
    }

    private void drawRoute(GeoapifyClient.Route r) {
        List<GeoPoint> pts = new ArrayList<>();
        for (double[] p : r.path) pts.add(GeoMap.point(p[0], p[1]));
        if (pts.isEmpty()) { pts.add(start); pts.add(destination); }
        if (routeLine != null) map.getOverlays().remove(routeLine);
        routeLine = new Polyline(map);
        routeLine.setPoints(pts);
        routeLine.getOutlinePaint().setColor(GeoapifyClient.MODE_DRIVE.equals(mode) ? 0xFF3A1B12 : 0xFFC8231B);
        routeLine.getOutlinePaint().setStrokeWidth(Ui.dp(this, 6));
        routeLine.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
        routeLine.setInfoWindow(null);
        map.getOverlays().add(1, routeLine);   // under the pins, above the attribution
        stats.setText(String.format(Locale.US, "%s · %s", Geo.km(r.km), minutes(r.minutes)));
        from.setText(startLabel);
        fitRoute();
    }

    private String minutes(double m) {
        long mins = Math.max(1, Math.round(m));
        if (mins < 60) return getString(R.string.n_min, mins);
        return getString(R.string.n_hr_min, mins / 60, mins % 60);
    }

    private void fitRoute() {
        List<GeoPoint> pts = new ArrayList<>();
        if (routeLine != null) pts.addAll(routeLine.getActualPoints());
        if (start != null) pts.add(start);
        pts.add(destination);
        fit(pts);
    }

    /** Fit the route into the gap between the top bar and the summary card floating over the map. */
    private void fit(List<GeoPoint> pts) {
        View top = findViewById(R.id.topCard), bottom = findViewById(R.id.bottomCard);
        int topCovered = top.getBottom() + Ui.dp(this, 8);
        int bottomCovered = map.getHeight() - bottom.getTop() + Ui.dp(this, 8);
        if (map.getHeight() == 0) { topCovered = 0; bottomCovered = 0; }
        GeoMap.fit(map, pts, Math.max(0, topCovered), Math.max(0, bottomCovered));
        map.invalidate();
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopListening();
        if (map != null) map.onDetach();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }
}
