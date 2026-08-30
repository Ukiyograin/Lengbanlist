package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.leng.commands.*;
import org.leng.listeners.*;
import org.leng.manager.*;
import org.leng.utils.GitHubUpdateChecker;
import org.leng.utils.AutoUpdateManager;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.lang.reflect.Field;

import org.leng.web.WebServer;

public class Lengbanlist extends JavaPlugin {
    private static Lengbanlist instance;
    public BanManager banManager;
    public MuteManager muteManager;
    public SyncManager syncManager;
    public WarnManager warnManager;
    public AuditManager auditManager;
    public ReportManager reportManager;
    public IpAssociationManager ipAssociationManager;
    public WebServer webServer;
    public SchedulerUtils.SchedulerTask broadcastTask;
    private SchedulerUtils.SchedulerTask historyCleanupTask;
    private SchedulerUtils.SchedulerTask expiryReminderTask;
    private ImmunityManager immunityManager;
    private EscalationManager escalationManager;
    private GuiSessionManager guiSessionManager;
    private AltsCommand altsCommand;
    private boolean isBroadcast;
    private FileConfiguration broadcastFC;
    private FileConfiguration chatConfig;
    private ModelChoiceListener modelChoiceListener;
    private String hitokoto;
    private ModelManager modelManager;
    private DatabaseManager databaseManager;
    private FileConfiguration eulaFC;

    private boolean eulaAgreed = false;
    private boolean initializationFailed = false;

@Override
public void onLoad() {
    instance = this;

    SchedulerUtils.init(this);

    File eulaFile = new File(getDataFolder(), "eula.yml");
    if (!eulaFile.exists()) {
        eulaFile.getParentFile().mkdirs();
        saveResource("eula.yml", false);
        eulaAgreed = false;
        return;
    }

    eulaFC = YamlConfiguration.loadConfiguration(eulaFile);
    Object agreementValue = eulaFC.get("I have read and agree to the above terms");
    String agreement = agreementValue == null ? "no" : String.valueOf(agreementValue).trim();
    eulaAgreed = "yes".equalsIgnoreCase(agreement) || "true".equalsIgnoreCase(agreement);

    if (!eulaAgreed) {
        return;
    }

    File configFile = new File(getDataFolder(), "config.yml");
    boolean firstLoad = !configFile.exists();
    saveDefaultConfig();
    if (firstLoad && getConfig().getBoolean("model-auto-detect", true)) {
        String language = java.util.Locale.getDefault().getLanguage();
        String detectedModel = language != null && language.toLowerCase().startsWith("zh") ? "Default" : "English";
        getConfig().set("Model", detectedModel);
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            getLogger().warning("写入模型自动检测结果失败: " + e.getMessage());
        }
        getLogger().info("首次加载，根据系统语言（" + language + "）自动选择模型: " + detectedModel);
    }

    if (!getConfig().contains("update-check.enabled")) {
        getConfig().set("update-check.enabled", getConfig().getBoolean("features.update-check", true));
        saveConfig();
    }

    databaseManager = new DatabaseManager(this);
    try {
        databaseManager.initialize();
        new StorageMigrationManager(this, databaseManager).migrateYamlIfNeeded();
        muteManager = new MuteManager(this);
    } catch (Exception e) {
        getLogger().severe("数据库初始化失败，插件将停止启用: " + e.getMessage());
        e.printStackTrace();
        initializationFailed = true;
        return;
    }

    banManager = new BanManager(this);
    syncManager = new SyncManager(this);
    warnManager = new WarnManager(this);
    immunityManager = new ImmunityManager(this);
    escalationManager = new EscalationManager(this);
    guiSessionManager = new GuiSessionManager();
    auditManager = new AuditManager(this);
    reportManager = new ReportManager(this);
    ipAssociationManager = new IpAssociationManager(this);
    webServer = new WebServer(this);
    isBroadcast = getConfig().getBoolean("opensendtime");

    // 生成自定义模型目录和示例文件
    File modelsDir = new File(getDataFolder(), "models");
    if (!modelsDir.exists()) {
        modelsDir.mkdirs();
    }
    File exampleModelFile = new File(modelsDir, "example-custom-model.yml");
    if (!exampleModelFile.exists()) {
        saveResource("models/example-custom-model.yml", false);
        getLogger().info("已生成自定义模型示例文件: models/example-custom-model.yml");
    }

    modelManager = ModelManager.getInstance();

    File chatConfigFile = new File(getDataFolder(), "chatconfig.yml");
    if (!chatConfigFile.exists()) {
        chatConfigFile.getParentFile().mkdirs();
        saveResource("chatconfig.yml", false);
    }
    chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);

    File broadcastFile = new File(getDataFolder(), "broadcast.yml");
    if (!broadcastFile.exists()) {
        broadcastFile.getParentFile().mkdirs();
        saveResource("broadcast.yml", false);
    }
    broadcastFC = YamlConfiguration.loadConfiguration(broadcastFile);

}

@Override
public void onEnable() {
    if (initializationFailed) {
        getLogger().severe("==================================================");
        getLogger().severe("插件启用被终止：数据库初始化失败，请检查 database 配置和数据库连接。");
        getLogger().severe("==================================================");
        Bukkit.getPluginManager().disablePlugin(Lengbanlist.this);
        return;
    }

    if (!eulaAgreed) {
        getLogger().severe("==================================================");
        getLogger().severe("插件启用被终止：您需要同意EULA才能使用本插件！");
        getLogger().severe("请编辑 plugins/Lengbanlist/eula.yml 文件");
        getLogger().severe("==================================================");
        Bukkit.getPluginManager().disablePlugin(Lengbanlist.this);
        return;
    }

    if (!Lengbanlist.this.isEnabled()) {
        return;
    }

    getServer().getConsoleSender().sendMessage(prefix() + "§f原神§2正在加载");
    SchedulerUtils.runAsync(this, () -> {
        String fetchedHitokoto = getHitokoto();
        if (!Lengbanlist.this.isEnabled()) {
            return;
        }
        SchedulerUtils.runTask(this, () -> {
            if (!Lengbanlist.this.isEnabled()) {
                return;
            }
            hitokoto = fetchedHitokoto;
            getServer().getConsoleSender().sendMessage(prefix() + ModelManager.getInstance().getCurrentModelName() + "§6偷偷告诉你: §e" + hitokoto);
        });
    });
    getServer().getConsoleSender().sendMessage(prefix() + "§f哇！传送锚点已解锁，当前Model: " + ModelManager.getInstance().getCurrentModelName());

    getServer().getPluginManager().registerEvents(new PlayerJoinListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new ChatListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new OpJoinListener(Lengbanlist.this), Lengbanlist.this);
    modelChoiceListener = new ModelChoiceListener(Lengbanlist.this);
    getServer().getPluginManager().registerEvents(modelChoiceListener, Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new GuiCleanupListener(this), this);
    
    LengbanlistCommand lbanCmd = new LengbanlistCommand("lban", Lengbanlist.this);
    PluginCommand lban = getCommand("lban");
    if (lban != null) {
        lban.setExecutor(lbanCmd);
        lban.setTabCompleter(lbanCmd);
    }
    registerFeatureCommands();

    getServer().getConsoleSender().sendMessage("§b  _                      ____              _      _     _   ");
    getServer().getConsoleSender().sendMessage("§6 | |                    |  _ \\            | |    (_)   | |  ");
    getServer().getConsoleSender().sendMessage("§b | |     ___ _ __   __ _| |_) | __ _ __ | |     _ ___| |_ ");
    getServer().getConsoleSender().sendMessage("§f | |    / _ \\ '_ \\ / _` |  _ < / _` | '_ \\| |    | / __| __|");
    getServer().getConsoleSender().sendMessage("§b | |___|  __/ | | | (_| | |_) | (_| | | | | |____| \\__ \\ |_ ");
    getServer().getConsoleSender().sendMessage("§6 |______\\___|_| |_|\\__,_|___/ \\__,_|_| |_|______|_|___/\\__|");
    getServer().getConsoleSender().sendMessage("§b                   __/ |                                    ");
    getServer().getConsoleSender().sendMessage("§f                   |___/                                     ");
    getServer().getConsoleSender().sendMessage("§6插件版本：v" + getPluginVersion());
    getServer().getConsoleSender().sendMessage("§3服务端版本：" + Bukkit.getServer().getVersion());

    new Metrics(Lengbanlist.this, 33262);

    if (getConfig().getBoolean("features.auto-update", false)) {
        getLogger().info("§a自动更新功能已启用，正在检查更新...");
        SchedulerUtils.runAsyncDelayed(this, this::checkUpdate, 5000);
    } else if (isUpdateCheckEnabled()) {
        SchedulerUtils.runAsync(this, GitHubUpdateChecker::checkUpdate);
    }

    if (isBroadcast) {
        startBroadcastTask();
    }

    if (getConfig().getBoolean("web.enabled", false)) {
        webServer.start();
    }

    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        new org.leng.placeholder.PlaceholderAPIHook(Lengbanlist.this).register();
        getServer().getConsoleSender().sendMessage(prefix() + "§a已接入 PlaceholderAPI，可使用 %lengbanlist_*% 占位符");
    }

    startHistoryCleanupTask();

    if (syncManager != null) {
        syncManager.startAutoSync();
    }
    
    if (isFeatureEnabled("expiry-reminder")) {
        long periodTicks = Math.max(20L, getConfig().getInt("expiry-reminder.interval", 60) * 20L);
        expiryReminderTask = SchedulerUtils.runTaskTimerAsynchronously(this, new ExpiryReminderTask(this), 200L, periodTicks);
    }
}

/**
 * 重新注册所有 feature 命令。需要从 onEnable、/lban reload、/api/reload 同时调用,
 * 以确保 features.* 切换后命令能立即生效或被释放。
 * 注意:Bukkit 中已被 unregisterCommand 释放的命令无法在不重启的情况下重新注册,
 * 该次刷新后会保持释放状态直到下次重启,并在控制台提示。
 */
public void registerFeatureCommands() {
    BanCommand banCmd = new BanCommand(Lengbanlist.this);
    setFeatureExecutor("ban", "ban", banCmd);
    PluginCommand ban = getCommand("ban");
    if (isFeatureEnabled("ban") && ban != null) {
        ban.setTabCompleter(banCmd);
    }
    BanIpCommand banIpCmd = new BanIpCommand(Lengbanlist.this);
    setFeatureExecutor("ban-ip", "ban-ip", banIpCmd);
    PluginCommand banIp = getCommand("ban-ip");
    if (isFeatureEnabled("ban-ip") && banIp != null) {
        banIp.setTabCompleter(banIpCmd);
    }
    setFeatureExecutor("unban", "unban", new UnbanCommand(Lengbanlist.this));
    WarnCommand warnCmd = new WarnCommand(Lengbanlist.this);
    setFeatureExecutor("warn", "warn", warnCmd);
    PluginCommand warn = getCommand("warn");
    if (isFeatureEnabled("warn") && warn != null) {
        warn.setTabCompleter(warnCmd);
    }
    setFeatureExecutor("unwarn", "unwarn", new UnwarnCommand(Lengbanlist.this));
    setFeatureExecutor("check", "check", new CheckCommand(Lengbanlist.this));
    setFeatureExecutor("report", "report", new ReportCommand(Lengbanlist.this));
    setFeatureExecutor("admin", "admin", new AdminReportCommand(Lengbanlist.this));
    setFeatureExecutor("kick", "kick", new KickCommand(Lengbanlist.this));
    setFeatureExecutor("info", "info", new InfoCommand(Lengbanlist.this));
    setFeatureExecutor("chat-filter", "allowmsg", new AllowMsgCommand(Lengbanlist.this));
    setFeatureExecutor("warn", "warnmsg", new WarnMsgCommand(Lengbanlist.this));
    setFeatureExecutor("setban", "setban", new SetBanCommand(Lengbanlist.this));
    HistoryCommand historyCmd = new HistoryCommand(Lengbanlist.this);
    setFeatureExecutor("history", "history", historyCmd);
    PluginCommand history = getCommand("history");
    if (isFeatureEnabled("history") && history != null) {
        history.setTabCompleter(historyCmd);
    }
    setFeatureExecutor("mute", "mute", new MuteCommand(Lengbanlist.this));
    setFeatureExecutor("mute", "unmute", new UnmuteCommand(Lengbanlist.this));
    setFeatureExecutor("mute", "listmute", new ListMuteCommand(Lengbanlist.this));
    setFeatureExecutor("getip", "getip", new GetIPCommand(Lengbanlist.this));
    setFeatureExecutor("staffchat", "sc", new StaffChatCommand(Lengbanlist.this));
    altsCommand = new AltsCommand(this);
    setFeatureExecutor("alts", "alts", altsCommand);
    getLogger().info("功能命令刷新完成(features.* 变更已生效)。");
}

public boolean reloadWebServer() {
    boolean enabled = getConfig().getBoolean("web.enabled", false);
    if (enabled && !webServer.isRunning()) {
        return webServer.start();
    } else if (!enabled && webServer.isRunning()) {
        webServer.stop();
        return true;
    } else if (enabled && webServer.isRunning()) {
        webServer.stop();
        return webServer.start();
    }
    return true;
}

@Override
public void onDisable() {
    getServer().getConsoleSender().sendMessage(prefix() + "§k§4正在收拾行李qwq...");

    if (broadcastTask != null) broadcastTask.cancel();
    if (historyCleanupTask != null) historyCleanupTask.cancel();
    if (expiryReminderTask != null) expiryReminderTask.cancel();
    if (syncManager != null) {
        syncManager.stopAutoSync();
    }
    if (webServer != null) webServer.stop();

    if (eulaAgreed) {
        shutdownStorage();
    }

    getServer().getConsoleSender().sendMessage(prefix() + "§f期待我们的下一次相遇！");
}

void shutdownStorage() {
    try {
        if (broadcastFC != null) {
            saveBroadcastConfig();
        }
    } catch (Exception e) {
        getLogger().warning("保存配置文件时出错: " + e.getMessage());
    }
    try {
        if (databaseManager != null) {
            databaseManager.close();
        }
    } catch (Exception e) {
        getLogger().warning("关闭数据库时出错: " + e.getMessage());
    }
}

    private void startBroadcastTask() {
        long interval = Math.max(getConfig().getInt("sendtime") * 1200L, 1200L);
        long delay = 200L;
        broadcastTask = SchedulerUtils.runTaskTimer(this,
                new BroadCastBanCountMessage(), delay, interval);
    }

    private void startHistoryCleanupTask() {
        historyCleanupTask = SchedulerUtils.runTaskTimerAsynchronously(this, () -> {
            databaseManager.deactivateExpiredBans();
            databaseManager.cleanupOldData(Math.max(1, getConfig().getInt("history-retention-days", 7)));
        }, 6000L, 72000L);
    }

    public String prefix() {
        return getConfig().getString("prefix");
    }

    public static Lengbanlist getInstance() {
        return instance;
    }

    public boolean isBroadcastEnabled() {
        return isBroadcast;
    }

    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }

    public boolean isUpdateCheckEnabled() {
        return getConfig().getBoolean("update-check.enabled", true);
    }

    private void setFeatureExecutor(String feature, String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            return;
        }
        if (!isFeatureEnabled(feature)) {
            // 功能禁用时释放该命令名,避免钩住并拦截其他插件注册的同名命令(如 /report)
            getLogger().info("功能 " + feature + " 已禁用,/" + commandName + " 命令已注销,可被其他插件接管。");
            unregisterCommand(command);
            return;
        }
        command.setExecutor(executor);
    }

    private void unregisterCommand(PluginCommand command) {
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap != null) {
                command.unregister(commandMap);
            }
        } catch (Exception e) {
            getLogger().warning("注销命令 " + command.getName() + " 时出现错误: " + e.getMessage());
        }
    }

    /**
     * 通过 Bukkit 内部 SimplePluginManager 反射拿 CommandMap,用于注销命令时告知 server。
     */
    private CommandMap getCommandMap() {
        try {
            org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
            if (pm instanceof SimplePluginManager) {
                Field field = SimplePluginManager.class.getDeclaredField("commandMap");
                field.setAccessible(true);
                return (CommandMap) field.get(pm);
            }
        } catch (Exception e) {
            getLogger().warning("无法获取 CommandMap: " + e.getMessage());
        }
        return null;
    }

    public void sendFeatureDisabled(CommandSender sender) {
        Utils.sendMessage(sender, prefix() + "§c该功能已被管理员禁用。");
    }

    public void setBroadcastEnabled(boolean broadcastEnabled) {
        this.isBroadcast = broadcastEnabled;
        if (isBroadcast) {
            startBroadcastTask();
        } else {
            if (broadcastTask != null) {
                broadcastTask.cancel();
            }
        }
    }

    public String toggleBroadcast() {
        setBroadcastEnabled(!isBroadcastEnabled());
        return isBroadcastEnabled() ? "§a已开启" : "§c已关闭";
    }

    public ModelManager getModelManager() {
        return ModelManager.getInstance();
    }

    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }
    
    public BanManager getBanManager() {
        return banManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public WarnManager getWarnManager() {
        return warnManager;
    }

    public ImmunityManager getImmunityManager() {
        return immunityManager;
    }

    public EscalationManager getEscalationManager() {
        return escalationManager;
    }

    public GuiSessionManager getGuiSessionManager() {
        return guiSessionManager;
    }

    public AltsCommand getAltsCommand() {
        return altsCommand;
    }

    public AuditManager getAuditManager() {
        return auditManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public IpAssociationManager getIpAssociationManager() {
        return ipAssociationManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public ModelChoiceListener getModelChoiceListener() {
        return modelChoiceListener;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FileConfiguration getBroadcastFC() {
        return broadcastFC;
    }

    public FileConfiguration getChatConfig() {
        return chatConfig;
    }

    public void saveBroadcastConfig() {
        try {
            broadcastFC.save(new File(getDataFolder(), "broadcast.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getHitokoto() {
        try {
            URL url = new URL("https://v1.hitokoto.cn/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    return "我不说了，嘿嘿~";
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String jsonResponse = response.toString();
                String hitokoto = jsonResponse.split("\"hitokoto\":\"")[1].split("\"")[0];
                String from = jsonResponse.split("\"from\":\"")[1].split("\"")[0];
                return hitokoto + " —— " + from;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return "我不说了，嘿嘿~";
        }
    }

    public void checkUpdate() {
        new AutoUpdateManager(this).checkAndAutoUpdate();
    }

    public boolean isFolia() {
        return SchedulerUtils.isFolia();
    }
}
