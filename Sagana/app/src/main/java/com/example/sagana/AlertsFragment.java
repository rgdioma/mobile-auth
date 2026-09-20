package com.example.sagana;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AlertsFragment extends Fragment {

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final List<CountdownTask> countdownTasks = new ArrayList<>();

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            for (CountdownTask task : countdownTasks) {
                task.update();
            }
            countdownHandler.postDelayed(this, 1000);
        }
    };

    public AlertsFragment() {
        super(R.layout.fragment_alerts);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Adjust padding for status bar
        final int basePadding = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), basePadding + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        setupRecentAlerts(view);
    }

    @Override
    public void onStart() {
        super.onStart();
        countdownHandler.post(countdownRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void setupRecentAlerts(View view) {
        LinearLayout container = view.findViewById(R.id.alertContainer);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        countdownTasks.clear();

        List<Alert> alerts = Arrays.asList(
            new Alert(getString(R.string.alert_closing_soon, "Pancit bihon"), 
                "Aling Nena's Carinderia · 0.4 km", 
                TimeUnit.MINUTES.toMillis(42) + TimeUnit.SECONDS.toMillis(39), 
                R.drawable.ic_warning_triangle, R.color.chili_light, R.color.chili, R.color.mango),
            new Alert(getString(R.string.alert_closing_soon, "Lumpiang togue"), 
                "Kusina ni Jun · 4.2 km", 
                TimeUnit.MINUTES.toMillis(17) + TimeUnit.SECONDS.toMillis(39), 
                R.drawable.ic_warning_triangle, R.color.chili_light, R.color.chili, R.color.chili),
            new Alert(getString(R.string.listings_nearby, 6, 5), 
                getString(R.string.closest_is, 0.4f), 
                -1, R.drawable.ic_pin, R.color.leaf_light, R.color.leaf, 0)
        );

        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            View alertView = inflater.inflate(R.layout.item_alert, container, false);
            
            ((TextView) alertView.findViewById(R.id.textTitle)).setText(alert.title);
            ((TextView) alertView.findViewById(R.id.textSubtitle)).setText(alert.subtitle);
            
            FrameLayout iconContainer = alertView.findViewById(R.id.iconContainer);
            ImageView iconView = alertView.findViewById(R.id.imageIcon);
            
            iconContainer.setBackgroundResource(R.drawable.bg_circle_solid);
            iconContainer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), alert.iconBgColor)));
            
            iconView.setImageResource(alert.iconRes);
            iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), alert.iconTint)));

            if (alert.durationMillis > 0) {
                TextView pill = alertView.findViewById(R.id.textPill);
                pill.setVisibility(View.VISIBLE);
                pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), alert.pillColor)));
                
                CountdownTask task = new CountdownTask(pill, alert.durationMillis);
                task.update();
                countdownTasks.add(task);
            } else {
                alertView.findViewById(R.id.imageArrow).setVisibility(View.VISIBLE);
            }

            container.addView(alertView);

            if (i < alerts.size() - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(0, 4, 0, 4);
                divider.setLayoutParams(params);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.input_border));
                container.addView(divider);
            }
        }
    }

    private class CountdownTask {
        final TextView textView;
        final long endTimeMillis;

        CountdownTask(TextView textView, long durationMillis) {
            this.textView = textView;
            this.endTimeMillis = System.currentTimeMillis() + durationMillis;
        }

        void update() {
            long remaining = endTimeMillis - System.currentTimeMillis();
            if (remaining <= 0) {
                textView.setText(R.string.countdown_finished);
                return;
            }
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
            textView.setText(getString(R.string.format_countdown, (int) minutes, (int) seconds));
        }
    }

    private static class Alert {
        final String title;
        final String subtitle;
        final long durationMillis;
        final int iconRes;
        final int iconBgColor;
        final int iconTint;
        final int pillColor;

        Alert(String title, String subtitle, long durationMillis, int iconRes, int iconBgColor, int iconTint, int pillColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.durationMillis = durationMillis;
            this.iconRes = iconRes;
            this.iconBgColor = iconBgColor;
            this.iconTint = iconTint;
            this.pillColor = pillColor;
        }
    }
}
