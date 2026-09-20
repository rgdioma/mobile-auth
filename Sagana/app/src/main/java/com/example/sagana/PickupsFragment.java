package com.example.sagana;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class PickupsFragment extends Fragment {

    public PickupsFragment() {
        super(R.layout.fragment_pickups);
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

        setupBanner(view);
    }

    private void setupBanner(View view) {
        TextView textBanner = view.findViewById(R.id.textBanner);
        String fullText = getString(R.string.pickup_banner_text) + " " + getString(R.string.browse_food);
        SpannableString ss = new SpannableString(fullText);

        String browseFood = getString(R.string.browse_food);
        int start = fullText.indexOf(browseFood);
        int end = start + browseFood.length();

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // Navigate to home or show message
                Toast.makeText(getContext(), R.string.toast_coming_soon, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        };

        ss.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.chili)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textBanner.setText(ss);
        textBanner.setMovementMethod(LinkMovementMethod.getInstance());
        textBanner.setHighlightColor(Color.TRANSPARENT);
    }
}
