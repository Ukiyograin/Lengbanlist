package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;
import org.leng.manager.ModelCloudManager;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /lban models 子命令 —— 云端模型管理。
 *
 * <p>子命令:
 * <pre>
 *   refresh                拉取云端索引 (立即)
 *   list                   列出本地 + 云端模型状态
 *   install &lt;id&gt;           下载安装指定模型
 *   pin &lt;id&gt;               锁定本地模型 (云端更新不再覆盖)
 *   unpin &lt;id&gt;             解除锁定
 *   featured               显示本月精选模型
 * </pre>
 */
public class ModelsCommand implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;
    private final ModelCloudManager cloud;

    public ModelsCommand(Lengbanlist plugin) {
        this.plugin = plugin;
        this.cloud = plugin.getModelCloudManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "refresh":
                cmdRefresh(sender);
                break;
            case "list":
                cmdList(sender);
                break;
            case "install":
                cmdInstall(sender, args);
                break;
            case "pin":
                cmdPin(sender, args, true);
                break;
            case "unpin":
                cmdPin(sender, args, false);
                break;
            case "featured":
                cmdFeatured(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    // ====================== 子命令 ======================

    private void cmdRefresh(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§e正在从云端拉取模型索引喵…");
        cloud.fetchIndexAsync().thenAccept(result -> {
            if (result.isPresent()) {
                ModelCloudManager.ModelIndex idx = result.get();
                Utils.sendMessage(sender, plugin.prefix() + "§a索引已更新：共 " + idx.models().size() + " 个云端模型" + (idx.updated() == null ? "" : "（更新于 " + idx.updated() + "）"));
                int installed = cloud.syncAll();
                if (installed > 0) {
                    org.leng.manager.ModelManager.getInstance().reloadModel();
                    Utils.sendMessage(sender, plugin.prefix() + "§a已自动安装 " + installed + " 个新模型，输入 §f/lban model <名称> §a切换使用");
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§7没有需要安装的新模型（本地已是最新）");
                }
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§c云端索引拉取失败，请检查网络或稍后重试。");
            }
        });
    }

    private void cmdList(CommandSender sender) {
        Optional<ModelCloudManager.ModelIndex> idx = cloud.getIndex();
        if (idx.isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c云端索引不可用，请先执行 §f/lban models refresh");
            return;
        }
        Utils.sendMessage(sender, "§7--§b Lengbanlist 模型列表 §7--");
        for (ModelCloudManager.ModelInfo info : idx.get().models()) {
            boolean placeholder = "0.0.0".equals(info.version());
            String localMark;
            if (placeholder) {
                localMark = "§7[未上架]";
            } else {
                localMark = cloud.isInstalled(info.id()) ? "§a[已安装]" : "§7[未安装]";
            }
            String pinMark = cloud.isPinned(info.id()) ? "§e[已锁定]" : "";
            String featuredMark = isFeatured(info) ? " §6★本月精选" : "";
            Utils.sendMessage(sender, localMark + pinMark + " §f" + info.name()
                    + " §7(" + info.id() + " v" + info.version() + ")" + featuredMark);
        }
    }

    private void cmdInstall(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban models install <模型ID>");
            return;
        }
        String id = args[1].toLowerCase();
        // 预检:占位条目（未上架）直接提示
        Optional<ModelCloudManager.ModelInfo> info = cloud.findInIndex(id);
        if (info.isPresent() && "0.0.0".equals(info.get().version())) {
            Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 尚未上架（迁移中），敬请期待");
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§e正在下载模型 " + id + " 喵…");
        ModelCloudManager.InstallResult result = cloud.installModel(id);
        switch (result) {
            case INSTALLED:
                org.leng.manager.ModelManager.getInstance().reloadModel();
                Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 安装成功！输入 §f/lban model " + id + " §a即可使用");
                break;
            case ALREADY_INSTALLED:
                Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 已是最新版本");
                break;
            case PINNED_SKIPPED:
                Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 已锁定，不会覆盖本地版本。如需更新先 §f/lban models unpin " + id);
                break;
            case NOT_FOUND:
                Utils.sendMessage(sender, plugin.prefix() + "§c云端没有找到模型 " + id + "，试试 §f/lban models list");
                break;
            default:
                Utils.sendMessage(sender, plugin.prefix() + "§c模型 " + id + " 下载失败，请稍后重试");
                break;
        }
    }

    private void cmdPin(CommandSender sender, String[] args, boolean pin) {
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban models " + (pin ? "pin" : "unpin") + " <模型ID>");
            return;
        }
        String id = args[1].toLowerCase();
        if (pin) {
            if (cloud.pin(id)) {
                Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 已锁定，云端更新不会覆盖本地");
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§c锁定失败，请查看控制台日志");
            }
        } else {
            if (cloud.unpin(id)) {
                Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 已解除锁定，将跟随云端更新");
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§c解除锁定失败，请查看控制台日志");
            }
        }
    }

    private void cmdFeatured(CommandSender sender) {
        Optional<ModelCloudManager.FeaturedModel> featured = cloud.currentFeatured();
        if (featured.isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§7本月没有精选模型");
            return;
        }
        ModelCloudManager.FeaturedModel f = featured.get();
        Utils.sendMessage(sender, plugin.prefix() + "§6★ " + f.title());
        if (f.description() != null && !f.description().isEmpty()) {
            Utils.sendMessage(sender, "§7" + f.description());
        }
        if (cloud.isInstalled(f.modelId())) {
            Utils.sendMessage(sender, "§a该模型已安装，输入 §f/lban model " + f.modelId() + " §a切换");
        } else {
            Utils.sendMessage(sender, "§e未安装，输入 §f/lban models install " + f.modelId() + " §e安装");
        }
    }

    private boolean isFeatured(ModelCloudManager.ModelInfo info) {
        Optional<ModelCloudManager.FeaturedModel> featured = cloud.getIndex().map(ModelCloudManager.ModelIndex::featured);
        return featured.isPresent()
                && featured.get().modelId() != null
                && featured.get().modelId().equalsIgnoreCase(info.id());
    }

    private void sendHelp(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§b Lengbanlist 模型管理 §7--");
        Utils.sendMessage(sender, "§6/lban models refresh §7- §3拉取云端模型索引");
        Utils.sendMessage(sender, "§6/lban models list §7- §3列出本地 + 云端模型");
        Utils.sendMessage(sender, "§6/lban models install <ID> §7- §3下载安装指定模型");
        Utils.sendMessage(sender, "§6/lban models pin <ID> §7- §3锁定本地模型（不随云端更新）");
        Utils.sendMessage(sender, "§6/lban models unpin <ID> §7- §3解除锁定");
        Utils.sendMessage(sender, "§6/lban models featured §7- §3查看本月精选");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args[0].toLowerCase();
            for (String sub : new String[]{"refresh", "list", "install", "pin", "unpin", "featured"}) {
                if (sub.startsWith(prefix)) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            if (sub.equals("pin") || sub.equals("unpin") || sub.equals("install")) {
                cloud.getIndex().ifPresent(idx -> {
                    for (ModelCloudManager.ModelInfo info : idx.models()) {
                        if (info.id().toLowerCase().startsWith(prefix)) completions.add(info.id());
                    }
                });
            }
        }
        return completions;
    }
}