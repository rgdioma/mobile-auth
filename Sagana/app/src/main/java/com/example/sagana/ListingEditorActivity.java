package com.example.sagana;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/** Edit one of your listings: the Post form in edit mode (UPDATE / DELETE). */
public class ListingEditorActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "com.example.sagana.EDIT_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_fragment_host);
        Ui.padForSystemBars(findViewById(R.id.hostContainer));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.hostContainer, PostFragment.edit(getIntent().getLongExtra(EXTRA_LISTING_ID, -1)))
                    .commit();
        }
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }
}
