package com.example.sagana

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Let the red header run under the status bar, keeping its content below it.
        val header = view.findViewById<View>(R.id.header)
        val headerTopPadding = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, headerTopPadding + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(header)

        val name = requireActivity().intent.getStringExtra(HomeActivity.EXTRA_DISPLAY_NAME)
            ?: getString(R.string.demo_display_name)
        view.findViewById<TextView>(R.id.textGreeting).text =
            getString(R.string.home_greeting, getString(greetingForNow()), name)

        val withinKm = view.findViewById<TextView>(R.id.textWithinKm)
        val slider = view.findViewById<Slider>(R.id.sliderDistance)
        withinKm.text = getString(R.string.within_km, slider.value.toInt())
        slider.addOnChangeListener { _, value, _ ->
            withinKm.text = getString(R.string.within_km, value.toInt())
        }

        view.findViewById<TextView>(R.id.textListingCount).text =
            getString(R.string.listing_count, SAMPLE_LISTINGS.size)

        val container = view.findViewById<LinearLayout>(R.id.listingContainer)
        val inflater = LayoutInflater.from(view.context)
        SAMPLE_LISTINGS.forEach { listing ->
            container.addView(bindListing(inflater.inflate(R.layout.item_listing, container, false), listing))
        }
    }

    private fun bindListing(card: View, listing: Listing): View {
        card.findViewById<TextView>(R.id.textTitle).text = listing.title
        card.findViewById<TextView>(R.id.textDetail).text = listing.detail
        card.findViewById<TextView>(R.id.textDistance).text = listing.distance
        card.findViewById<TextView>(R.id.textPlaceholder).text = listing.photoHint

        val pillColor = if (listing.urgent) R.color.chili else R.color.mango
        val textColor = if (listing.urgent) R.color.white else R.color.adobo
        val context = card.context
        card.findViewById<TextView>(R.id.textTimeLeft).apply {
            text = listing.timeLeft
            setTextColor(ContextCompat.getColor(context, textColor))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, pillColor))
            TextViewCompat.setCompoundDrawableTintList(
                this, ColorStateList.valueOf(ContextCompat.getColor(context, textColor))
            )
        }
        return card
    }

    private fun greetingForNow(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    private data class Listing(
        val title: String,
        val detail: String,
        val distance: String,
        val photoHint: String,
        val timeLeft: String,
        val urgent: Boolean,
    )

    companion object {
        // Mock data until listings come from a backend.
        private val SAMPLE_LISTINGS = listOf(
            Listing("Pancit bihon", "8 packs · Aling Nena's Carinderia", "0.4 km",
                "Put: pancit bihon in foil trays", "29m 42s left", urgent = true),
            Listing("Pandesal", "3 dozen · Golden Crust Bakery", "0.9 km",
                "Put: pandesal in a paper bag", "2h 19m left", urgent = false),
            Listing("Saba bananas", "2 bunches · Dela Cruz household", "1.3 km",
                "Put: two banana bunches", "5h 04m left", urgent = false),
            Listing("Chicken adobo", "5 servings · Tita Baby's Kitchen", "1.8 km",
                "Put: adobo in lidded tubs", "48m 10s left", urgent = true),
            Listing("Rice", "4 kg · Brgy. San Roque pantry", "2.6 km",
                "Put: rice in a sealed sack", "1 day left", urgent = false),
            Listing("Canned sardines", "12 cans · Santos sari-sari store", "3.4 km",
                "Put: sardine cans on a tray", "3 days left", urgent = false),
        )
    }
}
