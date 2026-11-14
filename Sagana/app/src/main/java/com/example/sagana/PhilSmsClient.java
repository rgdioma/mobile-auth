package com.example.sagana;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PhilSMS: sends the one-time password during registration (and when a member changes
 * their mobile number).
 *
 *   POST {PHILSMS_BASE_URL}/sms/send          (default https://dashboard.philsms.com/api/v3)
 *   Authorization: Bearer {PHILSMS_TOKEN}
 *   {"recipient":"639171234567","sender_id":"PhilSMS","type":"plain","message":"..."}
 *
 * Why the host matters: API tokens created on PhilSMS's new dashboard are only accepted by
 * dashboard.philsms.com. The old host (app.philsms.com) answers those tokens with
 * {"status":"error","message":"Unauthenticated."} even though the token is valid.
 *
 * The token, sender ID and host come from local.properties through BuildConfig; nothing
 * secret is written in the code.
 */
public final class PhilSmsClient {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSent();
        void onError(String message);
    }

    private PhilSmsClient() {
    }

    public static boolean isConfigured() {
        return !BuildConfig.PHILSMS_TOKEN.isEmpty();
    }

    /** 09171234567 or +639171234567 -> 639171234567, the format PhilSMS expects. */
    public static String toRecipient(String mobile) {
        String digits = mobile.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) return "63" + digits.substring(1);
        if (digits.startsWith("9") && digits.length() == 10) return "63" + digits;
        return digits;
    }

    public static void sendOtp(String mobile, String code, Callback cb) {
        String message = "Your Sagana verification code is " + code +
                ". It expires in 5 minutes. Don't share this code with anyone.";
        send(toRecipient(mobile), message, cb);
    }

    public static void send(String recipient, String message, Callback cb) {
        if (!isConfigured()) {
            MAIN.post(() -> cb.onError("PHILSMS_TOKEN is missing from local.properties"));
            return;
        }
        IO.execute(() -> {
            HttpURLConnection con = null;
            try {
                JSONObject body = new JSONObject();
                body.put("recipient", recipient);
                body.put("sender_id", BuildConfig.PHILSMS_SENDER_ID);
                body.put("type", "plain");
                body.put("message", message);

                con = (HttpURLConnection) new URL(BuildConfig.PHILSMS_BASE_URL + "/sms/send").openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10000);
                con.setReadTimeout(20000);
                con.setDoOutput(true);
                con.setRequestProperty("Authorization", "Bearer " + BuildConfig.PHILSMS_TOKEN);
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = con.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int http = con.getResponseCode();
                String text = GeoapifyClient.read(http < 400 ? con.getInputStream() : con.getErrorStream());
                JSONObject json;
                try {
                    json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                } catch (Exception notJson) {
                    // e.g. an HTML error page from a wrong host
                    throw new Exception("PhilSMS sent an unexpected reply (HTTP " + http + "). Check PHILSMS_BASE_URL.");
                }
                // PhilSMS answers HTTP 200 even for errors, so "status" is what counts.
                if (http < 400 && "success".equalsIgnoreCase(json.optString("status"))) {
                    MAIN.post(cb::onSent);
                } else {
                    String raw = json.optString("message", "");
                    if (raw.isEmpty() || "null".equals(raw)) raw = "PhilSMS error (HTTP " + http + ")";
                    String friendly = explain(raw);
                    MAIN.post(() -> cb.onError(friendly));
                }
            } catch (UnknownHostException e) {
                MAIN.post(() -> cb.onError("No internet connection, so the SMS couldn't be sent"));
            } catch (SocketTimeoutException e) {
                MAIN.post(() -> cb.onError("PhilSMS took too long to answer. Try again"));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Couldn't reach PhilSMS" : e.getMessage();
                MAIN.post(() -> cb.onError(msg));
            } finally {
                if (con != null) con.disconnect();
            }
        });
    }

    /** Turns PhilSMS's error text into something a user (or developer) can act on. */
    static String explain(String raw) {
        String m = raw.toLowerCase(Locale.US);
        if (m.contains("unauthenticated") || m.contains("credentials"))
            return "PhilSMS rejected the API token. Check PHILSMS_TOKEN and PHILSMS_BASE_URL";
        if (m.contains("sender"))
            return "The sender ID \"" + BuildConfig.PHILSMS_SENDER_ID + "\" isn't allowed on this PhilSMS account (" + raw + ")";
        if (m.contains("balance") || m.contains("credit") || m.contains("insufficient"))
            return "The PhilSMS account is out of credits";
        if (m.contains("recipient") || (m.contains("invalid") && m.contains("number")))
            return "PhilSMS couldn't use this mobile number (" + raw + ")";
        return raw;
    }
}
