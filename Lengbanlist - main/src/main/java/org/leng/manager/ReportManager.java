package org.leng.manager;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.List;

public class ReportManager {

    private final Lengbanlist plugin;

    public ReportManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void addReport(ReportEntry report) {
        updateReport(report);
        org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistReportEvent(report));
    }

    public void updateReport(ReportEntry report) {
        plugin.getDatabaseManager().upsertReport(report);
    }

    public void removeReport(String id) {
        plugin.getDatabaseManager().deleteReport(id);
    }

    public int getReportCount(String target) {
        return plugin.getDatabaseManager().getReportCount(target);
    }

    public ReportEntry getReport(String id) {
        return plugin.getDatabaseManager().getReport(id);
    }

    public List<ReportEntry> getReportsByReporterAndTarget(String reporter, String target) {
        return plugin.getDatabaseManager().getReportsByReporterAndTarget(reporter, target);
    }

    public List<ReportEntry> getPendingReports() {
        return plugin.getDatabaseManager().getPendingReports();
    }

    public int getPendingReportCount() {
        return plugin.getDatabaseManager().getPendingReportCount();
    }

    public BanManager.BanMutationResult tryBanFromReport(ReportEntry entry, String staff, long endTime, String reason, boolean isAuto) {
        BanEntry banEntry = new BanEntry(entry.getTarget(), staff, endTime, reason, isAuto);
        DatabaseManager.WriteResult writeResult = plugin.getDatabaseManager()
                .replaceActiveBanAndUpdateReport(banEntry, entry, "已处理");
        if (writeResult == DatabaseManager.WriteResult.DATABASE_ERROR) {
            return BanManager.BanMutationResult.DATABASE_ERROR;
        }
        if (writeResult == DatabaseManager.WriteResult.NO_CHANGE) {
            return BanManager.BanMutationResult.STATE_CHANGED;
        }
        entry = entry.withStatus("已处理");
        plugin.getBanManager().publishAppliedPlayerBan(banEntry, false);
        Player reporterPlayer = plugin.getServer().getPlayer(entry.getReporter());
        if (reporterPlayer != null) {
            long durationMillis = endTime == Long.MAX_VALUE ? Long.MAX_VALUE : endTime - System.currentTimeMillis();
            Utils.sendMessage(reporterPlayer, plugin.getModelManager().getCurrentModel().onReportBan(entry.getTarget(), TimeUtils.formatDuration(durationMillis)));
        }
        return BanManager.BanMutationResult.APPLIED;
    }

}
