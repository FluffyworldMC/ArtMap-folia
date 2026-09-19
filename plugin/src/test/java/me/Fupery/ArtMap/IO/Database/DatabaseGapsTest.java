package me.Fupery.ArtMap.IO.Database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.CompressedMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.MapId;
import me.Fupery.ArtMap.Painting.ArtSession;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The database paths the original DatabaseTest left out: search, restore and repair, map creation,
 *  the table helpers behind them, and the map wrapper. Same package so the tables are reachable. */
public class DatabaseGapsTest extends PluginTestSupport {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static ArtTable artworks;
    private static MapTable maps;
    private final List<LogRecord> logged = new java.util.ArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord r) { logged.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        artworks = (ArtTable) field("artworks");
        maps = (MapTable) field("maps");
    }

    private static Object field(String name) throws Exception {
        java.lang.reflect.Field f = Database.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(db);
    }

    @BeforeEach
    public void emptyTables() throws Exception {
        for (MapArt art : db.listMapArt()) artworks.deleteArtwork(art.getMapId());
        for (MapId id : maps.getMapIds()) maps.deleteMap(id.getId());
        ArtMap.instance().getLogger().addHandler(capture);
    }

    @AfterEach
    public void restore() {
        ArtMap.instance().getLogger().removeHandler(capture);
        when(mocks.getMockServer().getWorld(anyString())).thenReturn(null);
    }

    private static byte[] filled(byte value) {
        byte[] data = new byte[Map.Size.MAX.value];
        Arrays.fill(data, value);
        return data;
    }

    private MapArt saved(int id, String title, UUID artist) throws Exception {
        MapArt art = new MapArt(id, title, artist, "Artist", "01-01-2026");
        db.saveArtwork(art, CompressedMap.compress(id, filled((byte) 1)));
        return art;
    }

    // ---- lookups ------------------------------------------------------------------

    @Test
    @DisplayName("an in-progress map counts as unsaved artwork until it is deleted")
    public void unsavedArtwork() throws Exception {
        Map map = new Map(501);
        assertFalse(db.containsUnsavedArtwork(501));
        db.saveInProgressArt(map, filled((byte) 3));
        assertTrue(db.containsUnsavedArtwork(501));
        db.deleteInProgressArt(map);
        assertFalse(db.containsUnsavedArtwork(501));
    }

    @Test
    @DisplayName("deleting in-progress art never touches a map that belongs to a saved artwork")
    public void deleteInProgressSparesSavedArt() throws Exception {
        saved(502, "Keep", ALICE);
        db.deleteInProgressArt(new Map(502));
        assertTrue(db.getArtworkCompressedMap(502).isPresent());
    }

    @Test
    @DisplayName("search matches titles by substring, optionally for one artist, and artists via the heads cache")
    public void search() throws Exception {
        saved(503, "Sunset", ALICE);
        saved(504, "Sunrise", BOB);
        saved(505, "Moon", ALICE);

        List<MapArt> all = db.searchArtworks("sun", null);
        assertEquals(2, all.size());
        List<MapArt> alices = db.searchArtworks("sun", ALICE);
        assertEquals(1, alices.size());
        assertEquals("Sunset", alices.get(0).getTitle());
        assertEquals(0, db.searchArtworks("zzz", BOB).size());

        when(ArtMap.instance().getHeadsCache().searchCache("al")).thenReturn(List.of("Alice"));
        assertEquals(List.of("Alice"), db.searchArtists("al"));
    }

    @Test
    @DisplayName("an artist's name is read from the heads cache when it is cached")
    public void cachedArtistName() throws Exception {
        saved(506, "Cached", ALICE);
        when(ArtMap.instance().getHeadsCache().isHeadCached(ALICE)).thenReturn(true);
        when(ArtMap.instance().getHeadsCache().getPlayerName(ALICE)).thenReturn("AliceFromCache");
        try {
            assertEquals("AliceFromCache", db.getArtwork("Cached").get().getArtistName());
        } finally {
            when(ArtMap.instance().getHeadsCache().isHeadCached(ALICE)).thenReturn(false);
        }
    }

    @Test
    @DisplayName("containsArtwork honours the map id unless told to ignore it")
    public void containsArtworkById() throws Exception {
        MapArt art = saved(507, "Exact", ALICE);
        MapArt movedId = new MapArt(999, "Exact", ALICE, "Artist", "01-01-2026");
        assertTrue(db.containsArtwork(art, false));
        assertFalse(db.containsArtwork(movedId, false));
        assertTrue(db.containsArtwork(movedId, true));
        assertFalse(db.containsArtwork(new MapArt(1, "Missing", ALICE, "Artist", "x"), true));
    }

    // ---- tables ---------------------------------------------------------------------

    @Test
    @DisplayName("the art table can move an artwork to a new map id")
    public void artTableUpdateMapId() throws Exception {
        MapArt art = saved(508, "Moving", ALICE);
        artworks.updateMapID(art.updateMapId(509));
        assertTrue(db.getArtwork(509).isPresent());
        assertFalse(db.getArtwork(508).isPresent());
        artworks.deleteArtwork(509);
        assertFalse(db.getArtwork("Moving").isPresent());
    }

    @Test
    @DisplayName("the map table reports hashes and ids, moves ids, and inserts in batches")
    public void mapTableHelpers() throws Exception {
        assertNull(maps.getHash(510));
        assertFalse(maps.getMap(510).isPresent());
        CompressedMap a = CompressedMap.compress(510, filled((byte) 1));
        CompressedMap b = CompressedMap.compress(511, filled((byte) 2));
        assertTrue(maps.addMaps(List.of(a, b)).isEmpty(), "nothing failed to insert");
        assertEquals(a.getHash(), maps.getHash(510));
        List<MapId> ids = maps.getMapIds();
        assertEquals(2, ids.size());
        maps.updateMapId(511, 512);
        assertTrue(maps.containsMap(512));
        assertFalse(maps.containsMap(511));
    }

    // ---- map creation and closing --------------------------------------------------

    @Test
    @DisplayName("createMap needs the configured world; without it nothing is created and the log says why")
    public void createMap() throws Exception {
        assertNull(db.createMap());
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("World is null")));

        World world = mock(World.class);
        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(777);
        when(mocks.getMockServer().getWorld(anyString())).thenReturn(world);
        when(mocks.getMockServer().createMap(world)).thenReturn(view);

        Map map = db.createMap();
        assertNotNull(map);
        assertEquals(777, map.getMapId());
        assertTrue(map.exists());
        verify(ArtMap.instance().getReflection()).setWorldMap(view, Map.BLANK_MAP);
    }

    @Test
    @DisplayName("closing the database cancels its autosave task")
    public void closeCancelsAutosave() throws Exception {
        BukkitTask task = mock(BukkitTask.class);
        when(ArtMap.instance().getScheduler().ASYNC.runTimer(any(Runnable.class), anyInt(), anyInt())).thenReturn(task);
        Database other = new Database(ArtMap.instance());
        other.close();
        verify(task).cancel();
    }

    @Test
    @DisplayName("the autosave task persists every open session and logs one that fails")
    public void autosave() throws Exception {
        ArtistHandler handler = mock(ArtistHandler.class);
        ArtSession good = mock(ArtSession.class);
        ArtSession bad = mock(ArtSession.class);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("down")).when(bad).persistMap(false);
        UUID g = UUID.randomUUID(), b = UUID.randomUUID();
        when(handler.getArtists()).thenReturn(Set.of(g, b));
        when(handler.getCurrentSession(g)).thenReturn(good);
        when(handler.getCurrentSession(b)).thenReturn(bad);
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);
        try {
            IDatabase.AUTO_SAVE.run();
        } finally {
            when(ArtMap.instance().getArtistHandler()).thenReturn(new ArtistHandler());
        }
        verify(good).persistMap(false);
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Error saving artwork")));
    }

    // ---- restore ---------------------------------------------------------------------

    @Test
    @DisplayName("a map whose world data no longer matches the stored hash is corrupted, and repair rewrites it")
    public void restoreDetectsAndRepairs() throws Exception {
        Map map = new Map(520);
        assertFalse(db.restoreMap(map, false, false), "unknown maps are not corrupted");
        db.saveInProgressArt(map, filled((byte) 7)); // world data is still blank, so the hashes differ
        map.setMap(filled((byte) 0), false);
        assertTrue(db.restoreMap(map, false, false), "a mismatch is reported without repair");
        assertEquals(0, map.readData()[0]);
        assertTrue(db.restoreMap(map, true, false), "the repair reports the corruption it fixed");
        assertEquals(7, map.readData()[0], "the world map now holds the stored data");
        assertFalse(db.restoreMap(map, true, false), "and a second pass finds nothing wrong");
    }

    @Test
    @DisplayName("a map the server cannot even read is reset from blank.dat when a hard repair is allowed")
    public void hardRepair() throws Exception {
        Map map = new Map(521);
        db.saveInProgressArt(map, filled((byte) 7));
        AtomicInteger failures = new AtomicInteger(2);
        me.Fupery.ArtMap.Utils.Reflection reflection = ArtMap.instance().getReflection();
        byte[][] world = { filled((byte) 0) };
        doAnswer(i -> {
            if (failures.getAndDecrement() > 0) throw new IllegalStateException("unreadable");
            return world[0].clone();
        }).when(reflection).getMap(argThat((MapView v) -> v != null && v.getId() == 521));
        doAnswer(i -> { world[0] = ((byte[]) i.getArgument(1)).clone(); return null; })
                .when(reflection).setWorldMap(argThat((MapView v) -> v != null && v.getId() == 521), any(byte[].class));
        File dataFile = map.getDataFile();
        dataFile.getParentFile().mkdirs();
        try {
            assertTrue(db.restoreMap(map, true, false), "without a hard repair the failure is only logged");
            assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("severly corrupted")));

            failures.set(1);
            assertTrue(db.restoreMap(map, true, true));
            assertTrue(dataFile.exists(), "blank.dat was copied into place");
            assertEquals(7, map.readData()[0], "the reset map was then repaired from the database");
        } finally {
            dataFile.delete();
        }
    }

    // ---- the map wrapper --------------------------------------------------------------

    @Test
    @DisplayName("a map can be cloned into a fresh world map, cleared, and named on disk")
    public void mapWrapper() throws Exception {
        Map map = new Map(530);
        map.setMap(filled((byte) 9));
        assertNull(map.cloneMap(), "no world, no clone");

        World world = mock(World.class);
        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(531);
        when(mocks.getMockServer().getWorld(anyString())).thenReturn(world);
        when(mocks.getMockServer().createMap(world)).thenReturn(view);
        Map clone = map.cloneMap();
        assertEquals(531, clone.getMapId());
        assertTrue(db.containsUnsavedArtwork(531));

        map.clear();
        assertEquals(Map.BLANK_MAP[0], map.readData()[0]);
        assertTrue(map.getDataFile().getName().equals("map_530.dat"));
        assertEquals(128 * 128, Map.Size.MAX.size());
        map.setRenderer(null);
        assertTrue(map.getMap().getRenderers().isEmpty());
    }

    @Test
    @DisplayName("a hard repair that cannot delete the map file, or never recovers, still reports corruption")
    public void hardRepairFailures() throws Exception {
        me.Fupery.ArtMap.Utils.Reflection reflection = ArtMap.instance().getReflection();
        doAnswer(i -> { throw new IllegalStateException("unreadable"); })
                .when(reflection).getMap(argThat((MapView v) -> v != null && v.getId() == 523));
        Map map = new Map(523);
        db.saveInProgressArt(map, filled((byte) 7));
        File dataFile = map.getDataFile();
        dataFile.getParentFile().mkdirs();
        try {
            // A non-empty directory in the file's place cannot be deleted.
            dataFile.mkdir();
            new File(dataFile, "child").createNewFile();
            assertTrue(db.restoreMap(map, true, true));
            assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Failed deleting")));
        } finally {
            new File(dataFile, "child").delete();
            dataFile.delete();
        }
        try {
            // Deleting and resetting works, but the map is still unreadable afterwards.
            logged.clear();
            assertTrue(db.restoreMap(map, true, true));
            assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("All attempts to restore")));
        } finally {
            dataFile.delete();
        }
    }

    @Test
    @DisplayName("a database file that cannot be created yields no connection")
    public void unwritableDatabaseFile() {
        SQLiteDatabase broken = new SQLiteDatabase(new File(ArtMap.instance().getDataFolder(), "no/such/dir/Art.db"));
        assertNull(broken.getConnection());
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("File write error")));
    }
}
