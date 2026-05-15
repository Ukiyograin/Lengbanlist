package org.leng.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.leng.Lengbanlist;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** 统一调度器，兼容 Paper 和 Folia。Folia 反射在 init() 时缓存，避免每次调用的反射开销。 */
public class SchedulerUtils {

    private static boolean folia;
    private static boolean initialized;

    // Cached Folia scheduler instances
    private static Object globalRegionScheduler;
    private static Object asyncScheduler;

    // Cached Folia methods
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method scheduledTaskCancel;

    private SchedulerUtils() {}

    public static void init(Lengbanlist plugin) {
        if (initialized) return;
        initialized = true;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;

            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            globalRegionScheduler = getGlobalRegionScheduler.invoke(null);

            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncScheduler.invoke(null);

            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");

            Class<?> globalClass = globalRegionScheduler.getClass();
            globalRun = globalClass.getMethod("run", JavaPlugin.class, Consumer.class);
            globalRunDelayed = globalClass.getMethod("runDelayed", JavaPlugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, long.class, long.class);

            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = asyncClass.getMethod("runNow", JavaPlugin.class, Consumer.class);
            asyncRunDelayed = asyncClass.getMethod("runDelayed", JavaPlugin.class, Consumer.class, long.class);

            plugin.getLogger().info("Folia 调度器已初始化（反射缓存模式）");
        } catch (Exception e) {
            folia = false;
            plugin.getLogger().info("使用传统 Bukkit 调度器");
        }
    }

    public static boolean isFolia() {
        return folia;
    }

    // ─── Global (sync) task ───

    public static SchedulerTask runTask(Lengbanlist plugin, Runnable task) {
        if (folia) {
            try {
                Object result = globalRun.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run());
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global run failed: " + e.getMessage());
                task.run();
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
        return new SchedulerTask(bt);
    }

    public static SchedulerTask runTaskLater(Lengbanlist plugin, Runnable task, long delayTicks) {
        if (folia) {
            try {
                Object result = globalRunDelayed.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run(), delayTicks);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global runDelayed failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new SchedulerTask(bt);
    }

    public static SchedulerTask runTaskTimer(Lengbanlist plugin, Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                Object result = globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run(), delayTicks, periodTicks);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global runAtFixedRate failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new SchedulerTask(bt);
    }

    // ─── Async task ───

    public static void runAsync(Lengbanlist plugin, Runnable task) {
        if (folia) {
            try {
                asyncRunNow.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async runNow failed, running sync: " + e.getMessage());
                task.run();
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncDelayed(Lengbanlist plugin, Runnable task, long delayMs) {
        if (folia) {
            try {
                asyncRunDelayed.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run(), delayMs);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async runDelayed failed: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayMs / 50);
        }
    }

    // ─── Async repeating task ───

    public static SchedulerTask runTaskTimerAsynchronously(Lengbanlist plugin, Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            return new SchedulerTask(new FoliaAsyncRepeatingTask(plugin, task, delayTicks, periodTicks));
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new SchedulerTask(bt);
    }

    private static class FoliaAsyncRepeatingTask {
        private volatile boolean cancelled;
        private final Lengbanlist plugin;
        private final Runnable task;
        private final long periodMs;

        FoliaAsyncRepeatingTask(Lengbanlist plugin, Runnable task, long delayTicks, long periodTicks) {
            this.plugin = plugin;
            this.task = task;
            this.periodMs = periodTicks * 50;
            scheduleNext(delayTicks * 50);
        }

        private void scheduleNext(long delayMs) {
            if (cancelled) return;
            try {
                asyncRunDelayed.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> {
                    if (!cancelled) {
                        task.run();
                        scheduleNext(periodMs);
                    }
                }, delayMs);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async repeating task failed: " + e.getMessage());
            }
        }

        void cancel() {
            cancelled = true;
        }
    }

    // ─── SchedulerTask wrapper ───

    public static class SchedulerTask {
        private final Object foliaTask;
        private final BukkitTask bukkitTask;
        private final FoliaAsyncRepeatingTask foliaRepeatingTask;

        SchedulerTask(Object foliaTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = null;
            this.foliaRepeatingTask = null;
        }

        SchedulerTask(BukkitTask bukkitTask) {
            this.foliaTask = null;
            this.bukkitTask = bukkitTask;
            this.foliaRepeatingTask = null;
        }

        SchedulerTask(FoliaAsyncRepeatingTask foliaRepeatingTask) {
            this.foliaTask = null;
            this.bukkitTask = null;
            this.foliaRepeatingTask = foliaRepeatingTask;
        }

        public void cancel() {
            if (foliaTask != null && scheduledTaskCancel != null) {
                try {
                    scheduledTaskCancel.invoke(foliaTask);
                } catch (Exception ignored) {}
            }
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
            if (foliaRepeatingTask != null) {
                foliaRepeatingTask.cancel();
            }
        }
    }
}
