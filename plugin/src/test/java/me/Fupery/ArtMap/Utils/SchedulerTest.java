package me.Fupery.ArtMap.Utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;

/**
 * The wrapper's job is to forward every call to UniversalScheduler's chosen implementation with
 * the right plugin, delay and thread. UniversalScheduler itself is covered by its own project.
 */
public class SchedulerTest {

    private Scheduler scheduler;
    private com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler handle;
    private final Runnable task = () -> {};

    @BeforeEach
    public void fresh() {
        handle = mock(com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler.class);
        scheduler = new Scheduler(handle);
    }

    @Test
    @DisplayName("SYNC forwards global work with the given delays")
    public void syncVariants() {
        scheduler.SYNC.run(task);
        verify(handle).runTask(task);
        scheduler.SYNC.runLater(task, 7);
        verify(handle).runTaskLater(task, 7L);
        scheduler.SYNC.runTimer(task, 2, 9);
        verify(handle).runTaskTimer(task, 2L, 9L);
    }

    @Test
    @DisplayName("SYNC binds entity and block work to their region")
    public void syncEntityAndLocationVariants() {
        Entity entity = mock(Entity.class);
        Location location = new Location(null, 1, 64, 1);

        scheduler.SYNC.run(entity, task);
        verify(handle).runTask(entity, task);
        scheduler.SYNC.run(location, task);
        verify(handle).runTask(location, task);

        scheduler.SYNC.runLater(entity, task, 3);
        verify(handle).runTaskLater(entity, task, 3L);
        scheduler.SYNC.runLater(location, task, 4);
        verify(handle).runTaskLater(location, task, 4L);

        scheduler.SYNC.runTimer(entity, task, 1, 20);
        verify(handle).runTaskTimer(entity, task, 1L, 20L);
        scheduler.SYNC.runTimer(location, task, 2, 21);
        verify(handle).runTaskTimer(location, task, 2L, 21L);
    }

    @Test
    @DisplayName("ASYNC forwards work off the tick thread with the given delays")
    public void asyncVariants() {
        scheduler.ASYNC.run(task);
        verify(handle).runTaskAsynchronously(task);
        scheduler.ASYNC.runLater(task, 7);
        verify(handle).runTaskLaterAsynchronously(task, 7L);
        scheduler.ASYNC.runTimer(task, 2, 9);
        verify(handle).runTaskTimerAsynchronously(task, 2L, 9L);
    }

    @Test
    @DisplayName("runSafely runs inline on the global thread and schedules from any other")
    public void runSafely() {
        boolean[] ran = { false };
        Runnable mark = () -> ran[0] = true;
        when(handle.isGlobalThread()).thenReturn(true);
        scheduler.runSafely(mark);
        assertTrue(ran[0]);
        verify(handle, never()).runTask(any(Runnable.class));

        ran[0] = false;
        when(handle.isGlobalThread()).thenReturn(false);
        scheduler.runSafely(mark);
        assertFalse(ran[0], "off the global thread the task is handed to the scheduler");
        verify(handle).runTask(eq(mark));
    }

    @Test
    @DisplayName("runSafely binds entity and block work to the region that owns them")
    public void runSafelyEntityAndLocation() {
        boolean[] ran = { false };
        Runnable mark = () -> ran[0] = true;
        Entity entity = mock(Entity.class);
        Location location = new Location(null, 1, 64, 1);

        when(handle.isEntityThread(entity)).thenReturn(true);
        scheduler.runSafely(entity, mark);
        assertTrue(ran[0]);
        verify(handle, never()).runTask(entity, mark);

        ran[0] = false;
        when(handle.isEntityThread(entity)).thenReturn(false);
        scheduler.runSafely(entity, mark);
        assertFalse(ran[0]);
        verify(handle).runTask(entity, mark);

        when(handle.isRegionThread(location)).thenReturn(true);
        scheduler.runSafely(location, mark);
        assertTrue(ran[0]);
        verify(handle, never()).runTask(location, mark);

        ran[0] = false;
        when(handle.isRegionThread(location)).thenReturn(false);
        scheduler.runSafely(location, mark);
        assertFalse(ran[0]);
        verify(handle).runTask(location, mark);
    }

    @Test
    @DisplayName("a task handler delays and cancels the underlying scheduled task")
    public void taskHandlerDelays() {
        MyScheduledTask scheduled = mock(MyScheduledTask.class);
        when(handle.runTaskLater(task, 300L)).thenReturn(scheduled);
        Scheduler.TaskHandler handler = scheduler.getTaskHandler(task);
        handler.runLater(false, 300);
        verify(handle).runTaskLater(task, 300L);
        handler.cancel();
        verify(scheduled).cancel();

        Entity player = mock(Entity.class);
        Scheduler.TaskHandler entityHandler = scheduler.getTaskHandler(player, task);
        entityHandler.runLater(false, 40);
        verify(handle).runTaskLater(player, task, 40L);
    }
}
