package me.Fupery.ArtMap.Easel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Exception.ArtMapException;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Working out what kind of canvas a map item is: unfinished, a copy, a legacy untagged map, or nothing. */
public class CanvasGapsTest extends PluginTestSupport {

    private static final String UNFINISHED = ChatColor.AQUA.toString() + ChatColor.ITALIC + "Unfinished Artwork";
    private static final String COPY = ChatColor.AQUA.toString() + ChatColor.ITALIC + "Artwork Copy";

    private Database database;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() {
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
    }

    private static String byLine(String artist) {
        return String.format(Lang.RECIPE_ARTWORK_ARTIST.get(), artist);
    }

    private static ItemStack map(Integer id, String displayName, String... lore) {
        ItemStack map = item(Material.FILLED_MAP);
        MapMeta meta = mock(MapMeta.class);
        when(meta.hasMapView()).thenReturn(id != null);
        if (id != null) {
            MapView view = mock(MapView.class);
            when(view.getId()).thenReturn(id);
            when(meta.getMapView()).thenReturn(view);
        }
        when(meta.getDisplayName()).thenReturn(displayName);
        when(meta.hasLore()).thenReturn(lore.length > 0);
        when(meta.getLore()).thenReturn(Arrays.asList(lore));
        when(map.hasItemMeta()).thenReturn(true);
        when(map.getItemMeta()).thenReturn(meta);
        return map;
    }

    @Test
    @DisplayName("only filled maps can be canvases, and one without a map view is nothing")
    public void notACanvas() throws Exception {
        assertThrows(ArtMapException.class, () -> Canvas.getCanvas(null));
        ItemStack stone = item(Material.STONE);
        assertThrows(ArtMapException.class, () -> Canvas.getCanvas(stone));
        assertFalse(Canvas.getCanvas(map(null, "x")).isPresent());
    }

    @Test
    @DisplayName("an unfinished artwork names its artist from the lore")
    public void unfinished() throws Exception {
        Canvas canvas = Canvas.getCanvas(map(5, "Unfinished", UNFINISHED, byLine("Alice"))).get();
        assertEquals(5, canvas.getMapId());
        assertEquals("Alice", canvas.artist);
        assertEquals(Material.FILLED_MAP, canvas.getEaselItem().getType());
        assertEquals("unknown", Canvas.getCanvas(map(5, "Unfinished", UNFINISHED)).get().artist);
    }

    @Test
    @DisplayName("a copy of a known artwork remembers the original; of a deleted one it is a plain canvas")
    public void copies() throws Exception {
        MapArt original = new MapArt(60, "Sunset", UUID.randomUUID(), "Alice", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(original));
        Canvas copy = Canvas.getCanvas(map(6, "Sunset", COPY, byLine("Alice"))).get();
        assertTrue(copy instanceof Canvas.CanvasCopy);
        assertEquals(60, ((Canvas.CanvasCopy) copy).getOriginalId());
        assertEquals(6, copy.getMapId());
        assertEquals(Material.FILLED_MAP, copy.getEaselItem().getType());

        when(database.getArtwork("Gone")).thenReturn(Optional.empty());
        Canvas orphan = Canvas.getCanvas(map(7, "Gone", COPY, byLine("Bob"))).get();
        assertFalse(orphan instanceof Canvas.CanvasCopy);
        assertEquals("Bob", orphan.artist);
    }

    @Test
    @DisplayName("an untagged map is recognised from the database as unsaved, saved, or unknown")
    public void legacyMaps() throws Exception {
        when(database.containsUnsavedArtwork(8)).thenReturn(true);
        assertEquals("unknown", Canvas.getCanvas(map(8, "x")).get().artist);

        MapArt saved = new MapArt(9, "Old", UUID.randomUUID(), "Carol", "01-01-2026");
        when(database.containsUnsavedArtwork(9)).thenReturn(false);
        when(database.getArtwork(9)).thenReturn(Optional.of(saved));
        Canvas canvas = Canvas.getCanvas(map(9, "x")).get();
        assertTrue(canvas instanceof Canvas.CanvasCopy);
        assertEquals(9, ((Canvas.CanvasCopy) canvas).getOriginalId());

        when(database.containsUnsavedArtwork(10)).thenReturn(false);
        when(database.getArtwork(10)).thenReturn(Optional.empty());
        assertFalse(Canvas.getCanvas(map(10, "x")).isPresent());

        assertEquals(11, new Canvas(new Map(11), "Dan").getMapId());
    }
}
