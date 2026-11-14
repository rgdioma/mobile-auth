package com.example.sagana;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Geo Mapping API integration (Geoapify). Three services are used:
 *   1. Geocoding    barangay name to latitude/longitude, at sign-up and when posting food
 *   2. Routing      walking distance, time and the path from the user to the pickup desk
 *   3. Static Maps  map pictures: all pickup points on Home, the walking route on Food details
 *
 * Network work runs on a background thread; the answer comes back on the main thread.
 * The key comes from local.properties (GEOAPIFY_API_KEY) through BuildConfig, never the code.
 */
public final class GeoapifyClient {

    private static final String KEY = BuildConfig.GEOAPIFY_KEY;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onResult(T value);
        void onError(Exception e);
    }

    /** Result of a walking route. */
    public static final class Route {
        public double km, minutes;
        public final List<double[]> path = new ArrayList<>(); // {lat, lng}
    }

    private GeoapifyClient() {
    }

    public static boolean hasKey() {
        return KEY != null && !KEY.isEmpty();
    }

    // ------------------------------------------------------------------ 1. Geocoding
    /** "Villamonte" -> {lat, lng}. Call once and save the result in SQLite. */
    public static void geocodeBarangay(String barangay, Callback<double[]> cb) {
        run(() -> {
            String text = URLEncoder.encode(barangay + ", " + Geo.CITY + ", Philippines", "UTF-8");
            JSONObject json = get("https://api.geoapify.com/v1/geocode/search?text=" + text +
                    String.format(Locale.US, "&filter=countrycode:ph&bias=proximity:%f,%f", Geo.CITY_LNG, Geo.CITY_LAT) +
                    "&format=json&limit=1&apiKey=" + KEY);
            JSONArray results = json.getJSONArray("results");
            if (results.length() == 0) throw new Exception("Barangay not found");
            JSONObject r = results.getJSONObject(0);
            String type = r.optString("result_type");
            if (type.equals("state") || type.equals("country"))
                throw new Exception("Geoapify only matched the province");
            return new double[]{r.getDouble("lat"), r.getDouble("lon")};
        }, cb);
    }

    // ------------------------------------------------------------------ 2. Routing
    public static final String MODE_WALK = "walk";
    public static final String MODE_DRIVE = "drive";

    /** Walking route from the user to a desk: distance, minutes and the path to draw. */
    public static void walkingRoute(double fromLat, double fromLng, double toLat, double toLng, Callback<Route> cb) {
        route(fromLat, fromLng, toLat, toLng, MODE_WALK, cb);
    }

    /** Route in the given mode ("walk" or "drive"): distance, minutes and the path to draw. */
    public static void route(double fromLat, double fromLng, double toLat, double toLng, String mode, Callback<Route> cb) {
        run(() -> {
            String url = String.format(Locale.US,
                    "https://api.geoapify.com/v1/routing?waypoints=%f,%f%%7C%f,%f&mode=%s&apiKey=%s",
                    fromLat, fromLng, toLat, toLng, mode, KEY);
            JSONArray features = get(url).optJSONArray("features");
            if (features == null || features.length() == 0) throw new Exception("No route found");
            JSONObject feature = features.getJSONObject(0);
            JSONObject props = feature.getJSONObject("properties");
            Route r = new Route();
            r.km = props.getDouble("distance") / 1000.0;
            r.minutes = props.getDouble("time") / 60.0;
            // geometry is a MultiLineString: [[[lon, lat], ...], ...]
            JSONObject geom = feature.optJSONObject("geometry");
            if (geom != null) {
                JSONArray lines = geom.getJSONArray("coordinates");
                for (int i = 0; i < lines.length(); i++) {
                    JSONArray line = lines.getJSONArray(i);
                    for (int j = 0; j < line.length(); j++) {
                        JSONArray p = line.getJSONArray(j);
                        r.path.add(new double[]{p.getDouble(1), p.getDouble(0)});
                    }
                }
            }
            return r;
        }, cb);
    }

    // ------------------------------------------------------------------ 3. Static maps
    private static final String MAP_BASE = "https://maps.geoapify.com/v1/staticmap?style=osm-bright&scaleFactor=2";

    /** Walking route picture: green dot for the user, red pin for the desk, red line for the path. */
    public static String routeMapUrl(double youLat, double youLng, double deskLat, double deskLng, List<double[]> path) {
        StringBuilder sb = new StringBuilder(MAP_BASE).append("&width=600&height=300");
        sb.append(String.format(Locale.US,
                "&marker=lonlat:%.6f,%.6f;type:awesome;color:%%23c8231b;size:large;icon:utensils" +
                "%%7Clonlat:%.6f,%.6f;type:circle;color:%%232f7d4a;size:small",
                deskLng, deskLat, youLng, youLat));
        if (path != null && path.size() > 1) {
            sb.append("&geometry=polyline:");
            // keep the URL short: at most ~60 points
            int step = Math.max(1, path.size() / 60);
            boolean first = true;
            for (int i = 0; i < path.size(); i += step) {
                double[] p = path.get(i);
                if (!first) sb.append(',');
                sb.append(String.format(Locale.US, "%.5f,%.5f", p[1], p[0]));
                first = false;
            }
            double[] last = path.get(path.size() - 1);
            sb.append(String.format(Locale.US, ",%.5f,%.5f", last[1], last[0]));
            sb.append(";linewidth:5;linecolor:%23c8231b;lineopacity:0.85");
        }
        return sb.append("&apiKey=").append(KEY).toString();
    }

    // ------------------------------------------------------------------ 4. Map tiles (interactive maps)
    /** Raster tile URL for the interactive maps (Home "Map" view and the directions screen). */
    public static String tileUrl(int zoom, int x, int y) {
        return "https://maps.geoapify.com/v1/tile/osm-bright/" + zoom + "/" + x + "/" + y + "@2x.png?apiKey=" + KEY;
    }

    // ------------------------------------------------------------------ plumbing
    private interface Job<T> {
        T call() throws Exception;
    }

    private static <T> void run(Job<T> job, Callback<T> cb) {
        if (!hasKey()) {
            MAIN.post(() -> cb.onError(new IllegalStateException("No Geoapify key in local.properties")));
            return;
        }
        IO.execute(() -> {
            try {
                T v = job.call();
                MAIN.post(() -> cb.onResult(v));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e));
            }
        });
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new Exception("Geoapify error " + code);
            return new JSONObject(read(con.getInputStream()));
        } finally {
            con.disconnect();
        }
    }

    static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
