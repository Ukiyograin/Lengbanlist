package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.BanIpEntry;

/**
 * 封禁 IP 事件。
 */
public final class LengbanlistBanIpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BanIpEntry entry;
    private final boolean silent;

    public LengbanlistBanIpEvent(BanIpEntry entry, boolean silent) {
        this.entry = entry;
        this.silent = silent;
    }

    public BanIpEntry getEntry() { return entry; }
    public boolean isSilent() { return silent; }
    public String getIp() { return entry.ip(); }
    public String getStaff() { return entry.staff(); }
    public String getReason() { return entry.reason(); }
    public long getDurationMillis() { return entry.time(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}