package com.example.sagana;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Backs up every table to a plain text file and restores from it.
 *
 * File format:
 *   # SAGANA BACKUP
 *   [users] 2 rows
 *   email|name|mobile|...        <- header line with column names
 *   demo@sagana.ph|Demo User|... <- one row per line, fields split by |
 *
 * Inside a field:  \ becomes \\ ,  | becomes \p ,  a line break becomes \n
 */
public final class BackupManager {

    private static final String[] TABLES = {"users", "listings", "bookings", "archive"};

    private BackupManager() {
    }

    /** Builds the backup text. */
    public static String buildText(DatabaseHelper helper, int[] rowCount) {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sb = new StringBuilder();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        sb.append("# SAGANA BACKUP\n# created: ").append(now).append("\n")
          .append("# format: one [table] section, a header line, then one row per line, fields split by |\n\n");
        int total = 0;
        for (String table : TABLES) {
            try (Cursor c = db.rawQuery("SELECT * FROM " + table, null)) {
                sb.append('[').append(table).append("] ").append(c.getCount()).append(" rows\n");
                sb.append(TextUtils.join("|", c.getColumnNames())).append('\n');
                while (c.moveToNext()) {
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        if (i > 0) sb.append('|');
                        sb.append(c.isNull(i) ? "" : encode(c.getString(i)));
                    }
                    sb.append('\n');
                    total++;
                }
                sb.append('\n');
            }
        }
        if (rowCount != null) rowCount[0] = total;
        return sb.toString();
    }

    /** Writes Download/sagana_backup_yyyyMMdd_HHmm.txt and returns where it went. */
    public static String backup(Context ctx, DatabaseHelper helper, int[] rowCount) throws Exception {
        byte[] bytes = buildText(helper, rowCount).getBytes(StandardCharsets.UTF_8);
        String name = "sagana_backup_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".txt";
        return saveToDownloads(ctx, name, "text/plain", bytes);
    }

    /** Saves bytes in the public Download folder (Android 10+) or the app's files folder (older phones). */
    static String saveToDownloads(Context ctx, String name, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Couldn't create the file in Downloads");
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Couldn't open the file");
                out.write(bytes);
            }
            lastSavedUri = uri;
            return "Download/" + name;
        } else {
            File f = new File(ctx.getExternalFilesDir(null), name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            lastSavedUri = null;
            return f.getAbsolutePath();
        }
    }

    /** Uri of the last file written to Downloads (Android 10+), so it can be opened right away. */
    static Uri lastSavedUri;

    /**
     * Replaces every table with what's in the file. All or nothing:
     * a bad file leaves the old data untouched.
     */
    public static int restore(Context ctx, DatabaseHelper helper, Uri uri) throws Exception {
        SQLiteDatabase db = helper.getWritableDatabase();
        int total = 0;
        db.beginTransaction();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String first = r.readLine();
            if (first == null || !first.startsWith("# SAGANA BACKUP"))
                throw new IllegalArgumentException("This isn't a Sagana backup file.");

            // children first so foreign keys don't complain
            db.delete("bookings", null, null);
            db.delete("archive", null, null);
            db.delete("listings", null, null);
            db.delete("users", null, null);

            String table = null, line;
            String[] header = null;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    String t = line.substring(1, line.indexOf(']'));
                    table = java.util.Arrays.asList(TABLES).contains(t) ? t : null;
                    header = null;
                    continue;
                }
                if (table == null) continue;
                if (header == null) { header = line.split("\\|", -1); continue; }
                String[] vals = line.split("\\|", -1);
                ContentValues v = new ContentValues();
                for (int i = 0; i < header.length && i < vals.length; i++) {
                    if (vals[i].isEmpty()) v.putNull(header[i]);
                    else v.put(header[i], decode(vals[i]));
                }
                db.insertOrThrow(table, null, v);
                total++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return total;
    }

    static String encode(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    static String decode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'p' ? '|' : n == 'n' ? '\n' : n);
            } else sb.append(ch);
        }
        return sb.toString();
    }
}
