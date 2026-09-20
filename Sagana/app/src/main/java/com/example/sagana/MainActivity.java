package com.example.sagana;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String DEMO_EMAIL = "juan@email.com";
    private static final String DEMO_PASSWORD = "demo1234";

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

        findViewById(R.id.textForgotPassword).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.textCreateAccount).setOnClickListener(v -> showComingSoon());
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
            String displayName;
            if (DEMO_EMAIL.equals(email)) {
                displayName = getString(R.string.demo_display_name);
            } else {
                String prefix = email.split("@")[0];
                displayName = prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
            }
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra(HomeActivity.EXTRA_DISPLAY_NAME, displayName);
            startActivity(intent);
            finish();
        }
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.toast_coming_soon, Toast.LENGTH_SHORT).show();
    }
}
