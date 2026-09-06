package org.leng.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AuditManager.cleanupExports 单元测试 —— 覆盖导出文件清理逻辑。
 *
 * <p>验证 maxFiles 配置、文件排序（按 lastModified）、删除数量统计。
 */
@ExtendWith(MockitoExtension.class)
class AuditManagerTest {

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;
    AuditManager manager;

    @BeforeEach
    void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        // getLogger() 仅在清理发生时才被调用,某些测试路径不触发 —— lenient 避免误报
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        manager = new AuditManager(plugin);
    }

    @Test
    void cleanupExports_maxFilesZero_returnsZeroAndDeletesNothing(@TempDir Path tmp) throws Exception {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(0);
        File dir = tmp.toFile();
        File f1 = touch(dir, "audit_export_2024-01-01.json");
        File f2 = touch(dir, "audit_export_2024-01-02.json");
        assertEquals(0, manager.cleanupExports(dir));
        assertTrue(f1.exists());
        assertTrue(f2.exists());
    }

    @Test
    void cleanupExports_negativeMaxFiles_returnsZero(@TempDir Path tmp) throws Exception {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(-5);
        File dir = tmp.toFile();
        File f = touch(dir, "audit_export_x.json");
        assertEquals(0, manager.cleanupExports(dir));
        assertTrue(f.exists());
    }

    @Test
    void cleanupExports_filesUnderLimit_returnsZero(@TempDir Path tmp) throws Exception {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(5);
        File dir = tmp.toFile();
        for (int i = 0; i < 3; i++) {
            touch(dir, "audit_export_" + i + ".json");
        }
        assertEquals(0, manager.cleanupExports(dir));
        assertEquals(3, dir.listFiles((d, name) -> name.startsWith("audit_export_")).length);
    }

    @Test
    void cleanupExports_overLimit_keepsNewestDeletesOldest(@TempDir Path tmp) throws Exception {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(2);
        File dir = tmp.toFile();
        File old1 = touch(dir, "audit_export_old1.json");
        File old2 = touch(dir, "audit_export_old2.json");
        Thread.sleep(20);
        File new1 = touch(dir, "audit_export_new1.json");
        Thread.sleep(20);
        File new2 = touch(dir, "audit_export_new2.json");

        // 文件 lastModified: old1 < old2 < new1 < new2
        // maxFiles=2 应保留 new1 + new2,删除 old1 + old2
        assertEquals(2, manager.cleanupExports(dir));
        assertFalse(old1.exists());
        assertFalse(old2.exists());
        assertTrue(new1.exists());
        assertTrue(new2.exists());
    }

    @Test
    void cleanupExports_nonExportFiles_ignored(@TempDir Path tmp) throws Exception {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(1);
        File dir = tmp.toFile();
        File export = touch(dir, "audit_export_keep.json");
        File other = touch(dir, "other_file.json");
        File unrelated = touch(dir, "README.md");

        // 其他文件不计入 maxFiles,不参与清理
        assertEquals(0, manager.cleanupExports(dir));
        assertTrue(export.exists());
        assertTrue(other.exists());
        assertTrue(unrelated.exists());
    }

    @Test
    void cleanupExports_emptyDir_returnsZero(@TempDir Path tmp) {
        when(config.getInt("audit.export.max-files", 20)).thenReturn(20);
        assertEquals(0, manager.cleanupExports(tmp.toFile()));
    }

    private File touch(File dir, String name) throws Exception {
        File f = new File(dir, name);
        try (FileWriter w = new FileWriter(f)) {
            w.write("{}");
        }
        return f;
    }
}