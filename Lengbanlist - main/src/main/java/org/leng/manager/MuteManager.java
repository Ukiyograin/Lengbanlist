package org.leng.manager;

import org.bukkit.Bukkit;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;

import java.util.List;

public class MuteManager {
    public void mutePlayer(MuteEntry muteEntry) {
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
        if (entry == null) {
            return false;
        }
        if (entry.getTime() > 0 && entry.getTime() <= System.currentTimeMillis()) {
            Lengbanlist.getInstance().getDatabaseManager().deleteMute(playerName);
            return false;
        }
        return true;
    }
}
