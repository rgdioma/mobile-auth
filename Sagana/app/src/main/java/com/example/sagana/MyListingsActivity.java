package com.example.sagana;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** CRUD: read your own listings, open one to update it, or delete it. */
public class MyListingsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private DatabaseHelper.User me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_listings);
        Ui.padForSystemBars(findViewById(R.id.myListingsRoot));
        db = DatabaseHelper.get(this);
        me = Session.user(this);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonPostNew).setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.putExtra(HomeActivity.EXTRA_TAB, R.id.nav_post);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (me == null) { finish(); return; }
        List<DatabaseHelper.Listing> rows = db.getMyListings(me.email);
        LinearLayout box = findViewById(R.id.myContainer);
        box.removeAllViews();
        findViewById(R.id.textEmpty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inf = LayoutInflater.from(this);
        for (DatabaseHelper.Listing l : rows) {
            View card = inf.inflate(R.layout.item_my_listing, box, false);
            boolean open = l.isOpen();
            int booked = db.countScheduledFor(l.id);
            ((TextView) card.findViewById(R.id.textName)).setText(l.name);
            ((TextView) card.findViewById(R.id.textInfo)).setText(l.qty + " · " + l.category + " · " + l.desk);
            boolean pending = l.isPendingDropOff();
            String window = !open ? getString(R.string.window_ended, new SimpleDateFormat("MMM d", Locale.US).format(new Date(l.pickupEnd)))
                    : pending ? getString(R.string.window_pending_dropoff, Ui.clock(l.pickupEnd))
                    : getString(R.string.window_ends, Ui.clock(l.pickupEnd));
            if (booked > 0) window += " · " + getResources().getQuantityString(R.plurals.pickups_booked, booked, booked);
            ((TextView) card.findViewById(R.id.textWindow)).setText(window);

            // Pending Drop-off -> Active (desk has it) -> Completed (handed over) / Closed / Not dropped off
            int label, fg, bg;
            if (pending) {
                label = open ? R.string.status_pending_dropoff : R.string.status_not_dropped_off;
                fg = open ? R.color.adobo : R.color.text_secondary;
                bg = open ? R.color.mango : R.color.input_border;
            } else if (open) {
                label = R.string.status_active; fg = R.color.leaf; bg = R.color.leaf_light;
            } else if (db.countHandedOverFor(l.id) > 0) {
                label = R.string.status_completed; fg = R.color.leaf; bg = R.color.leaf_light;
            } else {
                label = R.string.status_closed; fg = R.color.text_secondary; bg = R.color.input_border;
            }
            TextView status = card.findViewById(R.id.textStatus);
            status.setText(label);
            status.setTextColor(ContextCompat.getColor(this, fg));
            status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, bg)));

            if (Ui.loadPhoto(card.findViewById(R.id.imageThumb), l.photo)) {
                card.findViewById(R.id.thumbCard).setVisibility(View.VISIBLE);
            }

            card.findViewById(R.id.buttonEdit).setOnClickListener(v -> {
                Intent i = new Intent(this, ListingEditorActivity.class);
                i.putExtra(ListingEditorActivity.EXTRA_LISTING_ID, l.id);
                Nav.push(this, i);
            });
            card.findViewById(R.id.buttonDelete).setOnClickListener(v -> confirmDelete(l, booked));
            box.addView(card);
        }
        Ui.stagger(box);
    }

    private void confirmDelete(DatabaseHelper.Listing l, int booked) {
        String msg = getString(R.string.delete_listing_body)
                + (booked > 0 ? " " + getResources().getQuantityString(R.plurals.delete_cancels, booked, booked) : "");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_listing_title, l.name))
                .setMessage(msg)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    db.deleteListing(l.id);
                    Toast.makeText(this, getString(R.string.toast_listing_deleted, l.name), Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton(R.string.action_keep, null)
                .show();
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }
}
