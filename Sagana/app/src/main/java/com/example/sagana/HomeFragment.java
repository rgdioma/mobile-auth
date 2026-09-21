package com.example.sagana;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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
import com.google.android.material.slider.Slider;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

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

        String name = requireActivity().getIntent().getStringExtra(HomeActivity.EXTRA_DISPLAY_NAME);
        if (name == null) {
            name = getString(R.string.demo_display_name);
        }
        String greeting = getString(greetingForNow());
        ((TextView) view.findViewById(R.id.textGreeting)).setText(
                getString(R.string.home_greeting, greeting, name));

        TextView withinKm = view.findViewById(R.id.textWithinKm);
        Slider slider = view.findViewById(R.id.sliderDistance);
        withinKm.setText(getString(R.string.within_km, (int) slider.getValue()));
        slider.addOnChangeListener((s, value, fromUser) -> withinKm.setText(getString(R.string.within_km, (int) value)));

        ((TextView) view.findViewById(R.id.textListingCount)).setText(
                getString(R.string.listing_count, SAMPLE_LISTINGS.size()));

        LinearLayout container = view.findViewById(R.id.listingContainer);
        LayoutInflater inflater = LayoutInflater.from(view.getContext());
        for (Listing listing : SAMPLE_LISTINGS) {
            container.addView(bindListing(inflater.inflate(R.layout.item_listing, container, false), listing));
        }
    }

    private View bindListing(View card, Listing listing) {
        ((TextView) card.findViewById(R.id.textTitle)).setText(listing.title);
        ((TextView) card.findViewById(R.id.textDetail)).setText(listing.detail());
        ((TextView) card.findViewById(R.id.textDistance)).setText(listing.distance);
        ((TextView) card.findViewById(R.id.textPlaceholder)).setText(
                getString(R.string.photo_hint_card, listing.photoHint));

        int pillColor = listing.urgent ? R.color.chili : R.color.mango;
        int textColor = listing.urgent ? R.color.white : R.color.adobo;
        
        TextView textTimeLeft = card.findViewById(R.id.textTimeLeft);
        textTimeLeft.setText(listing.timeLeft);
        textTimeLeft.setTextColor(ContextCompat.getColor(card.getContext(), textColor));
        textTimeLeft.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(card.getContext(), pillColor)));
        TextViewCompat.setCompoundDrawableTintList(
                textTimeLeft, ColorStateList.valueOf(ContextCompat.getColor(card.getContext(), textColor))
        );

        card.setOnClickListener(v -> openDetails(listing));

        return card;
    }

    private void openDetails(Listing listing) {
        Intent intent = new Intent(requireContext(), FoodDetailsActivity.class);
        intent.putExtra(FoodDetailsActivity.EXTRA_TITLE, listing.title);
        intent.putExtra(FoodDetailsActivity.EXTRA_QUANTITY, listing.quantity);
        intent.putExtra(FoodDetailsActivity.EXTRA_CATEGORY, listing.category);
        intent.putExtra(FoodDetailsActivity.EXTRA_DISTANCE, listing.distance);
        intent.putExtra(FoodDetailsActivity.EXTRA_DONOR, listing.donor);
        intent.putExtra(FoodDetailsActivity.EXTRA_PHOTO_HINT, listing.photoHint);
        intent.putExtra(FoodDetailsActivity.EXTRA_REMAINING_MILLIS, listing.remainingMillis);
        startActivity(intent);
    }

    private int greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour <= 11) {
            return R.string.greeting_morning;
        } else if (hour <= 17) {
            return R.string.greeting_afternoon;
        } else {
            return R.string.greeting_evening;
        }
    }

    private static class Listing {
        final String title;
        final String quantity;
        final String donor;
        final String category;
        final String distance;
        final String photoHint;
        final String timeLeft;
        final boolean urgent;
        final long remainingMillis;

        Listing(String title, String quantity, String donor, String category, String distance,
                String photoHint, String timeLeft, boolean urgent, long remainingMillis) {
            this.title = title;
            this.quantity = quantity;
            this.donor = donor;
            this.category = category;
            this.distance = distance;
            this.photoHint = photoHint;
            this.timeLeft = timeLeft;
            this.urgent = urgent;
            this.remainingMillis = remainingMillis;
        }

        /** The listing card shows the quantity and who is giving it away. */
        String detail() {
            return quantity + " · " + donor;
        }
    }

    private static final List<Listing> SAMPLE_LISTINGS = Arrays.asList(
            new Listing("Pancit bihon", "8 packs", "Aling Nena's Carinderia", "Cooked", "0.4 km",
                    "pancit bihon in foil trays", "29m 42s left", true,
                    TimeUnit.MINUTES.toMillis(29) + TimeUnit.SECONDS.toMillis(42)),
            new Listing("Pandesal", "3 dozen", "Golden Crust Bakery", "Bread", "0.9 km",
                    "pandesal in a paper bag", "2h 19m left", false,
                    TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(19)),
            new Listing("Saba bananas", "2 bunches", "Dela Cruz household", "Produce", "1.3 km",
                    "two banana bunches", "5h 04m left", false,
                    TimeUnit.HOURS.toMillis(5) + TimeUnit.MINUTES.toMillis(4)),
            new Listing("Chicken adobo", "5 servings", "Tita Baby's Kitchen", "Cooked", "1.8 km",
                    "adobo in lidded tubs", "48m 10s left", true,
                    TimeUnit.MINUTES.toMillis(48) + TimeUnit.SECONDS.toMillis(10)),
            new Listing("Rice", "4 kg", "Brgy. San Roque pantry", "Packaged", "2.6 km",
                    "rice in a sealed sack", "1 day left", false,
                    TimeUnit.DAYS.toMillis(1)),
            new Listing("Canned sardines", "12 cans", "Santos sari-sari store", "Packaged", "3.4 km",
                    "sardine cans on a tray", "3 days left", false,
                    TimeUnit.DAYS.toMillis(3))
    );
}
