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
            startForegroundService(intent);
            status.setText("Starting at " + lat + ", " + lon + "…");
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a valid latitude and longitude.", Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            status.setText("Allow precise location and select this app in Developer options → Select mock location app.");
        }
    }

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

