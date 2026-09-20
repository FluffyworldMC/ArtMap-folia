package me.Fupery.ArtMap.Utils;

import me.Fupery.ArtMap.ArtMap;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;


public class Scheduler {
    //todo add checks that ArtMap isn't disabled
    private final ArtMap plugin;
    public TaskScheduler SYNC = new TaskScheduler() {
        @Override
        public BukkitTask run(Runnable runnable) {
            return Bukkit.getScheduler().runTask(plugin, runnable);
        }

        @Override
        public BukkitTask runLater(Runnable runnable, int delay) {
            return Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }

        @Override
        public BukkitTask runTimer(Runnable runnable, int startDelay, int period) {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, startDelay, period);
        }
    };
    public TaskScheduler ASYNC = new TaskScheduler() {
        @Override
        public BukkitTask run(Runnable runnable) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }

        @Override
        public BukkitTask runLater(Runnable runnable, int delay) {
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
        }

        @Override
        public BukkitTask runTimer(Runnable runnable, int startDelay, int period) {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, startDelay, period);
        }
    };

    public Scheduler(ArtMap plugin) {
        this.plugin = plugin;
    }

    public TaskHandler getTaskHandler(BukkitRunnable runnable) {
        return new TaskHandler(runnable);
    }

    public void runSafely(Runnable runnable) {
        if (!Bukkit.getServer().isPrimaryThread()) {
            SYNC.run(runnable);
        } else {
            runnable.run();
        }
    }

    public interface TaskScheduler {
        BukkitTask run(Runnable runnable);

        BukkitTask runLater(Runnable runnable, int delay);

        BukkitTask runTimer(Runnable runnable, int startDelay, int period);
    }

    /** A BukkitRunnable scheduled with a delay (the timed previews use it). */
    public class TaskHandler {
        private BukkitRunnable runnable;

        private TaskHandler(BukkitRunnable runnable) {
            this.runnable = runnable;
        }

        public void runLater(boolean async, int delay) {
            if (async) runnable.runTaskLaterAsynchronously(plugin, delay);
            else runnable.runTaskLater(plugin, delay);
        }
    }
}
