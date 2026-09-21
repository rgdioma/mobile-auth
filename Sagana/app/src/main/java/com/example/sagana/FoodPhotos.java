package com.example.sagana;

import android.content.Context;

/**
 * Looks up a listing photo by drawable name so photos can be dropped into
 * res/drawable without editing code. Returns 0 when the file isn't there yet,
 * which leaves the striped placeholder showing.
 */
final class FoodPhotos {

    private FoodPhotos() {
    }

    static int resolve(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }
}
