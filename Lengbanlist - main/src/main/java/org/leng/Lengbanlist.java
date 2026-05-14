package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.leng.commands.*;
import org.leng.listeners.*;
import org.leng.manager.*;
import org.leng.utils.GitHubUpdateChecker;
import org.leng.utils.AutoUpdateManager;
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
    public BukkitTask task;
    private boolean isBroadcast;
    public FileConfiguration ipFC;
    private FileConfiguration banFC;
    private FileConfiguration banIpFC;
    private FileConfiguration muteFC;
    private FileConfiguration broadcastFC;
    private FileConfiguration warnFC;
    private FileConfiguration reportFC; 
    private FileConfiguration chatConfig;
    private ModelChoiceListener modelChoiceListener;
    private String hitokoto;
    private ModelManager modelManager;
    private DatabaseManager databaseManager;
    private FileConfiguration eulaFC;
    
    private boolean isFolia = false;
    private boolean eulaAgreed = false;
    private boolean initializationFailed = false;

@Override
public void onLoad() {
    try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        isFolia = true;
    } catch (ClassNotFoundException e) {
        isFolia = false;
    }
    
    saveDefaultConfig();
    instance = this;
    
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
    
    // EULA 同意后才初始化
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

    banManager = new BanManager();
    muteManager = new MuteManager();
    warnManager = new WarnManager();
    reportManager = new ReportManager(this);
    isBroadcast = getConfig().getBoolean("opensendtime");
    modelManager = ModelManager.getInstance();

    File chatConfigFile = new File(getDataFolder(), "chatconfig.yml");
    if (!chatConfigFile.exists()) {
        chatConfigFile.getParentFile().mkdirs();
        saveResource("chatconfig.yml", false);
    }
    chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);
    
    hitokoto = getHitokoto();

    File broadcastFile = new File(getDataFolder(), "broadcast.yml"); 
    if (!broadcastFile.exists()) {
        broadcastFile.getParentFile().mkdirs();
        try { 
            broadcastFile.createNewFile(); 
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
    }
    broadcastFC = YamlConfiguration.loadConfiguration(broadcastFile);

    if (!broadcastFC.contains("default-message")) {
        broadcastFC.set("default-message", "§b当前封禁人数：%s 人，封禁IP数：%i 人，总计：%t 人");
        try {
            broadcastFC.save(broadcastFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
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
    getServer().getConsoleSender().sendMessage(prefix() + ModelManager.getInstance().getCurrentModelName() + "§6偷偷告诉你: §e" + hitokoto);
    getServer().getConsoleSender().sendMessage(prefix() + "§f哇！传送锚点已解锁，当前Model: " + ModelManager.getInstance().getCurrentModelName());

    // 注册事件监听器
    getServer().getPluginManager().registerEvents(new PlayerJoinListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new ChatListener(Lengbanlist.this), Lengbanlist.this); 
    getServer().getPluginManager().registerEvents(new OpJoinListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new ChestUIListener(Lengbanlist.this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new AnvilGUIListener(Lengbanlist.this), Lengbanlist.this);
    modelChoiceListener = new ModelChoiceListener(Lengbanlist.this);
    getServer().getPluginManager().registerEvents(modelChoiceListener, Lengbanlist.this);

    // 注册命令
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
    
    // 先检查是否需要更新
    if (isFeatureEnabled("auto-update")) {
        getLogger().info("§a自动更新功能已启用，正在检查更新...");
        // 延迟5秒执行自动更新，让服务器完全启动
        runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000); // 等待5秒
                    checkUpdate();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    } else if (isFeatureEnabled("update-check")) {
        runAsync(new Runnable() {
            @Override
            public void run() {
                GitHubUpdateChecker.checkUpdate();
            }
        });
    }

    if (isFeatureEnabled("broadcast") && isBroadcast) {
        startBroadcastTask();
    }
}

@Override
public void onDisable() {
    getServer().getConsoleSender().sendMessage(prefix() + "§k§4正在卸载");

    if (task != null) task.cancel();

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
        
        if (isFolia) {
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                java.lang.reflect.Method getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                
                java.lang.reflect.Method runAtFixedRateMethod = globalScheduler.getClass().getMethod("runAtFixedRate", 
                    JavaPlugin.class, java.util.function.Consumer.class, long.class, long.class);
                
                Runnable broadcastTask = new Runnable() {
                    @Override
                    public void run() {
                        if (Lengbanlist.this.isEnabled()) {
                            new BroadCastBanCountMessage().run();
                        }
                    }
                };
                
                java.util.function.Consumer<Object> taskConsumer = t -> broadcastTask.run();
                runAtFixedRateMethod.invoke(globalScheduler, this, taskConsumer, delay, interval);
                
            } catch (Exception e) {
                if (!isFolia) {
                    task = new BroadCastBanCountMessage().runTaskTimer(this, delay, interval);
                }
            }
        } else {
            task = new BroadCastBanCountMessage().runTaskTimer(this, delay, interval);
        }
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
            if (task != null) task.cancel();
            return;
        }
        if (isBroadcast) {
            startBroadcastTask();
        } else {
            if (task != null) {
                task.cancel();
            }
        }
    }

private void unregisterCommands() {
    try {
        CommandMap commandMap = getCommandMap();
        if (commandMap != null) {
            String[] commands = {"lban", "ban", "ban-ip", "unban", "warn", "unwarn", "check", 
                               "report", "admin", "kick", "info", "allowmsg", "warnmsg", "setban"};
            
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

    public FileConfiguration getBanFC() {
        return banFC;
    }

    public FileConfiguration getBanIpFC() {
        return banIpFC;
    }

    public FileConfiguration getMuteFC() {
        return muteFC;
    }

    public FileConfiguration getBroadcastFC() {
        return broadcastFC;
    }

    public FileConfiguration getWarnFC() {
        return warnFC;
    }

    public FileConfiguration getIpFC() {
        return ipFC;
    }

    public FileConfiguration getReportFC() {
        return reportFC;
    }

    public void saveBanConfig() {
    }

    public void saveBanIpConfig() {
    }

    public void saveMuteConfig() {
    }

    public void saveBroadcastConfig() {
        try {
            broadcastFC.save(new File(getDataFolder(), "broadcast.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveWarnConfig() {
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

    public FileConfiguration getChatConfig() {
        return chatConfig;
    }

    public void checkUpdate() {
        new AutoUpdateManager(this).checkAndAutoUpdate();
    }
    
    public boolean isFolia() {
        return isFolia;
    }
    
public void runAsync(Runnable task) {
    if (isFolia) {
        try {
            // Folia 的异步调度器
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            java.lang.reflect.Method getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
            Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
            
            java.lang.reflect.Method runNowMethod = asyncScheduler.getClass().getMethod("runNow", 
                JavaPlugin.class, java.util.function.Consumer.class);
            
            java.util.function.Consumer<Object> taskConsumer = t -> task.run();
            runNowMethod.invoke(asyncScheduler, this, taskConsumer);
        } catch (Exception e) {
            // 如果反射失败，回退到传统方法（但可能在 Folia 中不可用）
            getLogger().warning("Folia async scheduler failed, falling back to traditional method");
            // 在 Folia 中不要使用传统的异步调度器
            task.run(); // 直接在当前线程运行
        }
    } else {
        // 传统服务器的异步调度
        Bukkit.getScheduler().runTaskAsynchronously(this, task);
    }
}
}