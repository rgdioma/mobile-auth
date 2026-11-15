package com.example.sagana;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Feature 4: pickup scheduling. Members choose a day and time slot for the food they
 * reserved (INSERT INTO bookings). Each booking gets a handover code drawn as a QR code;
 * the barangay desk staff scan it with ML Kit to hand the food over.
 */
public class PickupsFragment extends Fragment {

    private static final int[] SLOT_HOURS = {9, 11, 13, 15, 17, 19};

    private DatabaseHelper db;
    private DatabaseHelper.User me;
    private DatabaseHelper.Listing selected;
    private String selectedDay;
    private String selectedSlot;

    public PickupsFragment() {
        super(R.layout.fragment_pickups);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Ui.padForStatusBar(view);
        db = DatabaseHelper.get(requireContext());
        me = Session.user(requireContext());

        long id = -1;
        if (savedInstanceState != null) id = savedInstanceState.getLong("selected", -1);
        if (id <= 0 && requireActivity() instanceof HomeActivity) id = ((HomeActivity) requireActivity()).takeReserveId();
        if (id > 0) selected = db.getListing(id);
        if (selected != null && me != null && db.isOwnListing(selected.id, me.email)) {
            selected = null;   // nobody reserves their own food
            Toast.makeText(requireContext(), R.string.toast_own_listing, Toast.LENGTH_SHORT).show();
        } else if (selected != null && selected.isPendingDropOff()) {
            selected = null;   // the desk hasn't received it yet
            Toast.makeText(requireContext(), R.string.toast_listing_pending, Toast.LENGTH_SHORT).show();
        }

        selectedDay = DatabaseHelper.today();
        CalendarView cal = view.findViewById(R.id.calendarView);
        cal.setMinDate(System.currentTimeMillis() - 1000);
        cal.setOnDateChangeListener((v, y, m, d) -> {
            selectedDay = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            selectedSlot = null;
            buildSlots(view);
        });

        view.findViewById(R.id.buttonClearSelected).setOnClickListener(v -> {
            selected = null;
            showSelected(view);
        });
        view.findViewById(R.id.buttonConfirm).setOnClickListener(v -> confirm());

        setupBanner(view);
        showSelected(view);
        buildSlots(view);
        renderBookings(true);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (selected != null) out.putLong("selected", selected.id);
    }

    @Override
    public void onResume() {
        super.onResume();
        renderBookings(false); // the desk may have verified a code meanwhile
    }

    private void showSelected(View view) {
        boolean has = selected != null;
        view.findViewById(R.id.bannerChoose).setVisibility(has ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.cardSelected).setVisibility(has ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.buttonConfirm).setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            ((TextView) view.findViewById(R.id.textSelectedName)).setText(selected.name);
            ((TextView) view.findViewById(R.id.textSelectedInfo)).setText(selected.qty + " · " + selected.desk);
            View card = view.findViewById(R.id.cardSelected);
            card.setScaleX(0.92f);
            card.setScaleY(0.92f);
            card.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new OvershootInterpolator(2f)).start();
        }
    }

    private void buildSlots(View view) {
        ChipGroup group = view.findViewById(R.id.chipGroupTime);
        group.removeAllViews();
        boolean today = DatabaseHelper.today().equals(selectedDay);
        int hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (int h : SLOT_HOURS) {
            Chip chip = (Chip) inf.inflate(R.layout.item_time_chip, group, false);
            String label = slotLabel(h);
            chip.setText(label);
            chip.setId(View.generateViewId());
            boolean past = today && h <= hourNow;
            chip.setEnabled(!past);
            chip.setAlpha(past ? 0.4f : 1f);
            chip.setChecked(label.equals(selectedSlot));
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selectedSlot = label;
                else if (label.equals(selectedSlot)) selectedSlot = null;
            });
            group.addView(chip);
        }
    }

    private static String slotLabel(int hour) {
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return h12 + ":00 " + (hour < 12 ? "AM" : "PM");
    }

    // ------------------------------------------------------------------ booking (CREATE)
    private void confirm() {
        if (selected == null || me == null) return;
        if (db.isOwnListing(selected.id, me.email)) {
            Toast.makeText(requireContext(), R.string.toast_own_listing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedSlot == null) {
            Toast.makeText(requireContext(), R.string.toast_pick_slot, Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseHelper.Listing fresh = db.getListing(selected.id);
        if (fresh == null) {
            Toast.makeText(requireContext(), R.string.toast_listing_gone, Toast.LENGTH_SHORT).show();
            return;
        }
        selected = fresh;
        if (selected.isPendingDropOff()) {
            Toast.makeText(requireContext(), R.string.toast_listing_pending, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!selected.isOpen()) {
            Toast.makeText(requireContext(), R.string.toast_listing_closed, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_pickup_title)
                .setMessage(getString(R.string.confirm_pickup_body, selected.name, selected.qty,
                        Ui.prettyDay(selectedDay), selectedSlot, selected.desk))
                .setPositiveButton(R.string.action_confirm_pickup, (d, w) -> saveBooking())
                .setNegativeButton(R.string.action_not_yet, null)
                .show();
    }

    private void saveBooking() {
        DatabaseHelper.Booking b = new DatabaseHelper.Booking();
        b.listingId = selected.id;
        b.owner = me.email;
        b.receiver = me.name;
        b.food = selected.name;
        b.qty = selected.qty;
        b.donor = selected.donor;
        b.category = selected.category;
        b.desk = selected.desk;
        b.date = selectedDay;
        b.slot = selectedSlot;
        b.code = QrCodes.newCode(db);
        if (db.insertBooking(b) == -1) {   // refused: own listing, not reservable, or the database failed
            DatabaseHelper.Listing l = db.getListing(b.listingId);
            int why = l == null ? R.string.toast_listing_gone
                    : l.isPendingDropOff() ? R.string.toast_listing_pending
                    : !l.isOpen() ? R.string.toast_listing_closed : R.string.toast_own_listing;
            Toast.makeText(requireContext(), why, Toast.LENGTH_SHORT).show();
            return;
        }

        selected = null;
        selectedSlot = null;
        View view = requireView();
        showSelected(view);
        buildSlots(view);
        renderBookings(true);
        showQr(b, true);
    }

    // ------------------------------------------------------------------ list
    private void renderBookings(boolean animate) {
        View view = getView();
        if (view == null || me == null) return;
        List<DatabaseHelper.Booking> list = db.getMyBookings(me.email);
        ((TextView) view.findViewById(R.id.textPickupCount)).setText(String.valueOf(list.size()));
        view.findViewById(R.id.textNoPickups).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout box = view.findViewById(R.id.bookingContainer);
        box.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (DatabaseHelper.Booking b : list) box.addView(bindBooking(inf.inflate(R.layout.item_booking, box, false), b));
        if (animate) Ui.stagger(box);
    }

    private View bindBooking(View card, DatabaseHelper.Booking b) {
        ((TextView) card.findViewById(R.id.textFood)).setText(b.food);
        String info = b.qty + " · " + Ui.prettyDay(b.date) + ", " + b.slot + "\n" + b.desk;
        if ("Scheduled".equals(b.status) && b.rescheduled > 0 && b.prevDate != null) {
            info += "\n" + getString(R.string.rescheduled_from, Ui.prettyDay(b.prevDate), b.prevSlot);
        }
        ((TextView) card.findViewById(R.id.textInfo)).setText(info);

        TextView status = card.findViewById(R.id.textStatus);
        boolean scheduled = "Scheduled".equals(b.status), verified = "Verified".equals(b.status);
        status.setText(scheduled ? R.string.status_scheduled : verified ? R.string.status_verified : R.string.status_received);
        int bg = scheduled ? R.color.mango : R.color.leaf_light;
        int fg = scheduled ? R.color.adobo : R.color.leaf;
        status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bg)));
        status.setTextColor(ContextCompat.getColor(requireContext(), fg));

        if (scheduled) {
            card.findViewById(R.id.sectionCode).setVisibility(View.VISIBLE);
            ((TextView) card.findViewById(R.id.textCode)).setText(b.code);
            Bitmap qr = QrCodes.render(b.code, Ui.dp(requireContext(), 96));
            ImageView qrView = card.findViewById(R.id.imageQr);
            qrView.setImageBitmap(qr);
            qrView.setOnClickListener(v -> showQr(b, false));
            card.findViewById(R.id.buttonEnlarge).setOnClickListener(v -> showQr(b, false));
            View resched = card.findViewById(R.id.buttonReschedule);
            resched.setVisibility(View.VISIBLE);
            resched.setOnClickListener(v -> showReschedule(b));
            card.findViewById(R.id.buttonCancel).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.cancel_pickup_title)
                    .setMessage(getString(R.string.cancel_pickup_body, b.food))
                    .setPositiveButton(R.string.action_cancel_pickup, (d, w) -> {
                        db.cancelBooking(b.id);                      // DELETE
                        Toast.makeText(requireContext(), R.string.toast_pickup_cancelled, Toast.LENGTH_SHORT).show();
                        renderBookings(false);
                    })
                    .setNegativeButton(R.string.action_keep, null)
                    .show());
        } else if (verified) {
            card.findViewById(R.id.sectionVerified).setVisibility(View.VISIBLE);
            ((TextView) card.findViewById(R.id.textVerified)).setText(getString(R.string.verified_note, Ui.clock(b.verifiedAt)));
            card.findViewById(R.id.buttonReceived).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.received_title)
                    .setMessage(R.string.received_body)
                    .setPositiveButton(R.string.action_received_yes, (d, w) -> {
                        db.markReceived(b.id);                       // UPDATE
                        Toast.makeText(requireContext(), R.string.toast_handover_confirmed, Toast.LENGTH_SHORT).show();
                        renderBookings(false);
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show());
        } else if (b.rating == 0) {
            card.findViewById(R.id.sectionRate).setVisibility(View.VISIBLE);
            ((TextView) card.findViewById(R.id.textRatePrompt)).setText(getString(R.string.rate_prompt, b.donor));
            RatingBar input = card.findViewById(R.id.ratingInput);
            card.findViewById(R.id.buttonRate).setOnClickListener(v -> {
                int stars = Math.round(input.getRating());
                if (stars == 0) {
                    Toast.makeText(requireContext(), R.string.toast_tap_stars, Toast.LENGTH_SHORT).show();
                    return;
                }
                db.rateBooking(b.id, stars);                         // UPDATE
                Toast.makeText(requireContext(), R.string.toast_thanks_rating, Toast.LENGTH_SHORT).show();
                renderBookings(false);
            });
        } else {
            card.findViewById(R.id.sectionRated).setVisibility(View.VISIBLE);
            ((RatingBar) card.findViewById(R.id.ratingShown)).setRating(b.rating);
        }
        return card;
    }

    // ------------------------------------------------------------------ reschedule (UPDATE)
    /**
     * The receiver can't make it: pick another day and slot. The booking keeps its QR code,
     * and the desk's list shows the new time, marked Rescheduled with the old one.
     */
    private void showReschedule(DatabaseHelper.Booking b) {
        if (!isAdded() || me == null) return;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reschedule, null);
        ((TextView) content.findViewById(R.id.textCurrent)).setText(
                getString(R.string.reschedule_now, Ui.prettyDay(b.date), b.slot));
        TextView error = content.findViewById(R.id.textRescheduleError);
        CalendarView cal = content.findViewById(R.id.calendarReschedule);
        ChipGroup group = content.findViewById(R.id.chipGroupReschedule);

        final String[] day = {b.date.compareTo(DatabaseHelper.today()) < 0 ? DatabaseHelper.today() : b.date};
        final String[] slot = {null};
        cal.setMinDate(System.currentTimeMillis() - 1000);
        try {
            long ms = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day[0]).getTime();
            cal.setDate(Math.max(ms, System.currentTimeMillis()), false, true);
        } catch (Exception ignored) {
        }
        Runnable buildChips = () -> {
            group.removeAllViews();
            boolean today = DatabaseHelper.today().equals(day[0]);
            int hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            for (int h : SLOT_HOURS) {
                Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.item_time_chip, group, false);
                String label = slotLabel(h);
                chip.setText(label);
                chip.setId(View.generateViewId());
                boolean past = today && h <= hourNow;
                chip.setEnabled(!past);
                chip.setAlpha(past ? 0.4f : 1f);
                chip.setChecked(label.equals(slot[0]));
                chip.setOnCheckedChangeListener((c, checked) -> {
                    if (checked) slot[0] = label;
                    else if (label.equals(slot[0])) slot[0] = null;
                    error.setVisibility(View.GONE);
                });
                group.addView(chip);
            }
        };
        cal.setOnDateChangeListener((v, y, m, d) -> {
            day[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            slot[0] = null;
            error.setVisibility(View.GONE);
            buildChips.run();
        });
        buildChips.run();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reschedule_title)
                .setView(content)
                .setPositiveButton(R.string.action_save_new_time, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String problem = null;
            if (slot[0] == null) problem = getString(R.string.toast_pick_slot);
            else if (day[0].equals(b.date) && slot[0].equals(b.slot)) problem = getString(R.string.error_same_schedule);
            if (problem != null) {
                error.setText(problem);
                error.setVisibility(View.VISIBLE);
                return;
            }
            if (db.rescheduleBooking(b.id, me.email, day[0], slot[0]) == 1) {
                Toast.makeText(requireContext(), getString(R.string.toast_rescheduled,
                        Ui.prettyDay(day[0]), slot[0]), Toast.LENGTH_LONG).show();
            } else {
                // already handed over by the desk, cancelled, or not yours
                Toast.makeText(requireContext(), R.string.toast_cannot_reschedule, Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
            renderBookings(false);
        }));
        dialog.show();
    }

    /** Big QR for the desk staff to scan. */
    private void showQr(DatabaseHelper.Booking b, boolean justBooked) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qr, null);
        ImageView img = content.findViewById(R.id.imageQrLarge);
        img.setImageBitmap(QrCodes.render(b.code, Ui.dp(requireContext(), 240)));
        ((TextView) content.findViewById(R.id.textQrCode)).setText(b.code);
        ((TextView) content.findViewById(R.id.textQrInfo)).setText(
                b.food + " · " + Ui.prettyDay(b.date) + ", " + b.slot + "\n" + b.desk);
        img.setScaleX(0.7f);
        img.setScaleY(0.7f);
        img.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(new OvershootInterpolator(2f)).start();
        new AlertDialog.Builder(requireContext())
                .setTitle(justBooked ? R.string.pickup_confirmed_title : R.string.handover_qr_title)
                .setView(content)
                .setPositiveButton(R.string.action_done, null)
                .show();
    }

    private void setupBanner(View view) {
        TextView textBanner = view.findViewById(R.id.textBanner);
        String browse = getString(R.string.browse_food);
        String fullText = getString(R.string.pickup_banner_text) + " " + browse;
        SpannableString ss = new SpannableString(fullText);
        int start = fullText.indexOf(browse), end = start + browse.length();
        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (requireActivity() instanceof HomeActivity)
                    ((HomeActivity) requireActivity()).selectTab(R.id.nav_home);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.chili)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textBanner.setText(ss);
        textBanner.setMovementMethod(LinkMovementMethod.getInstance());
        textBanner.setHighlightColor(Color.TRANSPARENT);
    }
}
