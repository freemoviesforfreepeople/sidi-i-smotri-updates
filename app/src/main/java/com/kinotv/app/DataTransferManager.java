package com.kinotv.app;

import android.app.Activity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DataTransferManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long SERVER_LIFETIME_MS = 10L * 60L * 1000L;

    interface Callback {
        void onSuccess(Result result);
        void onError(String message, Throwable error);
    }

    static final class Result {
        final String transferUrl;
        final LocalJsonServer server;

        Result(String transferUrl, LocalJsonServer server) {
            this.transferUrl = transferUrl;
            this.server = server;
        }

        void stop() {
            if (server != null) server.stop();
        }
    }

    private DataTransferManager() {
    }

    static void serve(Activity activity, String json, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                Inet4Address localAddress = findLocalIpv4Address();
                if (localAddress == null) {
                    throw new IOException("Не найден локальный IPv4-адрес. Подключите устройства к одной сети.");
                }
                String ip = localAddress.getHostAddress();
                String token = UUID.randomUUID().toString().replace("-", "");
                LocalJsonServer server = new LocalJsonServer(json, token, localAddress);
                server.start();
                String url = "http://" + ip + ":" + server.getPort() + "/transfer/" + token + "/backup.json";
                Result result = new Result(url, server);
                activity.runOnUiThread(() -> callback.onSuccess(result));
            } catch (Throwable error) {
                activity.runOnUiThread(() -> callback.onError("Не удалось запустить передачу", error));
            }
        });
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

    static final class LocalJsonServer {
        private final byte[] json;
        private final String token;
        private final InetAddress bindAddress;
        private volatile boolean running;
        private ServerSocket serverSocket;
        private Thread thread;

        LocalJsonServer(String json, String token, InetAddress bindAddress) {
            this.json = json.getBytes(StandardCharsets.UTF_8);
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
            thread = new Thread(this::serveLoop, "SidiTransferServer");
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
        }

        private void serveLoop() {
            long deadline = System.currentTimeMillis() + SERVER_LIFETIME_MS;
            while (running && System.currentTimeMillis() < deadline) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (SocketTimeoutException ignored) {
                } catch (Throwable error) {
                    if (running) {
                        // Keep the server alive for transient client errors.
                    }
                }
            }
            stop();
        }

        private void handle(Socket socket) {
            try (Socket client = socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream output = client.getOutputStream()) {
                client.setSoTimeout(10_000);
                String requestLine = reader.readLine();
                if (requestLine == null) return;
                String[] parts = requestLine.split(" ");
                String path = parts.length > 1 ? parts[1] : "/";
                while (true) {
                    String line = reader.readLine();
                    if (line == null || line.isEmpty()) break;
                }

                String expected = "/transfer/" + token + "/backup.json";
                if (!path.equals(expected)) {
                    writeResponse(output, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                writeResponse(output, 200, "application/json; charset=utf-8", json);
            } catch (Throwable ignored) {
            }
        }

        private void writeResponse(OutputStream output, int code, String contentType, byte[] body) throws IOException {
            String status = code == 200 ? "OK" : "Not Found";
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.US_ASCII));
            writer.write("HTTP/1.1 " + code + " " + status + "\r\n");
            writer.write("Content-Type: " + contentType + "\r\n");
            writer.write("Content-Length: " + body.length + "\r\n");
            writer.write("Cache-Control: no-store\r\n");
            writer.write("Connection: close\r\n\r\n");
            writer.flush();
            output.write(body);
            output.flush();
        }
    }
}
