package com.example.sagana;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small helpers shared by the screens. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Pads a tab's root below the status bar (HomeActivity draws edge to edge). */
    public static void padForStatusBar(View view) {
        final int base = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), base + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Pads a full-screen activity for both system bars. */
    public static void padForSystemBars(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /**
     * Shows a listing photo. "res:name" is a bundled drawable; anything else is a file the
     * member picked. Returns false if there is no photo, so the striped placeholder stays.
     */
    public static boolean loadPhoto(ImageView into, String photo) {
        if (photo == null || photo.isEmpty()) return false;
        Context c = into.getContext();
        if (photo.startsWith("res:")) {
            int id = FoodPhotos.resolve(c, photo.substring(4));
            if (id == 0) return false;
            into.setImageResource(id);
            return true;
        }
        File f = new File(photo);
        if (!f.exists()) return false;
        Glide.with(into).load(f).centerCrop().into(into);
        return true;
    }

    /** Shrinks a picked photo to at most 1280 px and keeps a private copy in the app's files. */
    public static String copyPhoto(Context c, android.net.Uri uri, String prefix) throws Exception {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = c.getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2;
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        android.graphics.Bitmap bmp;
        try (java.io.InputStream in = c.getContentResolver().openInputStream(uri)) {
            bmp = android.graphics.BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new Exception("Not an image");
        File dir = new File(c.getFilesDir(), "photos");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("No storage");
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + ".jpg");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos);
        }
        return out.getAbsolutePath();
    }

    /** "1h 05m left", "12m 09s left", "2d 3h left" or "Closed". */
    public static String timeLeft(long ms) {
        if (ms <= 0) return "Closed";
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h >= 24) return String.format(Locale.US, "%dd %dh left", h / 24, h % 24);
        if (h > 0) return String.format(Locale.US, "%dh %02dm left", h, m);
        return String.format(Locale.US, "%dm %02ds left", m, sec);
    }

    public static String clock(long millis) {
        return new SimpleDateFormat("h:mm a", Locale.US).format(new Date(millis));
    }

    /** "2026-11-13" -> "Fri, Nov 13" */
    public static String prettyDay(String isoDay) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDay);
            return new SimpleDateFormat("EEE, MMM d", Locale.US).format(d);
        } catch (Exception e) {
            return isoDay;
        }
    }

    /** List items rise into place one after another (used on Home, My listings, Pickups, Reports). */
    public static void stagger(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(v.getContext(), 18));
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 55L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }
}
