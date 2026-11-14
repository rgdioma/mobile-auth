package com.example.sagana;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.WindowCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Opening screen with audio (res/raw/opening_chime.wav).
 *
 * Timeline (about 3.3 s, tap anywhere to skip):
 *   0.00  chime starts, warm glow blooms behind the logo, sparkles begin rising
 *   0.10  the bowl drops in from above with an overshoot
 *   0.80  it lands with a squash-and-stretch; sonar rings start pulsing outward
 *   0.85  three wisps of steam start rising from the bowl, one after another
 *   0.95  the heart on the bowl beats twice, in time with the chime's notes
 *   0.90  the letters S-a-g-a-n-a bounce up one by one
 *   1.55  a mango underline sweeps across, then the tagline rises
 *   1.40  the loading bar fills
 *   3.30  the whole screen zooms out into Login (or Home if already logged in)
 */
public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Animator> loops = new ArrayList<>();
    private MediaPlayer player;
    private boolean left;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        float scale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        boolean reduced = scale == 0f;

        buildLetters();
        View root = findViewById(R.id.splashRoot);
        root.setOnClickListener(v -> leave());

        if (reduced) {
            // Animations are switched off on this phone: show the finished screen briefly.
            playChime();
            handler.postDelayed(this::leave, 1200);
            return;
        }

        hideEverything();
        root.post(this::runIntro);
        playChime();
    }

    // ------------------------------------------------------------------ build
    private void buildLetters() {
        LinearLayout row = findViewById(R.id.nameRow);
        String name = getString(R.string.brand_name);
        // Baloo 2 ExtraBold, the same display font as the HTML prototype
        Typeface baloo = ResourcesCompat.getFont(this, R.font.baloo2_extrabold);
        if (baloo == null) baloo = Typeface.create("sans-serif-black", Typeface.NORMAL);
        for (int i = 0; i < name.length(); i++) {
            TextView t = new TextView(this);
            t.setText(String.valueOf(name.charAt(i)));
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
            t.setTypeface(baloo);
            t.setIncludeFontPadding(false);
            t.setShadowLayer(dp(6), 0, dp(3), 0x40000000);
            row.addView(t);
        }
    }

    private void hideEverything() {
        int[] ids = {R.id.glow, R.id.bowlGroup, R.id.steam1, R.id.steam2, R.id.steam3,
                R.id.underline, R.id.tagline, R.id.bar, R.id.footer};
        for (int id : ids) findViewById(id).setAlpha(0f);
        LinearLayout row = findViewById(R.id.nameRow);
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setAlpha(0f);
        findViewById(R.id.barFill).setScaleX(0f);
        findViewById(R.id.underline).setScaleX(0f);
    }

    // ------------------------------------------------------------------ choreography
    private void runIntro() {
        View glow = findViewById(R.id.glow);
        View bowl = findViewById(R.id.bowlGroup);
        View heart = findViewById(R.id.heart);
        ParticleView particles = findViewById(R.id.particles);
        RippleRingsView rings = findViewById(R.id.rings);

        particles.start();

        // 1. glow blooms
        glow.setScaleX(0.3f);
        glow.setScaleY(0.3f);
        glow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(new DecelerateInterpolator()).start();

        // 2. bowl drops in, then squashes on landing
        bowl.setTranslationY(-dp(320));
        bowl.setScaleX(0.7f);
        bowl.setScaleY(0.7f);
        bowl.setPivotY(bowl.getHeight() * 0.85f);
        AnimatorSet drop = new AnimatorSet();
        drop.playTogether(
                ObjectAnimator.ofFloat(bowl, View.ALPHA, 0f, 1f).setDuration(250),
                ObjectAnimator.ofFloat(bowl, View.TRANSLATION_Y, -dp(320), 0f),
                ObjectAnimator.ofFloat(bowl, View.SCALE_X, 0.7f, 1f),
                ObjectAnimator.ofFloat(bowl, View.SCALE_Y, 0.7f, 1f));
        drop.setDuration(650);
        drop.setInterpolator(new OvershootInterpolator(1.1f));
        drop.setStartDelay(100);

        ObjectAnimator squash = ObjectAnimator.ofPropertyValuesHolder(bowl,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 0.96f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.86f, 1.04f, 1f));
        squash.setDuration(380);

        AnimatorSet bowlIn = new AnimatorSet();
        bowlIn.playSequentially(drop, squash);
        bowlIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (isFinishing()) return;
                // 3. rings start from the bowl's centre
                int[] loc = new int[2], rootLoc = new int[2];
                bowl.getLocationOnScreen(loc);
                rings.getLocationOnScreen(rootLoc);
                rings.setCenterY(loc[1] - rootLoc[1] + bowl.getHeight() * 0.5f);
                rings.start();
                startSteam();
            }
        });
        bowlIn.start();

        // 4. heart beats twice, in time with the chime
        heart.setPivotX(heart.getWidth() * 0.5f);
        heart.setPivotY(heart.getHeight() * 0.6f);
        ObjectAnimator beat = ObjectAnimator.ofPropertyValuesHolder(heart,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.28f, 1f, 1.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.28f, 1f, 1.2f, 1f));
        beat.setDuration(820);
        beat.setStartDelay(950);
        beat.setInterpolator(new AccelerateDecelerateInterpolator());
        beat.start();

        // 5. letters bounce in one by one
        LinearLayout row = findViewById(R.id.nameRow);
        for (int i = 0; i < row.getChildCount(); i++) {
            View letter = row.getChildAt(i);
            letter.setTranslationY(dp(46));
            letter.setRotation(i % 2 == 0 ? -12f : 10f);
            letter.setScaleX(0.6f);
            letter.setScaleY(0.6f);
            letter.animate().alpha(1f).translationY(0f).rotation(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(900 + i * 75L).setDuration(520)
                    .setInterpolator(new OvershootInterpolator(2.2f)).start();
        }
        int lettersDone = 900 + row.getChildCount() * 75 + 300;

        // 6. underline sweeps, tagline rises, footer fades
        View underline = findViewById(R.id.underline);
        underline.animate().alpha(1f).scaleX(1f).setStartDelay(lettersDone).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
        View tagline = findViewById(R.id.tagline);
        tagline.setTranslationY(dp(14));
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(lettersDone + 160).setDuration(520)
                .setInterpolator(new DecelerateInterpolator()).start();
        findViewById(R.id.footer).animate().alpha(1f).setStartDelay(lettersDone + 400).setDuration(600).start();

        // 7. loading bar fills
        View bar = findViewById(R.id.bar);
        View fill = findViewById(R.id.barFill);
        fill.setPivotX(0f);
        bar.animate().alpha(1f).setStartDelay(1300).setDuration(300).start();
        fill.animate().scaleX(1f).setStartDelay(1400).setDuration(1750)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();

        handler.postDelayed(this::leave, 3300);
    }

    /** Three wisps rise and fade in a staggered loop. */
    private void startSteam() {
        int[] ids = {R.id.steam1, R.id.steam2, R.id.steam3};
        for (int i = 0; i < ids.length; i++) {
            View s = findViewById(ids[i]);
            s.setPivotY(s.getHeight());
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(s,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dp(10), -dp(16)),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f, 1.15f, 0.9f));
            a.setDuration(1500);
            a.setStartDelay(i * 260L);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.start();
            loops.add(a);
        }
    }

    // ------------------------------------------------------------------ audio
    private void playChime() {
        try {
            player = MediaPlayer.create(this, R.raw.opening_chime);
            if (player != null) {
                player.setOnCompletionListener(MediaPlayer::release);
                player.start();
            }
        } catch (Exception ignored) {
            // no sound is better than no app
        }
    }

    // ------------------------------------------------------------------ exit
    private void leave() {
        if (left || isFinishing()) return;
        left = true;
        handler.removeCallbacksAndMessages(null);
        // Onboarding comes first for anyone who hasn't seen it on this install, even if a saved
        // session exists (it then continues to Home). After that: logged in -> Home, else Login.
        DatabaseHelper.User user = Session.user(this);
        Class<?> target = !Session.onboarded(this) ? OnboardingActivity.class
                : user != null ? HomeActivity.class : MainActivity.class;
        Intent next = new Intent(this, target);
        Nav.zoom(this, next);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (Animator a : loops) a.cancel();
        ParticleView p = findViewById(R.id.particles);
        if (p != null) p.stop();
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        super.onDestroy();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
