package org.leng.manager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ModelCloudManager 单元测试 —— 拉取 + 镜像回退 + 缓存 + 失败回退。
 *
 * <p>使用 JDK 内置 HttpServer 启动本地 mock 端点,
 * 覆盖以下场景:成功 / 全失败 / 首失败次成功 / 无效 JSON / 缓存命中 / 网络穿透。
 */
@ExtendWith(MockitoExtension.class)
class ModelCloudManagerTest {

    private static final String MOCK_INDEX_JSON = "{\n" +
            "  \"version\": 1,\n" +
            "  \"updated\": \"2026-09-01\",\n" +
            "  \"models\": [\n" +
            "    {\"id\": \"hutao\", \"name\": \"胡桃\", \"version\": \"1.2.0\", \"author\": \"uki\",\n" +
            "     \"url\": \"%s\", \"sha256\": \"\"},\n" +
            "    {\"id\": \"furina\", \"name\": \"芙宁娜\", \"version\": \"1.0.0\", \"author\": \"uki\",\n" +
            "     \"url\": \"%s\", \"sha256\": \"\"}\n" +
            "  ],\n" +
            "  \"featured\": {\"month\": \"%s\", \"modelId\": \"hutao\", \"title\": \"本月精选\", \"description\": \"\"}\n" +
            "}";

    /** 用于写缓存的 index.json（model url 占位,test 不访问真实仓库） */
    private static String mockIndexWithModelUrl(String month, String modelUrl) {
        return String.format(MOCK_INDEX_JSON, modelUrl, modelUrl, month);
    }

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger intermittentHits = new AtomicInteger(0);

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;
    ModelCloudManager manager;

    @TempDir Path tmp;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        register("/success", 200, mockIndexWithModelUrl(currentMonth, url("model")));
        register("/fail", 500, "server error");
        register("/invalid", 200, "not json at all");
        register("/intermittent", -1, "{\"version\":1,\"models\":[]}");
        register("/model", 200, "name: \"hutao\"\nhelp:\n  - \"§c test help\"\nmessages: {}\n");
        server.start();
    }

    // 测试内统一"当前月份",避免跨月边界 flaky
    static final String currentMonth = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void register(String path, int codeOrNegative, String body) {
        String ctx = path.startsWith("/") ? path : "/" + path;
        server.createContext(ctx, new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                int code;
                if (codeOrNegative < 0) {
                    // 间歇性:第一次 500,后续 200
                    int hit = intermittentHits.incrementAndGet();
                    code = (hit % 2 == 1) ? 500 : 200;
                } else {
                    code = codeOrNegative;
                }
                byte[] resp = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(code, resp.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + port + "/" + path;
    }

    @BeforeEach
    void setUp() throws IOException {
        intermittentHits.set(0);
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getDataFolder()).thenReturn(tmp.toFile());
        lenient().when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of());
        lenient().when(config.getString(eq("models-cloud.repo"), any())).thenReturn("Serendisand/Lengbanlist-Models");
        lenient().when(config.getString(eq("models-cloud.branch"), any())).thenReturn("main");
        manager = new ModelCloudManager(plugin);
    }

    // Mockito any() 静态导入别名（避免与 JDK any 冲突）
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static String eq(String s) { return org.mockito.ArgumentMatchers.eq(s); }

    // ====================== fetchIndex 成功路径 ======================

    @Test
    void fetchIndex_success_parsesModelsAndFeatured() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertTrue(result.isPresent());
        ModelCloudManager.ModelIndex idx = result.get();
        assertEquals(1, idx.version());
        assertEquals(2, idx.models().size());
        assertEquals("hutao", idx.models().get(0).id());
        assertNotNull(idx.featured());
        assertEquals(currentMonth, idx.featured().month());
    }

    // ====================== 镜像回退 ======================

    @Test
    void fetchIndex_allMirrorsFail_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail"), url("fail"), url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertFalse(result.isPresent());
    }

    @Test
    void fetchIndex_firstFailsSecondSucceeds_usesSecond() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail"), url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void fetchIndex_invalidJson_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("invalid")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertFalse(result.isPresent());
    }

    // fetchIndex 不重试单个 mirror (按镜像列表逐个尝试,首个成功即用)。
    // 重试语义留给上层调度 (P4 定时任务)。

    // ====================== 缓存 ======================

    @Test
    void getIndex_usesMemoryCache_whenFresh() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        manager.fetchIndex(); // 填内存缓存

        // 不重 stub mirrors,getIndex 直接走内存缓存,不应访问网络
        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void getIndex_usesDiskCache_whenMemoryExpired() throws Exception {
        // 先写磁盘缓存
        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        // mirrors stub 在 getIndex 走磁盘路径时不被使用 —— lenient
        lenient().when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void getIndex_noCacheNoNetwork_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));
        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertFalse(result.isPresent());
    }

    @Test
    void writeIndexCache_writesFileAndReadBack() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        manager.fetchIndex();

        Path cacheFile = tmp.resolve("models/.cache/index.json");
        assertTrue(Files.exists(cacheFile));
        String body = Files.readString(cacheFile, StandardCharsets.UTF_8);
        assertTrue(body.contains("hutao"));
    }

    // ====================== 异步 ======================

    @Test
    void fetchIndexAsync_returnsCompletableFutureWithResult() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndexAsync().get();

        assertTrue(result.isPresent());
    }

    @Test
    void fetchIndexAsync_allFailures_resolvesToEmpty() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndexAsync().get();

        assertFalse(result.isPresent());
    }

    // ====================== featured / currentMonth ======================

    @Test
    void isFeaturedForCurrentMonth_match_returnsTrue() {
        String currentMonth = ModelCloudManager.currentMonth();
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel(currentMonth, "hutao", "x", "y");
        assertTrue(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void isFeaturedForCurrentMonth_noMatch_returnsFalse() {
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel("1999-01", "hutao", "x", "y");
        assertFalse(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void isFeaturedForCurrentMonth_null_returnsFalse() {
        assertFalse(manager.isFeaturedForCurrentMonth(null));
    }

    @Test
    void isFeaturedForCurrentMonth_nullMonth_returnsFalse() {
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel(null, "hutao", "x", "y");
        assertFalse(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void currentFeatured_returnsFeaturedWhenMonthMatches() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        Optional<ModelCloudManager.FeaturedModel> result = manager.currentFeatured();
        assertTrue(result.isPresent());
    }

    // ====================== mirrors 配置 ======================

    @Test
    void mirrors_emptyConfig_buildsDefaultChain() {
        List<String> mirrors = manager.mirrors();
        assertEquals(3, mirrors.size());
        assertTrue(mirrors.get(0).contains("raw.githubusercontent.com"));
        assertTrue(mirrors.get(1).contains("gh-proxy.com"));
        assertTrue(mirrors.get(2).contains("mirror.ghproxy.com"));
    }

    @Test
    void mirrors_userConfig_overridesDefault() {
        List<String> custom = List.of(url("success"), url("fail"));
        when(config.getStringList("models-cloud.mirrors")).thenReturn(custom);
        List<String> mirrors = manager.mirrors();
        assertEquals(custom, mirrors);
    }

    // ====================== pin / unpin ======================

    @Test
    void pin_writesMarkerFile_andIsPinnedTrue() {
        assertFalse(manager.isPinned("hutao"));
        assertTrue(manager.pin("hutao"));
        assertTrue(manager.isPinned("hutao"));
    }

    @Test
    void unpin_removesMarkerFile() {
        manager.pin("hutao");
        assertTrue(manager.unpin("hutao"));
        assertFalse(manager.isPinned("hutao"));
    }

    @Test
    void unpin_whenNotPinned_returnsTrue() {
        // deleteIfExists 对不存在的文件返回 true
        assertTrue(manager.unpin("hutao"));
    }

    // ====================== install ======================

    @Test
    void installModel_pinned_skipsDownload() {
        manager.pin("hutao");
        // pinned 的模型不访问网络,无需 stub mirrors
        assertEquals(ModelCloudManager.InstallResult.PINNED_SKIPPED, manager.installModel("hutao"));
    }

    @Test
    void installModel_notInIndex_returnsNotFound() throws Exception {
        // 构造只含成功索引的缓存（不带 models）
        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, "{\"version\":1,\"models\":[],\"featured\":null}");

        assertEquals(ModelCloudManager.InstallResult.NOT_FOUND, manager.installModel("nonexistent"));
    }

    @Test
    void installModel_success_writesFile() throws Exception {
        // 需要一个能返回模型的索引 —— 直接写缓存含 hutao
        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        // mirrors 候选全部 404（未注册路径）→ fallback 到 info.url() 的 mock /model 端点
        when(config.getStringList("models-cloud.mirrors"))
                .thenReturn(List.of(url("b1"), url("b2")));

        assertEquals(ModelCloudManager.InstallResult.INSTALLED, manager.installModel("hutao"));
        assertTrue(manager.isInstalled("hutao"));
        Path installed = tmp.resolve("models/hutao.yml");
        assertTrue(Files.exists(installed));
    }

    @Test
    void installModel_alreadyInstalledSameVersion_returnsAlreadyInstalled() throws Exception {
        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        // 已有 hutao.yml + hutao.version 相同
        Files.writeString(tmp.resolve("models/hutao.yml"), "name: hutao\n");
        Files.writeString(tmp.resolve("models/hutao.version"), "1.2.0");

        assertEquals(ModelCloudManager.InstallResult.ALREADY_INSTALLED, manager.installModel("hutao"));
    }
}