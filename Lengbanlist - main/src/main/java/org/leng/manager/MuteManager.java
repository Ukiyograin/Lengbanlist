package org.leng.manager;

import org.bukkit.Bukkit;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;

import java.util.List;

public class MuteManager {
    public void mutePlayer(MuteEntry muteEntry) {
        if (isPlayerMuted(muteEntry.getTarget())) {
            Lengbanlist.getInstance().getLogger().warning("玩家 " + muteEntry.getTarget() + " 已被禁言，跳过重复禁言");
            return;
        }
        Lengbanlist.getInstance().getDatabaseManager().upsertMute(muteEntry);
        Bukkit.broadcastMessage("§a玩家 " + muteEntry.getTarget() + " 已被禁言，原因：" + muteEntry.getReason());
    }

    public void unmutePlayer(String target) {
        Lengbanlist.getInstance().getDatabaseManager().deleteMute(target);
        Bukkit.broadcastMessage("§a玩家 " + target + " 已解除禁言");
    }

    public List<MuteEntry> getMuteList() {
        return Lengbanlist.getInstance().getDatabaseManager().getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        MuteEntry entry = Lengbanlist.getInstance().getDatabaseManager().getMute(playerName);
        return entry != null;
    }
}
