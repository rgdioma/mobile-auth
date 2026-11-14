package com.example.sagana;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sagana's local database. SQLite is built into every Android phone, so there is
 * nothing to install and no server: the file lives at
 * /data/data/com.example.sagana/databases/sagana.db
 *
 * Tables
 *   users     who can log in (members and barangay desk staff)
 *   listings  surplus food posted by members (full CRUD from Post / My listings)
 *   bookings  pickups reserved by members, verified by desk staff with the QR code
 *   archive   one row per finished pickup or expired listing; feeds the Reports charts
 *
 * Every screen makes its own helper: {@code DatabaseHelper db = DatabaseHelper.get(context);}
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "sagana.db";
    // 3: first_name/last_name split, profile photo
    // 4: bookings can be rescheduled (rescheduled, prev_date, prev_slot, updated_at)
    private static final int DB_VERSION = 4;
    private static final long MIN = 60_000L;

    private static DatabaseHelper instance;

    /** One shared helper for the whole app, so screens never fight over the file. */
    public static synchronized DatabaseHelper get(Context context) {
        if (instance == null) instance = new DatabaseHelper(context.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ------------------------------------------------------------------ schema
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "email TEXT PRIMARY KEY, " +
                "first_name TEXT NOT NULL, " +
                "last_name TEXT NOT NULL, " +
                "mobile TEXT NOT NULL, " +
                "photo TEXT, " +                      // profile picture saved in the app's files
                "role TEXT NOT NULL, " +              // 'member' or 'staff'
                "barangay TEXT NOT NULL, " +
                "staff_id TEXT, " +
                "lat REAL, lng REAL, " +              // from Geoapify Geocoding at sign-up
                "pass_hash TEXT NOT NULL, " +
                "created_at INTEGER)");

        db.execSQL("CREATE TABLE listings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner TEXT, " +                      // email of the member who posted it
                "donor TEXT NOT NULL, " +             // name shown on the card
                "donor_type TEXT, " +
                "rating REAL DEFAULT 5, " +
                "name TEXT NOT NULL, " +
                "qty TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +          // Cooked, Bread, Produce, Packaged
                "desk TEXT NOT NULL, " +              // Brgy. X social welfare desk
                "lat REAL, lng REAL, " +              // the desk's coordinates
                "pickup_start INTEGER NOT NULL, " +   // epoch millis
                "pickup_end INTEGER NOT NULL, " +
                "notes TEXT, " +
                "photo TEXT, " +                      // res:drawable_name or a file path
                "seed INTEGER DEFAULT 0, " +          // sample data that refreshes itself
                "archived INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE bookings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "listing_id INTEGER REFERENCES listings(id) ON DELETE SET NULL, " +
                "owner TEXT NOT NULL, " +             // receiver's email
                "receiver TEXT NOT NULL, " +
                "food TEXT NOT NULL, qty TEXT NOT NULL, donor TEXT, category TEXT, " +
                "desk TEXT NOT NULL, " +
                "pickup_date TEXT NOT NULL, " +       // yyyy-MM-dd
                "slot TEXT NOT NULL, " +
                "code TEXT NOT NULL UNIQUE, " +       // SG-7K2P, shown as a QR code
                "status TEXT NOT NULL DEFAULT 'Scheduled', " + // Scheduled, Verified, Received
                "verified_at INTEGER, " +
                "verified_by TEXT, " +
                "rating INTEGER DEFAULT 0, " +
                "created_at INTEGER, " +
                "rescheduled INTEGER DEFAULT 0, " +   // how many times the receiver moved it
                "prev_date TEXT, prev_slot TEXT, " +  // the schedule it was moved from (shown to desk staff)
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE archive (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "day TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "kg REAL NOT NULL, " +
                "outcome TEXT NOT NULL, " +           // picked_up or expired
                "desk TEXT NOT NULL)");

        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 3) {
            // very old layouts (before the name split) can't be converted: start fresh
            db.execSQL("DROP TABLE IF EXISTS archive");
            db.execSQL("DROP TABLE IF EXISTS bookings");
            db.execSQL("DROP TABLE IF EXISTS listings");
            db.execSQL("DROP TABLE IF EXISTS users");
            onCreate(db);
            return;
        }
        // from here on, upgrade in place so members keep their accounts, listings and pickups
        if (oldV < 4) {
            db.execSQL("ALTER TABLE bookings ADD COLUMN rescheduled INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE bookings ADD COLUMN prev_date TEXT");
            db.execSQL("ALTER TABLE bookings ADD COLUMN prev_slot TEXT");
            db.execSQL("ALTER TABLE bookings ADD COLUMN updated_at INTEGER");
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ------------------------------------------------------------------ users
    /** SHA-256 of email + password, so the same password gives different hashes per account. */
    public static String hash(String email, String password) {
        try {
            String salted = email.trim().toLowerCase(Locale.US) + ":" + password;
            byte[] d = MessageDigest.getInstance("SHA-256").digest(salted.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean emailTaken(String email) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM users WHERE email = ?",
                new String[]{email.trim().toLowerCase(Locale.US)})) {
            return c.moveToFirst();
        }
    }

    /** CREATE. Returns false if the email is already taken. */
    public boolean registerUser(User u, String passHash) {
        ContentValues v = u.toValues();
        v.put("pass_hash", passHash);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("users", null, v) != -1;
    }

    /** READ. Returns the user if the login is right, or null. */
    public User login(String email, String password) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM users WHERE email = ? AND pass_hash = ?",
                new String[]{email.trim().toLowerCase(Locale.US), hash(email, password)})) {
            return c.moveToFirst() ? User.from(c) : null;
        }
    }

    public User getUser(String email) {
        if (email == null) return null;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM users WHERE email = ?",
                new String[]{email})) {
            return c.moveToFirst() ? User.from(c) : null;
        }
    }

    /**
     * UPDATE name, mobile, barangay (and its coordinates), staff ID and photo.
     * The email is changed separately with changeEmail().
     */
    public boolean updateProfile(User u) {
        SQLiteDatabase db = getWritableDatabase();
        String oldEmail = u.email;
        db.beginTransaction();
        try {
            ContentValues v = u.toValues();
            v.remove("email");
            db.update("users", v, "email = ?", new String[]{oldEmail});
            // keep the name shown on their listings and pickups in step with the profile
            ContentValues d = new ContentValues();
            d.put("donor", u.fullName());
            db.update("listings", d, "owner = ?", new String[]{oldEmail});
            ContentValues r = new ContentValues();
            r.put("receiver", u.fullName());
            db.update("bookings", r, "owner = ?", new String[]{oldEmail});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** UPDATE the login email. Needs the password because the stored hash is salted with the email. */
    public boolean changeEmail(String oldEmail, String newEmail, String password) {
        newEmail = newEmail.trim().toLowerCase(Locale.US);
        if (login(oldEmail, password) == null || emailTaken(newEmail)) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("email", newEmail);
            v.put("pass_hash", hash(newEmail, password));
            db.update("users", v, "email = ?", new String[]{oldEmail});
            ContentValues o = new ContentValues();
            o.put("owner", newEmail);
            db.update("listings", o, "owner = ?", new String[]{oldEmail});
            db.update("bookings", o, "owner = ?", new String[]{oldEmail});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** UPDATE the password. Returns false if the current password is wrong. */
    public boolean changePassword(String email, String current, String next) {
        if (login(email, current) == null) return false;
        ContentValues v = new ContentValues();
        v.put("pass_hash", hash(email, next));
        return getWritableDatabase().update("users", v, "email = ?", new String[]{email}) == 1;
    }

    /**
     * DELETE the account. Their listings and waiting pickups go too; finished handovers
     * stay in the desk history (they keep the receiver's name, not the account).
     */
    public boolean deleteAccount(String email, String password) {
        if (login(email, password) == null) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("bookings", "owner = ? AND status = 'Scheduled'", new String[]{email});
            db.execSQL("DELETE FROM bookings WHERE status = 'Scheduled' AND listing_id IN " +
                    "(SELECT id FROM listings WHERE owner = ?)", new Object[]{email});
            db.delete("listings", "owner = ?", new String[]{email});
            db.delete("users", "email = ?", new String[]{email});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    // ------------------------------------------------------------------ listings (full CRUD)
    /** CREATE */
    public long insertListing(Listing l) {
        return getWritableDatabase().insert("listings", null, l.toValues());
    }

    public Listing getListing(long id) {
        List<Listing> r = queryListings("SELECT * FROM listings WHERE id = ?", new String[]{String.valueOf(id)});
        return r.isEmpty() ? null : r.get(0);
    }

    /** READ: listings whose pickup window is still open. Distance sorting happens in Java. */
    public List<Listing> getOpenListings() {
        refreshSeedListings();
        return queryListings("SELECT * FROM listings WHERE pickup_end > ? ORDER BY pickup_end",
                new String[]{String.valueOf(System.currentTimeMillis())});
    }

    /** READ: one owner's listings, for the My listings screen. */
    public List<Listing> getMyListings(String ownerEmail) {
        return queryListings("SELECT * FROM listings WHERE owner = ? ORDER BY pickup_end DESC",
                new String[]{ownerEmail});
    }

    /** UPDATE */
    public int updateListing(Listing l) {
        return getWritableDatabase().update("listings", l.toValues(), "id = ?",
                new String[]{String.valueOf(l.id)});
    }

    /** DELETE. Pickups still waiting for it are cancelled; finished ones stay in history. */
    public int deleteListing(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("bookings", "listing_id = ? AND status = 'Scheduled'", new String[]{String.valueOf(id)});
            int n = db.delete("listings", "id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return n;
        } finally {
            db.endTransaction();
        }
    }

    public int countScheduledFor(long listingId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM bookings WHERE listing_id = ? AND status = 'Scheduled'",
                new String[]{String.valueOf(listingId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private List<Listing> queryListings(String sql, String[] args) {
        List<Listing> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(Listing.from(c));
        }
        return out;
    }

    /** Sample listings would all expire a few hours after install; this keeps the demo alive. */
    private void refreshSeedListings() {
        long now = System.currentTimeMillis();
        List<Listing> stale = queryListings("SELECT * FROM listings WHERE seed = 1 AND pickup_end <= ?",
                new String[]{String.valueOf(now)});
        for (Listing l : stale) {
            long left = (25 + (l.id * 53) % 330) * MIN;
            l.pickupEnd = now + left;
            l.pickupStart = now - 90 * MIN;
            updateListing(l);
        }
    }

    // ------------------------------------------------------------------ bookings
    /** True if this listing was posted by that member (they can't reserve their own food). */
    public boolean isOwnListing(long listingId, String email) {
        if (email == null) return false;
        Listing l = getListing(listingId);
        return l != null && l.owner != null && l.owner.equalsIgnoreCase(email);
    }

    /** CREATE a pickup. Returns -1 and saves nothing if the member is booking their own listing. */
    public long insertBooking(Booking b) {
        if (isOwnListing(b.listingId, b.owner)) return -1;
        ContentValues v = new ContentValues();
        v.put("listing_id", b.listingId);
        v.put("owner", b.owner);
        v.put("receiver", b.receiver);
        v.put("food", b.food);
        v.put("qty", b.qty);
        v.put("donor", b.donor);
        v.put("category", b.category);
        v.put("desk", b.desk);
        v.put("pickup_date", b.date);
        v.put("slot", b.slot);
        v.put("code", b.code);
        v.put("status", "Scheduled");
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("bookings", null, v);
    }

    public boolean codeExists(String code) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM bookings WHERE code = ?", new String[]{code})) {
            return c.moveToFirst();
        }
    }

    public List<Booking> getMyBookings(String owner) {
        List<Booking> list = queryBookings("SELECT * FROM bookings WHERE owner = ?", new String[]{owner});
        // waiting first, then verified, then received; each group earliest first
        list.sort((a, b) -> {
            int s = Integer.compare(statusRank(a.status), statusRank(b.status));
            return s != 0 ? s : BY_SCHEDULE.compare(a, b);
        });
        return list;
    }

    private static int statusRank(String status) {
        return "Scheduled".equals(status) ? 0 : "Verified".equals(status) ? 1 : 2;
    }

    public Booking findByCode(String code) {
        List<Booking> r = queryBookings("SELECT * FROM bookings WHERE code = ?", new String[]{code});
        return r.isEmpty() ? null : r.get(0);
    }

    /** Pickups waiting at one desk, for the staff Desk screen. */
    public List<Booking> getWaitingAtDesk(String desk) {
        List<Booking> list = queryBookings("SELECT * FROM bookings WHERE desk = ? AND status = 'Scheduled'",
                new String[]{desk});
        list.sort(BY_SCHEDULE);
        return list;
    }

    /** Earliest day first, then earliest slot ("9:00 AM" before "11:00 AM" before "1:00 PM"). */
    private static final java.util.Comparator<Booking> BY_SCHEDULE = (a, b) -> {
        int d = a.date.compareTo(b.date);
        return d != 0 ? d : Integer.compare(slotMinutes(a.slot), slotMinutes(b.slot));
    };

    /** "1:00 PM" -> 780 (minutes after midnight). Unknown text sorts last. */
    static int slotMinutes(String slot) {
        try {
            String[] p = slot.trim().split("[: ]");
            int h = Integer.parseInt(p[0]) % 12, m = Integer.parseInt(p[1]);
            return (h + ("PM".equalsIgnoreCase(p[2]) ? 12 : 0)) * 60 + m;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Handovers already done at one desk, newest first, for the staff History screen. */
    public List<Booking> getHistoryAtDesk(String desk) {
        return queryBookings("SELECT * FROM bookings WHERE desk = ? AND status != 'Scheduled' ORDER BY verified_at DESC",
                new String[]{desk});
    }

    /** Desk staff hand over the food. Returns 1 if the code matched a waiting pickup. */
    public int verifyCode(String code, String staffName) {
        Booking b = findByCode(code);
        if (b == null || !"Scheduled".equals(b.status)) return 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("status", "Verified");
            v.put("verified_at", System.currentTimeMillis());
            v.put("verified_by", staffName);
            int n = db.update("bookings", v, "code = ? AND status = 'Scheduled'", new String[]{code});
            // record it for the reports
            ContentValues a = new ContentValues();
            a.put("day", today());
            a.put("category", b.category == null ? "Cooked" : b.category);
            a.put("kg", estimateKg(b.qty));
            a.put("outcome", "picked_up");
            a.put("desk", b.desk);
            db.insert("archive", null, a);
            db.setTransactionSuccessful();
            return n;
        } finally {
            db.endTransaction();
        }
    }

    public int markReceived(long id) {
        ContentValues v = new ContentValues();
        v.put("status", "Received");
        return getWritableDatabase().update("bookings", v, "id = ? AND status = 'Verified'", new String[]{String.valueOf(id)});
    }

    public int rateBooking(long id, int stars) {
        ContentValues v = new ContentValues();
        v.put("rating", stars);
        return getWritableDatabase().update("bookings", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public Booking getBooking(long id) {
        List<Booking> r = queryBookings("SELECT * FROM bookings WHERE id = ?", new String[]{String.valueOf(id)});
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * UPDATE: the receiver can't make it, so they move a waiting pickup to another day or slot.
     * The QR code stays the same. The old schedule is kept, so desk staff see it was moved.
     * Returns 1 if it was updated; 0 if it's not theirs, no longer waiting, or nothing changed.
     */
    public int rescheduleBooking(long id, String ownerEmail, String newDate, String newSlot) {
        Booking b = getBooking(id);
        if (b == null || !"Scheduled".equals(b.status) || !b.owner.equalsIgnoreCase(ownerEmail)) return 0;
        if (b.date.equals(newDate) && b.slot.equals(newSlot)) return 0;
        ContentValues v = new ContentValues();
        v.put("pickup_date", newDate);
        v.put("slot", newSlot);
        v.put("prev_date", b.date);
        v.put("prev_slot", b.slot);
        v.put("rescheduled", b.rescheduled + 1);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("bookings", v, "id = ? AND status = 'Scheduled'",
                new String[]{String.valueOf(id)});
    }

    public int cancelBooking(long id) {
        return getWritableDatabase().delete("bookings", "id = ? AND status = 'Scheduled'",
                new String[]{String.valueOf(id)});
    }

    public int countBookings(String owner, String status) {
        String sql = status == null ? "SELECT COUNT(*) FROM bookings WHERE owner = ?"
                : "SELECT COUNT(*) FROM bookings WHERE owner = ? AND status = ?";
        String[] args = status == null ? new String[]{owner} : new String[]{owner, status};
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private List<Booking> queryBookings(String sql, String[] args) {
        List<Booking> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(Booking.from(c));
        }
        return out;
    }

    // ------------------------------------------------------------------ reports
    /** Moves member listings whose window closed with nobody picking up into the archive as expired. */
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        List<Listing> closed = queryListings("SELECT * FROM listings WHERE seed = 0 AND archived = 0 AND pickup_end <= ?",
                new String[]{String.valueOf(now)});
        SQLiteDatabase db = getWritableDatabase();
        for (Listing l : closed) {
            boolean handedOver;
            try (Cursor c = db.rawQuery("SELECT 1 FROM bookings WHERE listing_id = ? AND status != 'Scheduled'",
                    new String[]{String.valueOf(l.id)})) {
                handedOver = c.moveToFirst();
            }
            if (!handedOver) {
                ContentValues a = new ContentValues();
                a.put("day", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(l.pickupEnd)));
                a.put("category", l.category);
                a.put("kg", estimateKg(l.qty));
                a.put("outcome", "expired");
                a.put("desk", l.desk);
                db.insert("archive", null, a);
            }
            ContentValues v = new ContentValues();
            v.put("archived", 1);
            db.update("listings", v, "id = ?", new String[]{String.valueOf(l.id)});
        }
    }

    /** Handovers per day for the bar chart. Each row: day, picked_up, expired, kg. */
    public Cursor handoversPerDay(String fromDay) {
        return getReadableDatabase().rawQuery(
                "SELECT day, " +
                "SUM(CASE WHEN outcome = 'picked_up' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN outcome = 'expired' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN outcome = 'picked_up' THEN kg ELSE 0 END) " +
                "FROM archive WHERE day >= ? GROUP BY day ORDER BY day", new String[]{fromDay});
    }

    /** Open listings by category for the pie chart. */
    public Cursor listingsByCategory() {
        return getReadableDatabase().rawQuery(
                "SELECT category, COUNT(*) FROM listings WHERE pickup_end > ? GROUP BY category",
                new String[]{String.valueOf(System.currentTimeMillis())});
    }

    /** Pickups per desk since a day, busiest first, for the horizontal bar chart. */
    public Cursor handoversByDesk(String fromDay) {
        return getReadableDatabase().rawQuery(
                "SELECT desk, COUNT(*) FROM archive WHERE outcome = 'picked_up' AND day >= ? " +
                "GROUP BY desk ORDER BY COUNT(*) DESC", new String[]{fromDay});
    }

    public int countOpenListings() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM listings WHERE pickup_end > ?",
                new String[]{String.valueOf(System.currentTimeMillis())})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int tableCount(String table) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ------------------------------------------------------------------ helpers
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String daysAgo(int d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(System.currentTimeMillis() - d * 86_400_000L));
    }

    /** Rough weight from a quantity like "4 packs" or "2 kg", for the "food rescued" total. */
    static double estimateKg(String qty) {
        if (qty == null) return 1.0;
        String[] p = qty.trim().split("\\s+", 2);
        double n;
        try { n = Double.parseDouble(p[0]); } catch (Exception e) { return 1.0; }
        String unit = p.length > 1 ? p[1].toLowerCase(Locale.US) : "";
        if (unit.startsWith("kg")) return n;
        if (unit.startsWith("dozen")) return n * 0.6;
        if (unit.startsWith("meal") || unit.startsWith("serving")) return n * 0.35;
        if (unit.startsWith("bunch")) return n * 1.5;
        return Math.round(n * 0.25 * 10) / 10.0;
    }

    /** Straight-line distance in km between two points (Haversine). No API call needed. */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    // ------------------------------------------------------------------ sample data
    private void seed(SQLiteDatabase db) {
        User demo = new User();
        demo.email = "demo@sagana.ph"; demo.setNames("Demo", "User"); demo.mobile = "09171234567";
        demo.role = "member"; demo.barangay = "Villamonte";
        double[] v = Geo.fallback("Villamonte");
        demo.lat = v[0] + 0.0031; demo.lng = v[1] + 0.0024;
        ContentValues dv = demo.toValues();
        dv.put("pass_hash", hash(demo.email, "Food@1234"));
        db.insert("users", null, dv);

        User staff = new User();
        staff.email = "staff@sagana.ph"; staff.setNames("Rosa", "Villanueva"); staff.mobile = "09281234567";
        staff.role = "staff"; staff.barangay = "Tangub"; staff.staffId = "BRGY-0421";
        double[] t = Geo.fallback("Tangub");
        staff.lat = t[0]; staff.lng = t[1];
        ContentValues sv = staff.toValues();
        sv.put("pass_hash", hash(staff.email, "Desk@1234"));
        db.insert("users", null, sv);

        long now = System.currentTimeMillis();
        Object[][] foods = {
                // name, qty, category, donor, type, rating, barangay, total min, left min, photo, notes, owner
                {"Pancit bihon", "8 packs", "Cooked", "Aling Nena's Carinderia", "Small eatery", 4.5, "Tangub", 180, 80, "food_pancit_bihon", "Cooked this morning. Keep covered, eat within the day.", null},
                {"Pandesal", "3 dozen", "Bread", "Golden Crust Bakery", "Bakery", 4.8, "Mandalagan", 300, 190, "food_pandesal", "Baked at 5 AM. Unsold from the morning batch.", null},
                {"Saba bananas", "2 bunches", "Produce", "Dela Cruz household", "Household", 4.0, "Villamonte", 720, 600, "food_saba_bananas", "Ripe, good for turon or boiling.", null},
                {"Chicken adobo with rice", "5 meals", "Cooked", "USLS canteen", "School canteen", 4.6, "Taculing", 120, 25, "food_chicken_adobo", "Lunch surplus. Packed and sealed at 12 noon.", null},
                {"Canned sardines", "12 cans", "Packaged", "Tita Lorna's sari-sari store", "Sari-sari store", 4.9, "Tangub", 2880, 1440, "food_canned_sardines", "Near best-before date but sealed and undented.", null},
                {"Rice", "4 kg", "Packaged", "Brgy. Bata pantry", "Community pantry", 4.7, "Bata", 1500, 1200, "food_rice", "Sealed sack, surplus from the relief drive.", null},
                {"Garlic rice", "6 packs", "Cooked", "Demo User", "Your listing", 4.8, "Villamonte", 240, 200, null, "Cooked too much for a family party. Packed and covered.", "demo@sagana.ph"},
        };
        long[] ids = new long[foods.length];
        for (int i = 0; i < foods.length; i++) {
            Object[] f = foods[i];
            Listing l = new Listing();
            l.name = (String) f[0]; l.qty = (String) f[1]; l.category = (String) f[2];
            l.donor = (String) f[3]; l.donorType = (String) f[4]; l.rating = (Double) f[5];
            l.desk = Geo.deskName((String) f[6]);
            double[] ll = Geo.fallback((String) f[6]);
            l.lat = ll[0]; l.lng = ll[1];
            l.pickupEnd = now + (Integer) f[8] * MIN;
            l.pickupStart = l.pickupEnd - (Integer) f[7] * MIN;
            l.photo = f[9] == null ? null : "res:" + f[9];
            l.notes = (String) f[10];
            l.owner = (String) f[11];
            l.seed = l.owner == null;
            ids[i] = db.insert("listings", null, l.toValues());
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        ContentValues b1 = new ContentValues();
        b1.put("listing_id", ids[0]); b1.put("owner", "maria@sagana.ph"); b1.put("receiver", "Maria Santos");
        b1.put("food", "Pancit bihon"); b1.put("qty", "2 packs"); b1.put("donor", "Aling Nena's Carinderia");
        b1.put("category", "Cooked"); b1.put("desk", Geo.deskName("Tangub")); b1.put("pickup_date", today);
        b1.put("slot", "5:00 PM"); b1.put("code", "SG-7K2P"); b1.put("status", "Scheduled"); b1.put("created_at", now);
        db.insert("bookings", null, b1);

        ContentValues b2 = new ContentValues();
        b2.put("listing_id", ids[4]); b2.put("owner", "jun@sagana.ph"); b2.put("receiver", "Jun Dela Peña");
        b2.put("food", "Canned sardines"); b2.put("qty", "4 cans"); b2.put("donor", "Tita Lorna's sari-sari store");
        b2.put("category", "Packaged"); b2.put("desk", Geo.deskName("Tangub")); b2.put("pickup_date", today);
        b2.put("slot", "9:00 AM"); b2.put("code", "SG-Q9M3"); b2.put("status", "Received");
        b2.put("verified_at", now - 5 * 3_600_000L); b2.put("verified_by", "Rosa Villanueva");
        b2.put("rating", 5); b2.put("created_at", now - 6 * 3_600_000L);
        db.insert("bookings", null, b2);

        // a week of past records so the reports have history on day one (labelled as sample data in the app)
        Random rnd = new Random(20261113);
        String[] cats = {"Cooked", "Bread", "Produce", "Packaged"};
        for (int d = 7; d >= 1; d--) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now - d * 86_400_000L));
            int n = 4 + rnd.nextInt(6);
            for (int i = 0; i < n; i++) {
                ContentValues a = new ContentValues();
                a.put("day", day);
                a.put("category", cats[rnd.nextInt(4)]);
                a.put("kg", Math.round((0.8 + rnd.nextDouble() * 3.2) * 10) / 10.0);
                a.put("outcome", rnd.nextDouble() < 0.16 ? "expired" : "picked_up");
                a.put("desk", Geo.deskName(Geo.BARANGAYS[rnd.nextInt(Geo.BARANGAYS.length - 1)]));
                db.insert("archive", null, a);
            }
        }
    }

    // ------------------------------------------------------------------ models
    public static class User {
        public String email, first, last, mobile, role, barangay, staffId, photo;
        /** "First Last", kept in step with first/last for the screens that show the whole name. */
        public String name;
        public double lat, lng;

        public boolean isStaff() { return "staff".equals(role); }
        public String desk() { return Geo.deskName(barangay); }
        public String firstName() { return first == null ? "" : first; }
        public String fullName() { return (first + " " + last).trim(); }

        public void setNames(String firstName, String lastName) {
            first = firstName.trim();
            last = lastName.trim();
            name = fullName();
        }

        ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("email", email.trim().toLowerCase(Locale.US));
            v.put("first_name", first);
            v.put("last_name", last);
            v.put("photo", photo);
            v.put("mobile", mobile);
            v.put("role", role);
            v.put("barangay", barangay);
            v.put("staff_id", staffId);
            v.put("lat", lat);
            v.put("lng", lng);
            return v;
        }

        static User from(Cursor c) {
            User u = new User();
            u.email = c.getString(c.getColumnIndexOrThrow("email"));
            u.setNames(c.getString(c.getColumnIndexOrThrow("first_name")), c.getString(c.getColumnIndexOrThrow("last_name")));
            u.photo = c.getString(c.getColumnIndexOrThrow("photo"));
            u.mobile = c.getString(c.getColumnIndexOrThrow("mobile"));
            u.role = c.getString(c.getColumnIndexOrThrow("role"));
            u.barangay = c.getString(c.getColumnIndexOrThrow("barangay"));
            u.staffId = c.getString(c.getColumnIndexOrThrow("staff_id"));
            u.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
            u.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
            return u;
        }
    }

    public static class Listing {
        public long id;
        public String owner, donor, donorType, name, qty, category, desk, notes, photo;
        public double lat, lng, rating = 5;
        public long pickupStart, pickupEnd;
        public boolean seed;
        public double distanceKm; // filled in on the phone, not stored

        public boolean isOpen() { return pickupEnd > System.currentTimeMillis(); }

        ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("owner", owner);
            v.put("donor", donor);
            v.put("donor_type", donorType);
            v.put("rating", rating);
            v.put("name", name);
            v.put("qty", qty);
            v.put("category", category);
            v.put("desk", desk);
            v.put("lat", lat);
            v.put("lng", lng);
            v.put("pickup_start", pickupStart);
            v.put("pickup_end", pickupEnd);
            v.put("notes", notes);
            v.put("photo", photo);
            v.put("seed", seed ? 1 : 0);
            return v;
        }

        static Listing from(Cursor c) {
            Listing l = new Listing();
            l.id = c.getLong(c.getColumnIndexOrThrow("id"));
            l.owner = c.getString(c.getColumnIndexOrThrow("owner"));
            l.donor = c.getString(c.getColumnIndexOrThrow("donor"));
            l.donorType = c.getString(c.getColumnIndexOrThrow("donor_type"));
            l.rating = c.getDouble(c.getColumnIndexOrThrow("rating"));
            l.name = c.getString(c.getColumnIndexOrThrow("name"));
            l.qty = c.getString(c.getColumnIndexOrThrow("qty"));
            l.category = c.getString(c.getColumnIndexOrThrow("category"));
            l.desk = c.getString(c.getColumnIndexOrThrow("desk"));
            l.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
            l.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
            l.pickupStart = c.getLong(c.getColumnIndexOrThrow("pickup_start"));
            l.pickupEnd = c.getLong(c.getColumnIndexOrThrow("pickup_end"));
            l.notes = c.getString(c.getColumnIndexOrThrow("notes"));
            l.photo = c.getString(c.getColumnIndexOrThrow("photo"));
            l.seed = c.getInt(c.getColumnIndexOrThrow("seed")) == 1;
            return l;
        }
    }

    public static class Booking {
        public long id, listingId, verifiedAt;
        public String owner, receiver, food, qty, donor, category, desk, date, slot, code, status, verifiedBy;
        public String prevDate, prevSlot;
        public long updatedAt;
        public int rating, rescheduled;

        static Booking from(Cursor c) {
            Booking b = new Booking();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.listingId = c.getLong(c.getColumnIndexOrThrow("listing_id"));
            b.owner = c.getString(c.getColumnIndexOrThrow("owner"));
            b.receiver = c.getString(c.getColumnIndexOrThrow("receiver"));
            b.food = c.getString(c.getColumnIndexOrThrow("food"));
            b.qty = c.getString(c.getColumnIndexOrThrow("qty"));
            b.donor = c.getString(c.getColumnIndexOrThrow("donor"));
            b.category = c.getString(c.getColumnIndexOrThrow("category"));
            b.desk = c.getString(c.getColumnIndexOrThrow("desk"));
            b.date = c.getString(c.getColumnIndexOrThrow("pickup_date"));
            b.slot = c.getString(c.getColumnIndexOrThrow("slot"));
            b.code = c.getString(c.getColumnIndexOrThrow("code"));
            b.status = c.getString(c.getColumnIndexOrThrow("status"));
            b.verifiedAt = c.getLong(c.getColumnIndexOrThrow("verified_at"));
            b.verifiedBy = c.getString(c.getColumnIndexOrThrow("verified_by"));
            b.rating = c.getInt(c.getColumnIndexOrThrow("rating"));
            b.rescheduled = c.getInt(c.getColumnIndexOrThrow("rescheduled"));
            b.prevDate = c.getString(c.getColumnIndexOrThrow("prev_date"));
            b.prevSlot = c.getString(c.getColumnIndexOrThrow("prev_slot"));
            b.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
            return b;
        }
    }
}
