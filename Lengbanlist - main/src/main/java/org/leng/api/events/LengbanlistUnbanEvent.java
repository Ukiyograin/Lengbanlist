package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 解封事件（玩家或 IP）。
 */
public final class LengbanlistUnbanEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String target;
    private final boolean isIp;
    private final String actor;

    public LengbanlistUnbanEvent(String target, boolean isIp, String actor) {
        this.target = target;
        this.isIp = isIp;
        this.actor = actor;
    }

    public String getTarget() { return target; }
    public boolean isIp() { return isIp; }
    public String getActor() { return actor; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}