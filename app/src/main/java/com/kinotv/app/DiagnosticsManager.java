package com.kinotv.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticsManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long COMMAND_TIMEOUT_MS = 30_000L;
    private static final long SERVER_LIFETIME_MS = 10 * 60 * 1000L;
    private static final Pattern PLAYER_LOG_PATTERN = Pattern.compile(
            "(?i)(SidiInput|SidiMedia|SidiPlayer|SidiControls|SidiAdBlock|SidiAdObserve|SidiAdPicker|SidiVideoAd|InputDispatcher|InputReader|WindowManager|interceptKey|kinotv|chromium|console|webview|kinobox|alloha|iframe|video|mediacodec|codec|av01|avc1|h264|hevc|m3u8|mp4|vast|vpaid|advert|redirect|window\\.open|404|sspoisk|villybizy|fbphdplay|stravers|theatre|vkvideo|cors|net::|ssl|error|exception|fatal)"
    );
    private static final Pattern INPUT_LOG_PATTERN = Pattern.compile(
            "(?i)(SidiInput|SidiMedia|InputDispatcher|InputReader|WindowManager.*(key|focus|input)|interceptKey|mInTouchMode|touchMode|focusedApp|mCurrentFocus|mFocusedApp)"
    );

    interface Callback {
        void onSuccess(Result result);
        void onError(String message, Throwable error);
    }

    static final class Result {
        final File zipFile;
        final String downloadUrl;
        final boolean fullSystemLog;
        final LocalFileServer server;

        Result(File zipFile, String downloadUrl, boolean fullSystemLog, LocalFileServer server) {
            this.zipFile = zipFile;
            this.downloadUrl = downloadUrl;
            this.fullSystemLog = fullSystemLog;
            this.server = server;
        }

        void stop() {
            if (server != null) server.stop();
        }
    }

    private DiagnosticsManager() {
    }

    static void collectAndServe(Activity activity, Callback callback) {
        collectAndServe(activity, "", callback);
    }

    static void collectAndServe(Activity activity, String context, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                Result result = collect(activity, context);
                activity.runOnUiThread(() -> callback.onSuccess(result));
            } catch (Throwable error) {
                activity.runOnUiThread(() -> callback.onError("Не удалось собрать диагностику", error));
            }
        });
    }

    private static Result collect(Activity activity, String context) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File base = new File(activity.getCacheDir(), "diagnostics");
        deleteOldDiagnostics(base, 24 * 60 * 60 * 1000L);
        File dir = new File(base, "kinotv-diagnostics-" + stamp);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cannot create diagnostics directory");

        boolean root = hasRootAccess(activity, dir);
        writeSummary(activity, new File(dir, "00-summary.txt"), root, stamp);
        if (context != null && !context.trim().isEmpty()) {
            try (FileWriter writer = new FileWriter(new File(dir, "00-player-context.txt"))) {
                writer.write(context.trim());
                writer.write("\n");
            }
        }

        File fullLog = new File(dir, "01-full-log.txt");
        if (root) {
            runCommandToFile("logcat -d -v threadtime", fullLog, true, COMMAND_TIMEOUT_MS);
        } else {
            runCommandToFile("logcat -d -v threadtime --pid=" + Process.myPid(), fullLog, false, COMMAND_TIMEOUT_MS);
        }
        createFilteredLog(fullLog, new File(dir, "02-player-filtered-log.txt"), PLAYER_LOG_PATTERN);
        createFilteredLog(fullLog, new File(dir, "02a-input-filtered-log.txt"), INPUT_LOG_PATTERN);

        captureScreenshot(activity, new File(dir, "03-screen.png"), root);
        runDiagnosticCommand("dumpsys package com.kinotv.app", new File(dir, "04-app-package.txt"), root);
        runDiagnosticCommand("dumpsys webviewupdate", new File(dir, "05-webview.txt"), root);
        runDiagnosticCommand("dumpsys activity activities", new File(dir, "06-activities.txt"), root);
        runDiagnosticCommand("dumpsys activity top", new File(dir, "07-activity-top.txt"), root);
        runDiagnosticCommand("dumpsys window windows", new File(dir, "08-windows.txt"), root);
        runDiagnosticCommand("dumpsys gfxinfo com.kinotv.app", new File(dir, "09-gfxinfo.txt"), root);
        runDiagnosticCommand("dumpsys media.codec", new File(dir, "10-media-codec.txt"), root);
        runDiagnosticCommand("dumpsys SurfaceFlinger --list", new File(dir, "11-surfaces.txt"), root);
        runDiagnosticCommand("settings get global http_proxy", new File(dir, "12-http-proxy.txt"), root);
        runDiagnosticCommand("pm list packages | grep -iE 'webview|chrome|browser|firefox|yandex|opera|edge|via'", new File(dir, "13-browser-packages.txt"), root);
        listFiles(activity.getFilesDir(), new File(dir, "14-app-files.txt"));

        File zip = new File(base, dir.getName() + ".zip");
        zipDirectory(dir, zip);

        Inet4Address localAddress = findLocalIpv4Address();
        if (localAddress == null) {
            throw new IOException("Не найден локальный IPv4-адрес. Подключите проектор к Wi-Fi или Ethernet.");
        }
        String ip = localAddress.getHostAddress();
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalFileServer server = new LocalFileServer(zip, token, localAddress);
        server.start();
        String url = "http://" + ip + ":" + server.getPort() + "/diagnostics/" + token + "/";
        return new Result(zip, url, root, server);
    }

    private static boolean hasRootAccess(Activity activity, File dir) {
        File result = new File(dir, "root-check.txt");
        try {
            boolean finished = runCommandToFile("id", result, true, 5_000L);
            if (!finished || !result.isFile()) return false;
            String value = readSmallText(result, 4096);
            return value.contains("uid=0");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void runDiagnosticCommand(String command, File target, boolean root) {
        try {
            boolean ok = runCommandToFile(command, target, root, COMMAND_TIMEOUT_MS);
            if (!ok) appendText(target, "\n[COMMAND TIMEOUT]\n");
        } catch (Throwable error) {
            try {
                appendText(target, "[COMMAND FAILED] " + error + "\n");
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean runCommandToFile(String command, File target, boolean root, long timeoutMs) throws Exception {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        String quotedTarget = shellQuote(target.getAbsolutePath());
        String shellCommand = command + " > " + quotedTarget + " 2>&1; chmod 0644 " + quotedTarget;
        java.lang.Process process = root
                ? new ProcessBuilder("su", "-c", shellCommand).start()
                : new ProcessBuilder("sh", "-c", shellCommand).start();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException running) {
                Thread.sleep(100L);
            }
        }
        process.destroy();
        return false;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void captureScreenshot(Activity activity, File target, boolean root) {
        if (root) {
            try {
                String quotedTarget = shellQuote(target.getAbsolutePath());
                String command = "screencap -p > " + quotedTarget + "; chmod 0644 " + quotedTarget;
                java.lang.Process process = new ProcessBuilder("su", "-c", command).start();
                long deadline = System.currentTimeMillis() + 10_000L;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        process.exitValue();
                        if (target.isFile() && target.length() > 1000) return;
                        break;
                    } catch (IllegalThreadStateException running) {
                        Thread.sleep(100L);
                    }
                }
                process.destroy();
            } catch (Throwable ignored) {
            }
        }
        captureOwnWindow(activity, target);
    }

    private static void captureOwnWindow(Activity activity, File target) {
        CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            Bitmap bitmap = null;
            try {
                View root = activity.getWindow().getDecorView().getRootView();
                int width = Math.max(1, root.getWidth());
                int height = Math.max(1, root.getHeight());
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                root.draw(canvas);
                try (FileOutputStream output = new FileOutputStream(target)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
            } catch (Throwable error) {
                try {
                    appendText(target, "Screenshot failed: " + error + "\n");
                } catch (Throwable ignored) {
                }
            } finally {
                if (bitmap != null) bitmap.recycle();
                latch.countDown();
            }
        });
        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeSummary(Activity activity, File target, boolean root, String stamp) throws Exception {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        try (PrintWriter writer = new PrintWriter(new FileWriter(target))) {
            writer.println("DATE=" + stamp);
            writer.println("PACKAGE=com.kinotv.app");
            writer.println("VERSION_NAME=" + getVersionName(activity));
            writer.println("VERSION_CODE=" + getVersionCode(activity));
            writer.println("ROOT_FULL_LOG=" + root);
            writer.println("MANUFACTURER=" + Build.MANUFACTURER);
            writer.println("MODEL=" + Build.MODEL);
            writer.println("DEVICE=" + Build.DEVICE);
            writer.println("ANDROID=" + Build.VERSION.RELEASE);
            writer.println("SDK=" + Build.VERSION.SDK_INT);
            writer.println("ABI=" + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown"));
            writer.println("SCREEN_PX=" + metrics.widthPixels + "x" + metrics.heightPixels);
            writer.println("DENSITY_DPI=" + metrics.densityDpi);
            writer.println("LOCAL_IP=" + findLocalIpv4());
            writer.println();
            writer.println("Root is required for full system logcat/dumpsys. Without root, some files contain permission errors or only app-owned logs.");
        }
    }

    private static String getVersionName(Activity activity) {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static long getVersionCode(Activity activity) {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void createFilteredLog(File source, File target, Pattern pattern) throws IOException {
        if (!source.isFile()) {
            appendText(target, "Source log is missing\n");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new FileWriter(target))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).find()) writer.println(line);
            }
        }
    }

    private static void listFiles(File root, File target) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(target))) {
            listFilesRecursive(root, root, writer, 0);
        }
    }

    private static void listFilesRecursive(File base, File file, PrintWriter writer, int depth) {
        if (file == null || depth > 8) return;
        if (file.isFile()) {
            String relative = file.getAbsolutePath().startsWith(base.getAbsolutePath())
                    ? file.getAbsolutePath().substring(base.getAbsolutePath().length()) : file.getAbsolutePath();
            writer.println(relative + "\t" + file.length() + " bytes");
            return;
        }
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) listFilesRecursive(base, child, writer, depth + 1);
    }

    private static void zipDirectory(File directory, File target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))) {
            zip.setLevel(6);
            addToZip(directory, directory, zip);
        }
    }

    private static void addToZip(File base, File file, ZipOutputStream zip) throws IOException {
        File[] children = file.listFiles();
        if (children == null) return;
        byte[] buffer = new byte[32 * 1024];
        for (File child : children) {
            if (child.isDirectory()) {
                addToZip(base, child, zip);
                continue;
            }
            String name = child.getAbsolutePath().substring(base.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
            zip.putNextEntry(new ZipEntry(name));
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(child))) {
                int read;
                while ((read = input.read(buffer)) >= 0) zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    private static Inet4Address findLocalIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String host = address.getHostAddress();
                        if (host != null && !host.startsWith("169.254.")) {
                            return (Inet4Address) address;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String findLocalIpv4() {
        Inet4Address address = findLocalIpv4Address();
        return address == null ? null : address.getHostAddress();
    }

    private static void deleteOldDiagnostics(File base, long maxAgeMs) {
        if (!base.exists()) return;
        File[] files = base.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        for (File file : files) {
            if (file.lastModified() < cutoff) deleteRecursively(file);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String readSmallText(File file, int limit) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int total = 0;
            int read;
            while (total < limit && (read = input.read(buffer, 0, Math.min(buffer.length, limit - total))) >= 0) {
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void appendText(File file, String text) throws IOException {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(text);
        }
    }

    static final class LocalFileServer {
        private final File file;
        private final String token;
        private final InetAddress bindAddress;
        private volatile boolean running;
        private ServerSocket serverSocket;
        private Thread thread;

        LocalFileServer(File file, String token, InetAddress bindAddress) {
            this.file = file;
            this.token = token;
            this.bindAddress = bindAddress;
        }

        synchronized void start() throws IOException {
            if (running) return;
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(bindAddress, 0), 8);
            serverSocket.setSoTimeout(1000);
            running = true;
            thread = new Thread(this::serveLoop, "KinotvDiagnosticsServer");
            thread.start();
        }

        int getPort() {
            return serverSocket == null ? -1 : serverSocket.getLocalPort();
        }

        synchronized void stop() {
            running = false;
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }
            serverSocket = null;
        }

        private void serveLoop() {
            long deadline = System.currentTimeMillis() + SERVER_LIFETIME_MS;
            while (running && System.currentTimeMillis() < deadline) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (SocketTimeoutException ignored) {
                } catch (Throwable error) {
                    if (running) android.util.Log.w("SidiDiagnostics", "Local server error", error);
                }
            }
            stop();
        }

        private void handle(Socket socket) {
            try (Socket client = socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                 OutputStream rawOutput = new BufferedOutputStream(client.getOutputStream())) {
                client.setSoTimeout(10_000);
                String requestLine = reader.readLine();
                if (requestLine == null) return;
                String[] parts = requestLine.split(" ");
                String method = parts.length > 0 ? parts[0] : "";
                String path = parts.length > 1 ? parts[1] : "";
                int queryIndex = path.indexOf('?');
                if (queryIndex >= 0) path = path.substring(0, queryIndex);

                String basePath = "/diagnostics/" + token + "/";
                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                    // Consume HTTP headers.
                }

                boolean head = "HEAD".equals(method);
                if (!("GET".equals(method) || head) || !path.startsWith(basePath)) {
                    writeResponse(rawOutput, "404 Not Found", "text/plain; charset=utf-8",
                            "Not found".getBytes(StandardCharsets.UTF_8), head, null);
                    return;
                }

                if (path.equals(basePath) || path.equals(basePath + "index.html")) {
                    byte[] page = buildLandingPage(basePath);
                    writeResponse(rawOutput, "200 OK", "text/html; charset=utf-8", page, head,
                            "X-Content-Type-Options: nosniff\r\n"
                                    + "Referrer-Policy: no-referrer\r\n"
                                    + "Content-Security-Policy: default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:;\r\n");
                    return;
                }

                if (path.equals(basePath + "download") || path.equals(basePath + file.getName())) {
                    writeZip(rawOutput, head);
                    return;
                }

                if (path.equals(basePath + "favicon.ico")) {
                    writeResponse(rawOutput, "204 No Content", "image/x-icon", new byte[0], head, null);
                    return;
                }

                writeResponse(rawOutput, "404 Not Found", "text/plain; charset=utf-8",
                        "Not found".getBytes(StandardCharsets.UTF_8), head, null);
            } catch (Throwable error) {
                android.util.Log.w("SidiDiagnostics", "Download failed", error);
            }
        }

        private void writeZip(OutputStream output, boolean head) throws IOException {
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/zip\r\n"
                    + "Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n"
                    + "Content-Length: " + file.length() + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "X-Content-Type-Options: nosniff\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.US_ASCII));
            if (!head) {
                try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                }
            }
            output.flush();
        }

        private byte[] buildLandingPage(String basePath) {
            String safeFileName = htmlEscape(file.getName());
            String jsFileName = jsEscape(file.getName());
            String html = "<!doctype html>"
                    + "<html lang=\"ru\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">"
                    + "<title>Сиди и смотри — диагностика</title>"
                    + "<style>"
                    + ":root{color-scheme:dark;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}"
                    + "*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#050b13;color:#f6f8fb;display:grid;place-items:center;padding:24px}"
                    + ".card{width:min(560px,100%);background:#0b1522;border:1px solid #243449;border-radius:22px;padding:28px;box-shadow:0 24px 80px rgba(0,0,0,.45)}"
                    + ".brand{font-size:14px;color:#52bfff;font-weight:700;letter-spacing:.08em;text-transform:uppercase}"
                    + "h1{font-size:28px;margin:10px 0 10px}p{color:#aebccc;line-height:1.5;margin:0 0 18px}"
                    + ".file{background:#07101b;border:1px solid #203148;border-radius:14px;padding:14px;margin:18px 0;overflow-wrap:anywhere;color:#dce8f7}"
                    + "button,a.button{width:100%;min-height:54px;border-radius:14px;border:0;font-size:17px;font-weight:700;display:flex;align-items:center;justify-content:center;text-decoration:none;cursor:pointer}"
                    + "button{background:linear-gradient(135deg,#08b7ff,#1769ff);color:white;box-shadow:0 10px 30px rgba(23,105,255,.28)}"
                    + "button:disabled{opacity:.55;cursor:wait}.secondary{margin-top:12px;background:#101d2d!important;color:#dce8f7!important;border:1px solid #2b415c!important;box-shadow:none!important}"
                    + ".status{min-height:44px;margin-top:16px;color:#9fb3c8;font-size:14px;line-height:1.45}.warn{font-size:12px;color:#7f91a5;margin-top:18px}"
                    + "</style></head><body><main class=\"card\">"
                    + "<div class=\"brand\">Сиди и смотри</div>"
                    + "<h1>Диагностика готова</h1>"
                    + "<p>Нажмите кнопку ниже. На совместимом Android-телефоне откроется системное меню отправки — выберите Telegram.</p>"
                    + "<div class=\"file\">" + safeFileName + "</div>"
                    + "<button id=\"share\" type=\"button\">Отправить ZIP в Telegram</button>"
                    + "<a class=\"button secondary\" href=\"" + basePath + "download\" download=\"" + safeFileName + "\">Скачать ZIP</a>"
                    + "<div id=\"status\" class=\"status\"></div>"
                    + "<div class=\"warn\">Архив может содержать технические URL и данные сеанса. Не публикуйте его в открытом доступе.</div>"
                    + "<script>"
                    + "const btn=document.getElementById('share'),status=document.getElementById('status');"
                    + "const fileName=\"" + jsFileName + "\",downloadUrl=\"" + basePath + "download\";let zipBlob=null;"
                    + "function fallbackDownload(){const a=document.createElement('a');a.href=downloadUrl;a.download=fileName;document.body.appendChild(a);a.click();a.remove();status.textContent='ZIP скачан. Откройте уведомление о загрузке, нажмите «Поделиться» и выберите Telegram.';}"
                    + "btn.disabled=true;status.textContent='Подготавливаю ZIP…';"
                    + "fetch(downloadUrl,{cache:'no-store'}).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.blob();}).then(b=>{zipBlob=b;btn.disabled=false;status.textContent='ZIP готов к отправке.';}).catch(()=>{btn.disabled=false;status.textContent='Не удалось подготовить ZIP заранее. Кнопка ниже скачает его обычным способом.';});"
                    + "btn.addEventListener('click',()=>{try{let f=null;if(zipBlob){try{f=new File([zipBlob],fileName,{type:'application/zip'});}catch(_e){}}"
                    + "if(f&&navigator.share&&(!navigator.canShare||navigator.canShare({files:[f]}))){navigator.share({title:'Диагностика «Сиди и смотри»',text:'Архив диагностики приложения',files:[f]}).then(()=>{status.textContent='Архив передан в системное меню.';}).catch(e=>{status.textContent=e&&e.name==='AbortError'?'Отправка отменена.':'Не удалось открыть меню отправки. ZIP можно скачать кнопкой ниже.';});}"
                    + "else if(navigator.share){navigator.share({title:'Диагностика «Сиди и смотри»',text:'Ссылка на ZIP действует 10 минут',url:location.href}).then(()=>{status.textContent='В Telegram можно отправить временную ссылку. Для самого ZIP используйте кнопку «Скачать ZIP».';}).catch(e=>{status.textContent=e&&e.name==='AbortError'?'Отправка отменена.':'Не удалось открыть меню отправки.';});}"
                    + "else{fallbackDownload();}}catch(_e){fallbackDownload();}});"
                    + "</script></main></body></html>";
            return html.getBytes(StandardCharsets.UTF_8);
        }

        private static String htmlEscape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }

        private static String jsEscape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
        }

        private static void writeResponse(OutputStream output, String status, String type, byte[] body, boolean head, String extraHeaders) throws IOException {
            String headers = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: " + type + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + (extraHeaders == null ? "" : extraHeaders)
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            if (!head) output.write(body);
            output.flush();
        }
    }
}
