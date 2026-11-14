package com.example.sagana;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;

import java.util.List;

/**
 * Shared set-up for the interactive maps (osmdroid MapView showing Geoapify map tiles):
 * the tile source, zoom controls that work with a mouse as well as fingers, the pin icons,
 * and a fix so a map inside a scrolling screen can still be panned.
 */
public final class GeoMap {

    private static final int CHILI = 0xFFC8231B, LEAF = 0xFF2F7D4A, WHITE = 0xFFFFFFFF;

    /** Geoapify "OSM Bright" raster tiles, 2x for sharp text on phone screens. */
    public static final OnlineTileSourceBase GEOAPIFY_TILES = new OnlineTileSourceBase(
            "GeoapifyOsmBright", 1, 20, 256, ".png",
            new String[]{"https://maps.geoapify.com/v1/tile/osm-bright/"},
            "© OpenStreetMap contributors · Powered by Geoapify") {
        @Override
        public String getTileURLString(long index) {
            return GeoapifyClient.tileUrl(MapTileIndex.getZoom(index), MapTileIndex.getX(index), MapTileIndex.getY(index));
        }
    };

    private GeoMap() {
    }

    public static GeoPoint point(double lat, double lng) {
        return new GeoPoint(lat, lng);
    }

    /** Tiles, pinch-zoom, +/- buttons (for mouse users), attribution, and a sensible start view. */
    public static void setup(MapView map, double lat, double lng, double zoom) {
        map.setTileSource(GEOAPIFY_TILES);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.ALWAYS);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(19.0);
        map.setVerticalMapRepetitionEnabled(false);
        map.getController().setZoom(zoom);
        map.getController().setCenter(point(lat, lng));
        CopyrightOverlay copyright = new CopyrightOverlay(map.getContext());
        copyright.setAlignRight(true);
        map.getOverlays().add(copyright);
    }

    /** Inside a NestedScrollView, let the map keep the drag instead of the page scrolling. */
    @SuppressLint("ClickableViewAccessibility")
    public static void keepTouchesInside(MapView map) {
        map.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            boolean holding = a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE
                    || a == MotionEvent.ACTION_POINTER_DOWN;
            v.getParent().requestDisallowInterceptTouchEvent(holding);
            return false; // let the map handle the gesture
        });
    }

    /** Zooms so every point fits, with some breathing room. Safe to call before layout. */
    public static void fit(MapView map, List<GeoPoint> points) {
        fit(map, points, 0, 0);
    }

    /**
     * Same, but keeps the points inside the part of the map that isn't covered by cards
     * floating over it: {@code topCoveredPx} at the top and {@code bottomCoveredPx} at the bottom.
     */
    public static void fit(MapView map, List<GeoPoint> points, int topCoveredPx, int bottomCoveredPx) {
        if (points.isEmpty()) return;
        Runnable doFit = () -> {
            if (points.size() == 1) {
                map.getController().setZoom(15.0);
                map.getController().setCenter(points.get(0));
                return;
            }
            BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
            int h = map.getHeight();
            int visible = h - topCoveredPx - bottomCoveredPx;
            if (visible > h / 4) {
                // stretch the box north and south so its original part lands in the visible gap
                double span = box.getLatNorth() - box.getLatSouth();
                double perPx = span / visible;
                box = new BoundingBox(box.getLatNorth() + perPx * topCoveredPx, box.getLonEast(),
                        box.getLatSouth() - perPx * bottomCoveredPx, box.getLonWest());
            }
            int pad = Ui.dp(map.getContext(), 40);
            map.zoomToBoundingBox(box, false, pad);
            if (map.getZoomLevelDouble() > 16.5) map.getController().setZoom(16.5);
        };
        if (map.getWidth() > 0 && map.getHeight() > 0) doFit.run();
        else map.addOnFirstLayoutListener((v, l, t, r, b) -> doFit.run());
    }

    // ------------------------------------------------------------------ icons
    /** Red map pin with the number of listings at that desk. */
    public static Drawable deskPin(Context c, int count) {
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(38 * d), h = Math.round(50 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float cx = w / 2f, r = w / 2f - 2 * d, cy = r + 2 * d;
        Path pin = new Path();
        pin.addCircle(cx, cy, r, Path.Direction.CW);
        pin.moveTo(cx - r * 0.62f, cy + r * 0.72f);
        pin.lineTo(cx, h - 1.5f * d);
        pin.lineTo(cx + r * 0.62f, cy + r * 0.72f);
        pin.close();

        p.setColor(0x33000000);                 // soft shadow
        cv.save();
        cv.translate(0, 1.5f * d);
        cv.drawPath(pin, p);
        cv.restore();
        p.setColor(CHILI);
        cv.drawPath(pin, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(WHITE);
        cv.drawPath(pin, p);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cy, r * 0.62f, p);

        p.setColor(CHILI);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(13 * d);
        String label = count > 99 ? "99+" : String.valueOf(count);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    /** Green "you are here" dot with a white ring and a soft halo. */
    public static Drawable youDot(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        int s = Math.round(28 * d);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x402F7D4A);
        cv.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(WHITE);
        cv.drawCircle(s / 2f, s / 2f, 8 * d, p);
        p.setColor(LEAF);
        cv.drawCircle(s / 2f, s / 2f, 6 * d, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    /** Destination pin for the directions screen: the red pin with a little bowl instead of a number. */
    public static Drawable destinationPin(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(38 * d), h = Math.round(50 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Drawable pin = deskPin(c, 0);
        pin.setBounds(0, 0, w, h);
        pin.draw(cv);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = w / 2f - 2 * d, cx = w / 2f, cy = r + 2 * d;
        p.setColor(WHITE);                      // cover the "0"
        cv.drawCircle(cx, cy, r * 0.62f, p);
        p.setColor(CHILI);                      // bowl: a half circle under a rim
        cv.drawArc(new RectF(cx - 6.5f * d, cy - 5 * d, cx + 6.5f * d, cy + 6 * d), 0, 180, true, p);
        cv.drawRect(cx - 6.5f * d, cy - 0.5f * d, cx + 6.5f * d, cy + 1.2f * d, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }
}
