package com.abhayshah.mocklocation;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText latitude;
    private EditText longitude;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        latitude = findViewById(R.id.latitude);
        longitude = findViewById(R.id.longitude);
        status = findViewById(R.id.status);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        }
        findViewById(R.id.start).setOnClickListener(v -> startMock());
        findViewById(R.id.stop).setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            status.setText("Inactive — real location restored.");
        });
    }

    private void startMock() {
        try {
            double lat = Double.parseDouble(latitude.getText().toString());
            double lon = Double.parseDouble(longitude.getText().toString());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new NumberFormatException();
            Intent intent = new Intent(this, MockLocationService.class)
                    .putExtra("latitude", lat).putExtra("longitude", lon);
            startForegroundService(intent);
            status.setText("Active at " + lat + ", " + lon + ".");
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a valid latitude and longitude.", Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            Toast.makeText(this, "Select Mock Location in Developer options first.", Toast.LENGTH_LONG).show();
        }
    }
}

