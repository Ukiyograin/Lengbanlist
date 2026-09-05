package org.leng.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证下载 jar 的结构校验逻辑：
 * 官方发布的 Bukkit 插件 jar 的 MANIFEST 不含 Main-Class（主类由 plugin.yml 的 main: 决定），
 * 旧版校验因此误拒所有正常发布；修复后校验 plugin.yml，且对 manifest 冲突的主类仍拒绝。
 */
class AutoUpdateManagerDownloadTest {

    @TempDir
    Path tempDir;

    private File writeJar(String pluginYml, String manifestMainClass) throws IOException {
        File jar = tempDir.resolve("test.jar").toFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            if (manifestMainClass != null) {
                out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                out.write(("Manifest-Version: 1.0\r\n"
                        + "Main-Class: " + manifestMainClass + "\r\n\r\n").getBytes("UTF-8"));
                out.closeEntry();
            }
            if (pluginYml != null) {
                out.putNextEntry(new ZipEntry("plugin.yml"));
                out.write(pluginYml.getBytes("UTF-8"));
                out.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void acceptsOfficialStyleJarWithoutManifestMainClass() throws Exception {
        // 官方构建：MANIFEST 无 Main-Class，主类只在 plugin.yml 中
        File jar = writeJar("name: Lengbanlist\nmain: org.leng.Lengbanlist\nversion: 1.9.9\n", null);
        assertDoesNotThrow(() -> AutoUpdateManager.validatePluginJar(jar));
    }

    @Test
    void rejectsJarMissingPluginYml() throws Exception {
        File jar = writeJar(null, null);
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("plugin.yml"));
    }

    @Test
    void rejectsJarWithWrongMainClassInPluginYml() throws Exception {
        File jar = writeJar("name: Other\nmain: com.evil.Main\nversion: 1.0\n", null);
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("plugin.yml 主类"));
    }

    @Test
    void rejectsJarWithConflictingManifestMainClass() throws Exception {
        // manifest 显式声明了冲突主类（防注入），即使 plugin.yml 正确也拒绝
        File jar = writeJar("name: Lengbanlist\nmain: org.leng.Lengbanlist\nversion: 1.9.9\n", "com.evil.Injected");
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("冲突的主类"));
    }
}
