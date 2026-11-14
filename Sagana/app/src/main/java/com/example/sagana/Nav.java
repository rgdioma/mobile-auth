package com.example.sagana;

import android.app.Activity;
import android.content.Intent;

/**
 * Screen transitions (res/anim):
 *   push   a deeper screen slides in from the right (details, my listings, reports, scanner, OTP)
 *   pop    going back: the previous screen returns from the left
 *   rise   after logging in or signing up: Home slides up into place
 *   fade   bottom-navigation tabs fade through (see HomeActivity)
 *   zoom   opening screen zooms out into Login
 */
public final class Nav {

    private Nav() {
    }

    public static void push(Activity a, Intent i) {
        a.startActivity(i);
        anim(a, R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /** Push animation for screens started through an ActivityResultLauncher. */
    public static void pushed(Activity a) {
        anim(a, R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void pop(Activity a) {
        anim(a, R.anim.slide_in_left, R.anim.slide_out_right);
    }

    public static void rise(Activity a, Intent i) {
        a.startActivity(i);
        anim(a, R.anim.slide_up_in, R.anim.fade_out);
    }

    public static void fade(Activity a, Intent i) {
        a.startActivity(i);
        anim(a, R.anim.fade_in, R.anim.fade_out);
    }

    public static void zoom(Activity a, Intent i) {
        a.startActivity(i);
        anim(a, R.anim.fade_in, R.anim.zoom_fade_out);
    }

    @SuppressWarnings("deprecation")
    private static void anim(Activity a, int enter, int exit) {
        a.overridePendingTransition(enter, exit);
    }
}
