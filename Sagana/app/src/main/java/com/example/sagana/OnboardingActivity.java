package com.example.sagana;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;

/**
 * First-run onboarding, shown once after the opening animation to people who haven't
 * seen it yet. Four swipeable pages with a parallax transition, floating illustrations
 * and stretchy page dots, then on to Login.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int[] ICONS = {R.drawable.ic_nav_post, R.drawable.ic_pin, R.drawable.ic_qr_scan, R.drawable.ic_chart};
    private static final int[] TITLES = {R.string.onb_title_1, R.string.onb_title_2, R.string.onb_title_3, R.string.onb_title_4};
    private static final int[] BODIES = {R.string.onb_body_1, R.string.onb_body_2, R.string.onb_body_3, R.string.onb_body_4};
    private static final int[] OUTER = {R.color.butter, R.color.leaf_light, R.color.chili_light, R.color.butter};
    private static final int[] INNER = {R.color.mango, R.color.leaf, R.color.chili, R.color.chili};

    private ViewPager2 pager;
    private MaterialButton next;
    private LinearLayout dots;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);
        Ui.padForSystemBars(findViewById(R.id.onboardingRoot));

        pager = findViewById(R.id.pager);
        next = findViewById(R.id.buttonNext);
        dots = findViewById(R.id.dots);

        pager.setAdapter(new PageAdapter());
        pager.setOffscreenPageLimit(1);
        pager.setPageTransformer(this::transformPage);
        buildDots();
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                boolean last = position == ICONS.length - 1;
                next.setText(last ? R.string.action_get_started : R.string.action_next);
                findViewById(R.id.buttonSkip).animate().alpha(last ? 0f : 1f).setDuration(200).start();
            }
        });

        next.setOnClickListener(v -> {
            int p = pager.getCurrentItem();
            if (p < ICONS.length - 1) pager.setCurrentItem(p + 1, true);
            else finishOnboarding();
        });
        findViewById(R.id.buttonSkip).setOnClickListener(v -> finishOnboarding());
    }

    /** Parallax: the illustration moves slower than the text and the page fades/scales out. */
    private void transformPage(@NonNull View page, float position) {
        float abs = Math.abs(position);
        View art = page.findViewById(R.id.art);
        View title = page.findViewById(R.id.title);
        View body = page.findViewById(R.id.body);
        if (abs >= 1f) {
            page.setAlpha(0f);
            return;
        }
        page.setAlpha(1f - abs * 0.6f);
        float w = page.getWidth();
        art.setTranslationX(position * w * 0.45f);
        art.setScaleX(1f - abs * 0.25f);
        art.setScaleY(1f - abs * 0.25f);
        art.setRotation(position * -14f);
        title.setTranslationX(position * w * 0.15f);
        body.setTranslationX(position * w * 0.3f);
    }

    private void buildDots() {
        for (int i = 0; i < ICONS.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            d.setLayoutParams(lp);
            d.setBackgroundResource(R.drawable.bg_splash_bar_fill);
            dots.addView(d);
        }
        updateDots(0);
    }

    /** The current dot stretches into a red pill; the others shrink back to beige dots. */
    private void updateDots(int current) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == current;
            d.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, on ? R.color.chili : R.color.input_border)));
            int from = d.getLayoutParams().width, to = Ui.dp(this, on ? 26 : 8);
            ValueAnimator a = ValueAnimator.ofInt(from, to);
            a.setDuration(260);
            a.addUpdateListener(an -> {
                d.getLayoutParams().width = (int) an.getAnimatedValue();
                d.requestLayout();
            });
            a.start();
        }
    }

    private void finishOnboarding() {
        if (leaving) return;
        leaving = true;
        Session.setOnboarded(this);
        // already logged in (e.g. viewing the intro again from Profile) -> Home, otherwise Login
        boolean loggedIn = Session.user(this) != null;
        Intent next = new Intent(this, loggedIn ? HomeActivity.class : MainActivity.class);
        if (loggedIn) next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Nav.rise(this, next);
        finish();
    }

    // ------------------------------------------------------------------ pages
    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final View outer, inner, dotA, dotB, art;
            final ImageView icon;
            final TextView title, body;

            Holder(View v) {
                super(v);
                art = v.findViewById(R.id.art);
                outer = v.findViewById(R.id.ringOuter);
                inner = v.findViewById(R.id.ringInner);
                icon = v.findViewById(R.id.icon);
                dotA = v.findViewById(R.id.dotA);
                dotB = v.findViewById(R.id.dotB);
                title = v.findViewById(R.id.title);
                body = v.findViewById(R.id.body);
                // the illustration bobs up and down; the accent dots drift the other way
                float d = getResources().getDisplayMetrics().density;
                bob(inner, View.TRANSLATION_Y, -6 * d, 6 * d, 2400);
                bob(icon, View.TRANSLATION_Y, -9 * d, 9 * d, 2400);
                bob(dotA, View.TRANSLATION_Y, 8 * d, -8 * d, 1900);
                bob(dotB, View.TRANSLATION_X, -8 * d, 8 * d, 2100);
            }
        }

        private void bob(View v, android.util.Property<View, Float> prop, float a, float b, long ms) {
            ObjectAnimator o = ObjectAnimator.ofFloat(v, prop, a, b);
            o.setDuration(ms);
            o.setRepeatMode(ValueAnimator.REVERSE);
            o.setRepeatCount(ValueAnimator.INFINITE);
            o.setInterpolator(new AccelerateDecelerateInterpolator());
            o.start();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int i) {
            h.icon.setImageResource(ICONS[i]);
            h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(OnboardingActivity.this, R.color.white)));
            h.outer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(OnboardingActivity.this, OUTER[i])));
            h.inner.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(OnboardingActivity.this, INNER[i])));
            h.title.setText(TITLES[i]);
            h.body.setText(BODIES[i]);
        }

        @Override
        public int getItemCount() {
            return ICONS.length;
        }
    }
}
