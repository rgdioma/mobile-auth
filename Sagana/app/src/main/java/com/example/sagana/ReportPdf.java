package com.example.sagana;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the weekly report as a 2-page A4 PDF with Android's built-in PdfDocument
 * (no extra library): header, summary tiles, the three charts, and two tables.
 */
public final class ReportPdf {

    private static final int W = 595, H = 842, M = 40;   // A4 in PostScript points
    private static final int CHILI = 0xFFC8231B, MANGO = 0xFFFFB400, ADOBO = 0xFF3A1B12,
            MUTED = 0xFF6B5A52, LINE = 0xFFEFDFCC, RICE = 0xFFFFFBF4, BUTTER = 0xFFFFF3CF;

    private ReportPdf() {
    }

    public static byte[] build(Context ctx, ReportData r, Bitmap bar, Bitmap pie, Bitmap desk, String generatedBy) throws Exception {
        PdfDocument doc = new PdfDocument();
        String period = Ui.prettyDay(r.days.get(0)) + " to " + Ui.prettyDay(r.days.get(6));
        String stamp = new SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US).format(new Date());

        // ------------------------------------------------ page 1
        PdfDocument.Page p1 = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        Canvas c = p1.getCanvas();
        int y = header(ctx, c, period, stamp, generatedBy);

        // summary tiles
        String[][] kpis = {
                {"Food rescued", String.format(Locale.US, "%.1f kg", r.kg)},
                {"Handovers", String.valueOf(r.totalPicked)},
                {"Rescue rate", r.rescueRate() + "%"},
                {"Active listings", String.valueOf(r.active)}};
        float tileW = (W - 2 * M - 3 * 10) / 4f;
        for (int i = 0; i < 4; i++) {
            float x = M + i * (tileW + 10);
            RectF box = new RectF(x, y, x + tileW, y + 62);
            c.drawRoundRect(box, 10, 10, fill(i == 0 ? BUTTER : RICE));
            c.drawRoundRect(box, 10, 10, stroke(LINE, 1));
            c.drawText(kpis[i][0], x + 10, y + 20, text(9, MUTED, true));
            c.drawText(kpis[i][1], x + 10, y + 48, text(20, i == 0 ? CHILI : ADOBO, true));
        }
        y += 62 + 8;
        c.drawText(r.totalExpired + " listings expired before pickup this week.", M, y + 10, text(9, MUTED, false));
        y += 28;

        y = section(c, "Handovers per day", "Stacked: picked up and expired before pickup", y);
        y = image(c, bar, y, 210);
        y += 18;
        y = section(c, "What's on offer now", "Open listings by category", y);
        image(c, pie, y, 200);
        footer(c, 1);
        doc.finishPage(p1);

        // ------------------------------------------------ page 2
        PdfDocument.Page p2 = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 2).create());
        c = p2.getCanvas();
        y = M;
        y = section(c, "Busiest pickup desks", "Handovers this week", y);
        y = image(c, desk, y, 200);
        y += 20;

        y = section(c, "Day by day", null, y);
        String[] dayHead = {"Day", "Picked up", "Expired", "Food rescued"};
        float[] dayCols = {M, M + 200, M + 300, M + 400};
        y = tableHeader(c, dayHead, dayCols, y);
        for (int i = 0; i < 7; i++) {
            String[] row = {Ui.prettyDay(r.days.get(i)) + (i == 6 ? " (today)" : ""), String.valueOf(r.picked[i]),
                    String.valueOf(r.expired[i]), String.format(Locale.US, "%.1f kg", r.kgPerDay[i])};
            y = tableRow(c, row, dayCols, y, i % 2 == 1);
        }
        String[] total = {"Total", String.valueOf(r.totalPicked), String.valueOf(r.totalExpired), String.format(Locale.US, "%.1f kg", r.kg)};
        y = tableRow(c, total, dayCols, y, false);
        c.drawLine(M, y - 18, W - M, y - 18, stroke(ADOBO, 1));
        y += 20;

        y = section(c, "By desk", null, y);
        String[] deskHead = {"Pickup desk", "Handovers"};
        float[] deskCols = {M, M + 400};
        y = tableHeader(c, deskHead, deskCols, y);
        int i = 0;
        for (Map.Entry<String, Integer> e : r.byDesk.entrySet()) {
            y = tableRow(c, new String[]{e.getKey(), String.valueOf(e.getValue())}, deskCols, y, i++ % 2 == 1);
        }
        y += 16;
        c.drawText("Earlier days include sample records so the report has history on a fresh install.", M, y, text(8.5f, MUTED, false));
        footer(c, 2);
        doc.finishPage(p2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.writeTo(out);
        doc.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ pieces
    private static int header(Context ctx, Canvas c, String period, String stamp, String by) {
        c.drawRect(0, 0, W, 108, fill(CHILI));
        c.drawRect(0, 108, W, 112, fill(MANGO));
        Drawable logo = ContextCompat.getDrawable(ctx, R.drawable.ic_logo);
        if (logo != null) {
            // white disc so the red logo circle stands out on the red band
            c.drawCircle(M + 30, 54, 31, fill(Color.WHITE));
            logo.setBounds(M + 2, 26, M + 58, 82);
            logo.draw(c);
        }
        Paint brand = text(30, Color.WHITE, true);
        Typeface baloo = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.baloo2_extrabold);
        if (baloo != null) brand.setTypeface(baloo);
        c.drawText("Sagana", M + 74, 52, brand);
        c.drawText("Weekly community impact report", M + 74, 72, text(12, 0xFFFFE3A3, true));
        c.drawText(period, M + 74, 90, text(10, Color.WHITE, false));
        Paint right = text(8.5f, 0xFFFFD2CC, false);
        right.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Generated " + stamp, W - M, 30, right);
        if (by != null && !by.isEmpty()) c.drawText("by " + by, W - M, 44, right);
        return 136;
    }

    private static int section(Canvas c, String title, String hint, int y) {
        c.drawText(title, M, y + 12, text(13, ADOBO, true));
        if (hint != null) {
            c.drawText(hint, M, y + 27, text(9, MUTED, false));
            return y + 36;
        }
        return y + 22;
    }

    /** Draws a chart snapshot scaled to the page width, in a light card. */
    private static int image(Canvas c, Bitmap bmp, int y, int maxH) {
        RectF card = new RectF(M, y, W - M, y + maxH);
        c.drawRoundRect(card, 10, 10, fill(Color.WHITE));
        c.drawRoundRect(card, 10, 10, stroke(LINE, 1));
        if (bmp != null && bmp.getWidth() > 0) {
            float availW = W - 2 * M - 16, availH = maxH - 16;
            float s = Math.min(availW / bmp.getWidth(), availH / bmp.getHeight());
            float w = bmp.getWidth() * s, h = bmp.getHeight() * s;
            float x = M + (W - 2 * M - w) / 2f, top = y + (maxH - h) / 2f;
            Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(bmp, null, new RectF(x, top, x + w, top + h), smooth);
        }
        return y + maxH;
    }

    private static int tableHeader(Canvas c, String[] cols, float[] xs, int y) {
        c.drawRect(M, y, W - M, y + 22, fill(BUTTER));
        for (int i = 0; i < cols.length; i++) c.drawText(cols[i], xs[i] + 8, y + 15, text(9.5f, ADOBO, true));
        return y + 22;
    }

    private static int tableRow(Canvas c, String[] cells, float[] xs, int y, boolean shade) {
        if (shade) c.drawRect(M, y, W - M, y + 20, fill(RICE));
        for (int i = 0; i < cells.length; i++) c.drawText(cells[i], xs[i] + 8, y + 14, text(9.5f, ADOBO, i == 0 && cells[0].equals("Total")));
        c.drawLine(M, y + 20, W - M, y + 20, stroke(LINE, 0.6f));
        return y + 20;
    }

    private static void footer(Canvas c, int page) {
        c.drawLine(M, H - 40, W - M, H - 40, stroke(LINE, 1));
        c.drawText("Sagana · Extra food, shared with neighbors nearby.", M, H - 24, text(8.5f, MUTED, false));
        Paint p = text(8.5f, MUTED, false);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Page " + page + " of 2", W - M, H - 24, p);
    }

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        return p;
    }

    private static Paint text(float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }
}
