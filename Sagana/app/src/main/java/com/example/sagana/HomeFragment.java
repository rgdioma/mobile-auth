package com.example.sagana;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;

/**
 * Feature 2: proximity-based matching. Reads open listings from SQLite (READ),
 * sorts them by distance from the member's saved coordinates, and filters by
 * radius, category and search text. The Map view is an interactive Geoapify map
 * whose pins open the food at that desk and offer directions (MapRouteActivity).
 */
public class HomeFragment extends Fragment {

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final List<TextView> countdowns = new ArrayList<>();
    private final List<Long> countdownEnds = new ArrayList<>();

    private DatabaseHelper.User user;
    private List<DatabaseHelper.Listing> all = new ArrayList<>();
    private float radiusKm = 6f;
    private String category = "All";
    private String query = "";
    private boolean showMap;
    private MapView map;
    private final List<Overlay> mapLayers = new ArrayList<>();
    private String lastMapKey;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (int i = 0; i < countdowns.size(); i++) styleTimeLeft(countdowns.get(i), countdownEnds.get(i) - now);
            ticker.postDelayed(this, 1000);
        }
    };

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Let the red header run under the status bar, keeping its content below it.
        View header = view.findViewById(R.id.header);
        final int headerTopPadding = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), headerTopPadding + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(header);

        user = Session.user(requireContext());
        String name = user != null ? user.firstName() : getString(R.string.demo_display_name);
        ((TextView) view.findViewById(R.id.textGreeting)).setText(
                getString(R.string.home_greeting, getString(greetingForNow()), name));

        view.findViewById(R.id.buttonNotifications).setOnClickListener(v ->
                ((HomeActivity) requireActivity()).selectTab(R.id.nav_alerts));

        TextView withinKm = view.findViewById(R.id.textWithinKm);
        Slider slider = view.findViewById(R.id.sliderDistance);
        radiusKm = slider.getValue();
        withinKm.setText(getString(R.string.within_km_f, radiusKm));
        slider.addOnChangeListener((s, value, fromUser) -> {
            radiusKm = value;
            withinKm.setText(getString(R.string.within_km_f, value));
            render(false);
        });

        ChipGroup chips = view.findViewById(R.id.chipGroupCategory);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = group.findViewById(ids.get(0));
            category = c.getText().toString();
            render(true);
        });

        EditText search = view.findViewById(R.id.editSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.US);
                render(false);
            }
        });

        double[] start = home();
        map = view.findViewById(R.id.mapView);
        GeoMap.setup(map, start[0], start[1], 13.5);
        GeoMap.keepTouchesInside(map);

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleView);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            showMap = id == R.id.buttonMap;
            view.findViewById(R.id.mapSection).setVisibility(showMap ? View.VISIBLE : View.GONE);
            render(false);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        all = DatabaseHelper.get(requireContext()).getOpenListings();
        render(true);
        ticker.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        ticker.removeCallbacks(tick);
    }

    @Override
    public void onDestroyView() {
        if (map != null) map.onDetach();
        map = null;
        super.onDestroyView();
    }

    private double[] home() {
        if (user != null && (user.lat != 0 || user.lng != 0)) return new double[]{user.lat, user.lng};
        return Geo.fallback(user != null ? user.barangay : "Villamonte");
    }

    private void render(boolean animate) {
        View view = getView();
        if (view == null) return;
        double[] me = home();
        List<DatabaseHelper.Listing> rows = new ArrayList<>();
        for (DatabaseHelper.Listing l : all) {
            if (!l.isOpen()) continue;
            l.distanceKm = DatabaseHelper.km(me[0], me[1], l.lat, l.lng);
            if (l.distanceKm > radiusKm) continue;
            if (!"All".equals(category) && !category.equals(l.category)) continue;
            if (!query.isEmpty() && !(l.name + " " + l.donor + " " + l.category).toLowerCase(Locale.US).contains(query)) continue;
            rows.add(l);
        }
        rows.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));

        ((TextView) view.findViewById(R.id.textListingCount)).setText(
                getResources().getQuantityString(R.plurals.listing_count, rows.size(), rows.size()));
        view.findViewById(R.id.textEmpty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);

        LinearLayout container = view.findViewById(R.id.listingContainer);
        container.removeAllViews();
        countdowns.clear();
        countdownEnds.clear();
        LayoutInflater inflater = LayoutInflater.from(view.getContext());
        for (DatabaseHelper.Listing l : rows) {
            container.addView(bindListing(inflater.inflate(R.layout.item_listing, container, false), l));
        }
        if (animate) Ui.stagger(container);
        if (showMap) showOnMap(me, rows);
    }

    // ------------------------------------------------------------------ interactive map
    /**
     * Draws the member (green dot), the search radius, and one tappable red pin per pickup
     * desk that has food in range, on Geoapify map tiles. Tapping a pin opens a sheet with
     * the food waiting there and a "Get directions" button.
     */
    private void showOnMap(double[] me, List<DatabaseHelper.Listing> rows) {
        View view = getView();
        if (view == null || map == null) return;
        if (!GeoapifyClient.hasKey()) {
            view.findViewById(R.id.textMapError).setVisibility(View.VISIBLE);
            return;
        }
        map.getOverlays().removeAll(mapLayers);
        mapLayers.clear();

        // search radius
        Polygon radius = new Polygon(map);
        radius.setPoints(Polygon.pointsAsCircle(GeoMap.point(me[0], me[1]), radiusKm * 1000.0));
        radius.getFillPaint().setColor(0x14C8231B);
        radius.getOutlinePaint().setColor(0x66C8231B);
        radius.getOutlinePaint().setStrokeWidth(Ui.dp(requireContext(), 1.5f));
        radius.setInfoWindow(null);
        radius.setOnClickListener((p, m, e) -> false); // taps fall through to the pins
        mapLayers.add(radius);

        // the member
        Marker you = new Marker(map);
        you.setPosition(GeoMap.point(me[0], me[1]));
        you.setIcon(GeoMap.youDot(requireContext()));
        you.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        you.setTitle(getString(R.string.map_you));
        you.setInfoWindow(null);
        you.setOnMarkerClickListener((m, mv) -> {
            Toast.makeText(requireContext(), R.string.map_you_saved, Toast.LENGTH_SHORT).show();
            return true;
        });
        mapLayers.add(you);

        // one pin per desk, holding every listing waiting there
        Map<String, List<DatabaseHelper.Listing>> byDesk = new LinkedHashMap<>();
        for (DatabaseHelper.Listing l : rows) {
            List<DatabaseHelper.Listing> list = byDesk.get(l.desk);
            if (list == null) { list = new ArrayList<>(); byDesk.put(l.desk, list); }
            list.add(l);
        }
        List<GeoPoint> fitPoints = new ArrayList<>();
        fitPoints.add(GeoMap.point(me[0], me[1]));
        for (Map.Entry<String, List<DatabaseHelper.Listing>> e : byDesk.entrySet()) {
            DatabaseHelper.Listing first = e.getValue().get(0);
            Marker pin = new Marker(map);
            pin.setPosition(GeoMap.point(first.lat, first.lng));
            pin.setIcon(GeoMap.deskPin(requireContext(), e.getValue().size()));
            pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            pin.setTitle(e.getKey());
            pin.setInfoWindow(null);
            pin.setOnMarkerClickListener((m, mv) -> {
                showDeskSheet(e.getKey(), e.getValue(), me);
                return true;
            });
            mapLayers.add(pin);
            fitPoints.add(pin.getPosition());
        }
        // pins go on top of the radius circle; the copyright overlay stays first
        map.getOverlays().addAll(mapLayers);
        String key = byDesk.keySet().toString() + radiusKm;
        if (!key.equals(lastMapKey)) {                    // re-frame only when the pins changed
            lastMapKey = key;
            GeoMap.fit(map, fitPoints);
        }
        map.invalidate();
    }

    /** Bottom sheet for a tapped pin: the desk, the food waiting there, and directions. */
    private void showDeskSheet(String desk, List<DatabaseHelper.Listing> listings, double[] me) {
        if (!isAdded()) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_desk, null);
        DatabaseHelper.Listing first = listings.get(0);
        double km = DatabaseHelper.km(me[0], me[1], first.lat, first.lng);
        ((TextView) v.findViewById(R.id.textDeskTitle)).setText(desk);
        ((TextView) v.findViewById(R.id.textDeskInfo)).setText(getString(R.string.sheet_desk_info,
                km, Geo.walkMinutes(km),
                getResources().getQuantityString(R.plurals.listing_count, listings.size(), listings.size())));

        LinearLayout rows = v.findViewById(R.id.foodRows);
        long now = System.currentTimeMillis();
        for (int i = 0; i < listings.size(); i++) {
            DatabaseHelper.Listing l = listings.get(i);
            if (i > 0) {
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.input_border));
                rows.addView(divider);
            }
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_sheet_food, rows, false);
            ((TextView) row.findViewById(R.id.textFoodName)).setText(l.name);
            ((TextView) row.findViewById(R.id.textFoodInfo)).setText(
                    l.qty + " · " + l.donor + " · " + Ui.timeLeft(l.pickupEnd - now));
            row.setOnClickListener(x -> {
                sheet.dismiss();
                Intent intent = new Intent(requireContext(), FoodDetailsActivity.class);
                intent.putExtra(FoodDetailsActivity.EXTRA_LISTING_ID, l.id);
                Nav.push(requireActivity(), intent);
            });
            rows.addView(row);
        }

        v.findViewById(R.id.buttonDirections).setOnClickListener(x -> {
            sheet.dismiss();
            Nav.push(requireActivity(), MapRouteActivity.intent(requireContext(), desk, first.lat, first.lng));
        });
        sheet.setContentView(v);
        sheet.show();
    }

    private View bindListing(View card, DatabaseHelper.Listing listing) {
        ((TextView) card.findViewById(R.id.textTitle)).setText(listing.name);
        ((TextView) card.findViewById(R.id.textDetail)).setText(listing.qty + " · " + listing.donor);
        ((TextView) card.findViewById(R.id.textDistance)).setText(getString(R.string.distance_walk,
                listing.distanceKm, Geo.walkMinutes(listing.distanceKm)));
        ((TextView) card.findViewById(R.id.textPlaceholder)).setText(getString(R.string.photo_hint_card, listing.name.toLowerCase(Locale.US)));

        if (Ui.loadPhoto(card.findViewById(R.id.imagePhoto), listing.photo)) {
            card.findViewById(R.id.photoCard).setVisibility(View.VISIBLE);
            card.findViewById(R.id.photoPlaceholder).setVisibility(View.GONE);
        }

        TextView textTimeLeft = card.findViewById(R.id.textTimeLeft);
        styleTimeLeft(textTimeLeft, listing.pickupEnd - System.currentTimeMillis());
        countdowns.add(textTimeLeft);
        countdownEnds.add(listing.pickupEnd);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), FoodDetailsActivity.class);
            intent.putExtra(FoodDetailsActivity.EXTRA_LISTING_ID, listing.id);
            Nav.push(requireActivity(), intent);
        });
        return card;
    }

    /** Mango pill normally, red under 30 minutes, grey once closed. */
    private void styleTimeLeft(TextView pill, long ms) {
        if (!isAdded()) return;
        boolean urgent = ms < 30 * 60_000L;
        int bg = ms <= 0 ? R.color.input_border : urgent ? R.color.chili : R.color.mango;
        int fg = ms <= 0 ? R.color.text_secondary : urgent ? R.color.white : R.color.adobo;
        pill.setText(Ui.timeLeft(ms));
        pill.setTextColor(ContextCompat.getColor(pill.getContext(), fg));
        pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(pill.getContext(), bg)));
        TextViewCompat.setCompoundDrawableTintList(pill, ColorStateList.valueOf(ContextCompat.getColor(pill.getContext(), fg)));
    }

    private int greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour <= 11) return R.string.greeting_morning;
        if (hour <= 17) return R.string.greeting_afternoon;
        return R.string.greeting_evening;
    }
}
