package com.example.sagana;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The numbers behind the Reports screen and the exported PDF, read from SQLite. */
public final class ReportData {

    public static final String[] CATEGORIES = {"Cooked", "Bread", "Produce", "Packaged"};
    public static final int[] CATEGORY_COLORS = {0xFFC8231B, 0xFFE8A300, 0xFF2F7D4A, 0xFF7A4A9E};

    public final List<String> days = new ArrayList<>();     // yyyy-MM-dd, oldest first, 7 days
    public final int[] picked = new int[7];
    public final int[] expired = new int[7];
    public final double[] kgPerDay = new double[7];
    public final Map<String, Integer> byCategory = new LinkedHashMap<>();
    public final Map<String, Integer> byDesk = new LinkedHashMap<>();
    public double kg;
    public int totalPicked, totalExpired, active;

    public int rescueRate() {
        int all = totalPicked + totalExpired;
        return all == 0 ? 0 : Math.round(totalPicked * 100f / all);
    }

    public static ReportData load(DatabaseHelper db) {
        db.sweepExpired();
        ReportData r = new ReportData();
        for (int d = 6; d >= 0; d--) r.days.add(DatabaseHelper.daysAgo(d));

        try (Cursor c = db.handoversPerDay(r.days.get(0))) {
            while (c.moveToNext()) {
                int i = r.days.indexOf(c.getString(0));
                if (i < 0) continue;
                r.picked[i] = c.getInt(1);
                r.expired[i] = c.getInt(2);
                r.kgPerDay[i] = c.getDouble(3);
            }
        }
        for (int i = 0; i < 7; i++) {
            r.totalPicked += r.picked[i];
            r.totalExpired += r.expired[i];
            r.kg += r.kgPerDay[i];
        }

        for (String cat : CATEGORIES) r.byCategory.put(cat, 0);
        try (Cursor c = db.listingsByCategory()) {
            while (c.moveToNext()) r.byCategory.put(c.getString(0), c.getInt(1));
        }
        r.active = db.countOpenListings();

        for (String desk : Geo.deskNames()) r.byDesk.put(desk, 0);
        try (Cursor c = db.handoversByDesk(r.days.get(0))) {
            while (c.moveToNext()) r.byDesk.put(c.getString(0), c.getInt(1));
        }
        // busiest first
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(r.byDesk.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        r.byDesk.clear();
        for (Map.Entry<String, Integer> e : sorted) r.byDesk.put(e.getKey(), e.getValue());
        return r;
    }
}
