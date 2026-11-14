package com.example.sagana;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handover codes: making them, drawing them as QR, and reading them back from a scan. */
public final class QrCodes {

    public static final Pattern CODE = Pattern.compile("^SG-[A-Z0-9]{4}$");
    private static final Pattern IN_TEXT = Pattern.compile("SG-[A-Z0-9]{4}");
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O or 1/I mix-ups

    private QrCodes() {
    }

    public static String newCode(DatabaseHelper db) {
        java.security.SecureRandom r = new java.security.SecureRandom();
        String code;
        do {
            StringBuilder sb = new StringBuilder("SG-");
            for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
            code = sb.toString();
        } while (db.codeExists(code));
        return code;
    }

    /** Pulls "SG-XXXX" out of whatever the camera read, or returns null. */
    public static String extract(String scanned) {
        if (scanned == null) return null;
        Matcher m = IN_TEXT.matcher(scanned.trim().toUpperCase(Locale.US));
        return m.find() ? m.group() : null;
    }

    /** Draws the code as a QR image, dark brown on white. */
    public static Bitmap render(String text, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = m.getWidth(), h = m.getHeight();
            int[] px = new int[w * h];
            int ink = Color.parseColor("#3A1B12");
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    px[y * w + x] = m.get(x, y) ? ink : Color.WHITE;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
