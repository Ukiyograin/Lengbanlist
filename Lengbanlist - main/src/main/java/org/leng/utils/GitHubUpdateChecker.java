package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.leng.Lengbanlist;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class GitHubUpdateChecker {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Ukiyograin/Lengbanlist/releases/latest";

    private static final List<String> STATIC_API_URLS = Arrays.asList(GITHUB_API_URL);

    private static final int TIMEOUT = 3000;

    /**
     * 获取最新版本号
     *
     * @return 最新版本号
     * @throws Exception 如果所有 API 都请求失败
     */
    public static String getLatestReleaseVersion() throws Exception {
        // 遍历所有 API 地址，尝试获取版本号
        for (String apiUrl : STATIC_API_URLS) {
            try {
                return fetchVersionFromUrl(apiUrl);
            } catch (Exception e) {
                Lengbanlist.getInstance().getLogger().warning("哇呜，当前 API 请求失败: " + apiUrl + "，喵喵正在尝试下一个备用 API...");
            }
        }
        throw new Exception("喵喵：所有 API 请求均失败，无法获取最新版本号");
    }

    /**
     * 从指定 URL 获取版本号
     *
     * @param url API 地址
     * @return 版本号
     * @throws Exception 如果请求失败
     */
    private static String fetchVersionFromUrl(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
            StringBuilder response = new StringBuilder();
            int data = reader.read();
            while (data != -1) {
                response.append((char) data);
                data = reader.read();
            }
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            return jsonResponse.get("tag_name").getAsString();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 比较两个版本号的大小
     *
     * @param version1 版本号1
     * @param version2 版本号2
     * @return 如果 version1 小于 version2，返回 -1；如果 version1 等于 version2，返回 0；如果 version1 大于 version2，返回 1
     */
public static int compareVersions(String v1, String v2) {
    int[] a = parseVersion(v1);
    int[] b = parseVersion(v2);
    int len = Math.max(a.length, b.length);
    for (int i = 0; i < len; i++) {
        int x = i < a.length ? a[i] : 0;
        int y = i < b.length ? b[i] : 0;
        if (x != y) return Integer.compare(x, y);
    }
    return 0;
}

private static int[] parseVersion(String ver) {
    String[] s = ver.replaceAll("^v", "").split("\\.");
    int[] arr = new int[s.length];
    for (int i = 0; i < s.length; i++) {
        arr[i] = Integer.parseInt(s[i].replaceAll("\\D+", "")); 
    }
    return arr;
}

    /**
     * 检查是否有更新
     *
     * @param localVersion 当前插件版本
     * @return 是否有更新
     * @throws Exception 如果请求失败
     */
    public static boolean isUpdateAvailable(String localVersion) throws Exception {
        String latestVersion = getLatestReleaseVersion();
        return compareVersions(localVersion, latestVersion) < 0; // 当前版本小于最新版本时才返回 true
    }
    
        /**
     * 异步获取最新版本号
     *
     * @param plugin 插件实例
     * @return CompletableFuture<String> 包含最新版本号的Future
     */
    public static CompletableFuture<String> getLatestReleaseVersionAsync(Lengbanlist plugin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getLatestReleaseVersion();
            } catch (Exception e) {
                plugin.getLogger().warning("异步获取最新版本失败: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * 检查更新并输出日志
     */
    public static void checkUpdate() {
        try {
            String localVersion = Lengbanlist.getInstance().getDescription().getVersion();
            String latestVersion = getLatestReleaseVersion();
            if (compareVersions(localVersion, latestVersion) < 0) {
                // 创建主消息组件
                TextComponent mainMessage = new TextComponent("§a喵喵发现有新版本可用，当前版本：§e" + localVersion + "§a，最新版本：§e" + latestVersion + "§a 请前往: §bhttps://github.com/Ukiyograin/Lengbanlist/releases");

                // 创建点击组件
                TextComponent clickableComponent = new TextComponent("§f【§b点击前往喵~§f】");
                clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Ukiyograin/Lengbanlist/releases"));
                clickableComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击打开更新页面喵~").create()));

                // 将 TextComponent 转换为字符串并输出日志
                String logMessage = mainMessage.toLegacyText() + " " + clickableComponent.toLegacyText();
                Lengbanlist.getInstance().getLogger().info(logMessage);
            } else {
                Lengbanlist.getInstance().getLogger().info("哇塞，喵呜现在是最新版本！QwQ");
            }
        } catch (Exception e) {
            Lengbanlist.getInstance().getLogger().warning("检测更新时出错: " + e.getMessage());
            e.printStackTrace(); // 打印完整的异常堆栈
        }
    }
    
/**
 * 获取下载文件的URL
 * @param version 版本号
 * @return 下载URL
 */
public static String getDownloadUrl(String version) {
    return "https://github.com/Ukiyograin/Lengbanlist/releases/download/" + 
           version + "/Lengbanlist-" + version + ".jar";  // GitHub release使用的是连字符格式
}

/**
 * 获取GitHub Release上的文件名（不含路径）
 * @param version 版本号
 * @return 文件名
 */
public static String getGitHubFileName(String version) {
    return "Lengbanlist-" + version + ".jar";  // GitHub使用的是连字符，没有空格
}

/**
 * 获取本地应该使用的文件名格式（带空格）
 * @param version 版本号
 * @return 文件名
 */
public static String getLocalFileName(String version) {
    return "Lengbanlist - " + version + ".jar";  // 本地使用带空格的格式
}

/**
 * 根据当前文件名格式生成新版本的文件名
 */
public static String generateNewFileName(String currentFileName, String newVersion) {
    // 如果当前文件名包含 " - " 格式，保持相同格式
    if (currentFileName.contains(" - ") && currentFileName.endsWith(".jar")) {
        String baseName = currentFileName.substring(0, currentFileName.lastIndexOf(" - "));
        return baseName + " - " + newVersion + ".jar";
    }
    // 否则使用默认带空格的格式
    return "Lengbanlist - " + newVersion + ".jar";
}
}