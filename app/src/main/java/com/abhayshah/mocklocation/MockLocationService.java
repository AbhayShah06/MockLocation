package com.abhayshah.mocklocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;

public class MockLocationService extends Service {
    private static final String PROVIDER = LocationManager.GPS_PROVIDER;
    private LocationManager manager;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("mock", "Mock location", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, "mock")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Mock location is active")
                .setContentText("Tap Stop in the app to restore real location.")
                .setOngoing(true).build();
        startForeground(100, notification);
        manager = getSystemService(LocationManager.class);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        double lat = intent.getDoubleExtra("latitude", 0);
        double lon = intent.getDoubleExtra("longitude", 0);
        try {
            manager.addTestProvider(PROVIDER, false, false, false, false, true, true, true, 0, 5);
        } catch (IllegalArgumentException ignored) { }
        manager.setTestProviderEnabled(PROVIDER, true);
        Location location = new Location(PROVIDER);
        location.setLatitude(lat); location.setLongitude(lon);
        location.setAccuracy(5); location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        manager.setTestProviderLocation(PROVIDER, location);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { manager.removeTestProvider(PROVIDER); } catch (Exception ignored) { }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

