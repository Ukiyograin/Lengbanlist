package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.ReportEntry;

import java.util.List;

public class ReportManager {
    private final Lengbanlist plugin;

    public ReportManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void addReport(ReportEntry report) {
        updateReport(report);
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

    public void saveReports() {
    }

    public void loadReports() {
    }

    public List<ReportEntry> getPendingReports() {
        return plugin.getDatabaseManager().getPendingReports();
    }

    public int getPendingReportCount() {
        return plugin.getDatabaseManager().getPendingReportCount();
    }
}
