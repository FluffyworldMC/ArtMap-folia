package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;
import net.md_5.bungee.api.chat.BaseComponent;

/** /art search and /art repair against a mocked database. */
public class SearchRepairTest extends PluginTestSupport {

    private Player player;
    private Database database;
    private final List<LogRecord> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord r) { logged.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() {
        mocks.resetMockPlayerPermissions();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(player, player.spigot());
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        ArtMap.instance().getLogger().addHandler(capture);
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        ArtMap.instance().getLogger().removeHandler(capture);
    }

    private ReturnMessage run(AsyncCommand command, String... args) {
        ReturnMessage msg = new ReturnMessage(player, null);
        command.runCommand(player, args, msg);
        return msg;
    }

    private static MapArt art(String title, UUID artist) {
        return new MapArt(1, title, artist, "Artist", "01-01-2026");
    }

    private List<String> linesSent() {
        ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player.spigot(), org.mockito.Mockito.atLeastOnce()).sendMessage(sent.capture());
        List<String> lines = new ArrayList<>();
        for (BaseComponent c : sent.getAllValues()) lines.add(c.toLegacyText());
        return lines;
    }

    // ---- search ----------------------------------------------------------------

    @Test
    @DisplayName("a plain search lists matching artists and artworks as clickable lines")
    public void plainSearch() throws Exception {
        UUID artist = UUID.randomUUID();
        when(database.searchArtworks("sun", null)).thenReturn(List.of(art("Sunset", artist), art("Sunrise", artist)));
        when(database.searchArtists("sun")).thenReturn(List.of("Sunny"));

        assertNull(run(new Search(), "search", "sun").message);

        List<String> lines = linesSent();
        // header, artists banner, one artist, artworks banner, two artworks
        assertEquals(6, lines.size(), lines.toString());
        assertTrue(lines.get(1).contains(Lang.SEARCH_ARTISTS.get() + ": 1"));
        assertTrue(lines.get(2).contains("Sunny"));
        assertTrue(lines.get(3).contains(Lang.SEARCH_ARTWORKS.get() + ": 2"));
        assertTrue(lines.get(4).contains("Sunset") && lines.get(4).contains(Lang.SEARCH_PREVIEW_BUTTON.get()));
        assertTrue(!lines.get(4).contains(Lang.SEARCH_GIVE_BUTTON.get()), "no give button without admin");
    }

    @Test
    @DisplayName("an artwork whose artist name is unknown still lists instead of failing the whole search")
    public void searchWithUnknownArtistName() throws Exception {
        // The artist's name comes from the heads cache and is null when it was never cached
        // (an artist who never joined, or `/art test -create` artworks).
        MapArt nameless = new MapArt(2, "Orphan", UUID.randomUUID(), null, "01-01-2026");
        when(database.searchArtworks("o", null)).thenReturn(List.of(art("Owl", UUID.randomUUID()), nameless));

        assertNull(run(new Search(), "search", "o").message);

        List<String> lines = linesSent();
        assertTrue(lines.get(2).contains(Lang.SEARCH_ARTWORKS.get() + ": 2"), lines.toString());
        assertTrue(lines.get(3).contains("Owl"), lines.toString());
        assertTrue(lines.get(4).contains("Orphan") && lines.get(4).contains(Lang.SEARCH_PREVIEW_BUTTON.get()),
                lines.toString());
    }

    @Test
    @DisplayName("an admin also gets a give button on every artwork")
    public void adminSearchHasGiveButton() throws Exception {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        when(database.searchArtworks("sun", null)).thenReturn(List.of(art("Sunset", UUID.randomUUID())));
        run(new Search(), "search", "sun");
        assertTrue(linesSent().get(3).contains(Lang.SEARCH_GIVE_BUTTON.get()));
    }

    @Test
    @DisplayName("--mine searches the sender's own artworks and skips the artist list")
    public void mineSearch() throws Exception {
        UUID me = player.getUniqueId();
        List<MapArt> mine = List.of(art("Mine", me));
        when(database.searchArtworks("", me)).thenReturn(mine);
        run(new Search(), "search", "--mine");
        List<String> lines = linesSent();
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(1).contains(Lang.SEARCH_ARTWORKS.get() + ": 1"));

        clearInvocations(player.spigot());
        when(database.searchArtworks("tree", me)).thenReturn(List.of());
        run(new Search(), "search", "--mine", "tree");
        verify(database).searchArtworks("tree", me);
    }

    @Test
    @DisplayName("--player resolves the name through the heads cache, with or without a term")
    public void playerSearch() throws Exception {
        UUID alice = UUID.randomUUID();
        when(ArtMap.instance().getHeadsCache().getPlayerUUID("Alice")).thenReturn(Optional.of(alice));
        when(database.searchArtworks(anyString(), eq(alice))).thenReturn(List.of(art("Hers", alice)));

        run(new Search(), "search", "--player", "Alice");
        verify(database).searchArtworks("", alice);
        run(new Search(), "search", "--player", "Alice", "her");
        verify(database).searchArtworks("her", alice);

        when(ArtMap.instance().getHeadsCache().getPlayerUUID("Nobody")).thenReturn(Optional.empty());
        assertEquals(String.format(Lang.SEARCH_PLAYER_NOT_FOUND.get(), "Nobody"), run(new Search(), "search", "--player", "Nobody").message);
    }

    @Test
    @DisplayName("a database failure during search is reported and the empty result still paginates")
    public void searchDatabaseFailure() throws Exception {
        when(database.searchArtworks(anyString(), isNull())).thenThrow(new SQLException("down"));
        run(new Search(), "search", "sun");
        verify(player).sendMessage(Lang.GENERIC_ERROR.get());
        assertTrue(linesSent().get(2).contains(Lang.SEARCH_ARTWORKS.get() + ": 0"));
    }

    // ---- repair ----------------------------------------------------------------

    private void artworkWithId(int id, boolean corrupted) throws Exception {
        MapArt art = new MapArt(id, "Art" + id, UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork(id)).thenReturn(Optional.of(art));
        when(database.getArtwork("Art" + id)).thenReturn(Optional.of(art));
        when(database.restoreMap(argThat((Map m) -> m != null && m.getMapId() == id), anyBoolean(), anyBoolean())).thenReturn(corrupted);
    }

    private static boolean anyBoolean() { return org.mockito.ArgumentMatchers.anyBoolean(); }

    @Test
    @DisplayName("scan and repair of one artwork by id or by name report its state")
    public void repairOne() throws Exception {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        artworkWithId(1, true);
        artworkWithId(2, false);

        assertEquals("Artwork is corrupted.", run(new Repair(), "repair", "-scan", "1").message);
        assertEquals("Artwork is in a good state.", run(new Repair(), "repair", "-scan", "Art2").message);
        assertEquals("Artwork repaired.", run(new Repair(), "repair", "-repair", "Art1").message);
        assertEquals("Artwork repair failed check the logs.", run(new Repair(), "repair", "-repair", "2").message);
        verify(database, times(2)).restoreMap(any(Map.class), eq(true), eq(true));

        when(database.getArtwork("Nope")).thenReturn(Optional.empty());
        assertEquals("No artwork found with this name: Nope", run(new Repair(), "repair", "-scan", "Nope").message);
        when(database.getArtwork(99)).thenReturn(Optional.empty());
        assertTrue(run(new Repair(), "repair", "-scan", "99").message.contains(":: 99"));

        when(database.getArtwork("Boom")).thenThrow(new SQLException("boom"));
        assertEquals("boom", run(new Repair(), "repair", "-scan", "Boom").message);
        when(database.getArtwork(7)).thenThrow(new SQLException("boom"));
        assertEquals("Database error! Check logs.", run(new Repair(), "repair", "-scan", "7").message,
                "a lookup failure must not read as a healthy artwork");
    }

    @Test
    @DisplayName("-all walks every artwork and logs the tally, counting missing ones as failed")
    public void repairAll() throws Exception {
        when(player.hasPermission("artmap.admin")).thenReturn(true);
        artworkWithId(1, true);
        artworkWithId(2, false);
        MapArt ghost = new MapArt(3, "Ghost", UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork(3)).thenReturn(Optional.empty());
        MapArt unreadable = new MapArt(4, "Unreadable", UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork(4)).thenThrow(new SQLException("down"));
        List<MapArt> all = List.of(database.getArtwork(1).get(), database.getArtwork(2).get(), ghost, unreadable);
        when(database.listMapArt()).thenReturn(all);

        ReturnMessage msg = run(new Repair(), "repair", "-scan", "-all");

        assertTrue(msg.message.contains("asynchronously"));
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().equals("Scan Complete: Completed: 4 Good: 1 Corrupted: 1 Failed: 2")),
                logged.stream().map(LogRecord::getMessage).toList().toString());

        logged.clear();
        run(new Repair(), "repair", "-repair", "-all");
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Repaired: 1 Failed: 2")));

        logged.clear();
        when(database.listMapArt()).thenThrow(new SQLException("down"));
        run(new Repair(), "repair", "-scan", "-all");
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Failed to retrieve artwork")));
    }
}
