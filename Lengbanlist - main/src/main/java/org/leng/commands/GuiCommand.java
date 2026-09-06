package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.GuiSessionManager;
import org.leng.manager.ModelManager;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 渲染 + 聊天向导一体化控制器（独立 Listener）。
 *
 * <p>从 LengbanlistCommand 拆出：GUI 渲染、点击处理、聊天向导（ban/unban/mute/unmute/ipban 五步流程）。
 * 拆分原因：LengbanlistCommand 1605 行过大，GUI 与路由器职责不同。
 */
public class GuiCommand implements Listener {

    private static final int GUI_PAGE_SIZE = 28;
    private static final int[] GUI_CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Lengbanlist plugin;

    public GuiCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    // ====================== GUI 渲染 ======================

    public void openChestUI(Player player) {
        Inventory chest = Bukkit.createInventory(null, 54, "§bLengbanlist");
        player.openInventory(chest);
        GuiSessionManager gui = plugin.getGuiSessionManager();
        gui.setView(player.getUniqueId(), "menu");
        gui.setPage(player.getUniqueId(), "menu", 0);
        renderGuiMenu(player, chest);
    }

    private void renderGuiMenu(Player player, Inventory chest) {
        ItemStack glass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§7我只是个装饰物");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                chest.setItem(i, glass);
            }
        }

        ItemStack toggleBroadcast = createItem(
                Material.LEVER,
                "§a切换自动广播 (" + (plugin.isBroadcastEnabled() ? "开启" : "关闭") + ")",
                "§7/lban toggle",
                "§7开启或关闭自动广播",
                Sound.BLOCK_LEVER_CLICK,
                player
        );
        ItemStack broadcast = createItem(
                Material.NOTE_BLOCK,
                "§a广播封禁人数",
                "§7/lban a",
                "§7广播当前封禁人数",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack list = createItem(
                Material.WRITABLE_BOOK,
                "§a查看封禁名单",
                "§7/lban list",
                "§7查看被封禁的玩家列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack reload = createItem(
                Material.COMPARATOR,
                "§a重新加载配置",
                "§7/lban reload",
                "§7重新加载插件配置",
                Sound.BLOCK_NOTE_BLOCK_BELL,
                player
        );
        ItemStack addBan = createItem(
                Material.REDSTONE_BLOCK,
                "§a添加封禁",
                "§7/lban add",
                "§7添加一个玩家到封禁名单",
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack removeBan = createItem(
                Material.EMERALD_BLOCK,
                "§a解除封禁",
                "§7/lban remove",
                "§7从封禁名单中移除一个玩家",
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack ipBan = createItem(
                Material.LAVA_BUCKET,
                "§c封禁IP",
                "§7/lban ipban",
                "§7封禁一个IP地址",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack help = createItem(
                Material.BOOK,
                "§a帮助信息",
                "§7/lban help",
                "§7显示帮助信息",
                Sound.BLOCK_NOTE_BLOCK_FLUTE,
                player
        );
        ItemStack model = createItem(
                Material.NAME_TAG,
                "§a切换模型 (" + ModelManager.getInstance().getCurrentModelName() + ")",
                "§7/lban model",
                "§7当前模型: " + ModelManager.getInstance().getCurrentModelName(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                player
        );
        ItemStack sponsor = createItem(
                Material.GOLD_INGOT,
                "§6赞助作者",
                "§7ACTION_SPONSOR",
                "§7点击获取赞助链接：https://afdian.com/a/lengmc",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack mute = createItem(
                Material.BARRIER,
                "§a禁言玩家",
                "§7/lban mute",
                "§7禁言一个玩家",
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack unmute = createItem(
                Material.MILK_BUCKET,
                "§a解除禁言",
                "§7/lban unmute",
                "§7解除一个玩家的禁言",
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack listMute = createItem(
                Material.BOOKSHELF,
                "§a查看禁言列表",
                "§7/lban list-mute",
                "§7查看被禁言的玩家列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack bansList = createItem(
                Material.RED_WOOL,
                "§c封禁列表",
                "VIEW_BANS",
                "§7查看封禁玩家/IP列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack mutesList = createItem(
                Material.GRAY_WOOL,
                "§c禁言列表",
                "VIEW_MUTES",
                "§7查看禁言玩家列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack reportsList = createItem(
                Material.PAPER,
                "§e举报列表",
                "VIEW_REPORTS",
                "§7查看待处理举报列表",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );

        chest.setItem(10, toggleBroadcast);
        chest.setItem(12, broadcast);
        chest.setItem(14, list);
        chest.setItem(16, reload);
        chest.setItem(11, bansList);
        chest.setItem(13, mutesList);
        chest.setItem(15, reportsList);
        chest.setItem(20, addBan);
        chest.setItem(22, removeBan);
        chest.setItem(19, ipBan);
        chest.setItem(24, help);
        chest.setItem(28, model);
        chest.setItem(30, mute);
        chest.setItem(32, unmute);
        chest.setItem(34, listMute);
        chest.setItem(40, sponsor);
    }

    private void renderGuiList(Player player, Inventory inventory, String view) {
        GuiSessionManager gui = plugin.getGuiSessionManager();
        int page = gui.getPage(player.getUniqueId(), view);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§7 ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass);
        }

        int start = page * GUI_PAGE_SIZE;

        if ("bans".equals(view)) {
            List<Object> list = new ArrayList<>();
            list.addAll(plugin.getBanManager().getBanList());
            list.addAll(plugin.getBanManager().getBanIpList());
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                Object entry = list.get(index);
                if (entry instanceof BanEntry) {
                    BanEntry ban = (BanEntry) entry;
                    inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.RED_WOOL,
                            "§c" + ban.getTarget(),
                            "§7处理人：" + ban.getStaff(),
                            "§7原因：" + ban.getReason(),
                            "§7解封时间：" + TimeUtils.timestampToReadable(ban.getTime())));
                } else if (entry instanceof BanIpEntry) {
                    BanIpEntry banIp = (BanIpEntry) entry;
                    inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.BLACK_WOOL,
                            "§c" + banIp.getIp(),
                            "§7处理人：" + banIp.getStaff(),
                            "§7原因：" + banIp.getReason(),
                            "§7解封时间：" + TimeUtils.timestampToReadable(banIp.getTime())));
                }
            }
        } else if ("mutes".equals(view)) {
            List<MuteEntry> list = plugin.getMuteManager().getMuteList();
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                MuteEntry mute = list.get(index);
                inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.GRAY_WOOL,
                        "§c" + mute.getTarget(),
                        "§7处理人：" + mute.getStaff(),
                        "§7原因：" + mute.getReason(),
                        "§7解禁时间：" + TimeUtils.timestampToReadable(mute.getTime())));
            }
        } else if ("reports".equals(view)) {
            List<ReportEntry> list = plugin.getReportManager().getPendingReports();
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                ReportEntry report = list.get(index);
                inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.PAPER,
                        "§e举报编号：" + report.getId(),
                        "§7REPORT:" + report.getId(),
                        "§7被举报人：" + report.getTarget(),
                        "§7举报人：" + report.getReporter(),
                        "§7原因：" + report.getReason()));
            }
        }

        int totalPages = guiTotalPages(view);
        inventory.setItem(45, createGuiItem(Material.ARROW, "§e上一页", "PAGE_PREV", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
        inventory.setItem(48, createGuiItem(Material.BARRIER, "§c返回主菜单", "VIEW_MENU", "§7点击返回主菜单"));
        inventory.setItem(49, createGuiItem(Material.PAPER, "§b" + (page + 1) + " / " + totalPages, "§7页码", "§7使用上一页/下一页按钮翻页"));
        inventory.setItem(53, createGuiItem(Material.ARROW, "§e下一页", "PAGE_NEXT", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
    }

    private int guiTotalPages(String view) {
        int size;
        if ("bans".equals(view)) {
            size = plugin.getBanManager().getBanList().size() + plugin.getBanManager().getBanIpList().size();
        } else if ("mutes".equals(view)) {
            size = plugin.getMuteManager().getMuteList().size();
        } else if ("reports".equals(view)) {
            size = plugin.getReportManager().getPendingReports().size();
        } else {
            size = 0;
        }
        return Math.max(1, (size + GUI_PAGE_SIZE - 1) / GUI_PAGE_SIZE);
    }

    private ItemStack createGuiItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String displayName, String command, String description, Sound sound, Player player) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add(command);
        lore.add(description);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§bLengbanlist")) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        GuiSessionManager gui = plugin.getGuiSessionManager();
        String view = gui.getView(player.getUniqueId());
        if (gui != null && view != null && view.startsWith("alts:")) {
            return;
        }

        if (!plugin.isFeatureEnabled("chest-ui")) {
            plugin.sendFeatureDisabled(player);
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta clickMeta = clickedItem.getItemMeta();
        if (clickMeta.getLore() == null || clickMeta.getLore().isEmpty()) {
            return;
        }

        String command = clickMeta.getLore().get(0).replace("§7", "");

        if (command.startsWith("REPORT:")) {
            player.closeInventory();
            player.performCommand("lban handle " + command.substring("REPORT:".length()) + " auto");
            return;
        }

        switch (command) {
            case "VIEW_BANS":
                if (!plugin.isFeatureEnabled("ban")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                gui.setView(player.getUniqueId(), "bans");
                gui.setPage(player.getUniqueId(), "bans", 0);
                renderGuiList(player, event.getView().getTopInventory(), "bans");
                return;
            case "VIEW_MUTES":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                gui.setView(player.getUniqueId(), "mutes");
                gui.setPage(player.getUniqueId(), "mutes", 0);
                renderGuiList(player, event.getView().getTopInventory(), "mutes");
                return;
            case "VIEW_REPORTS":
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                gui.setView(player.getUniqueId(), "reports");
                gui.setPage(player.getUniqueId(), "reports", 0);
                renderGuiList(player, event.getView().getTopInventory(), "reports");
                return;
            case "VIEW_MENU":
                gui.setView(player.getUniqueId(), "menu");
                gui.setPage(player.getUniqueId(), "menu", 0);
                renderGuiMenu(player, event.getView().getTopInventory());
                return;
            case "PAGE_PREV":
                if (view == null) {
                    return;
                }
                int prevPage = gui.getPage(player.getUniqueId(), view) - 1;
                if (prevPage < 0) {
                    return;
                }
                gui.setPage(player.getUniqueId(), view, prevPage);
                renderGuiList(player, event.getView().getTopInventory(), view);
                return;
            case "PAGE_NEXT":
                if (view == null) {
                    return;
                }
                int nextPage = gui.getPage(player.getUniqueId(), view) + 1;
                if (nextPage >= guiTotalPages(view)) {
                    return;
                }
                gui.setPage(player.getUniqueId(), view, nextPage);
                renderGuiList(player, event.getView().getTopInventory(), view);
                return;
            default:
                if (command.startsWith("/")) {
                    player.closeInventory();
                    switch (command) {
                        case "/lban add":
                            startChatWizard(player, "ban");
                            break;
                        case "/lban remove":
                            startChatWizard(player, "unban");
                            break;
                        case "/lban ipban":
                            startChatWizard(player, "ipban");
                            break;
                        case "/lban model":
                            ModelManager.getInstance().openModelSelectionUI(player);
                            break;
                        case "/lban mute":
                            startChatWizard(player, "mute");
                            break;
                        case "/lban unmute":
                            startChatWizard(player, "unmute");
                            break;
                        default:
                            player.performCommand(command.substring(1));
                            break;
                    }
                } else if (command.equals("ACTION_SPONSOR")) {
                    player.closeInventory();
                    player.spigot().sendMessage(
                            new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§6赞助作者："),
                            Utils.clickableUrl("§e【点击打开爱发电】", "https://afdian.com/a/lengmc")
                    );
                }
                break;
        }
    }

    // ====================== 聊天向导 ======================

    public void startChatWizard(Player player, String action) {
        switch (action) {
            case "ban":
                if (!plugin.isFeatureEnabled("ban")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                break;
            case "unban":
                if (!plugin.isFeatureEnabled("unban")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                break;
            case "mute":
            case "unmute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                break;
            case "ipban":
                if (!plugin.isFeatureEnabled("ban-ip")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                break;
        }
        player.setMetadata("lengbanlist-action", new org.bukkit.metadata.FixedMetadataValue(plugin, action));
        switch (action) {
            case "ban":
                player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "playerID"));
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f玩家名或IP§e：");
                break;
            case "ipban":
                player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "ip"));
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f封禁的IP地址§e：");
                break;
            case "unban":
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解封的玩家名或IP§e：");
                break;
            case "mute":
                player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "playerID"));
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f禁言的玩家名§e：");
                break;
            case "unmute":
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解除禁言的玩家名§e：");
                break;
        }
    }

    public void handleChatWizard(Player player, String input) {
        if (!player.hasMetadata("lengbanlist-action")) return;

        String action = player.getMetadata("lengbanlist-action").get(0).asString();

        switch (action) {
            case "ban":
                if (!plugin.isFeatureEnabled("ban")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                handleBanWizard(player, input);
                break;
            case "unban":
                if (!plugin.isFeatureEnabled("unban")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                BanManager.BanMutationResult unbanResult;
                if (input.contains(".")) {
                    unbanResult = plugin.getBanManager().tryUnbanIp(input, player.getName(), false);
                } else {
                    unbanResult = plugin.getBanManager().tryUnbanPlayer(input, player.getName(), false);
                }
                if (!unbanResult.isApplied()) {
                    BanMutationFeedback.sendFailure(player, unbanResult, input, input.contains("."));
                    if (unbanResult == BanManager.BanMutationResult.DATABASE_ERROR) {
                        return;
                    }
                }
                clearWizard(player);
                break;
            case "mute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                handleMuteWizard(player, input);
                break;
            case "unmute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                plugin.getMuteManager().unmutePlayer(input, player.getName());
                Utils.broadcast(ModelManager.getInstance().getCurrentModel().removeMute(input));
                clearWizard(player);
                break;
            case "ipban":
                if (!plugin.isFeatureEnabled("ban-ip")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                handleIPBanWizard(player, input);
                break;
        }
    }

    private void handleBanWizard(Player player, String input) {
        String step = player.getMetadata("lengbanlist-step").get(0).asString();
        if (step.equals("playerID")) {
            player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁时间§e（如：1d, 7d, forever）：");
        } else if (step.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                return;
            }
            player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁原因§e：");
        } else if (step.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            if (!playerID.contains(".") && !plugin.getImmunityManager().canPunish(player, playerID)) {
                Utils.sendMessage(player, plugin.getModelManager().getCurrentModel().getImmunityDenied(playerID));
                clearWizard(player);
                return;
            }
            long duration;
            boolean isAuto = false;
            if (time.equalsIgnoreCase("auto")) {
                isAuto = true;
                duration = playerID.contains(".")
                        ? plugin.getEscalationManager().resolveIpBan(playerID).durationMillis
                        : plugin.getEscalationManager().resolveBan(playerID).durationMillis;
            } else {
                duration = TimeUtils.parseTime(time);
            }
            if (duration <= 0) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵。");
                return;
            }
            long endTime = TimeUtils.calculateEndTime(duration);
            BanManager.BanMutationResult banResult;
            if (playerID.contains(".")) {
                if (!plugin.isFeatureEnabled("ban-ip")) {
                    plugin.sendFeatureDisabled(player);
                    clearWizard(player);
                    return;
                }
                banResult = plugin.getBanManager().tryBanIp(new BanIpEntry(playerID, player.getName(), endTime, input, isAuto));
            } else {
                banResult = plugin.getBanManager().tryBanPlayer(new BanEntry(playerID, player.getName(), endTime, input, isAuto));
            }
            if (!banResult.isApplied()) {
                BanMutationFeedback.sendFailure(player, banResult, playerID, playerID.contains("."));
                if (banResult == BanManager.BanMutationResult.DATABASE_ERROR) {
                    return;
                }
            }
            clearWizard(player);
        }
    }

    private void handleIPBanWizard(Player player, String input) {
        String step = player.getMetadata("lengbanlist-step").get(0).asString();
        if (step.equals("ip")) {
            if (input.equalsIgnoreCase("-s")) {
                player.setMetadata("lengbanlist-silent", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                Utils.sendMessage(player, plugin.prefix() + "§e已开启静默模式，请输入要§f封禁的IP地址§e：");
                return;
            }
            if (!IpMatcher.isIpv4(input)) {
                Utils.sendMessage(player, plugin.prefix() + "§cIP格式无效喵，请输入合法的 IPv4 地址。");
                return;
            }
            player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁时间§e（如：1d, 7d, forever, auto）：");
        } else if (step.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                return;
            }
            player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁原因§e：");
        } else if (step.equals("reason")) {
            String ip = player.getMetadata("lengbanlist-playerID").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            long duration;
            boolean isAuto = false;
            if (time.equalsIgnoreCase("auto")) {
                isAuto = true;
                duration = TimeUtils.daysToMillis(7);
            } else {
                duration = TimeUtils.parseTime(time);
            }
            if (duration <= 0) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵。");
                return;
            }
            long endTime = TimeUtils.calculateEndTime(duration);
            boolean silent = player.hasMetadata("lengbanlist-silent");
            BanManager.BanMutationResult banResult = plugin.getBanManager().tryBanIp(new BanIpEntry(ip, player.getName(), endTime, input, isAuto), silent);
            if (banResult.isApplied()) {
                Utils.sendMessage(player, plugin.prefix() + "§a封禁IP成功：" + ip);
                clearWizard(player);
            } else {
                BanMutationFeedback.sendFailure(player, banResult, ip, true);
                if (banResult == BanManager.BanMutationResult.DATABASE_ERROR) {
                    return;
                }
                clearWizard(player);
            }
        }
    }

    private void handleMuteWizard(Player player, String input) {
        String step = player.getMetadata("lengbanlist-step").get(0).asString();
        if (step.equals("playerID")) {
            if (input.equalsIgnoreCase("-s")) {
                player.setMetadata("lengbanlist-silent", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                Utils.sendMessage(player, plugin.prefix() + "§e已开启静默模式，请输入要§f禁言的玩家名§e：");
                return;
            }
            player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f禁言时间§e（如：10m, 1d, forever, auto）：");
        } else if (step.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                return;
            }
            player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f禁言原因§e：");
        } else if (step.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            if (!plugin.getImmunityManager().canPunish(player, playerID)) {
                Utils.sendMessage(player, plugin.getModelManager().getCurrentModel().getImmunityDenied(playerID));
                clearWizard(player);
                return;
            }
            long duration;
            if (time.equalsIgnoreCase("auto")) {
                duration = plugin.getEscalationManager().resolveMute(playerID);
            } else {
                duration = TimeUtils.parseTime(time);
            }
            MuteEntry entry = new MuteEntry(playerID, player.getName(), TimeUtils.calculateEndTime(duration), input);
            Long newMuteEnd = plugin.getMuteManager().mutePlayer(entry);
            if (newMuteEnd == null) {
                Utils.sendMessage(player, plugin.prefix() + "§e该目标已有相同时长的禁言记录，未重复禁言。");
                clearWizard(player);
                return;
            }
            if (player.hasMetadata("lengbanlist-silent")) {
                Utils.sendMessage(player, ModelManager.getInstance().getCurrentModel().addMute(playerID, input));
            } else {
                Utils.broadcast(ModelManager.getInstance().getCurrentModel().addMute(playerID, input));
            }
            clearWizard(player);
        }
    }

    private void clearWizard(Player player) {
        player.removeMetadata("lengbanlist-action", plugin);
        player.removeMetadata("lengbanlist-step", plugin);
        player.removeMetadata("lengbanlist-playerID", plugin);
        player.removeMetadata("lengbanlist-time", plugin);
        player.removeMetadata("lengbanlist-silent", plugin);
    }
}