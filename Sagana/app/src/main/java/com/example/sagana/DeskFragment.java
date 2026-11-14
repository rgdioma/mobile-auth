package com.example.sagana;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import java.util.List;
import java.util.Locale;

/**
 * Staff: the barangay desk. The receiver shows their QR code, staff scan it
 * (ML Kit, on-device) or type it, check the match, and hand the food over (UPDATE bookings).
 */
public class DeskFragment extends Fragment {

    private DatabaseHelper db;
    private DatabaseHelper.User me;

    private final ActivityResultLauncher<Intent> scan = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                String code = result.getData().getStringExtra(ScanActivity.EXTRA_CODE);
                View v = getView();
                if (code == null || v == null) return;
                ((EditText) v.findViewById(R.id.editCode)).setText(code);
                check(code);
            });

    public DeskFragment() {
        super(R.layout.fragment_desk);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = DatabaseHelper.get(requireContext());
        me = Session.user(requireContext());

        View header = view.findViewById(R.id.header);
        final int top = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), top + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(header);

        if (me != null) ((TextView) view.findViewById(R.id.textDeskName)).setText(me.desk());

        view.findViewById(R.id.buttonScan).setOnClickListener(v -> {
            scan.launch(new Intent(requireContext(), ScanActivity.class));
            Nav.pushed(requireActivity());
        });

        EditText edit = view.findViewById(R.id.editCode);
        view.findViewById(R.id.buttonCheck).setOnClickListener(v -> check(edit.getText().toString()));
        edit.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                check(edit.getText().toString());
                return true;
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        renderWaiting();
    }

    // ------------------------------------------------------------------ checking a code
    private void check(String raw) {
        View view = getView();
        if (view == null || me == null) return;
        TextView error = view.findViewById(R.id.textCodeError);
        LinearLayout result = view.findViewById(R.id.resultContainer);
        result.removeAllViews();
        error.setVisibility(View.GONE);

        String code = raw.trim().toUpperCase(Locale.US);
        if (code.isEmpty()) {
            error.setText(R.string.error_code_required);
            error.setVisibility(View.VISIBLE);
            return;
        }
        if (!QrCodes.CODE.matcher(code).matches()) {
            error.setText(R.string.error_code_format);
            error.setVisibility(View.VISIBLE);
            return;
        }

        DatabaseHelper.Booking b = db.findByCode(code);
        if (b == null) {
            banner(result, getString(R.string.desk_no_match, code), true);
        } else if (!me.desk().equals(b.desk)) {
            banner(result, getString(R.string.desk_wrong_desk, b.desk), true);
        } else if (!"Scheduled".equals(b.status)) {
            banner(result, getString(R.string.desk_already, Ui.clock(b.verifiedAt), b.verifiedBy), false);
        } else {
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.view_desk_match, result, false);
            ((TextView) card.findViewById(R.id.textFood)).setText(b.food);
            ((TextView) card.findViewById(R.id.textQtyDonor)).setText(getString(R.string.qty_from_donor, b.qty, b.donor));
            ((TextView) card.findViewById(R.id.textReceiver)).setText(b.receiver);
            String scheduled = Ui.prettyDay(b.date) + ", " + b.slot;
            if (b.rescheduled > 0 && b.prevDate != null) {
                scheduled += "\n" + getString(R.string.desk_moved_from, Ui.prettyDay(b.prevDate), b.prevSlot);
            }
            ((TextView) card.findViewById(R.id.textScheduled)).setText(scheduled);
            card.findViewById(R.id.buttonHandOver).setOnClickListener(v -> confirmHandOver(b));
            result.addView(card);
            card.setScaleX(0.9f);
            card.setScaleY(0.9f);
            card.setAlpha(0f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                    .setInterpolator(new OvershootInterpolator(1.8f)).start();
        }
    }

    private void banner(LinearLayout into, String text, boolean warn) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        int pad = Ui.dp(requireContext(), 14);
        t.setPadding(pad, pad, pad, pad);
        t.setBackgroundResource(warn ? R.drawable.bg_banner : R.drawable.bg_leaf_banner);
        if (warn) t.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chili_light)));
        t.setTextColor(ContextCompat.getColor(requireContext(), warn ? R.color.chili : R.color.leaf));
        t.setTextSize(14);
        into.addView(t);
        t.setTranslationX(Ui.dp(requireContext(), 12));
        t.animate().translationX(0).setInterpolator(new OvershootInterpolator(4f)).setDuration(300).start();
    }

    private void confirmHandOver(DatabaseHelper.Booking b) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.hand_over_title, b.food))
                .setMessage(getString(R.string.hand_over_body, b.qty, b.receiver))
                .setPositiveButton(R.string.action_handed_over, (d, w) -> {
                    if (db.verifyCode(b.code, me.name) == 1) {
                        Toast.makeText(requireContext(), getString(R.string.toast_handed_over, b.receiver.split(" ")[0]), Toast.LENGTH_SHORT).show();
                    }
                    View view = requireView();
                    ((EditText) view.findViewById(R.id.editCode)).setText("");
                    ((LinearLayout) view.findViewById(R.id.resultContainer)).removeAllViews();
                    renderWaiting();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ waiting list
    private void renderWaiting() {
        View view = getView();
        if (view == null || me == null) return;
        List<DatabaseHelper.Booking> rows = db.getWaitingAtDesk(me.desk());
        ((TextView) view.findViewById(R.id.textWaitingCount)).setText(getString(R.string.n_waiting, rows.size()));
        LinearLayout box = view.findViewById(R.id.waitingContainer);
        box.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        if (rows.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.desk_nobody_waiting);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            int p = Ui.dp(requireContext(), 12);
            empty.setPadding(0, p, 0, p);
            box.addView(empty);
            return;
        }
        for (DatabaseHelper.Booking b : rows) {
            View row = inf.inflate(R.layout.item_desk_row, box, false);
            ((TextView) row.findViewById(R.id.textTitle)).setText(b.receiver);
            String when = b.food + ", " + b.qty + " · " + Ui.prettyDay(b.date) + ", " + b.slot;
            TextView pill = row.findViewById(R.id.textPill);
            if (b.rescheduled > 0 && b.prevDate != null) {
                // the receiver moved this pickup: show the new time, tagged, with where it was moved from
                when += "\n" + getString(R.string.desk_moved_from, Ui.prettyDay(b.prevDate), b.prevSlot);
                pill.setText(R.string.status_rescheduled);
                pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chili_light)));
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.chili));
            } else {
                pill.setText(R.string.status_waiting);
            }
            ((TextView) row.findViewById(R.id.textSubtitle)).setText(when);
            box.addView(row);
        }
        Ui.stagger(box);
    }
}
