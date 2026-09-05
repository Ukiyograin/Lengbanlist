package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 移除警告事件。
 */
public final class LengbanlistUnwarnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String target;
    private final String warnId;
    private final String actor;

    public LengbanlistUnwarnEvent(String target, String warnId, String actor) {
        this.target = target;
        this.warnId = warnId;
        this.actor = actor;
    }

    public String getTarget() { return target; }
    public String getWarnId() { return warnId; }
    public String getActor() { return actor; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}