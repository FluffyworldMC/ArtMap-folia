package me.Fupery.ArtMap.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The immutable artwork record: identity, validity rules and the with-er copies. */
public class MapArtTest extends PluginTestSupport {

    private static Player artist;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        artist = mocks.getRandomMockPlayers(1)[0];
        when(artist.hasPlayedBefore()).thenReturn(true);
    }

    private static MapArt art(String title) {
        return new MapArt(7, title, artist.getUniqueId(), artist.getName(), "01-02-2026");
    }

    @Test
    @DisplayName("a Date is stored as dd-MM-yyyy")
    public void dateIsFormatted() {
        @SuppressWarnings("deprecation")
        Date date = new Date(2026 - 1900, 8, 19);
        assertEquals("19-09-2026", new MapArt(1, "t", artist.getUniqueId(), "n", date).getDate());
    }

    @Test
    @DisplayName("validity needs a 3 to 16 character title and an artist who has played here")
    public void validity() {
        assertTrue(art("Sunset").isValid());
        assertTrue(art("abc").isValid());
        assertTrue(art("sixteen chars ok").isValid());
        assertFalse(art(null).isValid());
        assertFalse(art("ab").isValid());
        assertFalse(art("seventeen chars!!").isValid());
        MapArt stranger = new MapArt(7, "Sunset", UUID.randomUUID(), "Nobody", "01-02-2026");
        assertFalse(stranger.isValid(), "an artist who never played here is not valid");
        assertEquals(artist.getUniqueId(), art("Sunset").getArtistPlayer().getUniqueId());
    }

    @Test
    @DisplayName("equality is title, date and artist, with the map id optional")
    public void equality() {
        MapArt a = art("Sunset");
        assertEquals(a, art("Sunset"));
        assertEquals(a.hashCode(), art("Sunset").hashCode());
        assertNotEquals(a, art("Sunrise"));
        assertNotEquals(a, "not an artwork");
        MapArt moved = a.updateMapId(99);
        assertFalse(a.equals(moved));
        assertTrue(a.equals(moved, true));
        assertFalse(a.equals(new MapArt(7, "Sunset", artist.getUniqueId(), artist.getName(), "02-02-2026"), true));
        assertFalse(a.equals(new MapArt(7, "Sunset", UUID.randomUUID(), artist.getName(), "01-02-2026"), true));
    }

    @Test
    @DisplayName("the with-er methods copy everything but the changed field")
    public void withers() {
        MapArt a = art("Sunset");
        MapArt renamed = a.setTitle("Dawn");
        assertEquals("Dawn", renamed.getTitle());
        assertEquals(a.getMapId(), renamed.getMapId());
        assertEquals(a.getArtist(), renamed.getArtist());
        MapArt reattributed = a.setAristName("Alias");
        assertEquals("Alias", reattributed.getArtistName());
        assertEquals(a.getTitle(), reattributed.getTitle());
        assertEquals(99, a.updateMapId(99).getMapId());
        assertEquals(7, a.getMap().getMapId());
        assertTrue(a.toString().contains("Sunset"));
    }
}
