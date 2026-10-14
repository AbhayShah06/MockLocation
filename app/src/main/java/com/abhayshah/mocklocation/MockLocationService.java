package com.abhayshah.mocklocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

public class MockLocationService extends Service {
    public static final String ACTION_START = "com.abhayshah.mocklocation.START";
    public static final String ACTION_STOP = "com.abhayshah.mocklocation.STOP";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    private static final String PROVIDER = LocationManager.GPS_PROVIDER;
    private static final String CHANNEL_ID = "mock_location_active";
    private static final int NOTIFICATION_ID = 100;
    private static final long UPDATE_INTERVAL_MS = 5_000;
    private static final String PREFS = "mock_location_state";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private double latitude;
    private double longitude;
    private boolean running;

    private final Runnable publishLocation = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                Location location = new Location(PROVIDER);
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                location.setAccuracy(5f);
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                locationManager.setTestProviderLocation(PROVIDER, location);
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
            } catch (RuntimeException error) {
                stopAndCleanUp();
                stopSelf();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = getSystemService(LocationManager.class);
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAndCleanUp();
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences state = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (intent != null && intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
            latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN);
            longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN);
        } else if (state.getBoolean(KEY_ACTIVE, false)) {
            latitude = Double.longBitsToDouble(state.getLong(KEY_LATITUDE, Double.doubleToRawLongBits(Double.NaN)));
            longitude = Double.longBitsToDouble(state.getLong(KEY_LONGITUDE, Double.doubleToRawLongBits(Double.NaN)));
        } else {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Coordinates.isValid(latitude, longitude)) {
            stopAndCleanUp();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        try {
            ensureTestProvider();
            locationManager.setTestProviderEnabled(PROVIDER, true);
            running = true;
            if (!state.edit().putLong(KEY_LATITUDE, Double.doubleToRawLongBits(latitude))
                    .putLong(KEY_LONGITUDE, Double.doubleToRawLongBits(longitude))
                    .putBoolean(KEY_ACTIVE, true).commit()) {
                throw new IllegalStateException("Unable to persist active location state");
            }
            publishLocation.run();
            return START_STICKY;
        } catch (RuntimeException error) {
            stopAndCleanUp();
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void ensureTestProvider() {
        try {
            locationManager.addTestProvider(PROVIDER, false, false, false, false,
                    true, true, true,
                    android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                    android.location.provider.ProviderProperties.ACCURACY_FINE);
        } catch (IllegalArgumentException alreadyExists) {
            // A prior process may have left the test provider registered.
        }
        locationManager.setTestProviderEnabled(PROVIDER, true);
    }

    private void stopAndCleanUp() {
        running = false;
        handler.removeCallbacks(publishLocation);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).commit();
        if (locationManager != null) {
            try {
                // Remove even when this process did not add it: it may be a provider
                // retained across a service process restart.
                locationManager.removeTestProvider(PROVIDER);
            } catch (RuntimeException ignored) {
                // Provider may already have been removed by Android or the user.
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Mock location", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shown while this app publishes a mock location");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, MockLocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 2, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Mock location is active")
                .setContentText(String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude))
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPendingIntent).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override public void onDestroy() {
        stopAndCleanUp();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
