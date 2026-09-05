package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.MuteEntry;

/**
 * 禁言事件。
 */
public final class LengbanlistMuteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MuteEntry entry;

    public LengbanlistMuteEvent(MuteEntry entry) {
        this.entry = entry;
    }

    public MuteEntry getEntry() { return entry; }
    public String getTarget() { return entry.target(); }
    public String getStaff() { return entry.staff(); }
    public String getReason() { return entry.reason(); }
    public long getDurationMillis() { return entry.time(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}