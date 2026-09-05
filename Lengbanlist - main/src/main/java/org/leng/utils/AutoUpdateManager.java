package org.leng.utils;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.leng.Lengbanlist;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;

public class AutoUpdateManager {
    private static final long MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024; // 64MB 上限，防磁盘填满
    private static final String MANIFEST_MAIN_CLASS = "org.leng.Lengbanlist";

    private final Lengbanlist plugin;
    private final Logger logger;
    private File currentPluginFile;

    public AutoUpdateManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentPluginFile = getCurrentPluginFile();
    }


    private File getCurrentPluginFile() {
        try {

            Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
            getFileMethod.setAccessible(true);
            return (File) getFileMethod.invoke(plugin);
        } catch (Exception e) {
            logger.warning("获取当前插件文件失败: " + e.getMessage());
            return null;
        }
    }


    private String getPluginBaseName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int lastHyphen = fileName.lastIndexOf("-");
        if (lastHyphen > 0 && fileName.endsWith(".jar")) {
            return fileName.substring(0, lastHyphen) + ".jar";
        }
        return fileName;
    }

    public void checkAndAutoUpdate() {
        try {
            String latestVersion = GitHubUpdateChecker.getLatestReleaseVersion();
            String currentVersion = plugin.getDescription().getVersion();
            if (GitHubUpdateChecker.isUpdateAvailable(currentVersion)) {
                logger.info("发现新版本：" + latestVersion + "，当前版本：" + currentVersion);
                downloadAndReplace(latestVersion);
            } else {
                logger.info("你正在使用最新版本：" + currentVersion);
            }
        } catch (Exception e) {
            logger.warning("检查更新时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void downloadAndReplace(String version) throws Exception {
        if (currentPluginFile == null) {
            throw new Exception("无法获取当前插件文件");
        }

        // 防止被劫持的 release 用 ../../payload 形式污染磁盘路径;允许 v 前缀、MAJOR.MINOR 或 MAJOR.MINOR.PATCH、可选 pre-release/build 元数据
        if (!version.matches("^v?\\d+\\.\\d+(\\.\\d+)?(-[\\w.]+)?(\\+[\\w.]+)?$")) {
            throw new IOException("拒绝非法版本号: " + version + "（需形如 1.0 / 1.0.0 / v1.0.0 / 1.0.0-beta.1）");
        }


        String currentFileName = currentPluginFile.getName();
        String baseName = getPluginBaseName(currentFileName);


        String newFileName;
        if (currentFileName.startsWith("Lengbanlist-")) {

            String namePart = currentFileName.substring(0, currentFileName.lastIndexOf("-"));
            newFileName = namePart + "-" + version + ".jar";
        } else {

            newFileName = "Lengbanlist-" + version + ".jar";
        }


        String downloadUrl = GitHubUpdateChecker.getLatestDownloadUrl();


        File tempFile = new File(currentPluginFile.getParentFile(),
                               newFileName + ".temp");


        logger.info("正在从 " + downloadUrl + " 下载新版本...");
        long[] totalBytes = {0};
        boolean[] headerValidated = {false};
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 算法不可用", e);
        }

        try (org.leng.utils.HttpHelper http = new org.leng.utils.HttpHelper(
                java.time.Duration.ofMillis(5000),
                java.time.Duration.ofMillis(15000),
                !GitHubUpdateChecker.isSslVerifyEnabled());
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            http.download(downloadUrl, GitHubUpdateChecker.getUserAgent(),
                    chunk -> {
                        if (!headerValidated[0]) {
                            if (chunk.length < 4 || !isZipHeader(chunk)) {
                                throw new IllegalStateException("下载内容不是有效的 JAR 文件（文件头异常），已拒绝安装，请检查更新源或镜像是否可信。");
                            }
                            headerValidated[0] = true;
                        }
                        digest.update(chunk);
                        try {
                            fos.write(chunk);
                        } catch (IOException e) {
                            throw new RuntimeException("写入临时文件失败", e);
                        }
                        totalBytes[0] += chunk.length;
                        if (totalBytes[0] >= MAX_DOWNLOAD_BYTES) {
                            throw new IllegalStateException("下载内容超过 " + MAX_DOWNLOAD_BYTES + " 字节，疑似非插件文件，已中断并拒绝安装。");
                        }
                    },
                    total -> { /* HttpHelper 已限制单 chunk, 此处仅作记录 */ });

            if (!headerValidated[0]) {
                throw new IOException("下载内容为空");
            }
        } catch (IllegalStateException e) {
            // 校验失败抛出的状态,转换为 IO 异常以兼容调用方
            throw new IOException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }

        long bytesRead = totalBytes[0];
        String sha256 = toHex(digest.digest());

        logger.info("新版本已下载到临时文件: " + tempFile.getName() +
                   " (" + bytesRead + " bytes, SHA-256: " + sha256 + ")");

        String expectedSha256 = normalizeSha256(GitHubUpdateChecker.getLatestSha256());
        if (expectedSha256 != null) {
            if (!expectedSha256.equalsIgnoreCase(sha256)) {
                tempFile.delete();
                throw new IOException("下载文件 SHA-256 与官方发布不一致（期望 " + expectedSha256 + "，实际 " + sha256 + "），已拒绝安装，请检查更新源是否被劫持。");
            }
            logger.info("SHA-256 校验通过：与官方发布一致");
        } else {
            tempFile.delete();
            throw new IOException("无法获取官方 SHA-256 摘要（当前更新源未提供），出于安全考虑，拒绝安装未经校验的 JAR 文件。请改用 GitHub 直连/代理镜像或手动下载更新。");
        }

        // 校验 jar 包结构（zip 完整性 + plugin.yml 主类），防止镜像返回被截断/篡改的文件。
        // 注意：不能校验 MANIFEST 的 Main-Class —— Bukkit 插件的 jar 从不写该字段
        // （由 plugin.yml 的 main: 决定主类），官方构建即如此，校验它会把正常文件误判为非法。
        try {
            validatePluginJar(tempFile);
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        }

        File newPluginFile = new File(currentPluginFile.getParentFile(), newFileName);


        if (newPluginFile.exists()) {
            logger.info("删除已存在的文件: " + newPluginFile.getName());
            if (!newPluginFile.delete()) {
                logger.warning("无法删除已存在的文件，尝试重命名...");
                File backupFile = new File(newPluginFile.getParentFile(),
                                         newPluginFile.getName() + ".backup");
                if (newPluginFile.renameTo(backupFile)) {
                    logger.info("已将旧文件备份为: " + backupFile.getName());
                }
            }
        }


        if (tempFile.renameTo(newPluginFile)) {
            logger.info("临时文件已重命名为: " + newFileName);
        } else {

            logger.info("重命名失败，尝试复制文件...");
            try {
                copyFile(tempFile, newPluginFile);
            } catch (IOException e) {
                logger.severe("复制临时文件失败: " + e.getMessage());
                throw e;
            } finally {
                if (!tempFile.delete()) {
                    logger.warning("无法立即删除临时文件，将在服务器退出时清理: " + tempFile.getName());
                    tempFile.deleteOnExit();
                }
            }
        }


        if (!currentPluginFile.equals(newPluginFile) && currentPluginFile.exists()) {
            logger.info("删除旧插件文件: " + currentPluginFile.getName());
            if (currentPluginFile.delete()) {
                logger.info("旧插件文件已删除");
            } else {
                currentPluginFile.deleteOnExit();
                logger.warning("无法立即删除旧插件文件，将在服务器退出时删除: " + currentPluginFile.getName());
            }
        }

        installUpdate(newPluginFile);
    }

    private static boolean isZipHeader(byte[] header) {
        return header != null && header.length >= 4
                && (header[0] & 0xFF) == 0x50
                && (header[1] & 0xFF) == 0x4B
                && ((header[2] & 0xFF) == 0x03 || (header[2] & 0xFF) == 0x05 || (header[2] & 0xFF) == 0x07);
    }

    /**
     * 校验下载的 jar 是否是可安装的 Lengbanlist 插件包：
     * 必须含可解析的 plugin.yml 且主类为 {@link #MANIFEST_MAIN_CLASS}。
     * 不校验 MANIFEST 的 Main-Class —— Bukkit 插件的 jar 从不写该字段，
     * 官方构建即如此；仅当 manifest 显式声明了冲突的主类时才拒绝（防注入）。
     */
    static void validatePluginJar(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                throw new IOException("下载的 JAR 缺少 plugin.yml，已拒绝安装，请检查更新源。");
            }
            String mainClass = null;
            try (InputStream in = jar.getInputStream(pluginYml)) {
                mainClass = new PluginDescriptionFile(in).getMain();
            } catch (Exception e) {
                throw new IOException("下载的 JAR 中 plugin.yml 无法解析（" + e.getMessage() + "），已拒绝安装，请检查更新源。", e);
            }
            if (!MANIFEST_MAIN_CLASS.equals(mainClass)) {
                throw new IOException("下载的 JAR 的 plugin.yml 主类不是 " + MANIFEST_MAIN_CLASS + "（实际: " + mainClass + "），已拒绝安装，请检查更新源。");
            }
            // 兼容旧版防篡改校验：若 manifest 声明了 Main-Class（非 Bukkit 插件常规构建），
            // 仍要求与期望主类一致，防止镜像在 plugin.yml 之外注入可执行入口。
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String declared = manifest.getMainAttributes().getValue("Main-Class");
                if (declared != null && !MANIFEST_MAIN_CLASS.equals(declared)) {
                    throw new IOException("下载的 JAR 清单声明了冲突的主类 " + declared + "，已拒绝安装，请检查更新源。");
                }
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String normalizeSha256(String digest) {
        if (digest == null || digest.trim().isEmpty()) {
            return null;
        }
        String value = digest.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1).trim();
        }
        if (!value.matches("^[0-9a-fA-F]{64}$")) {
            return null;
        }
        return value.toLowerCase();
    }


    private void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void installUpdate(File newPluginFile) {
        logger.info("新版本插件文件已安装: " + newPluginFile.getName());
        logger.info("请重启服务器以加载新版本。Paper 不支持安全地运行时替换并重载插件。");
    }
}
