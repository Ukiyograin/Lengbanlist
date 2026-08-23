package org.leng.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.leng.common.LengbanlistConstants;
import org.leng.config.SimpleYamlConfig;
import org.leng.manager.AuditManager;
import org.leng.manager.BanManager;
import org.leng.manager.DatabaseManager;
import org.leng.manager.EscalationManager;
import org.leng.manager.IpAssociationManager;
import org.leng.manager.ModelManager;
import org.leng.manager.MuteManager;
import org.leng.manager.ReportManager;
import org.leng.manager.WarnManager;
import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.PlatformHolder;
import org.leng.utils.GitHubUpdateChecker;
import org.leng.web.WebServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class FabricLengbanlist implements ModInitializer, LengbanlistPlatform {
    public static final String MOD_ID = "lengbanlist";
    private final Logger logger = Logger.getLogger("Lengbanlist");
    private File dataFolder;
    private SimpleYamlConfig config;
    private SimpleYamlConfig broadcastConfig;
    private SimpleYamlConfig chatConfig;
    private SimpleYamlConfig eulaConfig;
    private DatabaseManager databaseManager;
    private BanManager banManager;
    private MuteManager muteManager;
    private WarnManager warnManager;
    private ReportManager reportManager;
    private IpAssociationManager ipAssociationManager;
    private ModelManager modelManager;
    private AuditManager auditManager;
    private EscalationManager escalationManager;
    private WebServer webServer;
    private FabricServerFeatures serverFeatures;
    private Object server;
    private Thread updateCheckThread;
    private Thread broadcastThread;
    private Thread historyCleanupThread;
    private boolean initialized;
    private boolean stopped;

    @Override
    public void onInitialize() {
        PlatformHolder.set(this);
        dataFolder = FabricLoader.getInstance().getConfigDir().resolve("Lengbanlist").toFile();
        dataFolder.mkdirs();
        try {
            // 先只释放并加载 EULA：未同意时绝不生成其他配置文件或模型目录，避免污染用户首次安装。
            if (!loadEulaConfig()) {
                logger.severe("==================================================");
                logger.severe("插件启用被终止：您需要同意EULA才能使用本插件！");
                logger.severe("请编辑 plugins/Lengbanlist/eula.yml 文件");
                logger.severe("==================================================");
                return;
            }
            // 仅在同意 EULA 后才生成自定义模型目录和示例文件。
            File modelsDir = new File(dataFolder, "models");
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
            }
            File exampleModelFile = new File(modelsDir, "example-custom-model.yml");
            if (!exampleModelFile.exists()) {
                copyDefault("models/example-custom-model.yml");
            }
            loadConfigFiles();
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            banManager = new BanManager(this);
            muteManager = new MuteManager(this);
            warnManager = new WarnManager(this);
            reportManager = new ReportManager(this);
            ipAssociationManager = new IpAssociationManager(this);
            auditManager = new AuditManager(this);
            escalationManager = new EscalationManager(this);
            modelManager = ModelManager.getInstance();
            webServer = new WebServer(this);
            serverFeatures = new FabricServerFeatures(this);
            logger.info(prefix() + "§f原神§2正在加载");
            FabricCommandBridge.register(this);
            FabricJoinBridge.register(this);
            FabricChatBridge.register(this);
            FabricServerLifecycleBridge.register(this);
            startMetrics();
            if (getConfigBoolean("features.update-check", false)) {
                updateCheckThread = new Thread(GitHubUpdateChecker::checkUpdate, "Lengbanlist Update Check");
                updateCheckThread.start();
            }
            logger.info(prefix() + "§f哇！传送锚点已解锁，当前Model: " + ModelManager.getInstance().getCurrentModelName());
        } catch (Exception e) {
            logger.severe("数据库初始化失败，插件将停止启用: " + e.getMessage());
            e.printStackTrace();
            logger.severe("==================================================");
            logger.severe("插件启用被终止：数据库初始化失败，请检查 database 配置和数据库连接。");
            logger.severe("==================================================");
        }
    }

    private void loadConfigFiles() throws IOException {
        copyDefault("config.yml");
        copyDefault("broadcast.yml");
        copyDefault("chatconfig.yml");
        ensureConfigVersion();
        try (InputStream input = Files.newInputStream(new File(dataFolder, "config.yml").toPath())) {
            config = SimpleYamlConfig.load(input);
        }
        try (InputStream input = Files.newInputStream(new File(dataFolder, "broadcast.yml").toPath())) {
            broadcastConfig = SimpleYamlConfig.load(input);
        }
        try (InputStream input = Files.newInputStream(new File(dataFolder, "chatconfig.yml").toPath())) {
            chatConfig = SimpleYamlConfig.load(input);
        }
    }

    /**
     * 仅释放并加载 eula.yml，用于在 EULA 未同意时阻止其他配置文件被写出。
     * @return EULA 是否已被同意
     */
    private boolean loadEulaConfig() throws IOException {
        copyDefault("eula.yml");
        try (InputStream input = Files.newInputStream(new File(dataFolder, "eula.yml").toPath())) {
            eulaConfig = SimpleYamlConfig.load(input);
        }
        return isEulaAgreed();
    }

    private boolean isEulaAgreed() {
        String agreement = eulaConfig.getString("I have read and agree to the above terms", "no").trim();
        return "yes".equalsIgnoreCase(agreement) || "true".equalsIgnoreCase(agreement);
    }

    private void copyDefault(String name) throws IOException {
        File target = new File(dataFolder, name);
        if (target.exists()) return;
        try (InputStream input = getResourceStream(name)) {
            if (input != null) {
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void ensureConfigVersion() throws IOException {
        Path path = new File(dataFolder, "config.yml").toPath();
        String text = new String(Files.readAllBytes(path), "UTF-8");
        if (!text.contains("config-version:")) {
            if (!text.endsWith("\n")) text += "\n";
            text += "\n# 配置版本\nconfig-version: 1\n";
            Files.write(path, text.getBytes("UTF-8"));
        }
    }

    private void startMetrics() {
        new FabricMetrics(this, LengbanlistConstants.BSTATS_SERVICE_ID);
    }

    public void setServer(Object server) {
        this.server = server;
    }

    public void onServerStarted(Object server) {
        this.server = server;
        if (initialized || stopped) return;
        initialized = true;
        startPeriodicTasks();
        if (getConfigBoolean("web.enabled", false)) {
            webServer.start();
        }
    }

    public void onServerStopping() {
        if (stopped) return;
        stopped = true;
        interrupt(broadcastThread);
        interrupt(historyCleanupThread);
        interrupt(updateCheckThread);
        if (webServer != null) {
            webServer.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void startPeriodicTasks() {
        if (isFeatureEnabled("broadcast") && getConfigBoolean("opensendtime", false)) {
            long interval = Math.max(1, getConfigInt("sendtime", 5)) * 60L * 1000L;
            broadcastThread = repeat("Lengbanlist Broadcast", 10_000L, interval, this::broadcastBanCount);
        }
        historyCleanupThread = repeat("Lengbanlist History Cleanup", 5 * 60_000L, 60 * 60_000L,
                () -> databaseManager.deactivateExpiredBans());
    }

    private Thread repeat(String name, long initialDelay, long interval, Runnable task) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(initialDelay);
                while (!stopped) {
                    task.run();
                    Thread.sleep(interval);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.warning("定时任务执行出错: " + e.getMessage());
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void broadcastBanCount() {
        Object currentServer = server;
        if (currentServer == null || ReflectionSupport.onlinePlayers(currentServer).isEmpty()) return;
        String message = getBroadcastString("default-message", "")
                .replace("%s", String.valueOf(banManager.getBanList().size()))
                .replace("%i", String.valueOf(banManager.getBanIpList().size()))
                .replace("%t", String.valueOf(banManager.getBanList().size() + banManager.getBanIpList().size()));
        ReflectionSupport.execute(currentServer, () -> ReflectionSupport.broadcast(currentServer, message));
    }

    private void interrupt(Thread thread) {
        if (thread != null) thread.interrupt();
    }

    public void handleJoin(Object player, Object server) {
        this.server = server;
        serverFeatures.onPlayerJoin(player, server);
    }

    public boolean handleChat(Object player, String message) {
        return serverFeatures.onChat(player, message);
    }

    public void executeConsoleLike(Object source, String commandName, String[] args) {
        FabricCommandBridge.execute(this, source, commandName, args);
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPluginVersion() {
        // 从 fabric.mod.json 元数据读取真实版本，避免与构建版本脱节
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .filter(Objects::nonNull)
                .filter(version -> !version.isEmpty())
                .orElse("unknown");
    }

    @Override
    public String getConfigString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public int getConfigInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public boolean getConfigBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public List<String> getConfigStringList(String path) {
        return config.getStringList(path);
    }

    @Override
    public Object getConfigValue(String path) {
        return config.getObject(path);
    }

    @Override
    public boolean isConfigurationSection(String path) {
        return config.isConfigurationSection(path);
    }

    @Override
    public List<String> getConfigurationSectionKeys(String path) {
        return config.getConfigurationSectionKeys(path);
    }

    @Override
    public void setConfigValue(String path, Object value) {
        config.set(path, value);
    }

    @Override
    public void saveConfigFile() {
        try {
            saveFlatConfigValue(new File(dataFolder, "config.yml").toPath(), "Model", config.getString("Model", "Default"));
        } catch (IOException e) {
            logger.warning("保存配置文件时出错: " + e.getMessage());
        }
    }

    private void saveFlatConfigValue(Path path, String key, String value) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith(key + ":")) {
                String comment = "";
                int commentIndex = lines.get(i).indexOf('#');
                if (commentIndex >= 0) comment = " " + lines.get(i).substring(commentIndex);
                lines.set(i, key + ": \"" + value + "\"" + comment);
                updated = true;
                break;
            }
        }
        if (!updated) {
            lines.add(key + ": \"" + value + "\"");
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    @Override
    public String prefix() {
        return getConfigString("prefix", "");
    }

    @Override
    public boolean isFeatureEnabled(String feature) {
        return getConfigBoolean("features." + feature, true);
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public BanManager getBanManager() {
        return banManager;
    }

    @Override
    public MuteManager getMuteManager() {
        return muteManager;
    }

    @Override
    public WarnManager getWarnManager() {
        return warnManager;
    }

    @Override
    public ReportManager getReportManager() {
        return reportManager;
    }

    @Override
    public AuditManager getAuditManager() {
        return auditManager;
    }

    public EscalationManager getEscalationManager() {
        return escalationManager;
    }

    @Override
    public IpAssociationManager getIpAssociationManager() {
        return ipAssociationManager;
    }

    @Override
    public ModelManager getModelManager() {
        return modelManager;
    }

    @Override
    public void broadcastMessage(String message) {
        if (server == null) {
            logger.info(message);
            return;
        }
        ReflectionSupport.broadcast(server, message);
    }

    @Override
    public void runSync(Runnable task) {
        if (server == null) {
            task.run();
            return;
        }
        ReflectionSupport.execute(server, task);
    }

    @Override
    public org.leng.platform.CancellableTask runSyncCancellable(Runnable task) {
        runSync(task);
        return org.leng.platform.CancellableTask.NOOP;
    }

    @Override
    public void kickPlayerIfOnline(String playerName, String message) {
        if (server == null) return;
        Object player = ReflectionSupport.findPlayer(server, playerName);
        if (player != null) ReflectionSupport.kick(player, message);
    }

    @Override
    public int getOnlinePlayerCount() {
        return server == null ? 0 : ReflectionSupport.onlineCount(server);
    }

    @Override
    public int getMaxPlayers() {
        return server == null ? 0 : ReflectionSupport.maxPlayers(server);
    }

    @Override
    public String getBroadcastString(String path, String def) {
        return broadcastConfig == null ? def : broadcastConfig.getString(path, def);
    }

    public String getChatConfigString(String path, String def) {
        return chatConfig == null ? def : chatConfig.getString(path, def);
    }

    public int getChatConfigInt(String path, int def) {
        return chatConfig == null ? def : chatConfig.getInt(path, def);
    }

    public List<String> getChatConfigStringList(String path) {
        return chatConfig == null ? java.util.Collections.emptyList() : chatConfig.getStringList(path);
    }

    @Override
    public void reloadConfigFiles() {
        try {
            loadConfigFiles();
            ModelManager.getInstance().reloadModel();
        } catch (IOException e) {
            logger.warning("重载broadcast.yml失败: " + e.getMessage());
        }
    }

    @Override
    public void reloadWebServer() {
        try {
            boolean enabled = getConfigBoolean("web.enabled", false);
            if (enabled && !webServer.isRunning()) {
                webServer.start();
            } else if (!enabled && webServer.isRunning()) {
                webServer.stop();
            } else if (enabled && webServer.isRunning()) {
                webServer.stop();
                webServer.start();
            }
        } catch (Exception e) {
            logger.severe("Web 服务器重载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public InputStream getResourceStream(String path) {
        return FabricLengbanlist.class.getClassLoader().getResourceAsStream(path);
    }
}
