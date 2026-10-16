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

public class MainActivity extends Activity {
    private EditText latitude;
    private EditText longitude;
    private TextView status;
    private static final String PREFS = "mock_location_state";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        UpdateManager.attach(this, findViewById(R.id.root));
        latitude = findViewById(R.id.latitude);
        longitude = findViewById(R.id.longitude);
        status = findViewById(R.id.status);
        findViewById(R.id.start).setOnClickListener(v -> startMock());
        findViewById(R.id.stop).setOnClickListener(v -> stopMock());
        findViewById(R.id.setup).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        findViewById(R.id.useCurrent).setOnClickListener(v -> useCurrentLocation());
        findViewById(R.id.savePlace).setOnClickListener(v -> saveCurrentPlace());
        restoreLastTarget(); renderSavedPlaces();
        refreshStatus();
    }

    private void startMock() {
        try {
            double lat = Double.parseDouble(latitude.getText().toString());
            double lon = Double.parseDouble(longitude.getText().toString());
            if (!Coordinates.isValid(lat, lon)) throw new NumberFormatException();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
                status.setText("Allow notifications, then tap Start again so the active location stays visible.");
                return;
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
                status.setText("Allow precise location, then tap Start again.");
                return;
            }
            Intent intent = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_START)
                    .putExtra("latitude", lat).putExtra("longitude", lon);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_target_lat", String.valueOf(lat)).putString("last_target_lon", String.valueOf(lon)).apply();
            startForegroundService(intent);
            status.setText("Starting at " + lat + ", " + lon + "…");
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a valid latitude and longitude.", Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            status.setText("Allow precise location and select this app in Developer options → Select mock location app.");
        }
    }

    private void restoreLastTarget() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        latitude.setText(prefs.getString("last_target_lat", "43.6532")); longitude.setText(prefs.getString("last_target_lon", "-79.3832"));
    }

    private void useCurrentLocation() {
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
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void stopMock() {
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
            status.setText("Inactive — choose this app in Developer options → Select mock location app.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == 7 || requestCode == 8) && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            status.setText(requestCode == 7
                    ? "Precise location permission is required while mock location is active."
                    : "Notifications are needed to keep the active mock location visible.");
        }
    }
}

