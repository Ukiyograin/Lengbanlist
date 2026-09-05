package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.leng.Lengbanlist;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Web 管理面板主题配置 + 背景上传管理。
 *
 * 主题存储于 plugins/Lengbanlist/theme.yml:
 *   background-type: default | url | upload
 *   background-url: <图床链接>
 *   background-file: <相对 web-assets/ 的文件名>
 *   hidden-buttons: [ban, mute, audit, ...]
 *
 * 上传的文件保存到 plugins/Lengbanlist/web-assets/background/，
 * 文件名随机化防覆盖，原始扩展名保留。
 */
public class ThemeManager {

    /** 所有可被隐藏的按钮 ID（前端固定集合，服务端只保存子集） */
    public static final Set<String> ALL_BUTTONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ban", "unban", "mute", "unmute", "warn", "report",
            "audit", "player", "ip", "history", "alts", "broadcast"
    )));

    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024; // 5 MB
    public static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "webp", "gif");

    private final Lengbanlist plugin;
    private final File themeFile;
    private final File webAssetsDir;
    private final Logger logger;

    private String backgroundType = "default";
    private String backgroundUrl = "";
    private String backgroundFile = "";
    private Set<String> hiddenButtons = new HashSet<>();

    public ThemeManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.themeFile = new File(plugin.getDataFolder(), "theme.yml");
        this.webAssetsDir = new File(plugin.getDataFolder(), "web-assets/background");
        if (!webAssetsDir.exists()) {
            webAssetsDir.mkdirs();
        }
        load();
    }

    /** 提供给 WebServer 路径校验（防止穿越） */
    public File getWebAssetsDir() {
        return webAssetsDir;
    }

    public void load() {
        if (!themeFile.exists()) {
            save();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(themeFile);
        backgroundType = yaml.getString("background-type", "default");
        backgroundUrl = yaml.getString("background-url", "");
        backgroundFile = yaml.getString("background-file", "");
        hiddenButtons = new HashSet<>(yaml.getStringList("hidden-buttons"));
    }

    public void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("background-type", backgroundType);
            yaml.set("background-url", backgroundUrl);
            yaml.set("background-file", backgroundFile);
            yaml.set("hidden-buttons", new ArrayList<>(hiddenButtons));
            yaml.save(themeFile);
        } catch (IOException e) {
            logger.warning("保存 theme.yml 失败: " + e.getMessage());
        }
    }

    // ============ 背景配置 ============

    public String getBackgroundType() { return backgroundType; }
    public String getBackgroundUrl() { return backgroundUrl; }
    public String getBackgroundFile() { return backgroundFile; }

    public void setBackgroundUrl(String url) {
        this.backgroundType = url == null || url.isEmpty() ? "default" : "url";
        this.backgroundUrl = url == null ? "" : url;
        save();
    }

    public void setBackgroundFile(String filename) {
        this.backgroundType = filename == null || filename.isEmpty() ? "default" : "upload";
        this.backgroundFile = filename == null ? "" : filename;
        save();
    }

    public void resetBackground() {
        this.backgroundType = "default";
        this.backgroundUrl = "";
        this.backgroundFile = "";
        save();
    }

    /**
     * 保存上传的背景文件。返回相对 web-assets/background 的文件名，失败抛 IOException。
     * 文件名随机化防冲突，扩展名做白名单校验。
     */
    public String saveBackgroundUpload(byte[] data, String originalFilename) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("上传内容为空");
        }
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new IOException("文件超过 5MB 上限 (实际 " + data.length + " bytes)");
        }

        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot + 1).toLowerCase();
            }
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IOException("不支持的文件扩展名: " + ext + " (允许: " + ALLOWED_EXTENSIONS + ")");
        }

        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = Paths.get(webAssetsDir.getAbsolutePath(), safeName);
        Files.write(target, data);

        // 删除旧的上传背景（仅一个，避免堆积）
        if (!backgroundFile.isEmpty() && !backgroundFile.equals(safeName)) {
            Path old = Paths.get(webAssetsDir.getAbsolutePath(), backgroundFile);
            try { Files.deleteIfExists(old); } catch (IOException ignored) {}
        }

        setBackgroundFile(safeName);
        return safeName;
    }

    public File getBackgroundFileOnDisk() {
        if (backgroundFile.isEmpty()) return null;
        return new File(webAssetsDir, backgroundFile);
    }

    // ============ 按钮显隐 ============

    public Set<String> getHiddenButtons() {
        return Collections.unmodifiableSet(hiddenButtons);
    }

    public void setHiddenButtons(Set<String> buttons) {
        // 过滤掉未知按钮,防止 typo
        Set<String> filtered = new HashSet<>();
        for (String b : buttons) {
            if (ALL_BUTTONS.contains(b)) filtered.add(b);
        }
        this.hiddenButtons = filtered;
        save();
    }

    public boolean isButtonVisible(String buttonId) {
        return !hiddenButtons.contains(buttonId);
    }

    public Set<String> getVisibleButtons() {
        Set<String> visible = new HashSet<>(ALL_BUTTONS);
        visible.removeAll(hiddenButtons);
        return visible;
    }
}