package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 解除禁言事件。
 */
public final class LengbanlistUnmuteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String target;
    private final String actor;

    public LengbanlistUnmuteEvent(String target, String actor) {
        this.target = target;
        this.actor = actor;
    }

    public String getTarget() { return target; }
    public String getActor() { return actor; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}