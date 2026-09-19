package com.example.sagana

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class PostFragment : Fragment(R.layout.fragment_post) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust padding for status bar since HomeActivity uses enableEdgeToEdge()
        val basePadding = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, basePadding + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)

        setupQuantityStepper(view)
        setupTimePickers(view)
    }

    private fun setupQuantityStepper(view: View) {
        val tvQuantity = view.findViewById<TextView>(R.id.tvQuantity)
        val btnMinus = view.findViewById<ImageButton>(R.id.btnMinus)
        val btnPlus = view.findViewById<ImageButton>(R.id.btnPlus)

        btnMinus.setOnClickListener {
            val current = tvQuantity.text.toString().toIntOrNull() ?: 1
            if (current > 1) {
                val newQty = current - 1
                tvQuantity.text = getString(R.string.format_quantity, newQty)
            }
        }

        btnPlus.setOnClickListener {
            val current = tvQuantity.text.toString().toIntOrNull() ?: 1
            val newQty = current + 1
            tvQuantity.text = getString(R.string.format_quantity, newQty)
        }
    }

    private fun setupTimePickers(view: View) {
        val tvStartTime = view.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = view.findViewById<TextView>(R.id.tvEndTime)

        tvStartTime.setOnClickListener { showTimePicker(tvStartTime) }
        tvEndTime.setOnClickListener { showTimePicker(tvEndTime) }
    }

    private fun showTimePicker(textView: TextView) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(12)
            .setMinute(0)
            .setTitleText("Select time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = if (picker.hour == 0 || picker.hour == 12) 12 else picker.hour % 12
            val amPm = if (picker.hour < 12) "am" else "pm"
            textView.text = getString(R.string.format_time, hour, picker.minute, amPm)
        }

        picker.show(childFragmentManager, "time_picker")
    }
}