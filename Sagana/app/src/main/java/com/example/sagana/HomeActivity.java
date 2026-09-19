package com.example.sagana;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_DISPLAY_NAME = "com.example.sagana.DISPLAY_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.getOrCreateBadge(R.id.nav_alerts).setNumber(2);
        bottomNav.getOrCreateBadge(R.id.nav_alerts).setBackgroundColor(getColor(R.color.mango));
        bottomNav.getOrCreateBadge(R.id.nav_alerts).setBadgeTextColor(getColor(R.color.adobo));

        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        // Tapping the current tab again shouldn't rebuild it.
        bottomNav.setOnItemReselectedListener(item -> {});

        if (savedInstanceState == null) {
            showTab(R.id.nav_home);
        } else {
            // FragmentManager restores the fragment itself; just match the status bar.
            updateStatusBar(bottomNav.getSelectedItemId());
        }
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (itemId == R.id.nav_post) {
            fragment = new PostFragment();
        } else if (itemId == R.id.nav_pickups) {
            fragment = PlaceholderFragment.newInstance(R.string.nav_pickups, R.drawable.ic_nav_pickups);
        } else if (itemId == R.id.nav_alerts) {
            fragment = PlaceholderFragment.newInstance(R.string.nav_alerts, R.drawable.ic_nav_alerts);
        } else {
            fragment = PlaceholderFragment.newInstance(R.string.nav_profile, R.drawable.ic_nav_profile);
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        updateStatusBar(itemId);
    }

    /** Home has a red header, so it needs light status bar icons; other tabs are on Rice. */
    private void updateStatusBar(int itemId) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(itemId != R.id.nav_home);
    }
}
