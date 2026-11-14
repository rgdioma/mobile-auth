package com.example.sagana;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import java.util.Locale;

/** Account details (UPDATE users), links to My listings and the report, text-file backup and restore. */
public class ProfileFragment extends Fragment {


    private final ActivityResultLauncher<String[]> pickBackup = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::confirmRestore);

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Ui.padForStatusBar(view);

        view.findViewById(R.id.textSdgLink).setOnClickListener(v -> openSdgPage());
        View.OnClickListener edit = v ->
                Nav.push(requireActivity(), new Intent(requireContext(), EditProfileActivity.class));
        view.findViewById(R.id.rowEditProfile).setOnClickListener(edit);
        view.findViewById(R.id.avatarPlaceholder).setOnClickListener(edit);
        view.findViewById(R.id.avatarCard).setOnClickListener(edit);
        view.findViewById(R.id.rowMyListings).setOnClickListener(v ->
                Nav.push(requireActivity(), new Intent(requireContext(), MyListingsActivity.class)));
        view.findViewById(R.id.rowReport).setOnClickListener(v ->
                Nav.push(requireActivity(), new Intent(requireContext(), ReportsActivity.class)));
        view.findViewById(R.id.textReplayIntro).setOnClickListener(v ->
                Nav.push(requireActivity(), new Intent(requireContext(), OnboardingActivity.class)));
        view.findViewById(R.id.buttonBackup).setOnClickListener(v -> backup());
        view.findViewById(R.id.buttonRestore).setOnClickListener(v ->
                pickBackup.launch(new String[]{"text/plain", "application/octet-stream"}));
        view.findViewById(R.id.buttonLogout).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_body)
                .setPositiveButton(R.string.action_logout, (d, w) -> logout())
                .setNegativeButton(R.string.action_stay, null)
                .show());
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        View view = getView();
        DatabaseHelper.User u = Session.user(requireContext());
        if (view == null || u == null) return;
        DatabaseHelper db = DatabaseHelper.get(requireContext());

        ((TextView) view.findViewById(R.id.textName)).setText(u.fullName());
        boolean hasPhoto = Ui.loadPhoto(view.findViewById(R.id.imageAvatar), u.photo);
        view.findViewById(R.id.avatarCard).setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.avatarPlaceholder).setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        String role = getString(u.isStaff() ? R.string.role_staff_label : R.string.role_member_label) + " · Brgy. " + u.barangay
                + (u.isStaff() && u.staffId != null ? " · " + u.staffId : "");
        ((TextView) view.findViewById(R.id.textRole)).setText(role);
        ((TextView) view.findViewById(R.id.textEmail)).setText(u.email);
        ((TextView) view.findViewById(R.id.textMobile)).setText(u.mobile);
        view.findViewById(R.id.memberRows).setVisibility(u.isStaff() ? View.GONE : View.VISIBLE);
        ((RatingBar) view.findViewById(R.id.ratingBar)).setVisibility(u.isStaff() ? View.GONE : View.VISIBLE);

        if (u.isStaff()) {
            int waiting = db.getWaitingAtDesk(u.desk()).size();
            int done = db.getHistoryAtDesk(u.desk()).size();
            setStat(view, 1, waiting, R.string.stat_waiting);
            setStat(view, 2, done, R.string.stat_handed_over);
            setStat(view, 3, waiting + done, R.string.stat_total);
        } else {
            setStat(view, 1, db.getMyListings(u.email).size(), R.string.stat_posted);
            setStat(view, 2, db.countBookings(u.email, null), R.string.stat_pickups);
            setStat(view, 3, db.countBookings(u.email, "Received"), R.string.stat_received);
        }
        ((TextView) view.findViewById(R.id.textDbStats)).setText(String.format(Locale.US,
                "users %d · listings %d · bookings %d · archive %d",
                db.tableCount("users"), db.tableCount("listings"), db.tableCount("bookings"), db.tableCount("archive")));
    }

    private void setStat(View v, int i, int value, int label) {
        int vid = i == 1 ? R.id.statValue1 : i == 2 ? R.id.statValue2 : R.id.statValue3;
        int lid = i == 1 ? R.id.statLabel1 : i == 2 ? R.id.statLabel2 : R.id.statLabel3;
        ((TextView) v.findViewById(vid)).setText(String.valueOf(value));
        ((TextView) v.findViewById(lid)).setText(label);
    }

    // ------------------------------------------------------------------ backup / restore (text file)
    private void backup() {
        try {
            int[] rows = new int[1];
            String where = BackupManager.backup(requireContext(), DatabaseHelper.get(requireContext()), rows);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.backup_done_title)
                    .setMessage(getString(R.string.backup_done_body, rows[0], where))
                    .setPositiveButton(R.string.action_done, null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.toast_backup_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRestore(Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_body)
                .setPositiveButton(R.string.action_restore_replace, (d, w) -> restore(uri))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void restore(Uri uri) {
        try {
            int n = BackupManager.restore(requireContext(), DatabaseHelper.get(requireContext()), uri);
            if (Session.user(requireContext()) == null) {
                Toast.makeText(requireContext(), R.string.toast_restored_logout, Toast.LENGTH_LONG).show();
                logout();
                return;
            }
            Toast.makeText(requireContext(), getString(R.string.toast_restored, n), Toast.LENGTH_SHORT).show();
            render();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.toast_restore_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ misc
    private void openSdgPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sdg_url)));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.toast_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        Session.logout(requireContext());
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Nav.fade(requireActivity(), intent);
        requireActivity().finish();
    }
}
