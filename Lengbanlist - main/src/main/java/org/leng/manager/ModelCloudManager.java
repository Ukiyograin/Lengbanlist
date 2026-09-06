package org.leng.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;
import org.leng.utils.HttpHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * 模型云端仓库管理器 —— 从独立 GitHub 仓库拉取角色模型。
 *
 * <p>仓库结构约定（{@code Serendisand/Lengbanlist-Models}）:
 * <pre>
 *   /index.json                  ← 模型清单 + 月度精选
 *   /models/&lt;id&gt;/&lt;id&gt;.yml         ← 单个模型
 *   /models/&lt;id&gt;/meta.json          ← 可选元数据(version/author/tags)
 * </pre>
 *
 * <p>拉取策略:
 * <ul>
 *   <li>镜像列表（按顺序尝试,首个成功即用）</li>
 *   <li>本地缓存 {@code plugins/Lengbanlist/models/.cache/index.json},24h 过期</li>
 *   <li>失败静默回退（启动 / 定时任务 不阻塞主流程）</li>
 * </ul>
 *
 * <p>P1 范围:fetchIndex + 镜像回退 + 本地缓存 + 失败回退。
 * 模型文件下载 / pin / 命令集成在后续阶段。
 */
public class ModelCloudManager {

    private static final String DEFAULT_REPO = "Serendisand/Lengbanlist-Models";
    private static final String DEFAULT_BRANCH = "main";
    private static final long INDEX_CACHE_TTL_MS = 24L * 60 * 60 * 1000;
    private static final int HTTP_TIMEOUT_MS = 5000;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Lengbanlist plugin;
    private final AtomicReference<ModelIndex> cachedIndex = new AtomicReference<>();
    private volatile long cacheLoadedAt = 0;

    public ModelCloudManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    // ====================== 数据类 ======================

    public record ModelInfo(String id, String name, String version, String author, String url, String sha256) {
        public static ModelInfo fromJson(JsonObject obj) {
            return new ModelInfo(
                    str(obj, "id"),
                    str(obj, "name"),
                    str(obj, "version"),
                    str(obj, "author"),
                    str(obj, "url"),
                    str(obj, "sha256")
            );
        }
    }

    public record FeaturedModel(String month, String modelId, String title, String description) {
        public static FeaturedModel fromJson(JsonObject obj) {
            return new FeaturedModel(
                    str(obj, "month"),
                    str(obj, "modelId"),
                    str(obj, "title"),
                    str(obj, "description")
            );
        }
    }

    public record ModelIndex(int version, String updated, List<ModelInfo> models, FeaturedModel featured) {
        public static ModelIndex fromJson(JsonObject obj) {
            int version = obj.has("version") ? obj.get("version").getAsInt() : 1;
            String updated = str(obj, "updated");
            List<ModelInfo> models = new ArrayList<>();
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("models");
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        models.add(ModelInfo.fromJson(el.getAsJsonObject()));
                    }
                }
            }
            FeaturedModel featured = obj.has("featured") && obj.get("featured").isJsonObject()
                    ? FeaturedModel.fromJson(obj.getAsJsonObject("featured"))
                    : null;
            return new ModelIndex(version, updated, models, featured);
        }
    }

    // ====================== 配置 ======================

    /** 模型 id 白名单：小写字母/数字/连字符,≤32 字符。防路径穿越。 */
    private static final java.util.regex.Pattern ID_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9-]{1,32}");

    /** 只允许 https 下载（防 http 明文注入 / 中间人篡改）。loopback 例外用于本地调试。 */
    private static final String HTTPS_PREFIX = "https://";

    private static boolean isAllowedUrl(String url) {
        return url != null && (url.startsWith(HTTPS_PREFIX)
                || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://localhost"));
    }

    /** 校验模型 id 是否合法（拒绝 ../、/、\ 等路径穿越字符）。 */
    public static boolean isValidModelId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public String repo() {
        String repo = plugin.getConfig().getString("models-cloud.repo", DEFAULT_REPO);
        // 仓库名格式: owner/name（字母数字 -_.）
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            repo = DEFAULT_REPO;
        }
        return repo;
    }

    public String branch() {
        String branch = plugin.getConfig().getString("models-cloud.branch", DEFAULT_BRANCH);
        if (branch == null || !branch.matches("[A-Za-z0-9_.-]+")) {
            branch = DEFAULT_BRANCH;
        }
        return branch;
    }

    public List<String> mirrors() {
        List<String> list = plugin.getConfig().getStringList("models-cloud.mirrors");
        if (list == null || list.isEmpty()) {
            // 默认镜像链：主源 + 镜像回退
            String baseRaw = "https://raw.githubusercontent.com/" + repo() + "/" + branch() + "/index.json";
            list = List.of(
                    baseRaw,
                    "https://gh-proxy.com/" + baseRaw,
                    "https://mirror.ghproxy.com/" + baseRaw
            );
        } else {
            // 只保留 https（防 http 明文注入）；loopback 例外
            list = list.stream().filter(ModelCloudManager::isAllowedUrl).toList();
        }
        return list;
    }

    // ====================== 拉取 ======================

    /**
     * 同步获取模型索引（按镜像列表逐个尝试,首个成功即用）。
     * 失败返回空 Optional,不抛异常。
     */
    public Optional<ModelIndex> fetchIndex() {
        for (String url : mirrors()) {
            try (HttpHelper http = new HttpHelper(HTTP_TIMEOUT_MS, HTTP_TIMEOUT_MS)) {
                String body = http.get(url, "Lengbanlist-ModelCloud/1.0", "application/json");
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                ModelIndex index = ModelIndex.fromJson(obj);
                cachedIndex.set(index);
                cacheLoadedAt = System.currentTimeMillis();
                writeIndexCache(body);
                return Optional.of(index);
            } catch (IOException | InterruptedException e) {
                plugin.getLogger().log(Level.WARNING, "[ModelCloud] 镜像拉取失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[ModelCloud] 解析失败: " + url + " — " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 异步获取索引（不阻塞调用线程）。失败返回空 Optional。
     */
    public CompletableFuture<Optional<ModelIndex>> fetchIndexAsync() {
        return CompletableFuture.supplyAsync(this::fetchIndex);
    }

    /**
     * 取索引 —— 优先内存缓存,其次磁盘缓存,最后网络。
     * 缓存过期时间由 {@link #INDEX_CACHE_TTL_MS} 控制。
     */
    public Optional<ModelIndex> getIndex() {
        ModelIndex mem = cachedIndex.get();
        if (mem != null && System.currentTimeMillis() - cacheLoadedAt < INDEX_CACHE_TTL_MS) {
            return Optional.of(mem);
        }
        Optional<ModelIndex> disk = readIndexCache();
        if (disk.isPresent()) {
            cachedIndex.set(disk.get());
            cacheLoadedAt = System.currentTimeMillis();
            return disk;
        }
        return fetchIndex();
    }

    // ====================== 缓存 IO ======================

    private Path cacheFile() {
        return plugin.getDataFolder().toPath().resolve("models/.cache/index.json");
    }

    private void writeIndexCache(String body) {
        try {
            Path p = cacheFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 写入索引缓存失败", e);
        }
    }

    private Optional<ModelIndex> readIndexCache() {
        Path p = cacheFile();
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        try {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            return Optional.of(ModelIndex.fromJson(obj));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 读取索引缓存失败", e);
            return Optional.empty();
        }
    }

    // ====================== 业务方法 ======================

    /**
     * 当前月份是否匹配 featured.month（用于 GUI banner）。
     * 格式约定 yyyy-MM。
     */
    public boolean isFeaturedForCurrentMonth(FeaturedModel featured) {
        if (featured == null || featured.month() == null) return false;
        return featured.month().equals(currentMonth());
    }

    public Optional<FeaturedModel> currentFeatured() {
        return getIndex().map(ModelIndex::featured).filter(this::isFeaturedForCurrentMonth);
    }

    public static String currentMonth() {
        return LocalDate.now().format(MONTH_FMT);
    }

    private static String str(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
    }

    // ====================== 模型安装 / pin ======================

    /** 本地模型存放目录（与 ModelManager.loadCustomModels 扫描目录一致）。 */
    public Path localModelsDir() {
        return plugin.getDataFolder().toPath().resolve("models");
    }

    private Path modelFile(String id) {
        return localModelsDir().resolve(id + ".yml");
    }

    private Path pinnedFile(String id) {
        return localModelsDir().resolve(id + ".pinned");
    }

    /** 该模型是否已锁定（云端更新不覆盖本地）。 */
    public boolean isPinned(String id) {
        return Files.exists(pinnedFile(id));
    }

    /** 锁定模型:云端更新不再覆盖。返回是否成功。 */
    public boolean pin(String id) {
        try {
            Path p = pinnedFile(id);
            Files.createDirectories(p.getParent());
            Files.writeString(p, "", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 写入 pin 标记失败: " + id, e);
            return false;
        }
    }

    /** 解除锁定。文件不存在（本来就没锁）也算成功。 */
    public boolean unpin(String id) {
        try {
            Files.deleteIfExists(pinnedFile(id));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 删除 pin 标记失败: " + id, e);
            return false;
        }
    }

    /** 本地是否已安装该模型文件。 */
    public boolean isInstalled(String id) {
        return Files.exists(modelFile(id));
    }

    /**
     * 从云端索引中查找模型。若索引未加载,先尝试拉取/磁盘缓存。
     */
    public Optional<ModelInfo> findInIndex(String id) {
        Optional<ModelIndex> idx = getIndex();
        if (idx.isEmpty()) {
            return Optional.empty();
        }
        String lower = id.toLowerCase();
        return idx.get().models().stream()
                .filter(m -> m.id().equalsIgnoreCase(lower))
                .findFirst();
    }

    /**
     * 安装（下载）指定模型到本地。
     *
     * <p>规则:
     * <ul>
     *   <li>已 pinned 的模型跳过（保留本地）</li>
     *   <li>已安装且版本一致跳过（幂等）</li>
     *   <li>version=0.0.0 视为"尚未上架"的占位条目,拒绝安装</li>
     *   <li>下载内容需包含合法的 YAML name 字段,否则拒绝安装</li>
     * </ul>
     */
    public InstallResult installModel(String id) {
        if (!isValidModelId(id)) {
            return InstallResult.NOT_FOUND;
        }
        String lower = id.toLowerCase();
        if (isPinned(lower)) {
            return InstallResult.PINNED_SKIPPED;
        }
        Optional<ModelInfo> found = findInIndex(lower);
        if (found.isEmpty()) {
            return InstallResult.NOT_FOUND;
        }
        if (isPlaceholder(found.get())) {
            return InstallResult.NOT_FOUND;
        }
        if (isInstalled(lower) && isCurrent(lower, found.get().version())) {
            return InstallResult.ALREADY_INSTALLED;
        }
        return downloadModel(found.get());
    }

    /**
     * 同步:下载索引中所有非 pinned、非占位且未安装 / 版本落后的模型。
     * 返回本次安装数量。
     */
    public int syncAll() {
        Optional<ModelIndex> idx = getIndex();
        if (idx.isEmpty()) {
            return 0;
        }
        int installed = 0;
        for (ModelInfo info : idx.get().models()) {
            if (isPinned(info.id()) || isPlaceholder(info)) {
                continue;
            }
            if (isInstalled(info.id()) && isCurrent(info.id(), info.version())) {
                continue;
            }
            if (downloadModel(info) == InstallResult.INSTALLED) {
                installed++;
            }
        }
        return installed;
    }

    /** version=0.0.0 表示索引中的占位条目（模型尚未上架）。 */
    private boolean isPlaceholder(ModelInfo info) {
        return info.version() == null || info.version().isEmpty() || "0.0.0".equals(info.version());
    }

    /** 本地版本是否与云端一致（依据 <id>.version 记录）。 */
    private boolean isCurrent(String id, String version) {
        Path meta = localModelsDir().resolve(id + ".version");
        if (!Files.exists(meta) || version == null) {
            return false;
        }
        try {
            return Files.readString(meta, StandardCharsets.UTF_8).trim().equals(version);
        } catch (IOException e) {
            return false;
        }
    }

    private InstallResult downloadModel(ModelInfo info) {
        if (info.id() == null || info.id().isEmpty()) {
            return InstallResult.FAILED;
        }
        for (String url : modelDownloadCandidates(info)) {
            // 只允许 https（防 http 明文注入）；loopback 例外
            if (!isAllowedUrl(url)) {
                continue;
            }
            try (HttpHelper http = new HttpHelper(HTTP_TIMEOUT_MS, HTTP_TIMEOUT_MS)) {
                String body = http.get(url, "Lengbanlist-ModelCloud/1.0", "text/yaml");
                // 基础校验:必须包含 name 字段,防止垃圾响应被写进本地
                if (!body.contains("name:")) {
                    plugin.getLogger().warning("[ModelCloud] 模型 " + info.id() + " 下载内容缺少 name 字段,拒绝安装 (" + url + ")");
                    return InstallResult.FAILED;
                }
                Path target = modelFile(info.id());
                Files.createDirectories(target.getParent());
                Files.writeString(target, body, StandardCharsets.UTF_8);
                if (info.version() != null && !info.version().isEmpty()) {
                    Files.writeString(localModelsDir().resolve(info.id() + ".version"), info.version(), StandardCharsets.UTF_8);
                }
                plugin.getLogger().info("[ModelCloud] 模型已安装: " + info.id() + " (" + info.name() + " v" + info.version() + ") 来源 " + url);
                return InstallResult.INSTALLED;
            } catch (IOException | InterruptedException e) {
                plugin.getLogger().log(Level.FINE, "[ModelCloud] 下载候选失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return InstallResult.FAILED;
                }
            }
        }
        plugin.getLogger().warning("[ModelCloud] 模型下载失败: " + info.id() + "（所有镜像均不可用）");
        return InstallResult.FAILED;
    }

    /**
     * 生成模型下载候选 URL 列表：
     * 优先各镜像 base（与 index.json 同源,天然绕过直连证书/墙问题），
     * 最后兜底索引里声明的直连 url。
     */
    List<String> modelDownloadCandidates(ModelInfo info) {
        List<String> result = new ArrayList<>();
        for (String indexUrl : mirrors()) {
            String base = indexUrl.endsWith("/index.json")
                    ? indexUrl.substring(0, indexUrl.length() - "index.json".length())
                    : (indexUrl.endsWith("/") ? indexUrl : indexUrl + "/");
            result.add(base + "models/" + info.id() + "/" + info.id() + ".yml");
        }
        if (info.url() != null && !info.url().isEmpty() && !result.contains(info.url())) {
            result.add(info.url());
        }
        return result;
    }

    /** 安装结果枚举。 */
    public enum InstallResult {
        INSTALLED,
        ALREADY_INSTALLED,
        PINNED_SKIPPED,
        NOT_FOUND,
        FAILED
    }
}