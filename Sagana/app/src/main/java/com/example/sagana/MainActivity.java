package com.example.sagana;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Login screen. Checks the email and password against the users table (READ). */
public class MainActivity extends AppCompatActivity {

    private static final String DEMO_EMAIL = "demo@sagana.ph";
    private static final String DEMO_PASSWORD = "Food@1234";
    private static final String STAFF_EMAIL = "staff@sagana.ph";
    private static final String STAFF_PASSWORD = "Desk@1234";

    private EditText editEmail;
    private EditText editPassword;
    private ImageButton buttonTogglePassword;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        buttonTogglePassword = findViewById(R.id.buttonTogglePassword);

        buttonTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        editPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        findViewById(R.id.buttonLogin).setOnClickListener(v -> attemptLogin());

        findViewById(R.id.buttonDemo).setOnClickListener(v -> {
            editEmail.setText(DEMO_EMAIL);
            editPassword.setText(DEMO_PASSWORD);
            attemptLogin();
        });
        findViewById(R.id.textStaffDemo).setOnClickListener(v -> {
            editEmail.setText(STAFF_EMAIL);
            editPassword.setText(STAFF_PASSWORD);
            attemptLogin();
        });

        findViewById(R.id.textForgotPassword).setOnClickListener(v ->
                Toast.makeText(this, R.string.toast_coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.textCreateAccount).setOnClickListener(
                v -> Nav.push(this, new Intent(this, RegisterActivity.class))
        );
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        // Changing inputType resets the typeface and cursor, so restore both.
        editPassword.setTypeface(editEmail.getTypeface());
        editPassword.setSelection(editPassword.getText().length());

        buttonTogglePassword.setImageResource(
                isPasswordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility
        );
        buttonTogglePassword.setContentDescription(getString(
                isPasswordVisible ? R.string.cd_hide_password : R.string.cd_show_password
        ));
    }

    private void attemptLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        editEmail.setError(null);
        editPassword.setError(null);

        if (email.isEmpty()) {
            editEmail.setError(getString(R.string.error_email_required));
            editEmail.requestFocus();
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError(getString(R.string.error_email_invalid));
            editEmail.requestFocus();
        } else if (password.isEmpty()) {
            editPassword.setError(getString(R.string.error_password_required));
            editPassword.requestFocus();
        } else {
            DatabaseHelper.User user = DatabaseHelper.get(this).login(email, password);
            if (user == null) {
                editPassword.setError(getString(R.string.error_login_wrong));
                editPassword.requestFocus();
                return;
            }
            Session.login(this, user.email);
            Toast.makeText(this, getString(R.string.toast_welcome_back, user.firstName()), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Nav.rise(this, intent);
            finish();
        }
    }
}
