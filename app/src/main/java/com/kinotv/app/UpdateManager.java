package com.kinotv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateManager {
    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/freemoviesforfreepeople/sidi-i-smotri-updates/main/update.json";
    private static final String PREFS = "sidi_online_update";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_PENDING_APK = "pending_apk";
    private static final String KEY_PENDING_INSTALL_ATTEMPTS = "pending_install_attempts"; // legacy cleanup
    private static final String KEY_PENDING_INSTALL_AT = "pending_install_at"; // legacy cleanup
    private static final String KEY_WAITING_INSTALL_PERMISSION = "waiting_install_permission";
    private static final String KEY_AVAILABLE_JSON = "available_update_json";
    private static final long AUTO_CHECK_INTERVAL = 6L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile boolean busy;
    private static volatile UpdateInfo availableUpdate;
    private static volatile boolean installPromptShowing;

    private UpdateManager() {
    }

    interface CheckCallback {
        void onResult(boolean available);
    }

    static void check(Activity activity, boolean manual) {
        check(activity, manual, null);
    }

    static void check(Activity activity, boolean manual, CheckCallback callback) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL) {
            if (callback != null) callback.onResult(hasAvailableUpdate(activity));
            return;
        }
        if (busy) {
            if (manual) toast(activity, "Проверка уже выполняется");
            if (callback != null) callback.onResult(hasAvailableUpdate(activity));
            return;
        }
        busy = true;
        if (manual) toast(activity, "Проверяю обновления");
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo info = readUpdateInfo();
                long installed = installedVersionCode(activity);
                boolean available = info.versionCode > installed;
                SharedPreferences.Editor editor = prefs.edit()
                        .putLong(KEY_LAST_CHECK, System.currentTimeMillis());
                if (available) {
                    availableUpdate = info;
                    editor.putString(KEY_AVAILABLE_JSON, info.toJson().toString());
                } else {
                    availableUpdate = null;
                    editor.remove(KEY_AVAILABLE_JSON);
                }
                editor.apply();
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (manual) {
                        toast(activity, available
                                ? "Доступна версия " + info.versionName
                                : "Установлена последняя версия");
                    }
                    if (callback != null) callback.onResult(available);
                });
            } catch (Throwable error) {
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (manual) showError(activity, "Не удалось проверить обновление", error);
                    if (callback != null) callback.onResult(hasAvailableUpdate(activity));
                });
            }
        });
    }

    static boolean hasAvailableUpdate(Context context) {
        return getAvailableUpdate(context) != null;
    }

    static void startAvailableUpdate(Activity activity) {
        File pending = getPendingApk(activity);
        if (pending != null) {
            showInstallReadyDialog(activity, pending);
            return;
        }
        UpdateInfo info = getAvailableUpdate(activity);
        if (info == null) {
            check(activity, true);
            return;
        }
        showAvailableDialog(activity, info);
    }

    private static UpdateInfo getAvailableUpdate(Context context) {
        if (context == null) return null;
        try {
            UpdateInfo cached = availableUpdate;
            long installed = installedVersionCode(context);
            if (cached != null && cached.versionCode > installed) return cached;
            String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_AVAILABLE_JSON, "");
            if (json == null || json.isEmpty()) return null;
            UpdateInfo restored = UpdateInfo.fromJson(new JSONObject(json));
            if (restored.versionCode <= installed) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove(KEY_AVAILABLE_JSON).apply();
                availableUpdate = null;
                return null;
            }
            availableUpdate = restored;
            return restored;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void resumePendingInstall(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File pending = getPendingApk(activity);
        if (pending == null) return;

        // После возврата из системного экрана разрешения показываем понятную
        // кнопку установки. Сам установщик никогда не перезапускается автоматически.
        boolean waitingPermission = prefs.getBoolean(KEY_WAITING_INSTALL_PERMISSION, false);
        if (waitingPermission && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls())) {
            prefs.edit().remove(KEY_WAITING_INSTALL_PERMISSION).apply();
            activity.getWindow().getDecorView().post(() -> showInstallReadyDialog(activity, pending));
        }
    }

    private static File getPendingApk(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_PENDING_APK, "");
        if (path == null || path.isEmpty()) return null;
        File apk = new File(path);
        if (!apk.isFile()) {
            clearPendingInstall(prefs);
            return null;
        }
        try {
            validateDownloadedApk(context, apk);
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(
                    apk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            long archiveCode = archive == null ? -1L
                    : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? archive.getLongVersionCode() : archive.versionCode);
            if (archiveCode <= installedVersionCode(context)) {
                clearPendingInstall(prefs);
                return null;
            }
            return apk;
        } catch (Throwable invalid) {
            clearPendingInstall(prefs);
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            return null;
        }
    }

    private static void clearPendingInstall(SharedPreferences prefs) {
        prefs.edit()
                .remove(KEY_PENDING_APK)
                .remove(KEY_PENDING_INSTALL_ATTEMPTS)
                .remove(KEY_PENDING_INSTALL_AT)
                .remove(KEY_WAITING_INSTALL_PERMISSION)
                .apply();
    }

    private static UpdateInfo readUpdateInfo() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(UPDATE_URL + "?t=" + System.currentTimeMillis());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Sidi-i-smotri-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            String json = readText(connection.getInputStream(), 256 * 1024);
            return UpdateInfo.fromJson(new JSONObject(json));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void showAvailableDialog(Activity activity, UpdateInfo info) {
        String message = info.notes.isEmpty()
                ? "Доступна версия " + info.versionName
                : "Доступна версия " + info.versionName + "\n\n" + info.notes;
        new AlertDialog.Builder(activity)
                .setTitle("Обновление")
                .setMessage(message)
                .setNegativeButton("Позже", null)
                .setPositiveButton("Обновить", (dialog, which) -> download(activity, info))
                .show();
    }

    private static void download(Activity activity, UpdateInfo info) {
        if (busy) return;
        busy = true;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 22);
        box.setPadding(pad, dp(activity, 10), pad, dp(activity, 10));
        TextView status = new TextView(activity);
        status.setText("0%");
        status.setTextSize(14);
        box.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ProgressBar progressBar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 24));
        progressParams.topMargin = dp(activity, 8);
        box.addView(progressBar, progressParams);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("Скачивание обновления")
                .setView(box)
                .setCancelable(false)
                .create();
        progress.show();

        EXECUTOR.execute(() -> {
            File output = new File(activity.getCacheDir(), "updates/sidi-update.apk");
            try {
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Не удалось создать папку обновления");
                }
                downloadFile(info.apkUrl, output, (downloaded, total) -> {
                    int percent = total > 0 ? (int) Math.min(100L, downloaded * 100L / total) : 0;
                    activity.runOnUiThread(() -> {
                        progressBar.setIndeterminate(total <= 0);
                        if (total > 0) progressBar.setProgress(percent);
                        status.setText(total > 0 ? percent + "%" : "Скачивание…");
                    });
                });
                String actual = sha256(output);
                if (!actual.equalsIgnoreCase(info.sha256)) {
                    output.delete();
                    throw new SecurityException("SHA-256 не совпадает");
                }
                // SHA защищает от повреждения, а подпись APK — от подмены
                // update.json и релиза в скомпрометированном репозитории.
                validateDownloadedApk(activity, output);
                activity.runOnUiThread(() -> {
                    busy = false;
                    progress.dismiss();
                    requestInstall(activity, output);
                });
            } catch (Throwable error) {
                output.delete();
                activity.runOnUiThread(() -> {
                    busy = false;
                    progress.dismiss();
                    showError(activity, "Не удалось скачать обновление", error);
                });
            }
        });
    }

    private static void requestInstall(Activity activity, File apk) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_APK, apk.getAbsolutePath())
                .remove(KEY_PENDING_INSTALL_ATTEMPTS)
                .remove(KEY_PENDING_INSTALL_AT)
                .remove(KEY_WAITING_INSTALL_PERMISSION)
                .apply();
        showInstallReadyDialog(activity, apk);
    }

    private static void showInstallReadyDialog(Activity activity, File apk) {
        if (activity == null || activity.isFinishing() || apk == null || !apk.isFile()
                || installPromptShowing) return;
        installPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Обновление скачано")
                .setMessage("APK уже проверен и готов к установке. Повторно скачивать его не нужно.")
                .setNegativeButton("Позже", null)
                .setPositiveButton("Установить", (d, which) -> beginInstall(activity, apk))
                .create();
        dialog.setOnDismissListener(d -> installPromptShowing = false);
        dialog.show();
    }

    private static void beginInstall(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_WAITING_INSTALL_PERMISSION, true)
                    .apply();
            new AlertDialog.Builder(activity)
                    .setTitle("Разрешение на установку")
                    .setMessage("Разреши установку приложений и вернись назад. APK останется скачанным.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    })
                    .show();
            return;
        }
        installApk(activity, apk);
    }

    private static void installApk(Activity activity, File apk) {
        try {
            validateDownloadedApk(activity, apk);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                throw new IllegalStateException("Системный установщик APK не найден");
            }
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_WAITING_INSTALL_PERMISSION)
                    .apply();
            activity.startActivity(intent);
        } catch (Throwable error) {
            showError(activity, "Не удалось запустить установку", error);
        }
    }

    private static void validateDownloadedApk(Context context, File apk) throws Exception {
        if (apk == null || !apk.isFile() || apk.length() < 1024L) {
            throw new IllegalStateException("Скачанный APK пуст или повреждён");
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(apk))) {
            int p = input.read();
            int k = input.read();
            if (p != 'P' || k != 'K') {
                throw new IllegalStateException("Сервер вернул не APK-файл");
            }
        }

        int archiveFlags = PackageManager.GET_ACTIVITIES;
        int installedFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveFlags |= PackageManager.GET_SIGNING_CERTIFICATES;
            installedFlags |= PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            //noinspection deprecation
            archiveFlags |= PackageManager.GET_SIGNATURES;
            //noinspection deprecation
            installedFlags |= PackageManager.GET_SIGNATURES;
        }
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), archiveFlags);
        if (archive == null) {
            throw new IllegalStateException("Android не распознал скачанный APK");
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException("APK относится к другому приложению");
        }

        PackageInfo installed = context.getPackageManager().getPackageInfo(
                context.getPackageName(), installedFlags);
        Set<String> installedSigners = signerDigests(installed, true);
        Set<String> archiveSigners = signerDigests(archive, false);
        if (installedSigners.isEmpty() || archiveSigners.isEmpty()) {
            throw new SecurityException("Не удалось проверить подпись APK");
        }
        boolean trusted = false;
        for (String signer : archiveSigners) {
            if (installedSigners.contains(signer)) {
                trusted = true;
                break;
            }
        }
        if (!trusted) {
            throw new SecurityException("APK подписан другим ключом");
        }
    }

    private static Set<String> signerDigests(PackageInfo info, boolean includeHistory)
            throws Exception {
        Set<String> result = new HashSet<>();
        if (info == null) return result;
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signing = info.signingInfo;
            if (signing != null) {
                signatures = includeHistory && signing.hasPastSigningCertificates()
                        ? signing.getSigningCertificateHistory()
                        : signing.getApkContentsSigners();
            }
        } else {
            //noinspection deprecation
            signatures = info.signatures;
        }
        if (signatures == null) return result;
        for (Signature signature : signatures) {
            if (signature == null) continue;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder(64);
            for (byte b : value) hex.append(String.format(Locale.US, "%02x", b));
            result.add(hex.toString());
        }
        return result;
    }

    private static void downloadFile(String rawUrl, File output, ProgressCallback callback)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*");
            connection.setRequestProperty("User-Agent", "Sidi-i-smotri-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            long total = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong() : connection.getContentLength();
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0L;
                int read;
                long lastUpdate = 0L;
                while ((read = input.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= 150L) {
                        callback.onProgress(downloaded, total);
                        lastUpdate = now;
                    }
                }
                out.flush();
                callback.onProgress(downloaded, total);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.US, "%02x", value));
        return result.toString();
    }

    private static String readText(InputStream input, int maxBytes) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("Ответ слишком большой");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static long installedVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
        return info.versionCode;
    }

    private static void showError(Activity activity, String title, Throwable error) {
        if (activity == null || activity.isFinishing()) return;
        String message = error == null || error.getMessage() == null
                ? "Неизвестная ошибка" : error.getMessage();
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private interface ProgressCallback {
        void onProgress(long downloaded, long total);
    }

    private static final class UpdateInfo {
        long versionCode;
        String versionName;
        String apkUrl;
        String sha256;
        String notes;

        static UpdateInfo fromJson(JSONObject root) throws Exception {
            UpdateInfo info = new UpdateInfo();
            info.versionCode = root.getLong("versionCode");
            info.versionName = root.optString("versionName", String.valueOf(info.versionCode));
            info.apkUrl = root.getString("apkUrl").trim();
            info.sha256 = root.getString("sha256").trim().toLowerCase(Locale.US);
            info.notes = root.optString("notes", "").trim();
            if (!info.apkUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Некорректная ссылка APK");
            }
            if (!info.sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Некорректный SHA-256");
            }
            return info;
        }

        JSONObject toJson() throws Exception {
            JSONObject root = new JSONObject();
            root.put("versionCode", versionCode);
            root.put("versionName", versionName);
            root.put("apkUrl", apkUrl);
            root.put("sha256", sha256);
            root.put("notes", notes);
            return root;
        }
    }
}
