package com.example.sagana;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Edit profile (UPDATE / DELETE on users):
 *   photo, first and last name, email (needs the password), mobile (re-verified by PhilSMS OTP),
 *   barangay (re-geocoded with Geoapify), staff ID, password change, and deleting the account.
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final Pattern NAME = Pattern.compile("^[A-Za-zÑñ][A-Za-zÑñ '\\-.]{1,49}$");
    private static final Pattern MOBILE = Pattern.compile("^09\\d{9}$");
    private static final Pattern STAFF = Pattern.compile("^BRGY-\\d{4}$");

    private DatabaseHelper db;
    private DatabaseHelper.User user;      // as saved
    private DatabaseHelper.User draft;     // what is being saved right now
    private String photo;
    private boolean saving;

    private EditText first, last, email, mobile, staffId;
    private Spinner barangay;
    private TextView errorDetails;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    /** PhilSMS confirms the new mobile number before it is saved. */
    private final ActivityResultLauncher<Intent> verifyMobile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) stepBarangay();
                else {
                    saving = false;
                    showDetailsError(getString(R.string.error_mobile_not_verified));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editProfileRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = DatabaseHelper.get(this);
        user = Session.user(this);
        if (user == null) { finish(); return; }

        first = findViewById(R.id.editFirstName);
        last = findViewById(R.id.editLastName);
        email = findViewById(R.id.editEmail);
        mobile = findViewById(R.id.editMobile);
        staffId = findViewById(R.id.editStaffId);
        barangay = findViewById(R.id.spinnerBarangay);
        errorDetails = findViewById(R.id.errorDetails);

        first.setText(user.first);
        last.setText(user.last);
        email.setText(user.email);
        mobile.setText(user.mobile);
        for (int i = 0; i < barangay.getCount(); i++) {
            if (barangay.getItemAtPosition(i).toString().equals(user.barangay)) barangay.setSelection(i);
        }
        if (user.isStaff()) {
            findViewById(R.id.staffSection).setVisibility(View.VISIBLE);
            staffId.setText(user.staffId);
        }
        photo = user.photo;
        showPhoto();

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        View.OnClickListener choose = v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        findViewById(R.id.avatarFrame).setOnClickListener(choose);
        findViewById(R.id.textChangePhoto).setOnClickListener(choose);
        findViewById(R.id.buttonSaveDetails).setOnClickListener(v -> saveDetails());
        findViewById(R.id.buttonChangePassword).setOnClickListener(v -> changePassword());
        findViewById(R.id.buttonDeleteAccount).setOnClickListener(v -> deleteAccount());

        EditText newPass = findViewById(R.id.editNewPassword);
        newPass.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { showStrength(s.toString()); }
        });
        showStrength("");
    }

    // ------------------------------------------------------------------ photo
    private void onPhotoPicked(Uri uri) {
        if (uri == null) return;
        try {
            photo = Ui.copyPhoto(this, uri, "avatar");
            showPhoto();
            Toast.makeText(this, R.string.toast_photo_ready, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_photo_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhoto() {
        boolean has = Ui.loadPhoto(findViewById(R.id.imageAvatar), photo);
        findViewById(R.id.avatarCard).setVisibility(has ? View.VISIBLE : View.GONE);
        findViewById(R.id.avatarPlaceholder).setVisibility(has ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ details: validate, then email -> mobile -> barangay -> save
    private void saveDetails() {
        if (saving) return;
        errorDetails.setVisibility(View.GONE);
        String f = first.getText().toString().trim(), l = last.getText().toString().trim();
        String e = email.getText().toString().trim().toLowerCase(Locale.US);
        String m = mobile.getText().toString().trim();
        String s = staffId.getText().toString().trim().toUpperCase(Locale.US);

        if (!NAME.matcher(f).matches() || !NAME.matcher(l).matches()) { showDetailsError(getString(R.string.error_name_part_invalid)); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) { showDetailsError(getString(R.string.error_email_invalid)); return; }
        if (!e.equals(user.email) && db.emailTaken(e)) { showDetailsError(getString(R.string.error_email_taken)); return; }
        if (!MOBILE.matcher(m).matches()) { showDetailsError(getString(R.string.error_mobile_invalid)); return; }
        if (barangay.getSelectedItemPosition() == 0) { showDetailsError(getString(R.string.error_barangay_required)); return; }
        if (user.isStaff() && !STAFF.matcher(s).matches()) { showDetailsError(getString(R.string.error_staff_invalid)); return; }

        draft = Session.user(this);
        if (draft == null) return;
        draft.setNames(f, l);
        draft.mobile = m;
        draft.staffId = user.isStaff() ? s : null;
        draft.photo = photo;
        draft.barangay = barangay.getSelectedItem().toString();
        saving = true;

        if (!e.equals(user.email)) askPasswordForEmail(e);
        else stepMobile();
    }

    private void askPasswordForEmail(String newEmail) {
        EditText pass = passwordField();
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_email_change_title)
                .setMessage(getString(R.string.confirm_email_change_body, newEmail))
                .setView(wrap(pass))
                .setPositiveButton(R.string.action_continue, (d, w) -> {
                    if (!db.changeEmail(user.email, newEmail, pass.getText().toString())) {
                        saving = false;
                        showDetailsError(getString(R.string.error_wrong_password));
                        return;
                    }
                    Session.login(this, newEmail);
                    user.email = newEmail;
                    draft.email = newEmail;
                    stepMobile();
                })
                .setNegativeButton(R.string.action_cancel, (d, w) -> saving = false)
                .setOnCancelListener(d -> saving = false)
                .show();
    }

    private void stepMobile() {
        if (draft.mobile.equals(user.mobile)) { stepBarangay(); return; }
        Intent i = new Intent(this, OtpActivity.class);
        i.putExtra(OtpActivity.EXTRA_VERIFY_ONLY, true);
        i.putExtra(OtpActivity.EXTRA_MOBILE, draft.mobile);
        verifyMobile.launch(i);
        Nav.pushed(this);
    }

    private void stepBarangay() {
        if (draft.barangay.equals(user.barangay)) { commit(); return; }
        GeoapifyClient.geocodeBarangay(draft.barangay, new GeoapifyClient.Callback<double[]>() {
            @Override public void onResult(double[] ll) { draft.lat = ll[0]; draft.lng = ll[1]; commit(); }
            @Override public void onError(Exception e) {
                double[] ll = Geo.fallback(draft.barangay);
                draft.lat = ll[0];
                draft.lng = ll[1];
                commit();
            }
        });
    }

    private void commit() {
        saving = false;
        if (isFinishing()) return;
        db.updateProfile(draft);
        user = Session.user(this);
        Toast.makeText(this, R.string.toast_profile_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showDetailsError(String msg) {
        errorDetails.setText(msg);
        errorDetails.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ password
    private void showStrength(String p) {
        int met = 0;
        if (p.length() >= 8) met++;
        if (p.matches(".*[A-Z].*")) met++;
        if (p.matches(".*[a-z].*")) met++;
        if (p.matches(".*\\d.*")) met++;
        if (p.matches(".*[^A-Za-z0-9].*")) met++;
        ProgressBar bar = findViewById(R.id.progressStrength);
        bar.setProgress(met);
        bar.setProgressTintList(ContextCompat.getColorStateList(this,
                met <= 2 ? R.color.chili : met <= 4 ? R.color.mango : R.color.leaf));
    }

    private void changePassword() {
        TextView error = findViewById(R.id.errorPassword);
        error.setVisibility(View.GONE);
        EditText cur = findViewById(R.id.editCurrentPassword);
        EditText next = findViewById(R.id.editNewPassword);
        EditText conf = findViewById(R.id.editConfirmPassword);
        String c = cur.getText().toString(), n = next.getText().toString(), k = conf.getText().toString();
        String problem = null;
        if (c.isEmpty()) problem = getString(R.string.error_current_password_required);
        else if (((ProgressBar) findViewById(R.id.progressStrength)).getProgress() < 5) problem = getString(R.string.error_password_weak);
        else if (!n.equals(k)) problem = getString(R.string.error_confirm_mismatch);
        else if (n.equals(c)) problem = getString(R.string.error_password_same);
        else if (!db.changePassword(user.email, c, n)) problem = getString(R.string.error_wrong_password);
        if (problem != null) {
            error.setText(problem);
            error.setVisibility(View.VISIBLE);
            return;
        }
        cur.setText("");
        next.setText("");
        conf.setText("");
        Toast.makeText(this, R.string.toast_password_changed, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ delete account
    private void deleteAccount() {
        EditText pass = passwordField();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_body)
                .setView(wrap(pass))
                .setPositiveButton(R.string.action_delete_account, (d, w) -> {
                    if (!db.deleteAccount(user.email, pass.getText().toString())) {
                        Toast.makeText(this, R.string.error_wrong_password, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Session.logout(this);
                    Toast.makeText(this, R.string.toast_account_deleted, Toast.LENGTH_LONG).show();
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Nav.fade(this, i);
                })
                .setNegativeButton(R.string.action_keep_account, null)
                .show();
    }

    // ------------------------------------------------------------------ helpers
    private EditText passwordField() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint(R.string.hint_password);
        e.setBackgroundResource(R.drawable.bg_input);
        int p = Ui.dp(this, 14);
        e.setPadding(p, p, p, p);
        return e;
    }

    private View wrap(View v) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        int p = Ui.dp(this, 22);
        f.setPadding(p, Ui.dp(this, 8), p, 0);
        f.addView(v);
        return f;
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }
}
