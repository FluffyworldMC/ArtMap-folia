package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Argument handling for the admin commands, which parse argv by hand and were untested; malformed input
 *  must yield usage text, never a stack trace. */
public class CommandArgumentTest extends PluginTestSupport {

    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    private ReturnMessage run(AsyncCommand command, String... args) {
        ReturnMessage msg = new ReturnMessage(player, null);
        command.runCommand(player, args, msg);
        return msg;
    }

    // ── export ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("export refuses a player without artmap.admin")
    public void exportRequiresAdmin() {
        ReturnMessage msg = run(new CommandExport(), "export", "-all", "out.json");
        assertEquals(Lang.NO_PERM.get(), msg.message);
    }

    @Test
    @DisplayName("export with too few arguments answers with usage")
    public void exportTooFewArgs() {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        ReturnMessage msg = run(new CommandExport(), "export", "-all");
        assertEquals(Lang.COMMAND_EXPORT.get(), msg.message);
    }

    @Test
    @DisplayName("export with an unknown mode answers with usage and stops")
    public void exportUnknownModeStops() {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        // The default branch used to fall through with a null filename.
        ReturnMessage msg = assertDoesNotThrow(
                () -> run(new CommandExport(), "export", "-nonsense", "out.json"));
        assertEquals(Lang.COMMAND_EXPORT.get(), msg.message,
                "an unrecognised mode must report usage, not continue exporting");
    }

    @Test
    @DisplayName("export -artist with an unknown player name does not crash")
    public void exportUnknownArtistIsHandled() {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        assertDoesNotThrow(() -> run(new CommandExport(),
                "export", "-artist", "NoSuchPlayerHere", "out.json"));
    }

    // ── repair ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("repair refuses a player without artmap.admin")
    public void repairRequiresAdmin() {
        ReturnMessage msg = run(new Repair(), "repair", "-scan", "-all");
        assertEquals(Lang.NO_PERM.get(), msg.message);
    }

    @Test
    @DisplayName("repair with too few arguments answers with repair usage")
    public void repairTooFewArgs() {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        ReturnMessage msg = run(new Repair(), "repair", "-scan");
        assertEquals(Lang.REPAIR_USAGE.get(), msg.message);
    }

    @Test
    @DisplayName("repair with an unknown mode reports REPAIR usage, not export usage")
    public void repairUnknownModeReportsRepairUsage() {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        ReturnMessage msg = run(new Repair(), "repair", "-nonsense", "thing");
        assertNotNull(msg.message);
        assertNotEquals(Lang.COMMAND_EXPORT.get(), msg.message,
                "/art repair must not answer with the /art export usage string");
        assertEquals(Lang.REPAIR_USAGE.get(), msg.message);
    }

    // ── search ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search with no arguments answers with usage")
    public void searchTooFewArgs() {
        ReturnMessage msg = run(new Search(), "search");
        assertEquals(Lang.SEARCH_USAGE.get(), msg.message);
    }

    @Test
    @DisplayName("/art search --player with no name must not throw")
    public void searchPlayerWithoutNameIsHandled() {
        // args = [search, --player] passes the `length < 2` guard, then reads
        // args[2] unconditionally.
        assertDoesNotThrow(() -> run(new Search(), "search", "--player"),
                "a half-typed --player must produce usage, not ArrayIndexOutOfBounds");
    }

    @Test
    @DisplayName("/art search --mine with no search term is allowed")
    public void searchMineWithoutTermIsAllowed() {
        assertDoesNotThrow(() -> run(new Search(), "search", "--mine"));
    }
}
