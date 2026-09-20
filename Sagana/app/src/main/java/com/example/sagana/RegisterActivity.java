package com.example.sagana;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    /** Letters, spaces, hyphens and apostrophes; at least two characters. */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-zÑñ][A-Za-zÑñ '\\-.]{1,49}$");
    /** Philippine mobile format: 11 digits starting with 09. */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^09\\d{9}$");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");

    private EditText editFullName;
    private EditText editEmail;
    private EditText editMobile;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private Spinner spinnerBarangay;
    private RadioGroup radioGroupRole;
    private CheckBox checkTerms;
    private ImageButton buttonTogglePassword;
    private ProgressBar progressStrength;

    private TextView errorFullName;
    private TextView errorEmail;
    private TextView errorMobile;
    private TextView errorBarangay;
    private TextView errorPassword;
    private TextView errorConfirmPassword;
    private TextView errorRole;
    private TextView errorTerms;

    private TextView ruleLength;
    private TextView ruleUppercase;
    private TextView ruleLowercase;
    private TextView ruleNumber;
    private TextView ruleSymbol;

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editFullName = findViewById(R.id.editFullName);
        editEmail = findViewById(R.id.editEmail);
        editMobile = findViewById(R.id.editMobile);
        editPassword = findViewById(R.id.editPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);
        spinnerBarangay = findViewById(R.id.spinnerBarangay);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        checkTerms = findViewById(R.id.checkTerms);
        buttonTogglePassword = findViewById(R.id.buttonTogglePassword);
        progressStrength = findViewById(R.id.progressStrength);

        errorFullName = findViewById(R.id.errorFullName);
        errorEmail = findViewById(R.id.errorEmail);
        errorMobile = findViewById(R.id.errorMobile);
        errorBarangay = findViewById(R.id.errorBarangay);
        errorPassword = findViewById(R.id.errorPassword);
        errorConfirmPassword = findViewById(R.id.errorConfirmPassword);
        errorRole = findViewById(R.id.errorRole);
        errorTerms = findViewById(R.id.errorTerms);

        ruleLength = findViewById(R.id.ruleLength);
        ruleUppercase = findViewById(R.id.ruleUppercase);
        ruleLowercase = findViewById(R.id.ruleLowercase);
        ruleNumber = findViewById(R.id.ruleNumber);
        ruleSymbol = findViewById(R.id.ruleSymbol);

        buttonTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        editPassword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordRules(s.toString());
            }
        });

        Button buttonCreateAccount = findViewById(R.id.buttonCreateAccount);
        buttonCreateAccount.setOnClickListener(v -> attemptRegister());

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.textLogin).setOnClickListener(v -> finish());

        updatePasswordRules("");
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        // Changing inputType resets the typeface and cursor, so restore both.
        editPassword.setTypeface(editFullName.getTypeface());
        editPassword.setSelection(editPassword.getText().length());

        buttonTogglePassword.setImageResource(
                isPasswordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility
        );
        buttonTogglePassword.setContentDescription(getString(
                isPasswordVisible ? R.string.cd_hide_password : R.string.cd_show_password
        ));
    }

    /** Ticks off each rule as it is met and moves the strength bar from red to green. */
    private void updatePasswordRules(String password) {
        boolean hasLength = password.length() >= 8;
        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).matches();
        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).matches();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).matches();
        boolean hasSymbol = SYMBOL_PATTERN.matcher(password).matches();

        markRule(ruleLength, hasLength);
        markRule(ruleUppercase, hasUppercase);
        markRule(ruleLowercase, hasLowercase);
        markRule(ruleNumber, hasDigit);
        markRule(ruleSymbol, hasSymbol);

        int met = 0;
        if (hasLength) met++;
        if (hasUppercase) met++;
        if (hasLowercase) met++;
        if (hasDigit) met++;
        if (hasSymbol) met++;

        progressStrength.setProgress(met);
        int barColor;
        if (met <= 2) {
            barColor = R.color.chili;
        } else if (met <= 4) {
            barColor = R.color.mango;
        } else {
            barColor = R.color.leaf;
        }
        progressStrength.setProgressTintList(
                ContextCompat.getColorStateList(this, barColor)
        );
    }

    private void markRule(TextView rule, boolean met) {
        rule.setCompoundDrawablesRelativeWithIntrinsicBounds(
                met ? R.drawable.ic_rule_met : R.drawable.ic_rule_unmet, 0, 0, 0
        );
        rule.setTextColor(ContextCompat.getColor(this, met ? R.color.leaf : R.color.text_secondary));
    }

    private void attemptRegister() {
        String fullName = editFullName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String mobile = editMobile.getText().toString().trim();
        String password = editPassword.getText().toString();
        String confirmPassword = editConfirmPassword.getText().toString();
        boolean barangayChosen = spinnerBarangay.getSelectedItemPosition() > 0;
        int checkedRole = radioGroupRole.getCheckedRadioButtonId();

        clearErrors();
        boolean valid = true;
        View firstInvalid = null;

        if (TextUtils.isEmpty(fullName)) {
            showError(errorFullName, R.string.error_name_required);
            firstInvalid = editFullName;
            valid = false;
        } else if (!NAME_PATTERN.matcher(fullName).matches()) {
            showError(errorFullName, R.string.error_name_invalid);
            firstInvalid = editFullName;
            valid = false;
        }

        if (TextUtils.isEmpty(email)) {
            showError(errorEmail, R.string.error_email_required);
            if (firstInvalid == null) firstInvalid = editEmail;
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(errorEmail, R.string.error_email_invalid);
            if (firstInvalid == null) firstInvalid = editEmail;
            valid = false;
        }

        if (TextUtils.isEmpty(mobile)) {
            showError(errorMobile, R.string.error_mobile_required);
            if (firstInvalid == null) firstInvalid = editMobile;
            valid = false;
        } else if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            showError(errorMobile, R.string.error_mobile_invalid);
            if (firstInvalid == null) firstInvalid = editMobile;
            valid = false;
        }

        if (!barangayChosen) {
            showError(errorBarangay, R.string.error_barangay_required);
            if (firstInvalid == null) firstInvalid = spinnerBarangay;
            valid = false;
        }

        if (TextUtils.isEmpty(password)) {
            showError(errorPassword, R.string.error_password_required);
            if (firstInvalid == null) firstInvalid = editPassword;
            valid = false;
        } else if (progressStrength.getProgress() < 5) {
            showError(errorPassword, R.string.error_password_weak);
            if (firstInvalid == null) firstInvalid = editPassword;
            valid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            showError(errorConfirmPassword, R.string.error_confirm_required);
            if (firstInvalid == null) firstInvalid = editConfirmPassword;
            valid = false;
        } else if (!confirmPassword.equals(password)) {
            showError(errorConfirmPassword, R.string.error_confirm_mismatch);
            if (firstInvalid == null) firstInvalid = editConfirmPassword;
            valid = false;
        }

        if (checkedRole == -1) {
            showError(errorRole, R.string.error_role_required);
            if (firstInvalid == null) firstInvalid = radioGroupRole;
            valid = false;
        }

        if (!checkTerms.isChecked()) {
            showError(errorTerms, R.string.error_terms_required);
            if (firstInvalid == null) firstInvalid = checkTerms;
            valid = false;
        }

        if (!valid) {
            // Bring the first problem into view.
            final View target = firstInvalid;
            target.post(() -> {
                target.requestFocus();
                target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), false);
            });
            return;
        }

        // TODO: Replace with a real sign-up request.
        String firstName = fullName.split(" ")[0];
        Toast.makeText(this, getString(R.string.toast_account_created, firstName), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.EXTRA_DISPLAY_NAME, firstName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void clearErrors() {
        errorFullName.setVisibility(View.GONE);
        errorEmail.setVisibility(View.GONE);
        errorMobile.setVisibility(View.GONE);
        errorBarangay.setVisibility(View.GONE);
        errorPassword.setVisibility(View.GONE);
        errorConfirmPassword.setVisibility(View.GONE);
        errorRole.setVisibility(View.GONE);
        errorTerms.setVisibility(View.GONE);
    }

    private void showError(TextView label, int messageRes) {
        label.setText(messageRes);
        label.setVisibility(View.VISIBLE);
    }

    /** Saves implementing the two TextWatcher methods this screen doesn't use. */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
