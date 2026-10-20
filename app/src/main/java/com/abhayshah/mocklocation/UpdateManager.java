package com.abhayshah.mocklocation;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visible update status; downloads are verified before opening Android's installer. */
final class UpdateManager {
    private static final String MANIFEST = "https://raw.githubusercontent.com/AbhayShah06/MockLocation/main/update.json";
    private final Activity activity;
    private final TextView title;
    private final TextView detail;
    private final Button action;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private JSONObject available;
    private boolean waitingForInstallPermission;
    private volatile boolean closed;

    static UpdateManager attach(Activity activity, ViewGroup parent) {
        return new UpdateManager(activity, parent);
    }

    private UpdateManager(Activity a, ViewGroup parent) {
        activity = a;
        LinearLayout banner = new LinearLayout(a);
        banner.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(14 * a.getResources().getDisplayMetrics().density);
        banner.setPadding(padding, padding, padding, padding);
        banner.setBackgroundColor(Color.rgb(32, 58, 49));
        title = new TextView(a); title.setTextColor(Color.WHITE); title.setTextSize(16);
        detail = new TextView(a); detail.setTextColor(0xffc2d7cc);
        action = new Button(a); action.setAllCaps(false);
        banner.addView(title); banner.addView(detail); banner.addView(action);
        parent.addView(banner, 2);
        action.setOnClickListener(v -> { if (available == null) check(); else install(); });
        check();
    }

    private void ui(Runnable task) {
        activity.runOnUiThread(() -> { if (!closed && !activity.isDestroyed()) task.run(); });
    }

    private void check() {
        title.setText("App updates"); detail.setText("Checking for updates…");
        action.setText("Checking…"); action.setEnabled(false);
        worker.execute(() -> {
            try {
                JSONObject manifest = new JSONObject(read(MANIFEST));
                int installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (manifest.getInt("versionCode") > installed) {
                    UpdatePolicy.validate(manifest.getInt("versionCode"), installed, manifest.getString("apkUrl"), manifest.getString("sha256"));
                    ui(() -> {
                        available = manifest; title.setText("Update " + manifest.optString("versionName") + " available");
                        detail.setText(manifest.optString("notes", "Bug fixes and improvements"));
                        action.setEnabled(true); action.setText("Update now");
                    });
                } else ui(() -> {
                    available = null; title.setText("You're up to date");
                    try { detail.setText("Installed version " + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName); }
                    catch (Exception ignored) { detail.setText("No newer version is available."); }
                    action.setText("Check for updates"); action.setEnabled(true);
                });
            } catch (Exception error) {
                ui(() -> { title.setText("Couldn't check for updates"); detail.setText("Check your connection and try again. You can keep using the app."); action.setText("Retry check"); action.setEnabled(true); });
            }
        });
    }

    private void install() {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            detail.setText("Allow updates from Mock Location on the next screen. Download will continue when you return.");
            try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
            catch (RuntimeException e) { waitingForInstallPermission = false; detail.setText("Open Settings → Apps → Special app access → Install unknown apps, and allow Mock Location."); }
            return;
        }
        action.setEnabled(false); action.setText("Downloading…");
        JSONObject manifest = available;
        worker.execute(() -> {
            File apk = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "pending-update.apk");
            try {
                download(manifest.getString("apkUrl"), apk);
                if (!hash(apk).equalsIgnoreCase(manifest.getString("sha256"))) throw new SecurityException("Checksum mismatch");
                verifyPackage(apk, manifest.getInt("versionCode"));
                ui(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", apk);
                        activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        detail.setText("Confirm Install in Android's installer. If you cancel, tap Update now to retry.");
                    } catch (RuntimeException error) { detail.setText("Couldn't open the installer. Tap Update now to retry."); }
                    action.setText("Update now"); action.setEnabled(true);
                });
            } catch (Exception error) {
                apk.delete();
                ui(() -> { detail.setText("Update couldn't be downloaded or verified. Tap to retry."); action.setText("Retry update"); action.setEnabled(true); });
            }
        });
    }

    void onResume() {
        if (waitingForInstallPermission) {
            waitingForInstallPermission = false;
            if (activity.getPackageManager().canRequestPackageInstalls()) install();
            else detail.setText("Install permission wasn't granted. Tap Update now when you're ready.");
        }
    }

    void close() { closed = true; worker.shutdownNow(); }

    private void verifyPackage(File apk, int expectedVersion) throws Exception {
        PackageManager pm = activity.getPackageManager();
        PackageInfo next = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        PackageInfo current = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        if (next == null || !activity.getPackageName().equals(next.packageName) || next.versionCode != expectedVersion
                || next.versionCode <= current.versionCode || next.signatures == null || current.signatures == null
                || !java.util.Arrays.equals(next.signatures, current.signatures)) throw new SecurityException("Unexpected package, version or signer");
    }

    private static HttpURLConnection connection(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new SecurityException("HTTPS required");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MockLocation-Updater");
        if (c.getResponseCode() != 200) { c.disconnect(); throw new IOException("HTTP error"); }
        return c;
    }

    private static String read(String url) throws Exception {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) { if (out.size() + count > 65536) throw new IOException("Manifest too large"); out.write(buffer, 0, count); }
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private void download(String url, File apk) throws Exception {
        HttpURLConnection c = connection(url);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[16384]; int count; long received = 0; long lastUpdate = 0;
            while ((count = in.read(buffer)) != -1) {
                if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                received += count; if (received > 150L * 1024 * 1024) throw new IOException("APK too large");
                out.write(buffer, 0, count);
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastUpdate > 250) {
                    lastUpdate = now;
                    String progress = total > 0 ? "Downloading " + (received * 100 / total) + "%" : "Downloading " + received / 1024 + " KB";
                    ui(() -> action.setText(progress));
                }
            }
        } finally { c.disconnect(); }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return result.toString();
    }
}
