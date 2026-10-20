package com.abhayshah.mocklocation;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.ClipboardManager;
import android.content.ClipData;

public class MainActivity extends Activity {
    private EditText latitude;
    private EditText longitude;
    private TextView status;
    private UpdateManager updater;
    private org.osmdroid.views.MapView map;
    private org.osmdroid.views.overlay.Marker targetPin;
    private boolean updatingFields;
    private boolean pendingStart;
    private final java.util.concurrent.ExecutorService searchWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int searchGeneration;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener stateListener = (prefs, key) -> {
        if ("active".equals(key) || "service_error".equals(key)) refreshStatus();
    };
    private static final String PREFS = "mock_location_state";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        android.view.View content = ((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
        updater = UpdateManager.attach(this, findViewById(R.id.root));
        latitude = findViewById(R.id.latitude);
        longitude = findViewById(R.id.longitude);
        status = findViewById(R.id.status);
        getSharedPreferences(PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(stateListener);
        setupMap();
        findViewById(R.id.searchLocation).setOnClickListener(v -> searchLocation());
        ((EditText)findViewById(R.id.locationSearch)).setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { searchLocation(); return true; } return false; });
        findViewById(R.id.start).setOnClickListener(v -> startMock());
        findViewById(R.id.stop).setOnClickListener(v -> stopMock());
        findViewById(R.id.setup).setOnClickListener(v -> showSetup());
        findViewById(R.id.useCurrent).setOnClickListener(v -> useCurrentLocation());
        findViewById(R.id.savePlace).setOnClickListener(v -> saveCurrentPlace());
        findViewById(R.id.pasteLocation).setOnClickListener(v -> pasteLocation());
        restoreLastTarget();
        if (state != null) { latitude.setText(state.getString("target_lat", latitude.getText().toString())); longitude.setText(state.getString("target_lon", longitude.getText().toString())); }
        renderSavedPlaces(); renderRecentPlaces(); if (state == null) handleIncoming(getIntent());
        refreshStatus();
    }

    private void startMock() {
        try {
            double lat = Double.parseDouble(latitude.getText().toString());
            double lon = Double.parseDouble(longitude.getText().toString());
            if (!Coordinates.isValid(lat, lon)) throw new NumberFormatException();
            if (!mockSelected()) { showSetup(); return; }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                pendingStart = true;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
                return;
            }
            Intent intent = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_START)
                    .putExtra("latitude", lat).putExtra("longitude", lon);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_target_lat", String.valueOf(lat)).putString("last_target_lon", String.valueOf(lon)).apply();
            rememberRecent(lat, lon);
            startForegroundService(intent);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("notification_asked", false)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
            }
            status.setText("Starting at " + lat + ", " + lon + "…");
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a valid latitude and longitude.", Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            status.setText("Allow precise location and select this app in Developer options → Select mock location app.");
        }
    }

    private boolean mockSelected() {
        return getSystemService(android.app.AppOpsManager.class).checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private void showSetup() {
        new AlertDialog.Builder(this).setTitle("Select Mock Location once")
                .setMessage("In Developer options, tap ‘Select mock location app’ and choose ‘Mock Location’. Then return here and tap Start.\n\nIf Developer options is hidden, open About phone and tap Build number seven times first.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open settings", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                    catch (android.content.ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                }).show();
    }

    private void setupMap() {
        org.osmdroid.config.Configuration.getInstance().setUserAgentValue(getPackageName());
        org.osmdroid.config.Configuration.getInstance().setOsmdroidBasePath(new java.io.File(getCacheDir(), "maps"));
        org.osmdroid.config.Configuration.getInstance().setOsmdroidTileCache(new java.io.File(getCacheDir(), "maps/tiles"));
        map = findViewById(R.id.map);
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(13.0);
        map.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != android.view.MotionEvent.ACTION_UP && event.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL);
            return false;
        });
        map.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(new org.osmdroid.events.MapEventsReceiver() {
            public boolean singleTapConfirmedHelper(org.osmdroid.util.GeoPoint p) { setTarget(p); return true; }
            public boolean longPressHelper(org.osmdroid.util.GeoPoint p) { setTarget(p); return true; }
        }));
        targetPin = new org.osmdroid.views.overlay.Marker(map);
        targetPin.setAnchor(.5f, 1f);
        targetPin.setDraggable(true);
        targetPin.setTitle("Selected target");
        targetPin.setOnMarkerDragListener(new org.osmdroid.views.overlay.Marker.OnMarkerDragListener() {
            public void onMarkerDragStart(org.osmdroid.views.overlay.Marker marker) { }
            public void onMarkerDrag(org.osmdroid.views.overlay.Marker marker) { setTarget(marker.getPosition()); }
            public void onMarkerDragEnd(org.osmdroid.views.overlay.Marker marker) { setTarget(marker.getPosition()); }
        });
        map.getOverlays().add(targetPin);
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingFields) return;
                try { double lat = Double.parseDouble(latitude.getText().toString()), lon = Double.parseDouble(longitude.getText().toString());
                    if (Coordinates.isValid(lat, lon)) { org.osmdroid.util.GeoPoint p = new org.osmdroid.util.GeoPoint(lat, lon); targetPin.setPosition(p); map.getController().setCenter(p); map.invalidate(); }
                } catch (NumberFormatException ignored) { }
            }
            public void afterTextChanged(android.text.Editable text) { }
        };
        latitude.addTextChangedListener(watcher); longitude.addTextChangedListener(watcher);
    }

    private void setTarget(org.osmdroid.util.GeoPoint point) {
        updatingFields = true;
        latitude.setText(String.format(java.util.Locale.US, "%.6f", point.getLatitude()));
        longitude.setText(String.format(java.util.Locale.US, "%.6f", point.getLongitude()));
        updatingFields = false;
        targetPin.setPosition(point); map.invalidate();
    }

    private void searchLocation() {
        String query = ((EditText)findViewById(R.id.locationSearch)).getText().toString().trim();
        int generation = ++searchGeneration;
        if (query.isEmpty()) return;
        if (applyLocationText(query)) return;
        status.setText("Finding location…");
        searchWorker.execute(() -> {
            try {
                java.util.List<android.location.Address> results = new android.location.Geocoder(this).getFromLocationName(query, 5);
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != searchGeneration) return;
                    if (results == null || results.isEmpty()) { status.setText("No places found. Try a fuller address or coordinates."); return; }
                    String[] labels = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) labels[i] = results.get(i).getAddressLine(0);
                    new AlertDialog.Builder(this).setTitle("Choose location").setItems(labels, (d, which) -> {
                        android.location.Address a = results.get(which);
                        org.osmdroid.util.GeoPoint p = new org.osmdroid.util.GeoPoint(a.getLatitude(), a.getLongitude());
                        setTarget(p); map.getController().animateTo(p); refreshStatus();
                    }).setNegativeButton("Cancel", null).show();
                    refreshStatus();
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed() && generation == searchGeneration) status.setText("Search unavailable. Check internet, or enter coordinates / tap the map."); }); }
        });
    }

    private void restoreLastTarget() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        latitude.setText(prefs.getString("last_target_lat", "43.6532")); longitude.setText(prefs.getString("last_target_lon", "-79.3832"));
    }

    private void useCurrentLocation() {
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("active", false)) {
            Toast.makeText(this, "Stop mock location first to use your real location.", Toast.LENGTH_LONG).show(); return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7); return; }
        LocationManager manager = getSystemService(LocationManager.class); Location best = null;
        for (String provider : manager.getProviders(true)) try { Location candidate = manager.getLastKnownLocation(provider); if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate; } catch (SecurityException ignored) { }
        if (best == null) { Toast.makeText(this, "Android does not have a recent location yet.", Toast.LENGTH_LONG).show(); return; }
        latitude.setText(String.format(java.util.Locale.US, "%.6f", best.getLatitude())); longitude.setText(String.format(java.util.Locale.US, "%.6f", best.getLongitude()));
    }

    private void saveCurrentPlace() {
        final double lat, lon; try { lat = Double.parseDouble(latitude.getText().toString()); lon = Double.parseDouble(longitude.getText().toString()); } catch (NumberFormatException e) { Toast.makeText(this, "Enter valid coordinates first.", Toast.LENGTH_SHORT).show(); return; }
        if (!Coordinates.isValid(lat, lon)) { Toast.makeText(this, "Those coordinates are outside the valid range.", Toast.LENGTH_SHORT).show(); return; }
        final android.widget.EditText name = new android.widget.EditText(this); name.setHint("Place name");
        new AlertDialog.Builder(this).setTitle("Save this place").setView(name).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
            String label = name.getText().toString().trim(); if (label.isEmpty()) label = String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon);
            try { JSONArray places = savedPlaces(); JSONObject place = new JSONObject(); place.put("name", label); place.put("lat", lat); place.put("lon", lon); places.put(place); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("places", places.toString()).apply(); renderSavedPlaces(); } catch (Exception ignored) { }
        }).show();
    }

    private JSONArray savedPlaces() { try { return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString("places", "[]")); } catch (Exception ignored) { return new JSONArray(); } }

    private void renderSavedPlaces() {
        LinearLayout container = findViewById(R.id.savedPlaces); container.removeAllViews(); JSONArray places = savedPlaces();
        if (places.length() == 0) { TextView empty = new TextView(this); empty.setText("Save a location once, then return to it with one tap."); empty.setTextColor(0xff7f8884); empty.setPadding(4, 12, 4, 12); container.addView(empty); return; }
        for (int i = 0; i < places.length(); i++) try {
            JSONObject place = places.getJSONObject(i); int index = i; Button row = new Button(this); row.setAllCaps(false); row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); row.setText(place.getString("name") + "\n" + String.format(java.util.Locale.US, "%.5f, %.5f", place.getDouble("lat"), place.getDouble("lon"))); row.setOnClickListener(v -> { latitude.setText(String.valueOf(place.optDouble("lat"))); longitude.setText(String.valueOf(place.optDouble("lon"))); });
            row.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setMessage("Delete " + place.optString("name") + "?").setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> deletePlace(index)).show(); return true; }); container.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
        } catch (Exception ignored) { }
    }

    private void deletePlace(int index) { JSONArray old = savedPlaces(), next = new JSONArray(); for (int i = 0; i < old.length(); i++) if (i != index) next.put(old.opt(i)); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("places", next.toString()).apply(); renderSavedPlaces(); }

    private void pasteLocation() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class); ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0 || !applyLocationText(String.valueOf(data.getItemAt(0).coerceToText(this)))) Toast.makeText(this, "No coordinates found in the clipboard.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIncoming(intent); }

    private void handleIncoming(Intent intent) {
        if (intent == null) return; String text = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) text = intent.getStringExtra(Intent.EXTRA_TEXT);
        else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) text = intent.getData().toString();
        if (text != null && applyLocationText(text)) Toast.makeText(this, "Location ready.", Toast.LENGTH_SHORT).show();
    }

    private boolean applyLocationText(String text) { double[] point = Coordinates.parse(text); if (point == null) return false; latitude.setText(String.valueOf(point[0])); longitude.setText(String.valueOf(point[1])); return true; }

    private void rememberRecent(double lat, double lon) {
        JSONArray old = recentPlaces(), next = new JSONArray(); try { JSONObject current = new JSONObject(); current.put("lat", lat); current.put("lon", lon); current.put("at", System.currentTimeMillis()); next.put(current); } catch (Exception ignored) { }
        for (int i = 0; i < old.length() && next.length() < 5; i++) { JSONObject item = old.optJSONObject(i); if (item == null || (Math.abs(item.optDouble("lat") - lat) < .000001 && Math.abs(item.optDouble("lon") - lon) < .000001)) continue; next.put(item); }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("recent_places", next.toString()).apply(); renderRecentPlaces();
    }

    private JSONArray recentPlaces() { try { return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString("recent_places", "[]")); } catch (Exception ignored) { return new JSONArray(); } }
    private void renderRecentPlaces() {
        LinearLayout container = findViewById(R.id.recentPlaces); container.removeAllViews(); JSONArray recent = recentPlaces();
        if (recent.length() == 0) { TextView empty = new TextView(this); empty.setText("Locations you start will appear here."); empty.setTextColor(0xff7f8884); empty.setPadding(4, 12, 4, 12); container.addView(empty); return; }
        for (int i = 0; i < recent.length(); i++) { JSONObject point = recent.optJSONObject(i); if (point == null) continue; Button row = new Button(this); row.setAllCaps(false); row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); row.setText(String.format(java.util.Locale.US, "%.6f, %.6f", point.optDouble("lat"), point.optDouble("lon"))); row.setOnClickListener(v -> { latitude.setText(String.valueOf(point.optDouble("lat"))); longitude.setText(String.valueOf(point.optDouble("lon"))); }); container.addView(row, new LinearLayout.LayoutParams(-1, dp(56))); }
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void stopMock() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("service_error").apply();
        Intent stop = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP);
        try {
            startService(stop);
        } catch (RuntimeException error) {
            stopService(new Intent(this, MockLocationService.class));
        }
        status.setText("Stopping mock location…");
    }

    private void refreshStatus() {
        boolean active = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("active", false);
        if (active) {
            long latBits = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("latitude", Double.doubleToRawLongBits(0));
            long lonBits = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("longitude", Double.doubleToRawLongBits(0));
            status.setText("Active at " + Double.longBitsToDouble(latBits) + ", " + Double.longBitsToDouble(lonBits)
                    + ". Stop from here or the notification.");
        } else {
            String error = getSharedPreferences(PREFS, MODE_PRIVATE).getString("service_error", null);
            status.setText(error != null ? error : mockSelected() ? "Ready — choose a target and tap Start here." : "One-time setup needed — select Mock Location in Developer options.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
        if (map != null) map.onResume();
        if (updater != null) updater.onResume();
    }

    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("target_lat", latitude.getText().toString()); state.putString("target_lon", longitude.getText().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { searchGeneration++; searchWorker.shutdownNow(); if (updater != null) updater.close(); getSharedPreferences(PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(stateListener); if (map != null) map.onDetach(); super.onDestroy(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7) {
            boolean resume = pendingStart; pendingStart = false;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (resume) startMock(); else useCurrentLocation();
            } else status.setText("Allow precise location to start. You can still choose a target on the map.");
        }
    }
}

