package org.telegram.messenger;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class TgWsProxyBootstrap {
    private static final String PREFS = "tgwsproxy";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_RUNTIME_CONFIG = "runtime_config";
    private static final String KEY_MANAGED_PROXY = "managed_proxy";
    private static final String KEY_UPDATE_ENABLED = "update_enabled";
    private static final String KEY_UPDATE_LAST_SUCCESS = "update_last_success";
    private static final String KEY_UPDATE_LAST_ATTEMPT = "update_last_attempt";
    private static final String KEY_UPDATE_PENDING_MANIFEST = "update_pending_manifest";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 1443;
    private static final String LEGACY_RUNTIME_CONFIG = "@mtproto_worker_preconnect=1";
    private static final String DEFAULT_RUNTIME_CONFIG =
            "@connection_mode=cf_first,@mtproto_worker_preconnect=1";
    private static final String UPDATE_FEED_URL =
            "https://github.com/Regstar2/telegram-wsp/releases/latest/download/latest.json";
    private static final String UPDATE_APK_URL_PREFIX =
            "https://github.com/Regstar2/telegram-wsp/releases/download/";
    private static final long UPDATE_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final long UPDATE_RETRY_DELAY_MS = 10L * 60L * 1000L;
    private static final String RELEASES_URL = "https://github.com/Regstar2/telegram-wsp/releases";
    private static final int NETWORK_CONNECT_TIMEOUT_MS = 15_000;
    private static final int NETWORK_READ_TIMEOUT_MS = 30_000;
    private static final int UPDATE_METADATA_LIMIT_BYTES = 64 * 1024;
    private static final long UPDATE_APK_LIMIT_BYTES = 250L * 1024L * 1024L;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static boolean initialized;
    private static boolean checking;
    private static UpdateInfo pendingUpdate;
    private static long promptedVersionCode = -1L;

    private TgWsProxyBootstrap() {
    }

    public static void start(Context context) {
        synchronized (TgWsProxyBootstrap.class) {
            if (initialized) {
                return;
            }
            initialized = true;
        }

        Context appContext = context.getApplicationContext();

        Thread bootstrapThread = new Thread(
                () -> startInBackground(appContext),
                "TgWsProxyBootstrap"
        );
        bootstrapThread.setDaemon(true);
        bootstrapThread.start();

        if (appContext instanceof Application) {
            ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity a, Bundle state) {}
                    @Override public void onActivityStarted(Activity a) {}
                    @Override public void onActivityPaused(Activity a) {}
                    @Override public void onActivityStopped(Activity a) {}
                    @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity a) {}
                    @Override public void onActivityResumed(Activity a) {
                        if (a instanceof LaunchActivity) {
                            postToMain(() -> offerPendingUpdate(a));
                            requestUpdateCheck(appContext, false);
                        }
                    }
                });
        }
        restorePendingUpdate(appContext);
        requestUpdateCheck(appContext, false);
    }

    private static void restorePendingUpdate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!isCoolingDown(now, prefs.getLong(KEY_UPDATE_LAST_SUCCESS, 0L),
                UPDATE_CHECK_INTERVAL_MS)) {
            prefs.edit().remove(KEY_UPDATE_PENDING_MANIFEST).apply();
            return;
        }
        String saved = prefs.getString(KEY_UPDATE_PENDING_MANIFEST, null);
        if (saved == null) return;
        try {
            UpdateInfo update = validateUpdate(new JSONObject(saved));
            if (update.versionCode > getCurrentVersionCode(context)
                    && update.minimumSdk <= Build.VERSION.SDK_INT) {
                synchronized (TgWsProxyBootstrap.class) { pendingUpdate = update; }
            } else {
                prefs.edit().remove(KEY_UPDATE_PENDING_MANIFEST).apply();
            }
        } catch (Throwable error) {
            prefs.edit().remove(KEY_UPDATE_PENDING_MANIFEST).apply();
            FileLog.e("Telegram-WSP saved update manifest invalid: " + error);
        }
    }

    private static void startInBackground(Context context) {
        try {
            TgWsProxyController.start(context);
        } catch (Throwable error) {
            FileLog.e("TgWsProxy bootstrap failed: " + error.getClass().getSimpleName());
        }
    }

    private static void postToMain(Runnable runnable) {
        Handler handler = ApplicationLoader.applicationHandler;
        (handler == null ? new Handler(Looper.getMainLooper()) : handler).post(runnable);
    }

    private static void message(Context context, String text) {
        postToMain(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
    }

    public static void checkForUpdates(Context context) {
        requestUpdateCheck(context.getApplicationContext(), true);
    }

    public static void openReleases(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            FileLog.e("Could not open Telegram-WSP releases: " + error);
            message(context, "Could not open GitHub Releases.");
        }
    }

    private static boolean isCoolingDown(long now, long previous, long interval) {
        return previous > 0L && now >= previous && now - previous < interval;
    }

    private static void requestUpdateCheck(Context context, boolean manual) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!manual && !prefs.getBoolean(KEY_UPDATE_ENABLED, true)) {
            return;
        }
        synchronized (TgWsProxyBootstrap.class) {
            if (checking) {
                if (manual) message(context, "Telegram-WSP: update check already in progress.");
                return;
            }
            long now = System.currentTimeMillis();
            if (!manual && (isCoolingDown(now,
                    prefs.getLong(KEY_UPDATE_LAST_SUCCESS, 0L), UPDATE_CHECK_INTERVAL_MS)
                    || isCoolingDown(now,
                    prefs.getLong(KEY_UPDATE_LAST_ATTEMPT, 0L), UPDATE_RETRY_DELAY_MS))) {
                return;
            }
            checking = true;
            prefs.edit().putLong(KEY_UPDATE_LAST_ATTEMPT, now).apply();
        }

        Thread thread = new Thread(() -> {
            try {
                JSONObject metadata = new JSONObject(readSmallHttpsResource(UPDATE_FEED_URL));
                UpdateInfo update = validateUpdate(metadata);
                long remoteVersionCode = update.versionCode;
                long currentVersionCode = getCurrentVersionCode(context);
                SharedPreferences.Editor success = prefs.edit()
                        .putLong(KEY_UPDATE_LAST_SUCCESS, System.currentTimeMillis());
                if (remoteVersionCode > currentVersionCode
                        && update.minimumSdk <= Build.VERSION.SDK_INT) {
                    success.putString(KEY_UPDATE_PENDING_MANIFEST, metadata.toString());
                } else {
                    success.remove(KEY_UPDATE_PENDING_MANIFEST);
                }
                success.apply();
                if (remoteVersionCode <= currentVersionCode) {
                    synchronized (TgWsProxyBootstrap.class) { pendingUpdate = null; }
                    if (manual) message(context, "Telegram-WSP is up to date.");
                } else if (update.minimumSdk > Build.VERSION.SDK_INT) {
                    if (manual) message(context, update.tag + " requires Android API " + update.minimumSdk);
                } else {
                    synchronized (TgWsProxyBootstrap.class) {
                        pendingUpdate = update;
                        if (manual) promptedVersionCode = -1L;
                    }
                    postToMain(() -> {
                        if (LaunchActivity.instance != null) {
                            offerPendingUpdate(LaunchActivity.instance);
                        } else if (manual) {
                            message(context, "Update found. Open Telegram to see the update prompt.");
                        }
                    });
                }
            } catch (Throwable error) {
                FileLog.e("Telegram-WSP update check failed: " + error);
                if (manual) {
                    message(context, "Update check failed. Retry or open GitHub Releases.");
                }
            } finally {
                synchronized (TgWsProxyBootstrap.class) { checking = false; }
            }
        }, "TelegramWSPUpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private static UpdateInfo validateUpdate(JSONObject metadata) throws Exception {
        String version = metadata.getString("versionName");
        String tag = metadata.getString("tag");
        int revision = metadata.getInt("revision");
        long build = metadata.getLong("telegramBuild");
        long code = metadata.getLong("versionCode");
        int minSdk = metadata.getInt("minimumSdk");
        String apk = metadata.getString("apk");
        String hash = metadata.getString("apkSha256").toLowerCase(java.util.Locale.US);
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || revision < 1 || revision > 99
                || !tag.equals("v" + version + "-wsp." + revision)
                || build <= 0L || build > 2_000_000L
                || code != build * 1000L + revision * 10L + 9L
                || minSdk < 21 || minSdk > 100
                || !"org.telegram.messenger.web".equals(metadata.getString("packageId"))
                || !apk.equals(UPDATE_APK_URL_PREFIX + tag + "/Telegram-WSP-release.apk")
                || !hash.matches("[0-9a-f]{64}")) {
            throw new SecurityException("Invalid stable Telegram-WSP update manifest");
        }
        return new UpdateInfo(code, version, tag, apk, hash, minSdk);
    }

    private static void offerPendingUpdate(Activity activity) {
        if (!(activity instanceof LaunchActivity) || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        final UpdateInfo update;
        synchronized (TgWsProxyBootstrap.class) {
            update = pendingUpdate;
            if (update == null || promptedVersionCode == update.versionCode) return;
            promptedVersionCode = update.versionCode;
        }
        try {
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.AppUpdate))
                    .setMessage("Telegram-WSP " + update.tag
                            + " is available. Android will request installation after APK verification.")
                    .setPositiveButton(LocaleController.getString(R.string.AppUpdateNow),
                            (dialog, which) -> downloadAndInstallInBackground(activity.getApplicationContext(), update))
                    .setNeutralButton("Release notes", (dialog, which) -> openReleaseNotes(activity, update))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        } catch (Throwable error) {
            synchronized (TgWsProxyBootstrap.class) {
                if (promptedVersionCode == update.versionCode) promptedVersionCode = -1L;
            }
            FileLog.e("Telegram-WSP could not show update prompt: " + error);
        }
    }

    private static void openReleaseNotes(Context context, UpdateInfo update) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL + "/tag/" + update.tag));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            FileLog.e("Telegram-WSP could not open release notes: " + error);
            message(context, "Could not open release notes.");
        }
    }

    private static String readSmallHttpsResource(String url) throws Exception {
        HttpURLConnection connection = openHttpsConnection(url);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Update feed HTTP status " + status);
            }

            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > UPDATE_METADATA_LIMIT_BYTES) {
                        throw new IllegalStateException("Update metadata is too large");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openHttpsConnection(String url) throws Exception {
        URL current = new URL(url);
        // Only GitHub release paths and GitHub's release asset CDN can serve updater bytes.
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())
                    || current.getUserInfo() != null
                    || (current.getPort() != -1 && current.getPort() != 443)) {
                throw new SecurityException("Update URL must use HTTPS on port 443");
            }
            String host = current.getHost().toLowerCase(java.util.Locale.US);
            if (!(("github.com".equals(host)
                    && current.getPath().startsWith("/Regstar2/telegram-wsp/releases/"))
                    || "release-assets.githubusercontent.com".equals(host))) {
                throw new SecurityException("Update redirect leads to an untrusted host/path");
            }
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "Telegram-WSP Android Updater");
            int response = connection.getResponseCode();
            if (response != 301 && response != 302 && response != 303
                    && response != 307 && response != 308) {
                return connection;
            }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.isEmpty()) {
                throw new SecurityException("Update redirect has no Location");
            }
            current = new URL(current, location);
        }
        throw new SecurityException("Too many update redirects");
    }

    private static long getCurrentVersionCode(Context context) throws Exception {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                ApplicationLoader.getApplicationId(),
                0
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    private static void downloadAndInstallInBackground(Context appContext, UpdateInfo update) {
        message(appContext, "Telegram-WSP: downloading update...");

        Thread downloadThread = new Thread(() -> {
            try {
                File apk = getUpdateApkFile(appContext, update.versionCode);
                if (!isVerifiedUpdateApk(appContext, apk, update)) {
                    downloadUpdateApk(update.url, apk);
                }

                if (!isVerifiedUpdateApk(appContext, apk, update)) {
                    throw new SecurityException("Downloaded update APK did not pass verification");
                }

                Handler handler = ApplicationLoader.applicationHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                }
                handler.post(() -> {
                    if (isVerifiedUpdateApk(appContext, apk, update)) {
                        openUpdateInstaller(appContext, apk);
                    } else {
                        message(appContext, "Update verification failed; installation cancelled.");
                    }
                });
            } catch (Throwable error) {
                FileLog.e("Telegram-WSP update download failed: " + error);
                Handler handler = ApplicationLoader.applicationHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                }
                handler.post(() -> Toast.makeText(
                        appContext,
                        "Telegram-WSP update failed. Try again later.",
                        Toast.LENGTH_LONG
                ).show());
            }
        }, "TelegramWSPUpdateDownload");
        downloadThread.start();
    }

    private static File getUpdateApkFile(Context context, long versionCode) {
        File directory = new File(context.getFilesDir(), "cache");
        if (!directory.exists() && !directory.mkdirs()) {
            FileLog.e("Telegram-WSP updater could not create " + directory);
        }

        File[] oldFiles = directory.listFiles((dir, name) ->
                name.startsWith("Telegram-WSP-update-") && name.endsWith(".apk"));
        if (oldFiles != null) {
            String keep = "Telegram-WSP-update-" + versionCode + ".apk";
            for (File oldFile : oldFiles) {
                if (!keep.equals(oldFile.getName())) {
                    oldFile.delete();
                }
            }
        }

        return new File(directory, "Telegram-WSP-update-" + versionCode + ".apk");
    }

    private static void downloadUpdateApk(String url, File destination) throws Exception {
        File partial = new File(destination.getAbsolutePath() + ".part");
        if (partial.exists()) {
            partial.delete();
        }

        HttpURLConnection connection = openHttpsConnection(url);
        connection.setReadTimeout(120_000);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Update APK HTTP status " + status);
            }

            long declaredLength = connection.getContentLength();
            if (declaredLength > UPDATE_APK_LIMIT_BYTES) {
                throw new IllegalStateException("Update APK exceeds size limit");
            }

            long total = 0L;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > UPDATE_APK_LIMIT_BYTES) {
                        throw new IllegalStateException("Update APK exceeds size limit");
                    }
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }

            if (total <= 0L || (declaredLength >= 0L && total != declaredLength)) {
                throw new IllegalStateException("Downloaded update APK is empty or truncated");
            }

            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Could not replace existing update APK");
            }
            if (!partial.renameTo(destination)) {
                throw new IllegalStateException("Could not finalize update APK");
            }
        } finally {
            connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    private static boolean isVerifiedUpdateApk(
            Context context,
            File apk,
            UpdateInfo update
    ) {
        try {
            if (!apk.isFile()) {
                return false;
            }
            if (apk.length() <= 0L || apk.length() > UPDATE_APK_LIMIT_BYTES
                    || !update.sha256.equalsIgnoreCase(sha256(apk))) {
                return false;
            }
            PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            long version = candidate == null ? -1L
                    : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? candidate.getLongVersionCode() : candidate.versionCode);
            return version == update.versionCode && version > getCurrentVersionCode(context)
                    && hasSameSigningCertificate(context, apk);
        } catch (Throwable error) {
            FileLog.e("Telegram-WSP update verification failed: " + error);
            return false;
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        char[] chars = new char[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            int value = hash[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }

    private static boolean hasSameSigningCertificate(Context context, File apk) throws Exception {
        PackageManager packageManager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo installed = packageManager.getPackageInfo(
                ApplicationLoader.getApplicationId(),
                flags
        );
        PackageInfo archive = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || !ApplicationLoader.getApplicationId().equals(archive.packageName)) {
            return false;
        }

        Signature[] installedSignatures;
        Signature[] archiveSignatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (installed.signingInfo == null || archive.signingInfo == null) {
                return false;
            }
            installedSignatures = installed.signingInfo.getApkContentsSigners();
            archiveSignatures = archive.signingInfo.getApkContentsSigners();
        } else {
            installedSignatures = installed.signatures;
            archiveSignatures = archive.signatures;
        }
        return signaturesEqual(installedSignatures, archiveSignatures);
    }

    private static boolean signaturesEqual(Signature[] first, Signature[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }

        Set<String> firstSet = new HashSet<>();
        Set<String> secondSet = new HashSet<>();
        for (Signature signature : first) {
            firstSet.add(signature.toCharsString());
        }
        for (Signature signature : second) {
            secondSet.add(signature.toCharsString());
        }
        return firstSet.equals(secondSet);
    }

    private static void openUpdateInstaller(Context context, File apk) {
        try {
            PackageManager packageManager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !packageManager.canRequestPackageInstalls()) {
                Intent settings = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ApplicationLoader.getApplicationId())
                );
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settings);
                Toast.makeText(
                        context,
                        "Allow installs from Telegram-WSP, then choose Update again.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                        context,
                        ApplicationLoader.getApplicationId() + ".provider",
                        apk
                );
            } else {
                uri = Uri.fromFile(apk);
            }

            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(install);
        } catch (Throwable error) {
            FileLog.e("Telegram-WSP could not open APK installer: " + error);
            Toast.makeText(
                    context,
                    "Telegram-WSP could not open the Android installer.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private static void disableManagedProxy(Context context, SharedPreferences integrationPrefs) {
        if (!integrationPrefs.getBoolean(KEY_MANAGED_PROXY, false)) {
            return;
        }

        String secret = integrationPrefs.getString(KEY_SECRET, "");
        SharedPreferences telegramPrefs = context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        String address = telegramPrefs.getString("proxy_ip", "");
        int port = telegramPrefs.getInt("proxy_port", 0);
        String telegramSecret = telegramPrefs.getString("proxy_secret", "");

        if (HOST.equals(address) && PORT == port && secret != null && secret.equals(telegramSecret)) {
            telegramPrefs.edit().putBoolean("proxy_enabled", false).commit();
            applyTelegramProxy(false, "");
        }
        integrationPrefs.edit().putBoolean(KEY_MANAGED_PROXY, false).apply();
    }

    private static void applyTelegramProxy(boolean enabled, String secret) {
        ProxySettings settings = ProxySettings.builder()
                .setType(ProxySettings.Type.MTPROTO)
                .setAddress(HOST)
                .setPort(PORT)
                .setSecret(secret)
                .build();
        Runnable apply = () -> ConnectionsManager.setProxySettings(enabled, settings);
        Handler handler = ApplicationLoader.applicationHandler;
        if (handler != null) {
            handler.post(apply);
        } else {
            new Handler(Looper.getMainLooper()).post(apply);
        }
    }

    private static String getOrCreateSecret(SharedPreferences preferences) {
        String current = preferences.getString(KEY_SECRET, null);
        if (current != null && current.matches("[0-9a-fA-F]{32}")) {
            return current.toLowerCase(java.util.Locale.US);
        }

        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        char[] chars = new char[32];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        String generated = new String(chars);
        preferences.edit().putString(KEY_SECRET, generated).commit();
        return generated;
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    private static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String tag;
        final String url;
        final String sha256;
        final int minimumSdk;

        UpdateInfo(long versionCode, String versionName, String tag, String url, String sha256, int minimumSdk) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.tag = tag;
            this.url = url;
            this.sha256 = sha256;
            this.minimumSdk = minimumSdk;
        }
    }
}
