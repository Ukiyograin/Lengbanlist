package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.ReportEntry;

/**
 * 举报创建事件。
 */
public final class LengbanlistReportEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ReportEntry entry;

    public LengbanlistReportEvent(ReportEntry entry) {
        this.entry = entry;
    }

    public ReportEntry getEntry() { return entry; }
    public String getReporter() { return entry.reporter(); }
    public String getTarget() { return entry.target(); }
    public String getReason() { return entry.reason(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}