package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;
import org.leng.manager.ModelCloudManager;
import org.leng.manager.ModelManager;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * /lban models 子命令 —— 云端模型管理。
 *
 * <p>子命令:
 * <pre>
 *   refresh                拉取云端索引 (立即)
 *   list                   列出本地 + 云端模型状态
 *   install &lt;id|all&gt;       下载安装指定/全部模型
 *   pin &lt;id&gt;               下载到本地并锁定 (云端更新不再覆盖)
 *   unpin &lt;id&gt;             解除锁定
 *   featured               显示本月精选模型
 *   stats                  本服安装统计
 * </pre>
 *
 * <p>线程模型：涉及网络 IO（索引拉取/模型下载）的操作一律在异步线程执行,
 * 结果通过 {@link SchedulerUtils#runTask} 回主线程(Folia 安全,避免看门狗)。
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
            case "stats":
                cmdStats(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    // ====================== 异步封装 ======================

    /**
     * 异步执行任务（可能含网络 IO），完成后回主线程回调。
     * 命令线程不再被 HTTP 阻塞（Folia 看门狗防护）。
     */
    private <T> void runOffThread(CommandSender sender, Supplier<T> task, Consumer<T> onDone) {
        CompletableFuture.supplyAsync(task)
                .thenAccept(result -> SchedulerUtils.runTask(plugin, sender, () -> onDone.accept(result)));
    }

    // ====================== 子命令 ======================

    private void cmdRefresh(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§e正在从云端拉取模型索引喵…");
        runOffThread(sender, cloud::fetchIndex, result -> {
            if (result.isPresent()) {
                ModelCloudManager.ModelIndex idx = result.get();
                long ready = idx.models().stream().filter(m -> !"0.0.0".equals(m.version())).count();
                Utils.sendMessage(sender, plugin.prefix() + "§a索引已更新：共 " + idx.models().size() + " 个云端模型" + (idx.updated() == null ? "" : "（更新于 " + idx.updated() + "）"));
                if (ready > 0) {
                    Utils.sendMessage(sender, plugin.prefix() + "§7已上架 " + ready + " 个，输入 §f/lban models list §7查看，§f/lban models install <ID> §7按需下载");
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§7暂无可下载的模型（均在迁移中）");
                }
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§c云端索引拉取失败，请检查网络或稍后重试。");
            }
        });
    }

    private void cmdList(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§e正在读取模型列表喵…");
        runOffThread(sender, cloud::getIndex, result -> {
            if (result.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§c云端索引不可用，请先执行 §f/lban models refresh");
                return;
            }
            Utils.sendMessage(sender, "§7--§b Lengbanlist 模型列表 §7--");
            for (ModelCloudManager.ModelInfo info : result.get().models()) {
                boolean placeholder = "0.0.0".equals(info.version());
                String localMark;
                if (placeholder) {
                    localMark = "§7[未上架]";
                } else {
                    localMark = cloud.isInstalled(info.id()) ? "§a[已安装]" : "§7[未安装]";
                }
                String pinMark = cloud.isPinned(info.id()) ? "§e[已锁定]" : "";
                String featuredMark = isFeatured(result.get(), info) ? " §6★本月精选" : "";
                Utils.sendMessage(sender, localMark + pinMark + " §f" + info.name()
                        + " §7(" + info.id() + " v" + info.version() + ")" + featuredMark);
            }
        });
    }

    private void cmdInstall(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban models install <模型ID|all>");
            return;
        }
        String id = args[1].toLowerCase();
        if (id.equals("all")) {
            cmdInstallAll(sender);
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§e正在下载模型 " + id + " 喵…");
        runOffThread(sender, () -> cloud.installModel(id), result -> {
            switch (result) {
                case INSTALLED:
                    ModelManager.getInstance().reloadModel();
                    Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 安装成功！输入 §f/lban model " + id + " §a即可使用");
                    break;
                case ALREADY_INSTALLED:
                    Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 已是最新版本");
                    break;
                case PINNED_SKIPPED:
                    Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 已锁定，不会覆盖本地版本。如需更新先 §f/lban models unpin " + id);
                    break;
                case NOT_FOUND:
                    Utils.sendMessage(sender, plugin.prefix() + "§c云端没有找到模型 " + id + "（或尚未上架），试试 §f/lban models list");
                    break;
                default:
                    Utils.sendMessage(sender, plugin.prefix() + "§c模型 " + id + " 下载失败，请稍后重试");
                    break;
            }
        });
    }

    private void cmdInstallAll(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§e正在按需下载全部已上架模型（已 pin/已最新会跳过）喵…");
        runOffThread(sender, cloud::syncAll, installed -> {
            if (installed > 0) {
                ModelManager.getInstance().reloadModel();
                Utils.sendMessage(sender, plugin.prefix() + "§a已安装 " + installed + " 个模型，输入 §f/lban model <名称> §a切换使用");
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§7没有需要下载的模型（均已是最新或被锁定）");
            }
        });
    }

    private void cmdPin(CommandSender sender, String[] args, boolean pin) {
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban models " + (pin ? "pin" : "unpin") + " <模型ID>");
            return;
        }
        String id = args[1].toLowerCase();
        if (pin) {
            // pin 语义 = 下载到本地(models/ 目录) + 锁定,云端更新不再覆盖
            if (!cloud.isInstalled(id)) {
                Utils.sendMessage(sender, plugin.prefix() + "§e模型 " + id + " 尚未下载，正在下载后锁定喵…");
                runOffThread(sender, () -> cloud.installModel(id), result -> {
                    if (result == ModelCloudManager.InstallResult.INSTALLED) {
                        ModelManager.getInstance().reloadModel();
                        finishPin(sender, id);
                    } else if (result == ModelCloudManager.InstallResult.NOT_FOUND) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c云端没有找到模型 " + id + "（或尚未上架）");
                    } else if (result == ModelCloudManager.InstallResult.FAILED) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c模型 " + id + " 下载失败，无法锁定");
                    } else {
                        // ALREADY_INSTALLED / PINNED_SKIPPED 不适用(已判未安装),兜底直接锁
                        finishPin(sender, id);
                    }
                });
            } else {
                finishPin(sender, id);
            }
        } else {
            if (cloud.unpin(id)) {
                Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 已解除锁定，将跟随云端更新");
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§c解除锁定失败，请查看控制台日志");
            }
        }
    }

    private void finishPin(CommandSender sender, String id) {
        if (cloud.pin(id)) {
            Utils.sendMessage(sender, plugin.prefix() + "§a模型 " + id + " 已下载到本地并锁定，云端更新不会覆盖");
        } else {
            Utils.sendMessage(sender, plugin.prefix() + "§c锁定失败，请查看控制台日志");
        }
    }

    private void cmdFeatured(CommandSender sender) {
        runOffThread(sender, cloud::currentFeatured, featured -> {
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
        });
    }

    /** 展示本地安装统计排行（供作者参考月度精选的投票热度）。 */
    private void cmdStats(CommandSender sender) {
        List<String[]> stats = cloud.downloadStats();
        if (stats.isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§7本服还没有安装过云端模型（使用 /lban models install 或 pin 后会产生统计）");
            return;
        }
        Utils.sendMessage(sender, "§7--§b 本服模型安装统计 §7--");
        int rank = 1;
        for (String[] s : stats) {
            Utils.sendMessage(sender, "§f#" + rank + " §e" + s[0] + " §7安装 " + s[1] + " 次");
            rank++;
        }
        Utils.sendMessage(sender, "§7（统计存于本服 models/.cache/stats.json,自动上报开启时同步参与月度精选评选）");
    }

    private boolean isFeatured(ModelCloudManager.ModelIndex idx, ModelCloudManager.ModelInfo info) {
        ModelCloudManager.FeaturedModel featured = idx.featured();
        return featured != null && featured.modelId() != null && featured.modelId().equalsIgnoreCase(info.id());
    }

    private void sendHelp(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§b Lengbanlist 模型管理 §7--");
        Utils.sendMessage(sender, "§6/lban models refresh §7- §3拉取云端模型索引");
        Utils.sendMessage(sender, "§6/lban models list §7- §3列出本地 + 云端模型");
        Utils.sendMessage(sender, "§6/lban models install <ID|all> §7- §3下载安装指定/全部模型");
        Utils.sendMessage(sender, "§6/lban models pin <ID> §7- §3下载到本地并锁定（不随云端更新）");
        Utils.sendMessage(sender, "§6/lban models unpin <ID> §7- §3解除锁定");
        Utils.sendMessage(sender, "§6/lban models featured §7- §3查看本月精选");
        Utils.sendMessage(sender, "§6/lban models stats §7- §3查看本服模型安装统计");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args[0].toLowerCase();
            for (String sub : new String[]{"refresh", "list", "install", "pin", "unpin", "featured", "stats"}) {
                if (sub.startsWith(prefix)) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            if (sub.equals("pin") || sub.equals("unpin") || sub.equals("install")) {
                // "all" 置顶（install all 一键下载）
                if ("all".startsWith(prefix)) {
                    completions.add("all");
                }
                // 只读缓存索引（绝不触网,避免 Tab 补全阻塞主线程）
                Optional<ModelCloudManager.ModelIndex> idx = cloud.cachedIndexOnly();
                if (idx.isPresent()) {
                    for (ModelCloudManager.ModelInfo info : idx.get().models()) {
                        // install/pin 只补已上架;unpin 只补已安装
                        boolean up = !"0.0.0".equals(info.version());
                        if (sub.equals("unpin")) {
                            if (cloud.isInstalled(info.id()) && info.id().startsWith(prefix)) {
                                completions.add(info.id());
                            }
                        } else if (up && info.id().toLowerCase().startsWith(prefix)) {
                            completions.add(info.id());
                        }
                    }
                }
            }
        }
        return completions;
    }
}