package org.leng.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.utils.TimeUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导出 controller：CSV 格式导出封禁/禁言列表（Excel 友好：UTF-8 BOM）。
 * /api/exports/bans、/api/exports/ipbans、/api/exports/mutes。
 */
public class ExportController extends WebController {

    public ExportController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/exports/bans", this::handleBans);
        server.createContext("/api/exports/ipbans", this::handleIpBans);
        server.createContext("/api/exports/mutes", this::handleMutes);
    }

    private void handleBans(HttpExchange exchange) {
        if (!requireAuth(exchange)) return;
        try {
            List<BanEntry> bans = plugin.getBanManager().getBanList();
            StringBuilder sb = new StringBuilder(64 + bans.size() * 64);
            sb.append("target,staff,end_time,reason,active,remaining\n");
            for (BanEntry b : bans) {
                long remaining = b.time() - System.currentTimeMillis();
                sb.append(csv(b.target())).append(',')
                        .append(csv(b.staff())).append(',')
                        .append(b.time()).append(',')
                        .append(csv(b.reason())).append(',')
                        .append(b.isActive()).append(',')
                        .append(csv(TimeUtils.formatDuration(remaining))).append('\n');
            }
            writeCsv(exchange, "bans.csv", sb.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "导出封禁列表失败");
        }
    }

    private void handleIpBans(HttpExchange exchange) {
        if (!requireAuth(exchange)) return;
        try {
            List<BanIpEntry> bans = plugin.getBanManager().getBanIpList();
            StringBuilder sb = new StringBuilder(64 + bans.size() * 64);
            sb.append("ip,staff,end_time,reason,active,remaining\n");
            for (BanIpEntry b : bans) {
                long remaining = b.time() - System.currentTimeMillis();
                sb.append(csv(b.ip())).append(',')
                        .append(csv(b.staff())).append(',')
                        .append(b.time()).append(',')
                        .append(csv(b.reason())).append(',')
                        .append(b.isActive()).append(',')
                        .append(csv(TimeUtils.formatDuration(remaining))).append('\n');
            }
            writeCsv(exchange, "ipbans.csv", sb.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "导出 IP 封禁列表失败");
        }
    }

    private void handleMutes(HttpExchange exchange) {
        if (!requireAuth(exchange)) return;
        try {
            List<MuteEntry> mutes = plugin.getMuteManager().getMuteList();
            StringBuilder sb = new StringBuilder(64 + mutes.size() * 64);
            sb.append("target,staff,end_time,reason,active,remaining\n");
            for (MuteEntry m : mutes) {
                long remaining = m.time() - System.currentTimeMillis();
                sb.append(csv(m.target())).append(',')
                        .append(csv(m.staff())).append(',')
                        .append(m.time()).append(',')
                        .append(csv(m.reason())).append(',')
                        .append(m.time() > System.currentTimeMillis()).append(',')
                        .append(csv(TimeUtils.formatDuration(remaining))).append('\n');
            }
            writeCsv(exchange, "mutes.csv", sb.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "导出禁言列表失败");
        }
    }

    /** CSV 字段转义：双引号 + 含逗号/换行的字段加引号 */
    private static String csv(String value) {
        if (value == null) return "";
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        if (!needQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void writeCsv(HttpExchange exchange, String filename, String body) throws IOException {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[bom.length + data.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(data, 0, payload, bom.length, data.length);

        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        } finally {
            exchange.close();
        }
    }
}