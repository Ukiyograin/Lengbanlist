package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandMap;
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
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Lengbanlist extends JavaPlugin {
    private static Lengbanlist instance;
    public BanManager banManager;
    public MuteManager muteManager;
    public WarnManager warnManager;
    public ReportManager reportManager;
    public SchedulerUtils.SchedulerTask broadcastTask;
    private SchedulerUtils.SchedulerTask historyCleanupTask;
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
    saveDefaultConfig();
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

    SchedulerUtils.runAsync(this, () -> {
        hitokoto = getHitokoto();
    });

    getServer().getConsoleSender().sendMessage(prefix() + "§f原神§2正在加载");
    if (hitokoto != null) {
        getServer().getConsoleSender().sendMessage(prefix() + ModelManager.getInstance().getCurrentModelName() + "§6偷偷告诉你: §e" + hitokoto);
    }
    getServer().getConsoleSender().sendMessage(prefix() + "§f哇！传送锚点已解锁，当前Model: " + ModelManager.getInstance().getCurrentModelName());

    getServer().getPluginManager().registerEvents(new PlayerJoinListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new ChatListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new OpJoinListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new ChestUIListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new AnvilGUIListener(Lengbanlist.this), Lengbanlist.this);
    modelChoiceListener = new ModelChoiceListener(Lengbanlist.this);
    getServer().getPluginManager().registerEvents(modelChoiceListener, Lengbanlist.this);

    getCommand("lban").setExecutor(new LengbanlistCommand("lban", Lengbanlist.this));
    getCommand("ban").setExecutor(new BanCommand(Lengbanlist.this));
    getCommand("ban-ip").setExecutor(new BanIpCommand(Lengbanlist.this));
    getCommand("unban").setExecutor(new UnbanCommand(Lengbanlist.this));
    getCommand("warn").setExecutor(new WarnCommand(Lengbanlist.this));
    getCommand("unwarn").setExecutor(new UnwarnCommand(Lengbanlist.this));
    getCommand("check").setExecutor(new CheckCommand(Lengbanlist.this));
    getCommand("report").setExecutor(new ReportCommand(Lengbanlist.this));
    getCommand("admin").setExecutor(new AdminReportCommand(Lengbanlist.this));
    getCommand("kick").setExecutor(new KickCommand(Lengbanlist.this));
    getCommand("info").setExecutor(new InfoCommand(Lengbanlist.this));
    getCommand("allowmsg").setExecutor(new AllowMsgCommand(Lengbanlist.this));
    getCommand("warnmsg").setExecutor(new WarnMsgCommand(Lengbanlist.this));
    getCommand("setban").setExecutor(new SetBanCommand(Lengbanlist.this));
    HistoryCommand historyCmd = new HistoryCommand(Lengbanlist.this);
    getCommand("history").setExecutor(historyCmd);
    getCommand("history").setTabCompleter(historyCmd);
    getCommand("mute").setExecutor(new MuteCommand(Lengbanlist.this));
    getCommand("unmute").setExecutor(new UnmuteCommand(Lengbanlist.this));
    getCommand("listmute").setExecutor(new ListMuteCommand(Lengbanlist.this));

    getServer().getConsoleSender().sendMessage("§b  _                      ____              _      _     _   ");
    getServer().getConsoleSender().sendMessage("§6 | |                    |  _ \\            | |    (_)   | |  ");
    getServer().getConsoleSender().sendMessage("§b | |     ___ _ __   __ _| |_) | __ _ __ | |     _ ___| |_ ");
    getServer().getConsoleSender().sendMessage("§f | |    / _ \\ '_ \\ / _` |  _ < / _` | '_ \\| |    | / __| __|");
    getServer().getConsoleSender().sendMessage("§b | |___|  __/ | | | (_| | |_) | (_| | | | | |____| \\__ \\ |_ ");
    getServer().getConsoleSender().sendMessage("§6 |______\\___|_| |_|\\__,_|___/ \\__,_|_| |_|______|_|___/\\__|");
    getServer().getConsoleSender().sendMessage("§b                   __/ |                                    ");
    getServer().getConsoleSender().sendMessage("§f                   |___/                                     ");
    getServer().getConsoleSender().sendMessage("§6当前运行版本：v" + getPluginVersion());
    getServer().getConsoleSender().sendMessage("§3当前运行在：" + Bukkit.getServer().getVersion());

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

    startHistoryCleanupTask();
}

@Override
public void onDisable() {
    getServer().getConsoleSender().sendMessage(prefix() + "§k§4正在卸载");

    if (broadcastTask != null) broadcastTask.cancel();
    if (historyCleanupTask != null) historyCleanupTask.cancel();

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
        int retentionDays = getConfig().getInt("history-retention-days", 7);
        if (retentionDays <= 0) return;
        historyCleanupTask = SchedulerUtils.runTaskTimerAsynchronously(this, () -> {
            databaseManager.deactivateExpiredBans();
            databaseManager.cleanupOldBans(retentionDays);
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

    public void sendFeatureDisabled(CommandSender sender) {
        Utils.sendMessage(sender, prefix() + "§c此功能已被管理员禁用。");
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

    public ChestUIListener getChestUIListener() {
        return new ChestUIListener(this);
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
}
