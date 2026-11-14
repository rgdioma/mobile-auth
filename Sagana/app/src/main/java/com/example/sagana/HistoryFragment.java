package com.example.sagana;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Staff: every handover at this desk, newest first (READ bookings). */
public class HistoryFragment extends Fragment {

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Ui.padForStatusBar(view);
        DatabaseHelper.User me = Session.user(requireContext());
        if (me == null) return;
        ((TextView) view.findViewById(R.id.textHistorySubtitle)).setText(getString(R.string.history_subtitle, me.desk()));

        List<DatabaseHelper.Booking> rows = DatabaseHelper.get(requireContext()).getHistoryAtDesk(me.desk());
        LinearLayout box = view.findViewById(R.id.historyContainer);
        LayoutInflater inf = LayoutInflater.from(requireContext());
        if (rows.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.history_empty);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            int p = Ui.dp(requireContext(), 12);
            empty.setPadding(0, p, 0, p);
            box.addView(empty);
            return;
        }
        SimpleDateFormat day = new SimpleDateFormat("MMM d", Locale.US);
        for (DatabaseHelper.Booking b : rows) {
            View row = inf.inflate(R.layout.item_desk_row, box, false);
            row.findViewById(R.id.iconBg).setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.leaf_light)));
            ImageView icon = row.findViewById(R.id.icon);
            icon.setImageResource(R.drawable.ic_rule_met);
            icon.setImageTintList(null);
            ((TextView) row.findViewById(R.id.textTitle)).setText(b.food + ", " + b.qty);
            ((TextView) row.findViewById(R.id.textSubtitle)).setText(getString(R.string.history_row,
                    b.receiver, b.verifiedBy, day.format(new Date(b.verifiedAt)), Ui.clock(b.verifiedAt), b.code));
            TextView pill = row.findViewById(R.id.textPill);
            pill.setText("Received".equals(b.status) ? R.string.status_received : R.string.status_given);
            pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.leaf_light)));
            pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.leaf));
            box.addView(row);
        }
        Ui.stagger(box);
    }
}
