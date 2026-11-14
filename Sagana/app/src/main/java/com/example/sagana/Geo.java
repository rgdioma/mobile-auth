package com.example.sagana;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Barangays and pickup desks Sagana serves. Coordinates normally come from the
 * Geoapify Geocoding API (see GeoapifyClient.geocodeBarangay) and are saved in SQLite;
 * the values below are only a fallback when the phone is offline.
 */
public final class Geo {

    /** Change this (and BARANGAYS) if you deploy Sagana in another city. */
    public static final String CITY = "Bacolod";
    /** Centre of the city, used to bias Geoapify results toward the right barangay. */
    public static final double CITY_LAT = 10.66, CITY_LNG = 122.95;

    public static final String[] BARANGAYS = {
            "Bata", "Mandalagan", "Tangub", "Taculing", "Villamonte", "Singcang-Airport"
    };

    // Values Geoapify returned for each barangay, kept for offline use.
    private static final Map<String, double[]> FALLBACK = new HashMap<>();
    static {
        FALLBACK.put("Bata", new double[]{10.7035, 122.9720});
        FALLBACK.put("Mandalagan", new double[]{10.6918, 122.9661});
        FALLBACK.put("Tangub", new double[]{10.6345, 122.9345});
        FALLBACK.put("Taculing", new double[]{10.6492, 122.9609});
        FALLBACK.put("Villamonte", new double[]{10.6687, 122.9641});
        FALLBACK.put("Singcang-Airport", new double[]{10.6579, 122.9332});
    }

    private static final Pattern DESK = Pattern.compile("Brgy\\. (.+?) social welfare desk");

    private Geo() {
    }

    public static double[] fallback(String barangay) {
        double[] ll = FALLBACK.get(barangay);
        return ll != null ? ll.clone() : FALLBACK.get("Villamonte").clone();
    }

    public static String deskName(String barangay) {
        return "Brgy. " + barangay + " social welfare desk";
    }

    public static String[] deskNames() {
        String[] out = new String[BARANGAYS.length];
        for (int i = 0; i < BARANGAYS.length; i++) out[i] = deskName(BARANGAYS[i]);
        return out;
    }

    public static String barangayOf(String desk) {
        if (desk == null) return "Villamonte";
        Matcher m = DESK.matcher(desk);
        return m.find() ? m.group(1) : "Villamonte";
    }

    /** Walking time estimate used before (or without) the Geoapify Routing answer. */
    public static int walkMinutes(double km) {
        return Math.max(2, (int) Math.round(km * 1.3 / 4.8 * 60));
    }

    public static String km(double km) {
        return String.format(Locale.US, "%.1f km", km);
    }
}
