package me.Fupery.ArtMap.Utils;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import me.Fupery.ArtMap.ArtMap;

/**
 * Thin wrapper over UniversalScheduler so the rest of the plugin never has to touch
 * {@code Bukkit.getScheduler()} directly. UniversalScheduler picks the region scheduler on
 * Folia/Canvas, Paper's scheduler on Paper and Bukkit's scheduler everywhere else.
 */
public class Scheduler {
    private final com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler handle;

    public TaskScheduler SYNC;
    public AsyncTaskScheduler ASYNC;

    public Scheduler(ArtMap plugin) {
        this(UniversalScheduler.getScheduler(plugin));
    }

    /** Injection point for tests: lets them supply a mocked UniversalScheduler instance. */
    Scheduler(com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler handle) {
        this.handle = handle;
        this.SYNC = new SyncTaskScheduler();
        this.ASYNC = new AsyncTaskSchedulerImpl();
    }

    public boolean isFolia() {
        return UniversalScheduler.isFolia || UniversalScheduler.isCanvas;
    }

    /** Cancels every task this plugin has scheduled. Called from {@code onDisable()}. */
    public void cancelTasks() {
        handle.cancelTasks();
    }

    public TaskHandler getTaskHandler(Runnable runnable) {
        return new TaskHandler(this, null, runnable);
    }

    public TaskHandler getTaskHandler(Entity entity, Runnable runnable) {
        return new TaskHandler(this, entity, runnable);
    }

    /**
     * Runs on the global/main thread, or inline when already there.
     */
    public void runSafely(Runnable runnable) {
        if (handle.isGlobalThread()) {
            runnable.run();
        } else {
            SYNC.run(runnable);
        }
    }

    /**
     * Runs on the thread that owns the entity, or inline when already there. On Bukkit this is
     * the main thread; on Folia it is the entity's region thread.
     */
    public void runSafely(Entity entity, Runnable runnable) {
        if (entity != null && handle.isEntityThread(entity)) {
            runnable.run();
        } else {
            SYNC.run(entity, runnable);
        }
    }

    /**
     * Runs on the thread that owns the location, or inline when already there. On Bukkit this is
     * the main thread; on Folia it is the region thread owning the chunk.
     */
    public void runSafely(Location location, Runnable runnable) {
        if (location != null && handle.isRegionThread(location)) {
            runnable.run();
        } else {
            SYNC.run(location, runnable);
        }
    }

    public interface TaskScheduler {
        MyScheduledTask run(Runnable runnable);

        MyScheduledTask run(Entity entity, Runnable runnable);

        MyScheduledTask run(Location location, Runnable runnable);

        MyScheduledTask runLater(Runnable runnable, long delay);

        MyScheduledTask runLater(Entity entity, Runnable runnable, long delay);

        MyScheduledTask runLater(Location location, Runnable runnable, long delay);

        MyScheduledTask runTimer(Runnable runnable, long startDelay, long period);

        MyScheduledTask runTimer(Entity entity, Runnable runnable, long startDelay, long period);

        MyScheduledTask runTimer(Location location, Runnable runnable, long startDelay, long period);
    }

    public interface AsyncTaskScheduler {
        MyScheduledTask run(Runnable runnable);

        MyScheduledTask runLater(Runnable runnable, long delay);

        MyScheduledTask runTimer(Runnable runnable, long startDelay, long period);
    }

    private class SyncTaskScheduler implements TaskScheduler {
        @Override
        public MyScheduledTask run(Runnable runnable) {
            return handle.runTask(runnable);
        }

        @Override
        public MyScheduledTask run(Entity entity, Runnable runnable) {
            return handle.runTask(entity, runnable);
        }

        @Override
        public MyScheduledTask run(Location location, Runnable runnable) {
            return handle.runTask(location, runnable);
        }

        @Override
        public MyScheduledTask runLater(Runnable runnable, long delay) {
            return handle.runTaskLater(runnable, delay);
        }

        @Override
        public MyScheduledTask runLater(Entity entity, Runnable runnable, long delay) {
            return handle.runTaskLater(entity, runnable, delay);
        }

        @Override
        public MyScheduledTask runLater(Location location, Runnable runnable, long delay) {
            return handle.runTaskLater(location, runnable, delay);
        }

        @Override
        public MyScheduledTask runTimer(Runnable runnable, long startDelay, long period) {
            return handle.runTaskTimer(runnable, startDelay, period);
        }

        @Override
        public MyScheduledTask runTimer(Entity entity, Runnable runnable, long startDelay, long period) {
            return handle.runTaskTimer(entity, runnable, startDelay, period);
        }

        @Override
        public MyScheduledTask runTimer(Location location, Runnable runnable, long startDelay, long period) {
            return handle.runTaskTimer(location, runnable, startDelay, period);
        }
    }

    private class AsyncTaskSchedulerImpl implements AsyncTaskScheduler {
        @Override
        public MyScheduledTask run(Runnable runnable) {
            return handle.runTaskAsynchronously(runnable);
        }

        @Override
        public MyScheduledTask runLater(Runnable runnable, long delay) {
            return handle.runTaskLaterAsynchronously(runnable, delay);
        }

        @Override
        public MyScheduledTask runTimer(Runnable runnable, long startDelay, long period) {
            return handle.runTaskTimerAsynchronously(runnable, startDelay, period);
        }
    }

    /** A delayed task (the timed previews use it) that can be cancelled later. */
    public static class TaskHandler {
        private final Scheduler scheduler;
        private final Entity entity;
        private final Runnable runnable;
        private MyScheduledTask task;

        private TaskHandler(Scheduler scheduler, Entity entity, Runnable runnable) {
            this.scheduler = scheduler;
            this.entity = entity;
            this.runnable = runnable;
        }

        public void runLater(boolean async, long delay) {
            if (async) {
                task = scheduler.ASYNC.runLater(runnable, delay);
            } else if (entity != null) {
                task = scheduler.SYNC.runLater(entity, runnable, delay);
            } else {
                task = scheduler.SYNC.runLater(runnable, delay);
            }
        }

        public void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }
}
