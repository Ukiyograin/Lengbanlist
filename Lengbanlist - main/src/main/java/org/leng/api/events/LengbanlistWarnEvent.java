package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.WarnEntry;

/**
 * 警告事件。
 */
public final class LengbanlistWarnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final WarnEntry entry;

    public LengbanlistWarnEvent(WarnEntry entry) {
        this.entry = entry;
    }

    public WarnEntry getEntry() { return entry; }
    public String getTarget() { return entry.player(); }
    public String getStaff() { return entry.staff(); }
    public String getReason() { return entry.reason(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}