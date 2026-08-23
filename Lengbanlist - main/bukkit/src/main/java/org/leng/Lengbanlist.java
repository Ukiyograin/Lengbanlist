package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.leng.commands.*;
import org.leng.listeners.*;
import org.leng.manager.*;
import org.leng.utils.GitHubUpdateChecker;
import org.leng.utils.AutoUpdateManager;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;
import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.PlatformHolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import org.leng.web.WebServer;

public class Lengbanlist extends JavaPlugin implements LengbanlistPlatform {
    private static Lengbanlist instance;
    public BanManager banManager;
    public MuteManager muteManager;
    public WarnManager warnManager;
    public ReportManager reportManager;
    public IpAssociationManager ipAssociationManager;
    public WebServer webServer;
    public SchedulerUtils.SchedulerTask broadcastTask;
    private SchedulerUtils.SchedulerTask historyCleanupTask;
    private SchedulerUtils.SchedulerTask expiryReminderTask;
    private boolean isBroadcast;
    private FileConfiguration broadcastFC;
    private FileConfiguration chatConfig;
    private ModelChoiceListener modelChoiceListener;
    private String hitokoto;
    private ModelManager modelManager;
    private DatabaseManager databaseManager;
    private FileConfiguration eulaFC;
    private AuditManager auditManager;
    private GuiSessionManager guiSessionManager;
    private ImmunityManager immunityManager;
    private EscalationManager escalationManager;
    private AltsCommand altsCommand;

    private boolean eulaAgreed = false;
    private boolean initializationFailed = false;

@Override
public void onLoad() {
    instance = this;
    PlatformHolder.set(this);

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

    saveDefaultConfig();

    File configFile = new File(getDataFolder(), "config.yml");
    boolean firstLoad = !configFile.exists();
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
        getConfig().set("update-check.enabled", true);
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            getLogger().warning("写入 update-check 配置失败: " + e.getMessage());
        }
    }

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

    databaseManager = new DatabaseManager(this);
    try {
        databaseManager.initialize();
        new StorageMigrationManager(this, databaseManager).migrateYamlIfNeeded();
    } catch (Exception e) {
        getLogger().severe("数据库初始化失败，插件将停止启用: " + e.getMessage());
        e.printStackTrace();
        initializationFailed = true;
        return;
    }

    banManager = new BanManager(this);
    muteManager = new MuteManager(this);
    warnManager = new WarnManager(this);
    reportManager = new ReportManager(this);
    ipAssociationManager = new IpAssociationManager(this);
    webServer = new WebServer(this);
    auditManager = new AuditManager(this);
    guiSessionManager = new GuiSessionManager();
    immunityManager = new ImmunityManager(this);
    escalationManager = new EscalationManager(this);
    isBroadcast = getConfig().getBoolean("opensendtime");
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
    getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new GuiCleanupListener(Lengbanlist.this), this);
    altsCommand = new AltsCommand(Lengbanlist.this);

    getCommand("lban").setExecutor(new LengbanlistCommand("lban", Lengbanlist.this));
    BanCommand banCmd = new BanCommand(Lengbanlist.this);
    setFeatureExecutor("ban", "ban", banCmd);
    if (isFeatureEnabled("ban")) {
        getCommand("ban").setTabCompleter(banCmd);
    }
    BanIpCommand banIpCmd = new BanIpCommand(Lengbanlist.this);
    setFeatureExecutor("ban-ip", "ban-ip", banIpCmd);
    if (isFeatureEnabled("ban-ip")) {
        getCommand("ban-ip").setTabCompleter(banIpCmd);
    }
    setFeatureExecutor("unban", "unban", new UnbanCommand(Lengbanlist.this));
    WarnCommand warnCmd = new WarnCommand(Lengbanlist.this);
    setFeatureExecutor("warn", "warn", warnCmd);
    if (isFeatureEnabled("warn")) {
        getCommand("warn").setTabCompleter(warnCmd);
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
    if (isFeatureEnabled("history")) {
        getCommand("history").setTabCompleter(historyCmd);
    }
    setFeatureExecutor("mute", "mute", new MuteCommand(Lengbanlist.this));
    setFeatureExecutor("mute", "unmute", new UnmuteCommand(Lengbanlist.this));
    setFeatureExecutor("mute", "listmute", new ListMuteCommand(Lengbanlist.this));
    setFeatureExecutor("getip", "getip", new GetIPCommand(Lengbanlist.this));
    setFeatureExecutor("staffchat", "sc", new StaffChatCommand(Lengbanlist.this));
    setFeatureExecutor("alts", "alts", altsCommand);

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

    new Metrics(Lengbanlist.this, 24495);

    if (getConfig().getBoolean("features.auto-update", false)) {
        getLogger().info("§a自动更新功能已启用，正在检查更新...");
        SchedulerUtils.runAsyncDelayed(this, this::checkUpdate, 5000);
    } else if (getConfig().getBoolean("features.update-check", false)) {
        SchedulerUtils.runAsync(this, GitHubUpdateChecker::checkUpdate);
    }

    if (isFeatureEnabled("broadcast") && isBroadcast) {
        startBroadcastTask();
    }

    if (getConfig().getBoolean("web.enabled", false)) {
        webServer.start();
    }

    startHistoryCleanupTask();

    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        new org.leng.placeholder.PlaceholderAPIHook(Lengbanlist.this).register();
        getServer().getConsoleSender().sendMessage(prefix() + "§a已接入 PlaceholderAPI，可使用 %lengbanlist_*% 占位符");
    }

    if (isFeatureEnabled("expiry-reminder")) {
        long periodTicks = Math.max(20L, getConfig().getInt("expiry-reminder.interval", 60) * 20L);
        expiryReminderTask = SchedulerUtils.runTaskTimerAsynchronously(this, new ExpiryReminderTask(this), 200L, periodTicks);
    }
}

public void reloadWebServer() {
    try {
        boolean enabled = getConfig().getBoolean("web.enabled", false);
        if (enabled && !webServer.isRunning()) {
            webServer.start();
        } else if (!enabled && webServer.isRunning()) {
            webServer.stop();
        } else if (enabled && webServer.isRunning()) {
            webServer.stop();
            webServer.start();
        }
    } catch (Exception e) {
        getLogger().severe("Web 服务器重载失败: " + e.getMessage());
        e.printStackTrace();
    }
}

@Override
public void onDisable() {
    getServer().getConsoleSender().sendMessage(prefix() + "§k§4正在收拾行李qwq...");

    if (broadcastTask != null) broadcastTask.cancel();
    if (historyCleanupTask != null) historyCleanupTask.cancel();
    if (expiryReminderTask != null) expiryReminderTask.cancel();
    if (webServer != null) webServer.stop();

    if (eulaAgreed) {
        try {
            saveBroadcastConfig();
            if (databaseManager != null) databaseManager.close();
        } catch (Exception e) {
            getLogger().warning("保存配置文件时出错: " + e.getMessage());
        }
    }

    getServer().getConsoleSender().sendMessage(prefix() + "§f期待我们的下一次相遇！");
}

    private void startBroadcastTask() {
        long interval = getConfig().getInt("sendtime") * 1200L;
        long delay = 200L;
        broadcastTask = SchedulerUtils.runTaskTimer(this,
                new BroadCastBanCountMessage(), delay, interval);
    }

    private void startHistoryCleanupTask() {
        historyCleanupTask = SchedulerUtils.runTaskTimerAsynchronously(this, () -> {
            databaseManager.deactivateExpiredBans();
        }, 6000L, 72000L);
    }

    public String prefix() {
        return getConfig().getString("prefix");
    }

    public static Lengbanlist getInstance() {
        return instance;
    }

    public static CommandMap getCommandMap() {
        CommandMap commandMap = null;
        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commandMap;
    }

    public boolean isBroadcastEnabled() {
        return isBroadcast;
    }

    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }

    private void setFeatureExecutor(String feature, String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            return;
        }
        if (!isFeatureEnabled(feature)) {
            // 功能禁用时释放该命令，避免钩住并拦截其他插件注册的同名命令（如 /report）
            getLogger().info("功能 " + feature + " 已禁用，/"+ commandName + " 命令已取消注册，可被其他插件接管。");
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

    public void sendFeatureDisabled(CommandSender sender) {
        Utils.sendMessage(sender, prefix() + "§c该功能已被管理员禁用。");
    }

    public void setBroadcastEnabled(boolean broadcastEnabled) {
        this.isBroadcast = broadcastEnabled;
        if (!isFeatureEnabled("broadcast")) {
            if (broadcastTask != null) broadcastTask.cancel();
            return;
        }
        if (isBroadcast) {
            startBroadcastTask();
        } else {
            if (broadcastTask != null) {
                broadcastTask.cancel();
            }
        }
    }

private void unregisterCommands() {
    try {
        CommandMap commandMap = getCommandMap();
        if (commandMap != null) {
            String[] commands = {"lban", "ban", "ban-ip", "unban", "warn", "unwarn", "check",
                               "report", "admin", "kick", "info", "allowmsg", "warnmsg", "setban", "history",
                               "mute", "unmute", "listmute"};

            for (String commandName : commands) {
                org.bukkit.command.Command command = commandMap.getCommand(commandName);
                if (command != null) {
                    command.unregister(commandMap);
                }
            }
        }
    } catch (Exception e) {
        getLogger().warning("取消注册命令时出现错误: " + e.getMessage());
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

    public BanManager getBanManager() {
        return banManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public WarnManager getWarnManager() {
        return warnManager;
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

    public AuditManager getAuditManager() {
        return auditManager;
    }

    public GuiSessionManager getGuiSessionManager() {
        return guiSessionManager;
    }

    public ImmunityManager getImmunityManager() {
        return immunityManager;
    }

    @Override
    public boolean canPunish(int operatorWeight, String targetName) {
        return immunityManager.canPunish(operatorWeight, targetName);
    }

    @Override
    public boolean canPunishTarget(int operatorWeight, String target) {
        return immunityManager.canPunishTarget(operatorWeight, target);
    }

    @Override
    public int getWebOperatorWeight() {
        return immunityManager.getWebOperatorWeight();
    }

    public EscalationManager getEscalationManager() {
        return escalationManager;
    }

    public AltsCommand getAltsCommand() {
        return altsCommand;
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
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "我不说了，嘿嘿~";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String jsonResponse = response.toString();
            String hitokoto = jsonResponse.split("\"hitokoto\":\"")[1].split("\"")[0];
            String from = jsonResponse.split("\"from\":\"")[1].split("\"")[0];
            return hitokoto + " —— " + from;
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

    @Override
    public String getConfigString(String path, String def) {
        return getConfig().getString(path, def);
    }

    @Override
    public int getConfigInt(String path, int def) {
        return getConfig().getInt(path, def);
    }

    @Override
    public boolean getConfigBoolean(String path, boolean def) {
        return getConfig().getBoolean(path, def);
    }

    @Override
    public List<String> getConfigStringList(String path) {
        return getConfig().getStringList(path);
    }

    @Override
    public Object getConfigValue(String path) {
        return getConfig().get(path);
    }

    @Override
    public boolean isConfigurationSection(String path) {
        return getConfig().isConfigurationSection(path);
    }

    @Override
    public List<String> getConfigurationSectionKeys(String path) {
        if (!getConfig().isConfigurationSection(path)) return new ArrayList<>();
        return new ArrayList<>(getConfig().getConfigurationSection(path).getKeys(false));
    }

    @Override
    public void setConfigValue(String path, Object value) {
        getConfig().set(path, value);
    }

    @Override
    public void saveConfigFile() {
        saveConfig();
    }

    @Override
    public void broadcastMessage(String message) {
        getServer().broadcastMessage(message);
    }

    @Override
    public void runSync(Runnable task) {
        SchedulerUtils.runTask(this, task);
    }

    @Override
    public org.leng.platform.CancellableTask runSyncCancellable(Runnable task) {
        SchedulerUtils.SchedulerTask schedulerTask = SchedulerUtils.runTask(this, task);
        return schedulerTask::cancel;
    }

    @Override
    public void kickPlayerIfOnline(String playerName, String message) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            player.kickPlayer(message);
        }
    }

    @Override
    public int getOnlinePlayerCount() {
        return getServer().getOnlinePlayers().size();
    }

    @Override
    public int getMaxPlayers() {
        return getServer().getMaxPlayers();
    }

    @Override
    public String getBroadcastString(String path, String def) {
        return broadcastFC == null ? def : broadcastFC.getString(path, def);
    }

    @Override
    public void reloadConfigFiles() {
        reloadConfig();
        File broadcastFile = new File(getDataFolder(), "broadcast.yml");
        if (broadcastFile.exists()) {
            try {
                broadcastFC.load(broadcastFile);
            } catch (Exception e) {
                getLogger().warning("重载broadcast.yml失败: " + e.getMessage());
            }
        }
        File chatConfigFile = new File(getDataFolder(), "chatconfig.yml");
        if (chatConfigFile.exists()) {
            try {
                chatConfig.load(chatConfigFile);
            } catch (Exception e) {
                getLogger().warning("重载chatconfig.yml失败: " + e.getMessage());
            }
        }
    }

    @Override
    public InputStream getResourceStream(String path) {
        return getResource(path);
    }
}
