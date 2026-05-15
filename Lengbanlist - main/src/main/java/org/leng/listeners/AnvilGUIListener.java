package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

public class AnvilGUIListener implements Listener {
    private final Lengbanlist plugin;

    public AnvilGUIListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getTitle().contains("封禁玩家") || event.getView().getTitle().contains("解封玩家") || event.getView().getTitle().contains("禁言玩家") || event.getView().getTitle().contains("解除禁言") || event.getView().getTitle().contains("封禁IP"))) {
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
        if (!(event.getView().getTitle().contains("封禁玩家") || event.getView().getTitle().contains("解封玩家") || event.getView().getTitle().contains("禁言玩家") || event.getView().getTitle().contains("解除禁言") || event.getView().getTitle().contains("封禁IP"))) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        MetadataValue action = player.getMetadata("lengbanlist-action").get(0);
        MetadataValue step = player.getMetadata("lengbanlist-step").get(0);
        String actionType = action.asString();
        String currentStep = step.asString();

        if (actionType.equals("ban")) {
            handleBan(player, event, currentStep);
        } else if (actionType.equals("unban")) {
            handleUnban(player, event);
        } else if (actionType.equals("mute")) {
            handleMute(player, event, currentStep);
        } else if (actionType.equals("unmute")) {
            handleUnmute(player, event);
        } else if (actionType.equals("ipban")) {
            handleIPBan(player, event, currentStep);
        }
    }

    private void handleBan(Player player, InventoryClickEvent event, String currentStep) {
        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String input = item.getItemMeta().getDisplayName();

        if (currentStep.equals("playerID")) {
            player.setMetadata("lengbanlist-playerID", new FixedMetadataValue(plugin, input));
            openAnvilForBan(player, "time");
        } else if (currentStep.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, "§c时间格式无效，请使用以下格式：10s, 5m, 2h, 7d, 1w, 1M, 1y");
                return;
            }

            player.setMetadata("lengbanlist-time", new FixedMetadataValue(plugin, input));
            openAnvilForBan(player, "reason");
        } else if (currentStep.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            long banTimestamp = TimeUtils.parseTime(time);

            if (playerID.contains(".")) {
                // 封禁 IP
                plugin.getBanManager().banIp(new BanIpEntry(playerID, player.getName(), banTimestamp, input, false));
                Utils.sendMessage(player, "§a成功封禁IP：" + playerID + "，时长：" + time + "，原因：" + input);
            } else {
                // 封禁玩家
                plugin.getBanManager().banPlayer(new BanEntry(playerID, player.getName(), banTimestamp, input, false)); // 添加 isAuto 参数
                Utils.sendMessage(player, "§a成功封禁玩家：" + playerID + "，时长：" + time + "，原因：" + input);
            }

            player.removeMetadata("lengbanlist-action", plugin);
            player.removeMetadata("lengbanlist-step", plugin);
            player.removeMetadata("lengbanlist-playerID", plugin);
            player.removeMetadata("lengbanlist-time", plugin);
        }
    }

    private void handleUnban(Player player, InventoryClickEvent event) {
        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String playerID = item.getItemMeta().getDisplayName();

        if (playerID.contains(".")) {
            // 解封 IP
            plugin.getBanManager().unbanIp(playerID);
            Utils.sendMessage(player, "§a成功解封IP：" + playerID);
        } else {
            // 解封玩家
            plugin.getBanManager().unbanPlayer(playerID);
            Utils.sendMessage(player, "§a成功解封玩家：" + playerID);
        }

        player.removeMetadata("lengbanlist-action", plugin);
    }

    private void handleMute(Player player, InventoryClickEvent event, String currentStep) {
        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String input = item.getItemMeta().getDisplayName();

        if (currentStep.equals("playerID")) {
            player.setMetadata("lengbanlist-playerID", new FixedMetadataValue(plugin, input));
            openAnvilForMute(player, "reason");
        } else if (currentStep.equals("reason")) {
            String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
            MuteEntry muteEntry = new MuteEntry(playerID, player.getName(), System.currentTimeMillis(), input);
            plugin.getMuteManager().mutePlayer(muteEntry);
            Utils.sendMessage(player, "§a成功禁言玩家：" + playerID + "，原因：" + input);
            player.removeMetadata("lengbanlist-action", plugin);
            player.removeMetadata("lengbanlist-step", plugin);
            player.removeMetadata("lengbanlist-playerID", plugin);
        }
    }

    private void handleUnmute(Player player, InventoryClickEvent event) {
        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String playerID = item.getItemMeta().getDisplayName();

        plugin.getMuteManager().unmutePlayer(playerID);
        Utils.sendMessage(player, "§a成功解除禁言玩家：" + playerID);
        player.removeMetadata("lengbanlist-action", plugin);
    }

    private void handleIPBan(Player player, InventoryClickEvent event, String currentStep) {
        ItemStack item = event.getInventory().getItem(0);
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        String input = item.getItemMeta().getDisplayName();

        if (currentStep.equals("ip")) {
            player.setMetadata("lengbanlist-ip", new FixedMetadataValue(plugin, input));
            openAnvilForIPBan(player, "time");
        } else if (currentStep.equals("time")) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, "§c时间格式无效，请使用以下格式：10s, 5m, 2h, 7d, 1w, 1M, 1y");
                return;
            }

            player.setMetadata("lengbanlist-time", new FixedMetadataValue(plugin, input));
            openAnvilForIPBan(player, "reason");
        } else if (currentStep.equals("reason")) {
            String ip = player.getMetadata("lengbanlist-ip").get(0).asString();
            String time = player.getMetadata("lengbanlist-time").get(0).asString();
            long banTimestamp = TimeUtils.parseTime(time);

            plugin.getBanManager().banIp(new BanIpEntry(ip, player.getName(), banTimestamp, input, false));
            Utils.sendMessage(player, "§a成功封禁IP：" + ip + "，时长：" + time + "，原因：" + input);

            player.removeMetadata("lengbanlist-action", plugin);
            player.removeMetadata("lengbanlist-step", plugin);
            player.removeMetadata("lengbanlist-ip", plugin);
            player.removeMetadata("lengbanlist-time", plugin);
        }
    }

    private void openAnvilForBan(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b封禁玩家 - 输入" + (step.equals("playerID") ? "玩家ID或IP" : (step.equals("time") ? "时间" : "原因")));
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }

    private void openAnvilForMute(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b禁言玩家 - 输入" + (step.equals("playerID") ? "玩家ID" : "原因"));
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }

    private void openAnvilForUnban(Player player) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b解封玩家");
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入玩家ID或IP");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "unban"));
    }

    private void openAnvilForUnmute(Player player) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b解除禁言");
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入玩家ID");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-action", new FixedMetadataValue(plugin, "unmute"));
    }

    private void openAnvilForIPBan(Player player, String step) {
        Inventory anvil = Bukkit.createInventory(player, 9, "§b封禁IP - 输入" + (step.equals("ip") ? "IP地址" : (step.equals("time") ? "时间" : "原因")));
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a输入内容");
        item.setItemMeta(meta);
        anvil.setItem(0, item);

        player.openInventory(anvil);
        player.setMetadata("lengbanlist-step", new FixedMetadataValue(plugin, step));
    }
}