package com.example.sagana;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Feature 1: surplus food listings.
 *   New listing (Post tab)          -> INSERT INTO listings
 *   Edit listing (from My listings) -> UPDATE listings ... / DELETE FROM listings ...
 */
public class PostFragment extends Fragment {

    private static final String ARG_LISTING_ID = "listing_id";
    private static final Pattern FOOD_PATTERN = Pattern.compile("^[A-Za-zÀ-ÿÑñ0-9 ,.'()&-]{3,40}$");

    private int quantity = 1;
    private int startMin, endMin;   // minutes after midnight, today
    private String photoPath;       // file path or res:name
    private DatabaseHelper.Listing editing;
    private boolean saving;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    public PostFragment() {
        super(R.layout.fragment_post);
    }

    /** Edit mode, used by ListingEditorActivity. */
    public static PostFragment edit(long listingId) {
        PostFragment f = new PostFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_LISTING_ID, listingId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        long id = getArguments() != null ? getArguments().getLong(ARG_LISTING_ID, -1) : -1;
        if (id > 0) editing = DatabaseHelper.get(requireContext()).getListing(id);
        if (editing == null) Ui.padForStatusBar(view); // in a tab; the editor activity pads itself

        Spinner desk = view.findViewById(R.id.spinnerDesk);
        ArrayAdapter<String> deskAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, Geo.deskNames());
        desk.setAdapter(deskAdapter);

        setupQuantityStepper(view);
        setupTimePickers(view);
        view.findViewById(R.id.photoFrame).setOnClickListener(v -> pickPhoto.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        view.findViewById(R.id.btnPost).setOnClickListener(v -> save());
        view.findViewById(R.id.rowMyListings).setOnClickListener(v ->
                Nav.push(requireActivity(), new Intent(requireContext(), MyListingsActivity.class)));

        if (editing != null) fillForEdit(view);
        else resetForm(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        DatabaseHelper.User me = Session.user(requireContext());
        if (v != null && me != null && editing == null) {
            int mine = DatabaseHelper.get(requireContext()).getMyListings(me.email).size();
            ((TextView) v.findViewById(R.id.textMyCount)).setText(String.valueOf(mine));
        }
    }

    // ------------------------------------------------------------------ form setup
    private void resetForm(View view) {
        DatabaseHelper.User me = Session.user(requireContext());
        quantity = 1;
        updateQuantityDisplay(view);
        Calendar c = Calendar.getInstance();
        startMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        endMin = Math.min(23 * 60 + 59, startMin + 180);
        showTimes(view);
        ((EditText) view.findViewById(R.id.etFoodName)).setText("");
        ((EditText) view.findViewById(R.id.etNotes)).setText("");
        ((Spinner) view.findViewById(R.id.spinnerCategory)).setSelection(0);
        ((Spinner) view.findViewById(R.id.spinnerUnit)).setSelection(0);
        ((CheckBox) view.findViewById(R.id.cbSafety)).setChecked(false);
        if (me != null) selectDesk(view, me.desk());
        photoPath = null;
        showPhoto(view);
    }

    private void fillForEdit(View view) {
        view.findViewById(R.id.buttonBack).setVisibility(View.VISIBLE);
        view.findViewById(R.id.buttonBack).setOnClickListener(v -> requireActivity().finish());
        view.findViewById(R.id.rowMyListings).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.textPostTitle)).setText(R.string.title_edit_listing);
        ((TextView) view.findViewById(R.id.textPostSubtitle)).setText(R.string.subtitle_edit_listing);
        ((TextView) view.findViewById(R.id.btnPost)).setText(R.string.action_save_changes);
        View delete = view.findViewById(R.id.btnDelete);
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> confirmDelete());

        ((EditText) view.findViewById(R.id.etFoodName)).setText(editing.name);
        ((EditText) view.findViewById(R.id.etNotes)).setText(editing.notes);
        String[] q = editing.qty.split("\\s+", 2);
        try { quantity = Integer.parseInt(q[0]); } catch (Exception e) { quantity = 1; }
        updateQuantityDisplay(view);
        if (q.length > 1) selectItem(view.findViewById(R.id.spinnerUnit), q[1]);
        selectItem(view.findViewById(R.id.spinnerCategory), editing.category);
        selectDesk(view, editing.desk);
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(editing.pickupStart);
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(editing.pickupEnd);
        startMin = s.get(Calendar.HOUR_OF_DAY) * 60 + s.get(Calendar.MINUTE);
        endMin = e.get(Calendar.HOUR_OF_DAY) * 60 + e.get(Calendar.MINUTE);
        showTimes(view);
        ((CheckBox) view.findViewById(R.id.cbSafety)).setChecked(true);
        photoPath = editing.photo;
        showPhoto(view);
    }

    private void selectDesk(View view, String desk) {
        List<String> desks = Arrays.asList(Geo.deskNames());
        int i = desks.indexOf(desk);
        if (i >= 0) ((Spinner) view.findViewById(R.id.spinnerDesk)).setSelection(i);
    }

    private static void selectItem(Spinner s, String value) {
        for (int i = 0; i < s.getCount(); i++) {
            if (s.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
                s.setSelection(i);
                return;
            }
        }
    }

    private void setupQuantityStepper(View view) {
        updateQuantityDisplay(view);
        view.findViewById(R.id.btnMinus).setOnClickListener(v -> {
            if (quantity > 1) quantity--;
            updateQuantityDisplay(view);
        });
        view.findViewById(R.id.btnPlus).setOnClickListener(v -> {
            if (quantity < 99) quantity++;
            updateQuantityDisplay(view);
        });
    }

    private void updateQuantityDisplay(View view) {
        ((TextView) view.findViewById(R.id.tvQuantity)).setText(getString(R.string.format_quantity, quantity));
    }

    private void setupTimePickers(View view) {
        view.findViewById(R.id.tvStartTime).setOnClickListener(v -> pickTime(view, true));
        view.findViewById(R.id.tvEndTime).setOnClickListener(v -> pickTime(view, false));
    }

    private void pickTime(View view, boolean start) {
        int current = start ? startMin : endMin;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(current / 60)
                .setMinute(current % 60)
                .setTitleText(start ? R.string.title_start_time : R.string.title_end_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int m = picker.getHour() * 60 + picker.getMinute();
            if (start) startMin = m; else endMin = m;
            showTimes(view);
        });
        picker.show(getChildFragmentManager(), "time_picker");
    }

    private void showTimes(View view) {
        ((TextView) view.findViewById(R.id.tvStartTime)).setText(Ui.clock(todayAt(startMin)));
        ((TextView) view.findViewById(R.id.tvEndTime)).setText(Ui.clock(todayAt(endMin)));
    }

    private static long todayAt(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ------------------------------------------------------------------ photo
    private void onPhotoPicked(Uri uri) {
        if (uri == null || getView() == null) return;
        try {
            photoPath = Ui.copyPhoto(requireContext(), uri, "food");
            showPhoto(getView());
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.toast_photo_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhoto(View view) {
        boolean has = Ui.loadPhoto(view.findViewById(R.id.imagePreview), photoPath);
        view.findViewById(R.id.photoCard).setVisibility(has ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.photoPicker).setVisibility(has ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ save / delete
    private void save() {
        View view = getView();
        if (view == null || saving) return;
        EditText nameField = view.findViewById(R.id.etFoodName);
        String name = nameField.getText().toString().trim();
        Spinner cat = view.findViewById(R.id.spinnerCategory);
        TextView error = view.findViewById(R.id.textPostError);
        error.setVisibility(View.GONE);

        String problem = null;
        if (name.isEmpty()) problem = getString(R.string.error_food_required);
        else if (!FOOD_PATTERN.matcher(name).matches()) problem = getString(R.string.error_food_invalid);
        else if (cat.getSelectedItemPosition() == 0) problem = getString(R.string.error_category_required);
        else if (endMin <= startMin) problem = getString(R.string.error_window_order);
        else if (todayAt(endMin) <= System.currentTimeMillis()) problem = getString(R.string.error_window_past);
        else if (!((CheckBox) view.findViewById(R.id.cbSafety)).isChecked()) problem = getString(R.string.error_safety_required);
        if (problem != null) {
            error.setText(problem);
            error.setVisibility(View.VISIBLE);
            if (name.isEmpty() || !FOOD_PATTERN.matcher(name).matches()) nameField.setError(problem);
            return;
        }

        DatabaseHelper.User me = Session.user(requireContext());
        if (me == null) return;
        DatabaseHelper.Listing l = editing != null ? editing : new DatabaseHelper.Listing();
        l.name = name;
        l.qty = quantity + " " + ((Spinner) view.findViewById(R.id.spinnerUnit)).getSelectedItem();
        l.category = cat.getSelectedItem().toString();
        l.desk = ((Spinner) view.findViewById(R.id.spinnerDesk)).getSelectedItem().toString();
        l.notes = ((EditText) view.findViewById(R.id.etNotes)).getText().toString().trim();
        l.photo = photoPath;
        l.pickupStart = todayAt(startMin);
        l.pickupEnd = todayAt(endMin);
        l.owner = me.email;
        l.donor = me.name;
        if (l.donorType == null) l.donorType = getString(R.string.donor_type_member);
        l.seed = false;

        // the desk's coordinates from Geoapify Geocoding (falls back to saved values offline)
        saving = true;
        String brgy = Geo.barangayOf(l.desk);
        GeoapifyClient.geocodeBarangay(brgy, new GeoapifyClient.Callback<double[]>() {
            @Override public void onResult(double[] ll) { finishSave(l, ll); }
            @Override public void onError(Exception e) { finishSave(l, Geo.fallback(brgy)); }
        });
    }

    private void finishSave(DatabaseHelper.Listing l, double[] ll) {
        saving = false;
        if (!isAdded()) return;
        l.lat = ll[0];
        l.lng = ll[1];
        DatabaseHelper db = DatabaseHelper.get(requireContext());
        if (editing != null) {
            db.updateListing(l);
            Toast.makeText(requireContext(), R.string.toast_listing_updated, Toast.LENGTH_SHORT).show();
            requireActivity().finish();
        } else {
            db.insertListing(l);
            Toast.makeText(requireContext(), R.string.toast_listing_posted, Toast.LENGTH_SHORT).show();
            resetForm(requireView());
            Nav.push(requireActivity(), new Intent(requireContext(), MyListingsActivity.class));
        }
    }

    private void confirmDelete() {
        int booked = DatabaseHelper.get(requireContext()).countScheduledFor(editing.id);
        String msg = getString(R.string.delete_listing_body)
                + (booked > 0 ? " " + getResources().getQuantityString(R.plurals.delete_cancels, booked, booked) : "");
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_listing_title, editing.name))
                .setMessage(msg)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    DatabaseHelper.get(requireContext()).deleteListing(editing.id);
                    Toast.makeText(requireContext(), getString(R.string.toast_listing_deleted, editing.name), Toast.LENGTH_SHORT).show();
                    requireActivity().finish();
                })
                .setNegativeButton(R.string.action_keep, null)
                .show();
    }
}
