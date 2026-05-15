package org.leng.manager;

import org.bukkit.Bukkit;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;

import java.util.List;

/** 禁言管理，处理玩家禁言/解禁及状态查询。 */
public class MuteManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public MuteManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void mutePlayer(MuteEntry muteEntry) {
        if (isPlayerMuted(muteEntry.getTarget())) {
            plugin.getLogger().warning("玩家 " + muteEntry.getTarget() + " 已被禁言，跳过重复禁言");
            return;
        }
        db.upsertMute(muteEntry);
        Bukkit.broadcastMessage("§a玩家 " + muteEntry.getTarget() + " 已被禁言，原因：" + muteEntry.getReason());
    }

    public void unmutePlayer(String target) {
        db.deleteMute(target);
        Bukkit.broadcastMessage("§a玩家 " + target + " 已解除禁言");
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        MuteEntry entry = db.getMute(playerName);
        return entry != null;
    }
}
