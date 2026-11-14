package com.example.sagana;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers who is logged in, plus the Alerts switches. */
public final class Session {

    private static final String PREFS = "sagana_session";
    private static final String KEY_EMAIL = "email";

    private Session() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void login(Context c, String email) {
        prefs(c).edit().putString(KEY_EMAIL, email).apply();
    }

    public static void logout(Context c) {
        prefs(c).edit().remove(KEY_EMAIL).apply();
    }

    public static String email(Context c) {
        return prefs(c).getString(KEY_EMAIL, null);
    }

    /** The logged-in user, read fresh from the database, or null. */
    public static DatabaseHelper.User user(Context c) {
        return DatabaseHelper.get(c).getUser(email(c));
    }

    /** True once this phone has gone through (or skipped) onboarding. */
    public static boolean onboarded(Context c) {
        return prefs(c).getBoolean("onboarded", false);
    }

    public static void setOnboarded(Context c) {
        prefs(c).edit().putBoolean("onboarded", true).apply();
    }

    public static boolean alertOn(Context c, String key) {
        return prefs(c).getBoolean("alert_" + key, true);
    }

    public static void setAlert(Context c, String key, boolean on) {
        prefs(c).edit().putBoolean("alert_" + key, on).apply();
    }
}
