package me.Fupery.ArtMap.Preview;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** PreviewManager decides whether a click is swallowed as "end the preview", one of the cancel paths ruled
 *  out while diagnosing #376 and previously almost untested. */
public class PreviewManagerTest extends PluginTestSupport {

    private PreviewManager manager;
    private Player player;

    /** Minimal Preview that records what happened to it. */
    private static final class FakePreview implements Preview {
        boolean started, ended, allowEvents = true;

        @Override public boolean start(Player player) { started = true; return true; }
        @Override public boolean end(Player player) { ended = true; return true; }
        @Override public boolean isEventAllowed(UUID player, Event event) { return allowEvents; }
    }

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshManager() {
        manager = new PreviewManager();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    @Test
    @DisplayName("starting a preview registers it and runs the preview's start hook")
    public void startRegistersPreview() {
        FakePreview preview = new FakePreview();

        assertTrue(manager.startPreview(player, preview));

        assertTrue(preview.started, "start() should have been invoked");
        assertTrue(manager.isPreviewing(player), "player should now be previewing");
        assertTrue(manager.isPreviewing(player.getUniqueId()), "UUID lookup should agree");
    }

    @Test
    @DisplayName("ending a preview clears it and reports that it consumed the action")
    public void endClearsPreview() {
        FakePreview preview = new FakePreview();
        manager.startPreview(player, preview);

        assertTrue(manager.endPreview(player), "ending an active preview reports true");

        assertTrue(preview.ended, "end() should have been invoked");
        assertFalse(manager.isPreviewing(player), "preview must be cleared");
    }

    @Test
    @DisplayName("ending a preview that is not running is a no-op, not a cancel")
    public void endWithoutPreviewIsFalse() {
        assertFalse(manager.endPreview(player),
                "listeners cancel their event on a true return, so an idle player must return false");
    }

    @Test
    @DisplayName("starting a second preview replaces the first")
    public void startReplacesExistingPreview() {
        FakePreview first = new FakePreview();
        FakePreview second = new FakePreview();
        manager.startPreview(player, first);

        manager.startPreview(player, second);

        assertTrue(first.ended, "the previous preview should have been ended");
        assertTrue(manager.isPreviewing(player), "the new preview should be active");
    }

    @Test
    @DisplayName("checkAllowed ends the preview when the preview rejects the event")
    public void checkAllowedEndsOnDisallowedEvent() {
        FakePreview preview = new FakePreview();
        preview.allowEvents = false;
        manager.startPreview(player, preview);

        manager.checkAllowed(player, mock(Event.class));

        assertFalse(manager.isPreviewing(player), "a disallowed event must end the preview");
    }

    @Test
    @DisplayName("checkAllowed leaves an allowed event alone")
    public void checkAllowedKeepsPreviewForAllowedEvent() {
        FakePreview preview = new FakePreview();
        manager.startPreview(player, preview);

        manager.checkAllowed(player, mock(Event.class));

        assertTrue(manager.isPreviewing(player), "an allowed event must not end the preview");
    }

    /** Timeout path: endPreview(uuid) runs 300 ticks later; if the player logged off Bukkit.getPlayer is null,
     *  and the UUID overload used to pass that null straight to endPreview(Player). */
    @Test
    @DisplayName("ending an offline player's preview must not throw, and must clear it")
    public void endPreviewForOfflinePlayerIsSafe() {
        UUID ghost = UUID.randomUUID(); // never registered with the mock server
        Player offline = mock(Player.class);
        when(offline.getUniqueId()).thenReturn(ghost);
        manager.startPreview(offline, new FakePreview());
        assertTrue(manager.isPreviewing(ghost), "precondition: preview registered");

        assertDoesNotThrow(() -> manager.endPreview(ghost),
                "the preview timeout fires after logout; it must not NPE");

        assertFalse(manager.isPreviewing(ghost),
                "the stale entry must be cleared, or the player stays 'previewing' forever");
    }

    @Test
    @DisplayName("endAllPreviews clears everything without tripping over its own removals")
    public void endAllPreviewsClearsAll() {
        Player[] players = mocks.getRandomMockPlayers(3);
        for (Player p : players) {
            manager.startPreview(preparePlayer(p), new FakePreview());
        }

        assertDoesNotThrow(() -> manager.endAllPreviews(),
                "shutdown must not blow up while iterating the previews it is removing");

        for (Player p : players) {
            assertFalse(manager.isPreviewing(p), p.getName() + " should no longer be previewing");
        }
    }
}
