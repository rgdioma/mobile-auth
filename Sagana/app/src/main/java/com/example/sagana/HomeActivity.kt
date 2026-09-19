package com.example.sagana

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.getOrCreateBadge(R.id.nav_alerts).apply {
            number = 2
            backgroundColor = getColor(R.color.mango)
            badgeTextColor = getColor(R.color.adobo)
        }

        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        // Tapping the current tab again shouldn't rebuild it.
        bottomNav.setOnItemReselectedListener { }

        if (savedInstanceState == null) {
            showTab(R.id.nav_home)
        } else {
            // FragmentManager restores the fragment itself; just match the status bar.
            updateStatusBar(bottomNav.selectedItemId)
        }
    }

    private fun showTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_post -> PostFragment()
            R.id.nav_pickups -> PlaceholderFragment.newInstance(R.string.nav_pickups, R.drawable.ic_nav_pickups)
            R.id.nav_alerts -> PlaceholderFragment.newInstance(R.string.nav_alerts, R.drawable.ic_nav_alerts)
            else -> PlaceholderFragment.newInstance(R.string.nav_profile, R.drawable.ic_nav_profile)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        updateStatusBar(itemId)
    }

    /** Home has a red header, so it needs light status bar icons; other tabs are on Rice. */
    private fun updateStatusBar(itemId: Int) {
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = itemId != R.id.nav_home
    }

    companion object {
        const val EXTRA_DISPLAY_NAME = "com.example.sagana.DISPLAY_NAME"
    }
}
