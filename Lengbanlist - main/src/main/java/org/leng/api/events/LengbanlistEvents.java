package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.object.ReportEntry;

/**
* 封禁玩家事件。其他插件可监听此事件来响应 Lengbanlist 的封禁操作。
*/
public final class LengbanlistBanEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BanEntry entry;
    private final boolean silent;

    public LengbanlistBanEvent(BanEntry entry, boolean silent) {
        this.entry = entry;
        this.silent = silent;
    }

    public BanEntry getEntry() { return entry; }
    public boolean isSilent() { return silent; }
    public String getTarget() { return entry.target(); }
    public String getStaff() { return entry.staff(); }
    public String getReason() { return entry.reason(); }
    public long getDurationMillis() { return entry.time(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

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