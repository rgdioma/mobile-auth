package com.example.sagana;

import android.app.Application;

import org.osmdroid.config.Configuration;

import java.io.File;

/**
 * App start-up. Configures the interactive map library (osmdroid) before any map is shown:
 * it must identify itself with a user agent, and it caches Geoapify map tiles in the app's
 * own cache folder so no storage permission is needed.
 */
public class SaganaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        File base = new File(getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));
    }
}
