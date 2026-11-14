package com.example.sagana;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/** Members open the Community impact report from Profile; staff have it as a tab. */
public class ReportsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_fragment_host);
        Ui.padForSystemBars(findViewById(R.id.hostContainer));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.hostContainer, ReportsFragment.withBack())
                    .commit();
        }
    }

    @Override
    public void finish() {
        super.finish();
        Nav.pop(this);
    }
}
