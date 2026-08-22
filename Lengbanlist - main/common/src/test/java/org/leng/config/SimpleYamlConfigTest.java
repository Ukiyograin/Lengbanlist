package org.leng.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：解析 list-of-maps 时不应在第一个条目就抛 IndexOutOfBoundsException。
 *
 * 触发场景：plugins/Lengbanlist/config.yml 中
 * {@code update-check.mirrors} 是 list-of-maps，首次加载默认文件时必中。
 */
class SimpleYamlConfigTest {

    @Test
    void parsesListOfMapsWithoutCrashing() throws Exception {
        String yaml = ""
                + "update-check:\n"
                + "  enabled: true\n"
                + "  mirrors:\n"
                + "    - name: gh-proxy\n"
                + "      type: github-proxy\n"
                + "      url: \"https://example.com/a\"\n"
                + "    - name: jsDelivr\n"
                + "      type: jsdelivr\n"
                + "      url: \"https://example.com/b\"\n";

        SimpleYamlConfig config = SimpleYamlConfig.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(true, config.getBoolean("update-check.enabled", false));
        assertTrue(config.isConfigurationSection("update-check"));

        List<String> keys = config.getConfigurationSectionKeys("update-check");
        assertTrue(keys.contains("mirrors"));

        Object raw = config.getObject("update-check.mirrors");
        assertInstanceOf(List.class, raw);
        List<?> mirrors = (List<?>) raw;
        assertEquals(2, mirrors.size());

        Map<?, ?> first = assertInstanceOf(Map.class, mirrors.get(0));
        assertEquals("gh-proxy", first.get("name"));
        assertEquals("github-proxy", first.get("type"));
        assertEquals("https://example.com/a", first.get("url"));

        Map<?, ?> second = assertInstanceOf(Map.class, mirrors.get(1));
        assertEquals("jsDelivr", second.get("name"));
        assertEquals("jsdelivr", second.get("type"));
        assertEquals("https://example.com/b", second.get("url"));
    }

    @Test
    void parsesListOfScalarsAlongsideListOfMaps() throws Exception {
        // 同一个配置文件里同时包含 list-of-maps 和 list-of-scalars，两条路径
        // 都走过 ensureList，确保不会互相干扰。
        String yaml = ""
                + "mirrors:\n"
                + "  - a\n"
                + "  - b\n"
                + "tiers:\n"
                + "  - 7d\n"
                + "  - 30d\n";

        SimpleYamlConfig config = SimpleYamlConfig.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        List<String> mirrors = config.getStringList("mirrors");
        assertEquals(2, mirrors.size());
        assertEquals("a", mirrors.get(0));
        assertEquals("b", mirrors.get(1));

        List<String> tiers = config.getStringList("tiers");
        assertEquals(2, tiers.size());
        assertEquals("7d", tiers.get(0));
        assertEquals("30d", tiers.get(1));
    }

    @Test
    void emptyListSectionIsTolerated() throws Exception {
        String yaml = ""
                + "update-check:\n"
                + "  mirrors:\n";

        SimpleYamlConfig config = SimpleYamlConfig.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // 不应崩溃，且 mirrors 应能正常读取为空列表
        List<String> mirrors = config.getStringList("update-check.mirrors");
        assertNotNull(mirrors);
        assertTrue(mirrors.isEmpty());
    }
}