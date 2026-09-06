package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;

import java.io.IOException;
import java.util.logging.Level;

/**
 * IP 归属地查询工具 —— 统一 Lengbanlist 所有 IP → 地理位置查询。
 *
 * <p>早期版本散落在 GetIPCommand / LengbanlistCommand 两处，且其中一处仍用 HttpURLConnection
 * 且调用不一致的 API（ipapi.co）。本工具统一使用 ip-api.com + HttpHelper（A6 规范）。
 *
 * <p>调用方负责异步包装（本工具只做同步 IO）。
 */
public final class IpGeoLookup {

    private static final String API_URL = "https://ip-api.com/json/%s?lang=zh-CN";
    private static final String USER_AGENT = "Lengbanlist-IPLoc/1.0";
    private static final int TIMEOUT_MS = 3000;

    private final Lengbanlist plugin;

    public IpGeoLookup(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /**
     * 查询 IP 归属地。失败返回 null，错误已记录到 plugin logger。
     */
    public String lookup(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        String url = String.format(API_URL, ip);
        try (HttpHelper http = new HttpHelper(TIMEOUT_MS, TIMEOUT_MS)) {
            String response = http.get(url, USER_AGENT, "*/*");
            JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
            if (!"success".equals(safeGetString(obj, "status"))) {
                plugin.getLogger().warning("[IpGeoLookup] IP API 请求失败: " + safeGetString(obj, "message"));
                return null;
            }
            String country = safeGetString(obj, "country");
            String region = safeGetString(obj, "regionName");
            String city = safeGetString(obj, "city");
            return country + ", " + region + ", " + city;
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[IpGeoLookup] 查询失败: " + ip, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String safeGetString(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "未知";
    }
}