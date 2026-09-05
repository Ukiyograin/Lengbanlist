package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.ReportEntry;

import java.io.IOException;
import java.util.List;

/**
 * 举报 controller：/api/reports /api/report/action
 */
public class ReportController extends WebController {

    public ReportController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/reports", this::handleReports);
        server.createContext("/api/report/action", this::handleReportAction);
    }

    private void handleReports(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        List<ReportEntry> reports = plugin.getReportManager().getPendingReports();
        JsonArray arr = new JsonArray();
        for (ReportEntry r : reports) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("target", r.target());
            o.addProperty("reporter", r.reporter());
            o.addProperty("reason", r.reason());
            o.addProperty("status", r.status());
            o.addProperty("timestamp", r.timestamp());
            arr.add(o);
        }
        JsonObject result = new JsonObject();
        result.add("reports", arr);
        result.addProperty("total", arr.size());
        WebResponse.sendJson(exchange, 200, result.toString());
    }

    private void handleReportAction(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String id = json.get("id").getAsString();
            String action = json.get("action").getAsString();
            ReportEntry report = plugin.getReportManager().getReport(id);
            if (report == null) {
                WebResponse.sendError(exchange, 404, "举报不存在");
                return;
            }
            if ("accept".equalsIgnoreCase(action)) {
                report = report.withStatus("受理中");
                plugin.getReportManager().updateReport(report);
                plugin.getAuditManager().log("受理举报", authManager.resolveActor(extractToken(exchange)),
                        report.target(), "编号: " + id + " - " + report.reason());
            } else if ("close".equalsIgnoreCase(action)) {
                report = report.withStatus("已关闭");
                plugin.getReportManager().updateReport(report);
                plugin.getAuditManager().log("关闭举报", authManager.resolveActor(extractToken(exchange)),
                        report.target(), "编号: " + id + " - " + report.reason());
            } else {
                WebResponse.sendError(exchange, 400, "未知 action: " + action);
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "举报 " + id + " 处理完成");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误");
        }
    }
}