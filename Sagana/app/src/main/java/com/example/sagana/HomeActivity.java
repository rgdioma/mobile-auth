package com.example.sagana;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The app after login. Members get Home / Post / Pickups / Alerts / Profile;
 * barangay desk staff get Desk / History / Reports / Profile.
 * Switching tabs fades through (screen transition: fade).
 */
public class HomeActivity extends AppCompatActivity {

    /** Set by Food details "Reserve": open Pickups with this listing selected. */
    public static final String EXTRA_RESERVE_ID = "com.example.sagana.RESERVE_ID";
    /** Open a particular tab, e.g. from an alert. */
    public static final String EXTRA_TAB = "com.example.sagana.TAB";

    private BottomNavigationView bottomNav;
    private DatabaseHelper.User user;
    private long pendingReserveId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = Session.user(this);
        if (user == null) {               // logged out or account removed by a restore
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(user.isStaff() ? R.menu.bottom_nav_staff : R.menu.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> { });

        if (savedInstanceState == null) {
            handleIntent(getIntent(), true);
        } else {
            updateStatusBar(bottomNav.getSelectedItemId());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) refreshAlertBadge();
    }

    private void handleIntent(Intent intent, boolean first) {
        long reserve = intent.getLongExtra(EXTRA_RESERVE_ID, -1);
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        if (reserve > 0 && !user.isStaff()) {
            pendingReserveId = reserve;
            tab = R.id.nav_pickups;
        }
        if (tab == 0) tab = user.isStaff() ? R.id.nav_desk : R.id.nav_home;
        if (!first && bottomNav.getSelectedItemId() == tab) {
            showTab(tab);                 // already there: rebuild so the new reservation shows
        } else {
            bottomNav.setSelectedItemId(tab);
            if (first && bottomNav.getSelectedItemId() == tab && getSupportFragmentManager().findFragmentById(R.id.fragmentContainer) == null) {
                showTab(tab);
            }
        }
    }

    /** Pickups asks once for the listing chosen on Food details. */
    public long takeReserveId() {
        long id = pendingReserveId;
        pendingReserveId = -1;
        return id;
    }

    public void selectTab(int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_home) fragment = new HomeFragment();
        else if (itemId == R.id.nav_post) fragment = new PostFragment();
        else if (itemId == R.id.nav_pickups) fragment = new PickupsFragment();
        else if (itemId == R.id.nav_alerts) fragment = new AlertsFragment();
        else if (itemId == R.id.nav_desk) fragment = new DeskFragment();
        else if (itemId == R.id.nav_history) fragment = new HistoryFragment();
        else if (itemId == R.id.nav_reports) fragment = new ReportsFragment();
        else fragment = new ProfileFragment();

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        updateStatusBar(itemId);
        refreshAlertBadge();
    }

    /** Badge on Alerts = listings closing within 2 hours, if expiry alerts are on. */
    public void refreshAlertBadge() {
        if (user.isStaff()) return;
        int n = AlertsFragment.closingSoonCount(this);
        if (n > 0 && Session.alertOn(this, "expiry")) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_alerts);
            badge.setNumber(n);
            badge.setBackgroundColor(getColor(R.color.mango));
            badge.setBadgeTextColor(getColor(R.color.adobo));
        } else {
            bottomNav.removeBadge(R.id.nav_alerts);
        }
    }

    /** Home and Desk have a red header, so they need light status bar icons. */
    private void updateStatusBar(int itemId) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(itemId != R.id.nav_home && itemId != R.id.nav_desk);
    }
}
