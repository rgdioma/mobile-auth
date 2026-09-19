package com.example.sagana

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

/** Stand-in for tabs that haven't been designed yet (Post, Pickups, Alerts, Profile). */
class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        view.findViewById<TextView>(R.id.textTitle).setText(args.getInt(ARG_TITLE))
        view.findViewById<ImageView>(R.id.imageIcon).setImageResource(args.getInt(ARG_ICON))

        val basePadding = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, basePadding + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ICON = "icon"

        fun newInstance(@StringRes title: Int, @DrawableRes icon: Int) = PlaceholderFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_TITLE, title)
                putInt(ARG_ICON, icon)
            }
        }
    }
}
