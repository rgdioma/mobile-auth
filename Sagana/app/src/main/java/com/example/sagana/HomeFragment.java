package com.example.sagana;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
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

/**
 * Feature 2: proximity-based matching. Reads open listings from SQLite (READ),
 * sorts them by distance from the member's saved coordinates, and filters by
 * radius, category and search text. The Map view is a Geoapify static map.
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
    private String lastMapUrl;

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
        all = DatabaseHelper.get(requireContext()).getOpenListings();
        render(true);
        ticker.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
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
        if (showMap) loadMap(me, rows);
    }

    /** One Geoapify Static Maps request showing the member and every desk with food in range. */
    private void loadMap(double[] me, List<DatabaseHelper.Listing> rows) {
        View view = getView();
        if (view == null) return;
        Map<String, double[]> desks = new LinkedHashMap<>();
        for (DatabaseHelper.Listing l : rows) desks.put(l.desk, new double[]{l.lat, l.lng});
        String url = GeoapifyClient.nearbyMapUrl(me[0], me[1], new ArrayList<>(desks.values()));
        if (url.equals(lastMapUrl)) return;
        lastMapUrl = url;
        ImageView map = view.findViewById(R.id.imageMap);
        View progress = view.findViewById(R.id.progressMap);
        View error = view.findViewById(R.id.textMapError);
        if (!GeoapifyClient.hasKey()) {
            progress.setVisibility(View.GONE);
            error.setVisibility(View.VISIBLE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        Glide.with(this).load(url).timeout(20000).listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean first) {
                progress.setVisibility(View.GONE);
                error.setVisibility(View.VISIBLE);
                lastMapUrl = null;
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable r, @NonNull Object model, Target<Drawable> target, @NonNull DataSource ds, boolean first) {
                progress.setVisibility(View.GONE);
                return false;
            }
        }).transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).into(map);
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
