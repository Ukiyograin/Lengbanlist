package org.leng.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class FabricMetrics {
    private static final String URL = "https://bStats.org/api/v2/data/fabric";
    private final FabricLengbanlist plugin;
    private final int serviceId;
    private final String serverUuid;

    public FabricMetrics(FabricLengbanlist plugin, int serviceId) {
        this.plugin = plugin;
        this.serviceId = serviceId;
        this.serverUuid = loadServerUuid();
        // 构造器仅初始化字段；线程启动延迟到 start()，确保 plugin 字段（特别是 server）就绪后再上报。
    }

    /** 启动 metrics 上报线程。仅在插件完成字段初始化后调用。 */
    public void start() {
        submitAsync();
    }

    private String loadServerUuid() {
        try {
            File file = new File(plugin.getDataFolder(), "bstats-uuid.txt");
            if (file.exists()) {
                String value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) return value;
            }
            String value = UUID.randomUUID().toString();
            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
            return value;
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private void submitAsync() {
        Thread thread = new Thread(this::submitData, "Lengbanlist Fabric Metrics");
        thread.setDaemon(true);
        thread.start();
    }

    private void submitData() {
        try {
            JsonObject data = new JsonObject();
            data.addProperty("serverUUID", serverUuid);
            data.addProperty("playerAmount", plugin.getOnlinePlayerCount());
            data.addProperty("onlineMode", 1);
            data.addProperty("minecraftVersion", "1.21.x");
            data.addProperty("javaVersion", System.getProperty("java.version"));
            data.addProperty("osName", System.getProperty("os.name"));
            data.addProperty("osArch", System.getProperty("os.arch"));
            data.addProperty("osVersion", System.getProperty("os.version"));
            data.addProperty("coreCount", Runtime.getRuntime().availableProcessors());

            JsonObject service = new JsonObject();
            service.addProperty("id", serviceId);
            service.add("customCharts", new JsonArray());
            JsonArray services = new JsonArray();
            services.add(service);
            data.add("services", services);

            byte[] compressed = compress(data.toString());
            HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Metrics-Service/1");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(compressed);
            }
            connection.getInputStream().close();
            connection.disconnect();
        } catch (Exception ignored) {
        }
    }

    private byte[] compress(String str) throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }
}
