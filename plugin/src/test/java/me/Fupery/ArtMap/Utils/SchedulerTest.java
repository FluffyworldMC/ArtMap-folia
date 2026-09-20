package me.Fupery.ArtMap.Utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The thin wrapper over Bukkit's scheduler: every call lands on the right Bukkit method with the plugin. */
public class SchedulerTest extends PluginTestSupport {

    private Scheduler scheduler;
    private BukkitScheduler bukkit;
    private final Runnable task = () -> {};

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() {
        scheduler = new Scheduler(ArtMap.instance());
        bukkit = Bukkit.getScheduler();
        clearInvocations(bukkit); // shared mock
    }

    @Test
    @DisplayName("SYNC schedules on the main thread with the given delays")
    public void syncVariants() {
        scheduler.SYNC.run(task);
        verify(bukkit).runTask(ArtMap.instance(), task);
        scheduler.SYNC.runLater(task, 7);
        verify(bukkit).runTaskLater(ArtMap.instance(), task, 7L);
        scheduler.SYNC.runTimer(task, 2, 9);
        verify(bukkit).runTaskTimer(ArtMap.instance(), task, 2L, 9L);
    }

    @Test
    @DisplayName("ASYNC schedules off the main thread with the given delays")
    public void asyncVariants() {
        scheduler.ASYNC.run(task);
        verify(bukkit).runTaskAsynchronously(ArtMap.instance(), task);
        scheduler.ASYNC.runLater(task, 7);
        verify(bukkit).runTaskLaterAsynchronously(ArtMap.instance(), task, 7L);
        scheduler.ASYNC.runTimer(task, 2, 9);
        verify(bukkit).runTaskTimerAsynchronously(ArtMap.instance(), task, 2L, 9L);
    }

    @Test
    @DisplayName("runSafely runs inline on the main thread and schedules from any other")
    public void runSafely() {
        boolean[] ran = { false };
        Runnable mark = () -> ran[0] = true;
        when(Bukkit.getServer().isPrimaryThread()).thenReturn(true);
        scheduler.runSafely(mark);
        org.junit.jupiter.api.Assertions.assertTrue(ran[0]);
        verify(bukkit, never()).runTask(any(), any(Runnable.class));

        ran[0] = false;
        when(Bukkit.getServer().isPrimaryThread()).thenReturn(false);
        scheduler.runSafely(mark);
        org.junit.jupiter.api.Assertions.assertFalse(ran[0], "off the main thread the task is handed to the scheduler");
        verify(bukkit).runTask(eq(ArtMap.instance()), eq(mark));
        when(Bukkit.getServer().isPrimaryThread()).thenReturn(true);
    }

    @Test
    @DisplayName("a task handler delays its BukkitRunnable on the requested thread")
    public void taskHandlerDelays() {
        BukkitRunnable runnable = mock(BukkitRunnable.class);
        Scheduler.TaskHandler handler = scheduler.getTaskHandler(runnable);
        handler.runLater(false, 300);
        verify(runnable).runTaskLater(ArtMap.instance(), 300L);
        handler.runLater(true, 40);
        verify(runnable).runTaskLaterAsynchronously(ArtMap.instance(), 40L);
    }
}
