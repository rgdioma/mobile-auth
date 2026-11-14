package com.example.sagana;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.security.SecureRandom;

/**
 * Registration step 2: confirm the mobile number with a one-time password sent by PhilSMS.
 * The account is only written to the database (CREATE) after the right code is typed.
 */
public class OtpActivity extends AppCompatActivity {

    public static final String EXTRA_FIRST_NAME = "otp.first";
    public static final String EXTRA_LAST_NAME = "otp.last";
    /** Edit profile: only confirm the new number and return RESULT_OK, don't create an account. */
    public static final String EXTRA_VERIFY_ONLY = "otp.verifyOnly";
    public static final String EXTRA_EMAIL = "otp.email";
    public static final String EXTRA_MOBILE = "otp.mobile";
    public static final String EXTRA_BARANGAY = "otp.barangay";
    public static final String EXTRA_ROLE = "otp.role";
    public static final String EXTRA_STAFF_ID = "otp.staff";
    public static final String EXTRA_PASS_HASH = "otp.hash";

    private static final int DIGITS = 6;
    private static final long CODE_LIFETIME = 5 * 60_000L;
    private static final long RESEND_WAIT = 60_000L;
    private static final int MAX_TRIES = 5;

    private final EditText[] boxes = new EditText[DIGITS];
    private TextView status, resend, debugCode;
    private ProgressBar progress;
    private View verifyButton;

    private String code;
    private long expiresAt;
    private long resendAt;
    private int tries;
    private boolean busy;
    private CountDownTimer timer;
    private boolean leaving; // going forward to Home, so skip the "back" animation
    private boolean verifyOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_otp);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.otpRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        status = findViewById(R.id.textOtpStatus);
        resend = findViewById(R.id.textResend);
        debugCode = findViewById(R.id.textDebugCode);
        progress = findViewById(R.id.progressOtp);
        verifyButton = findViewById(R.id.buttonVerify);

        String mobile = getIntent().getStringExtra(EXTRA_MOBILE);
        verifyOnly = getIntent().getBooleanExtra(EXTRA_VERIFY_ONLY, false);
        ((TextView) findViewById(R.id.textOtpSubtitle)).setText(getString(
                verifyOnly ? R.string.otp_subtitle_change : R.string.otp_subtitle, mask(mobile)));
        if (verifyOnly) ((TextView) verifyButton).setText(R.string.otp_verify_number);

        buildBoxes();
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        verifyButton.setOnClickListener(v -> verify());
        resend.setOnClickListener(v -> {
            if (System.currentTimeMillis() >= resendAt && !busy) sendCode();
        });
        debugCode.setOnClickListener(v -> showTestCode());

        if (savedInstanceState != null && savedInstanceState.getString("code") != null) {
            code = savedInstanceState.getString("code");
            expiresAt = savedInstanceState.getLong("expires");
            resendAt = savedInstanceState.getLong("resendAt");
            tries = savedInstanceState.getInt("tries");
            setStatus(getString(R.string.otp_sent), false);
            startResendTimer();
        } else {
            sendCode();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("code", code);
        out.putLong("expires", expiresAt);
        out.putLong("resendAt", resendAt);
        out.putInt("tries", tries);
    }

    // ------------------------------------------------------------------ the six boxes
    private void buildBoxes() {
        LinearLayout row = findViewById(R.id.otpBoxes);
        int size = dp(48), gap = dp(8);
        for (int i = 0; i < DIGITS; i++) {
            EditText e = new EditText(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, dp(58));
            if (i > 0) lp.setMarginStart(i == 3 ? gap * 2 : gap);
            e.setLayoutParams(lp);
            e.setBackgroundResource(R.drawable.bg_input);
            e.setGravity(Gravity.CENTER);
            e.setTextSize(24);
            e.setTextColor(ContextCompat.getColor(this, R.color.adobo));
            e.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DIGITS)}); // allows pasting the whole code
            e.setContentDescription(getString(R.string.cd_otp_digit, i + 1));
            e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            final int index = i;
            e.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 1) {           // pasted or autofilled several digits
                        String all = s.toString().replaceAll("\\D", "");
                        e.removeTextChangedListener(this);
                        e.setText("");
                        e.addTextChangedListener(this);
                        fillFrom(index, all);
                        return;
                    }
                    if (s.length() == 1 && index < DIGITS - 1) boxes[index + 1].requestFocus();
                    if (enteredCode().length() == DIGITS) verify();
                }
            });
            e.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && e.getText().length() == 0 && index > 0) {
                    boxes[index - 1].setText("");
                    boxes[index - 1].requestFocus();
                    return true;
                }
                return false;
            });
            boxes[i] = e;
            row.addView(e);
        }
        boxes[0].requestFocus();
    }

    private void fillFrom(int start, String digits) {
        for (int i = 0; i < digits.length() && start + i < DIGITS; i++) {
            boxes[start + i].setText(String.valueOf(digits.charAt(i)));
        }
        boxes[Math.min(DIGITS - 1, start + digits.length())].requestFocus();
    }

    private String enteredCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText b : boxes) sb.append(b.getText().toString().trim());
        return sb.toString();
    }

    private void clearBoxes() {
        for (EditText b : boxes) b.setText("");
        boxes[0].requestFocus();
    }

    // ------------------------------------------------------------------ sending
    private void sendCode() {
        code = String.format(java.util.Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
        expiresAt = System.currentTimeMillis() + CODE_LIFETIME;
        resendAt = System.currentTimeMillis() + RESEND_WAIT;
        tries = 0;
        debugCode.setVisibility(View.GONE);
        startResendTimer();

        if (!PhilSmsClient.isConfigured()) {
            // No token in local.properties: let the app still be tested.
            setStatus(getString(R.string.otp_not_configured), true);
            debugCode.setVisibility(View.VISIBLE);
            return;
        }

        setBusy(true);
        setStatus(getString(R.string.otp_sending), false);
        PhilSmsClient.sendOtp(getIntent().getStringExtra(EXTRA_MOBILE), code, new PhilSmsClient.Callback() {
            @Override
            public void onSent() {
                if (isFinishing()) return;
                setBusy(false);
                setStatus(getString(R.string.otp_sent), false);
                Toast.makeText(OtpActivity.this, getString(R.string.toast_sms_sent,
                        mask(getIntent().getStringExtra(EXTRA_MOBILE))), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                setBusy(false);
                setStatus(getString(R.string.otp_send_failed, message), true);
                Toast.makeText(OtpActivity.this, R.string.toast_sms_failed, Toast.LENGTH_SHORT).show();
                resendAt = 0; // let them retry right away
                if (BuildConfig.DEBUG) debugCode.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Only reachable when SMS isn't set up, or in a debug build after sending failed. */
    private void showTestCode() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.otp_test_code_title)
                .setMessage(getString(R.string.otp_test_code_body, code))
                .setPositiveButton(R.string.action_ok, (d, w) -> fillFrom(0, code))
                .show();
    }

    private void startResendTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(Math.max(0, resendAt - System.currentTimeMillis()) + 500, 1000) {
            @Override
            public void onTick(long left) {
                long s = Math.max(0, (resendAt - System.currentTimeMillis()) / 1000);
                if (s <= 0) { onFinish(); return; }
                resend.setText(getString(R.string.otp_resend_in, s / 60, s % 60));
                resend.setTextColor(ContextCompat.getColor(OtpActivity.this, R.color.text_secondary));
            }

            @Override
            public void onFinish() {
                resend.setText(R.string.otp_resend);
                resend.setTextColor(ContextCompat.getColor(OtpActivity.this, R.color.chili));
            }
        }.start();
    }

    // ------------------------------------------------------------------ verifying
    private void verify() {
        if (busy) return;
        String typed = enteredCode();
        if (typed.length() < DIGITS) {
            setStatus(getString(R.string.otp_enter_all), true);
            return;
        }
        if (code == null || System.currentTimeMillis() > expiresAt) {
            setStatus(getString(R.string.otp_expired), true);
            clearBoxes();
            return;
        }
        if (!typed.equals(code)) {
            tries++;
            shake();
            clearBoxes();
            if (tries >= MAX_TRIES) {
                code = null;
                resendAt = 0;
                startResendTimer();
                setStatus(getString(R.string.otp_too_many), true);
            } else {
                setStatus(getResources().getQuantityString(R.plurals.otp_wrong, MAX_TRIES - tries, MAX_TRIES - tries), true);
            }
            return;
        }
        if (verifyOnly) {
            celebrate(() -> {
                setResult(RESULT_OK);
                finish();
            });
            return;
        }
        createAccount();
    }

    /** Right code: find the barangay with Geoapify, save the account, and go to Home. */
    private void createAccount() {
        setBusy(true);
        setStatus(getString(R.string.otp_creating), false);
        String barangay = getIntent().getStringExtra(EXTRA_BARANGAY);
        GeoapifyClient.geocodeBarangay(barangay, new GeoapifyClient.Callback<double[]>() {
            @Override
            public void onResult(double[] ll) {
                save(ll);
            }

            @Override
            public void onError(Exception e) {
                save(Geo.fallback(barangay)); // offline: use the saved barangay centre
            }
        });
    }

    private void save(double[] ll) {
        if (isFinishing()) return;
        DatabaseHelper.User u = new DatabaseHelper.User();
        Intent in = getIntent();
        u.setNames(in.getStringExtra(EXTRA_FIRST_NAME), in.getStringExtra(EXTRA_LAST_NAME));
        u.email = in.getStringExtra(EXTRA_EMAIL);
        u.mobile = in.getStringExtra(EXTRA_MOBILE);
        u.barangay = in.getStringExtra(EXTRA_BARANGAY);
        u.role = in.getStringExtra(EXTRA_ROLE);
        u.staffId = in.getStringExtra(EXTRA_STAFF_ID);
        u.lat = ll[0];
        u.lng = ll[1];
        if (!DatabaseHelper.get(this).registerUser(u, in.getStringExtra(EXTRA_PASS_HASH))) {
            setBusy(false);
            setStatus(getString(R.string.error_email_taken), true);
            return;
        }
        Session.login(this, u.email);
        celebrate(() -> {
            Toast.makeText(this, getString(R.string.toast_account_created, u.firstName()), Toast.LENGTH_SHORT).show();
            Intent home = new Intent(this, HomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Nav.rise(this, home);
            leaving = true;
            finish();
        });
    }

    // ------------------------------------------------------------------ feedback
    private void celebrate(Runnable then) {
        View badge = findViewById(R.id.otpBadge);
        ImageView icon = findViewById(R.id.otpIcon);
        badge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.leaf_light));
        icon.setImageResource(R.drawable.ic_rule_met);
        icon.setImageTintList(null);
        badge.setScaleX(0.6f);
        badge.setScaleY(0.6f);
        badge.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new OvershootInterpolator(3f))
                .withEndAction(() -> badge.postDelayed(then, 350)).start();
        setStatus(getString(R.string.otp_verified), false);
        status.setTextColor(ContextCompat.getColor(this, R.color.leaf));
    }

    private void shake() {
        View row = findViewById(R.id.otpBoxes);
        row.animate().cancel();
        row.setTranslationX(0);
        android.animation.ObjectAnimator.ofFloat(row, View.TRANSLATION_X, 0, dp(14), -dp(12), dp(8), -dp(4), 0)
                .setDuration(380).start();
    }

    private void setBusy(boolean b) {
        busy = b;
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        verifyButton.setEnabled(!b);
    }

    private void setStatus(String text, boolean error) {
        status.setText(text);
        status.setTextColor(ContextCompat.getColor(this, error ? R.color.chili : R.color.text_secondary));
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 4) + " ••• " + mobile.substring(mobile.length() - 4);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void finish() {
        super.finish();
        if (!leaving) Nav.pop(this);
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}
