package com.example.sagana;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature 3: expiry and safety alerts, built live from the database:
 * listings closing within 2 hours, today's scheduled pickups, and what's nearby.
 */
public class AlertsFragment extends Fragment {

    private static final long TWO_HOURS = 2 * 3_600_000L;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final List<TextView> pills = new ArrayList<>();
    private final List<Long> ends = new ArrayList<>();

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (int i = 0; i < pills.size(); i++) pills.get(i).setText(Ui.timeLeft(ends.get(i) - now));
            countdownHandler.postDelayed(this, 1000);
        }
    };

    public AlertsFragment() {
        super(R.layout.fragment_alerts);
    }

    /** Used for the badge on the Alerts tab. */
    public static int closingSoonCount(Context c) {
        int n = 0;
        long now = System.currentTimeMillis();
        for (DatabaseHelper.Listing l : DatabaseHelper.get(c).getOpenListings()) {
            if (l.pickupEnd - now < TWO_HOURS) n++;
        }
        return n;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Ui.padForStatusBar(view);
        bindSwitch(view, R.id.switchExpiry, "expiry");
        bindSwitch(view, R.id.switchNewFood, "nearby");
        bindSwitch(view, R.id.switchReminders, "reminders");
    }

    private void bindSwitch(View view, int id, String key) {
        CompoundButton s = view.findViewById(id);
        s.setChecked(Session.alertOn(requireContext(), key));
        s.setOnCheckedChangeListener((b, on) -> {
            Session.setAlert(requireContext(), key, on);
            render(false);
            if (requireActivity() instanceof HomeActivity) ((HomeActivity) requireActivity()).refreshAlertBadge();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        render(true);
        countdownHandler.post(countdownRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void render(boolean animate) {
        View view = getView();
        if (view == null) return;
        Context c = requireContext();
        DatabaseHelper db = DatabaseHelper.get(c);
        DatabaseHelper.User me = Session.user(c);
        LinearLayout container = view.findViewById(R.id.alertContainer);
        container.removeAllViews();
        pills.clear();
        ends.clear();
        LayoutInflater inflater = LayoutInflater.from(c);
        long now = System.currentTimeMillis();
        double[] home = me != null ? new double[]{me.lat, me.lng} : Geo.fallback("Villamonte");
        List<DatabaseHelper.Listing> open = db.getOpenListings();

        if (Session.alertOn(c, "expiry")) {
            for (DatabaseHelper.Listing l : open) {
                if (l.pickupEnd - now >= TWO_HOURS) continue;
                double km = DatabaseHelper.km(home[0], home[1], l.lat, l.lng);
                View row = addRow(container, inflater, getString(R.string.alert_closing_soon, l.name),
                        l.donor + " · " + Geo.km(km), R.drawable.ic_warning_triangle, R.color.chili_light, R.color.chili);
                TextView pill = row.findViewById(R.id.textPill);
                pill.setVisibility(View.VISIBLE);
                boolean urgent = l.pickupEnd - now < 30 * 60_000L;
                pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, urgent ? R.color.chili : R.color.mango)));
                pill.setTextColor(ContextCompat.getColor(c, urgent ? R.color.white : R.color.adobo));
                pill.setText(Ui.timeLeft(l.pickupEnd - now));
                pills.add(pill);
                ends.add(l.pickupEnd);
                row.setOnClickListener(v -> {
                    Intent i = new Intent(c, FoodDetailsActivity.class);
                    i.putExtra(FoodDetailsActivity.EXTRA_LISTING_ID, l.id);
                    Nav.push(requireActivity(), i);
                });
            }
        }

        if (Session.alertOn(c, "reminders") && me != null) {
            for (DatabaseHelper.Booking b : db.getMyBookings(me.email)) {
                if (!"Scheduled".equals(b.status)) continue;
                View row = addRow(container, inflater, getString(R.string.alert_pickup_at, b.slot, Ui.prettyDay(b.date)),
                        b.food + " · " + getString(R.string.code_label, b.code), R.drawable.ic_nav_pickups, R.color.butter, R.color.placeholder_ink);
                row.findViewById(R.id.imageArrow).setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> ((HomeActivity) requireActivity()).selectTab(R.id.nav_pickups));
            }
        }

        if (Session.alertOn(c, "nearby")) {
            int within = 0;
            double closest = Double.MAX_VALUE;
            for (DatabaseHelper.Listing l : open) {
                double km = DatabaseHelper.km(home[0], home[1], l.lat, l.lng);
                if (km <= 5) within++;
                closest = Math.min(closest, km);
            }
            View row = addRow(container, inflater,
                    getResources().getQuantityString(R.plurals.listings_nearby, within, within, 5),
                    open.isEmpty() ? getString(R.string.nothing_open) : getString(R.string.closest_is, (float) closest),
                    R.drawable.ic_pin, R.color.leaf_light, R.color.leaf);
            row.findViewById(R.id.imageArrow).setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> ((HomeActivity) requireActivity()).selectTab(R.id.nav_home));
        }

        if (container.getChildCount() == 0) {
            TextView empty = new TextView(c);
            empty.setText(R.string.alerts_all_off);
            empty.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
            empty.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            container.addView(empty);
        }
        if (animate) Ui.stagger(container);
    }

    private View addRow(LinearLayout container, LayoutInflater inflater, String title, String subtitle,
                        int icon, int iconBg, int iconTint) {
        Context c = requireContext();
        if (container.getChildCount() > 0) {
            View divider = new View(c);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(0, 4, 0, 4);
            divider.setLayoutParams(params);
            divider.setBackgroundColor(ContextCompat.getColor(c, R.color.input_border));
            container.addView(divider);
        }
        View row = inflater.inflate(R.layout.item_alert, container, false);
        ((TextView) row.findViewById(R.id.textTitle)).setText(title);
        ((TextView) row.findViewById(R.id.textSubtitle)).setText(subtitle);
        FrameLayout iconContainer = row.findViewById(R.id.iconContainer);
        iconContainer.setBackgroundResource(R.drawable.bg_circle_solid);
        iconContainer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, iconBg)));
        ImageView iconView = row.findViewById(R.id.imageIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, iconTint)));
        android.util.TypedValue ripple = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        container.addView(row);
        return row;
    }
}
