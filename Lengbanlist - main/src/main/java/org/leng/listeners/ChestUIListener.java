package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;
import org.leng.manager.ModelManager;

public class ChestUIListener implements Listener {
    private final Lengbanlist plugin;

    public ChestUIListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§bLengbanlist")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) {
            return;
        }

        String command = meta.getLore().get(0).replace("§7", "");
        Player player = (Player) event.getWhoClicked();

        switch (command) {
            case "/lban add":
                openAnvilForBan(player, "playerID");
                break;
            case "/lban remove":
                openAnvilForUnban(player);
                break;
            case "/lban mute":
                openAnvilForMute(player, "playerID");
                break;
            case "/lban unmute":
                openAnvilForUnmute(player);
                break;
            case "/lban model":
                ModelManager.getInstance().openModelSelectionUI(player);
                break;
            case "/lban ipban":
                openAnvilForIPBan(player, "ip");
                break;
            default:
                plugin.getServer().dispatchCommand(player, command);
                break;
        }
    }

    public void openAnvilForBan(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b封禁玩家 - 输入" + (step.equals("playerID") ? "玩家ID或IP" : (step.equals("time") ? "时间" : "原因")));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "ban"));
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }

    public void openAnvilForMute(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b禁言玩家 - 输入" + (step.equals("playerID") ? "玩家ID" : "原因"));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "mute"));
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }

    public void openAnvilForUnban(Player player) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b解封玩家");
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入玩家ID或IP");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "unban"));
    }

    public void openAnvilForUnmute(Player player) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b解除禁言");
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入玩家ID");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "unmute"));
    }

    public void openAnvilForIPBan(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b封禁IP - 输入" + (step.equals("ip") ? "IP地址" : (step.equals("time") ? "时间" : "原因")));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "ipban"));
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!event.getView().getTitle().contains("封禁玩家") && 
            !event.getView().getTitle().contains("解封玩家") && 
            !event.getView().getTitle().contains("禁言玩家") && 
            !event.getView().getTitle().contains("解除禁言") && 
            !event.getView().getTitle().contains("封禁IP")) {
            return;
        }

        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a输入内容");
            item.setItemMeta(meta);
        }
        event.getInventory().setItem(0, item);
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("封禁玩家") && 
            !event.getView().getTitle().contains("解封玩家") && 
            !event.getView().getTitle().contains("禁言玩家") && 
            !event.getView().getTitle().contains("解除禁言") && 
            !event.getView().getTitle().contains("封禁IP")) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        String action = player.getMetadata("lengbanlist-action").get(0).asString();
        String step = player.getMetadata("lengbanlist-step").get(0).asString();

        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String input = item.getItemMeta().getDisplayName();

        switch (action) {
            case "ban":
                handleBan(player, step, input);
                break;
            case "unban":
                handleUnban(player, input);
                break;
            case "mute":
                handleMute(player, step, input);
                break;
            case "unmute":
                handleUnmute(player, input);
                break;
            case "ipban":
                handleIPBan(player, step, input);
                break;
        }
    }

    private void handleBan(Player player, String step, String input) {
        if (step.equals("playerID")) {
            player.setMetadata("lengbanlist-playerID", new FixedMetadataValue(plugin, input));
            openAnvilForBan(player, "time");
        } else if (step.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, "§c时间格式无效，请使用以下格式：10s, 5m, 2h, 7d, 1w, 1M, 1y");
                return;
            }
            player.setMetadata("lengbanlist-time", new FixedMetadataValue(plugin, input));
            openAnvilForBan(player, "reason");
        } else if (step.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            long duration = TimeUtils.parseTime(time);
            if (playerID.contains(".")) {
                plugin.getBanManager().banIp(new BanIpEntry(playerID, player.getName(), duration, input, false));
                Utils.sendMessage(player, "§a封禁IP成功：" + playerID);
            } else {
                plugin.getBanManager().banPlayer(new BanEntry(playerID, player.getName(), duration, input, false));
                Utils.sendMessage(player, "§a封禁玩家成功：" + playerID);
            }
            clearMetadata(player);
        }
    }

    private void handleUnban(Player player, String input) {
        if (input.contains(".")) {
            plugin.getBanManager().unbanIp(input);
            Utils.sendMessage(player, "§a解封IP成功：" + input);
        } else {
            plugin.getBanManager().unbanPlayer(input);
            Utils.sendMessage(player, "§a解封玩家成功：" + input);
        }
        clearMetadata(player);
    }

    private void handleMute(Player player, String step, String input) {
        if (step.equals("playerID")) {
            player.setMetadata("lengbanlist-playerID", new FixedMetadataValue(plugin, input));
            openAnvilForMute(player, "reason");
        } else if (step.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            MuteEntry entry = new MuteEntry(playerID, player.getName(), System.currentTimeMillis(), input);
            plugin.getMuteManager().mutePlayer(entry);
            Utils.sendMessage(player, "§a禁言玩家成功：" + playerID);
            clearMetadata(player);
        }
    }

    private void handleUnmute(Player player, String input) {
        plugin.getMuteManager().unmutePlayer(input);
        Utils.sendMessage(player, "§a解除禁言成功：" + input);
        clearMetadata(player);
    }

    private void handleIPBan(Player player, String step, String input) {
        if (step.equals("ip")) {
            player.setMetadata("lengbanlist-ip", new FixedMetadataValue(plugin, input));
            openAnvilForIPBan(player, "time");
        } else if (step.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, "§c时间格式无效，请使用以下格式：10s, 5m, 2h, 7d, 1w, 1M, 1y");
                return;
            }
            player.setMetadata("lengbanlist-time", new FixedMetadataValue(plugin, input));
            openAnvilForIPBan(player, "reason");
        } else if (step.equals("reason")) {
            String ip = player.getMetadata("lengbanlist-ip").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            long duration = TimeUtils.parseTime(time);
            plugin.getBanManager().banIp(new BanIpEntry(ip, player.getName(), duration, input, false));
            Utils.sendMessage(player, "§a封禁IP成功：" + ip);
            clearMetadata(player);
        }
    }

    private void clearMetadata(Player player) {
        player.removeMetadata("lengbanlist-action", plugin);
        player.removeMetadata("lengbanlist-step", plugin);
        player.removeMetadata("lengbanlist-playerID", plugin);
        player.removeMetadata("lengbanlist-time", plugin);
        player.removeMetadata("lengbanlist-ip", plugin);
    }
}