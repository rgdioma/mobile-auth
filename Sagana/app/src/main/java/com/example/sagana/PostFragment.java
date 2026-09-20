package com.example.sagana;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

public class PostFragment extends Fragment {

    private int quantity = 1;

    public PostFragment() {
        super(R.layout.fragment_post);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Adjust padding for status bar since HomeActivity uses enableEdgeToEdge()
        final int basePadding = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), basePadding + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        setupQuantityStepper(view);
        setupTimePickers(view);
    }

    private void setupQuantityStepper(View view) {
        TextView tvQuantity = view.findViewById(R.id.tvQuantity);
        ImageButton btnMinus = view.findViewById(R.id.btnMinus);
        ImageButton btnPlus = view.findViewById(R.id.btnPlus);

        // Sync initial state
        updateQuantityDisplay(tvQuantity);

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateQuantityDisplay(tvQuantity);
            }
        });

        btnPlus.setOnClickListener(v -> {
            quantity++;
            updateQuantityDisplay(tvQuantity);
        });
    }

    private void updateQuantityDisplay(TextView textView) {
        String text = getString(R.string.format_quantity, quantity);
        textView.setText(text);
    }

    private void setupTimePickers(View view) {
        TextView tvStartTime = view.findViewById(R.id.tvStartTime);
        TextView tvEndTime = view.findViewById(R.id.tvEndTime);

        tvStartTime.setOnClickListener(v -> showTimePicker(tvStartTime));
        tvEndTime.setOnClickListener(v -> showTimePicker(tvEndTime));
    }

    private void showTimePicker(TextView textView) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText(R.string.title_select_time)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            int hour = (picker.getHour() == 0 || picker.getHour() == 12) ? 12 : picker.getHour() % 12;
            String amPm = getString(picker.getHour() < 12 ? R.string.time_am : R.string.time_pm);
            String timeText = getString(R.string.format_time, hour, picker.getMinute(), amPm);
            textView.setText(timeText);
        });

        picker.show(getChildFragmentManager(), "time_picker");
    }
}
