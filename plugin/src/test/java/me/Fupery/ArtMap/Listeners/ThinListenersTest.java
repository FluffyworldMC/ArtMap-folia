package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Queue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.Painting.ArtSession;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.Utils.ChunkLocation;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The small housekeeping listeners: chunk unload, map initialise, command blacklist and join. */
public class ThinListenersTest extends PluginTestSupport {

    private Player player;
    private Database database;
    private final java.util.List<LogRecord> logged = new java.util.ArrayList<>();
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
    public void fresh() throws Exception {
        mocks.resetMockPlayerPermissions();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(player);
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        ArtMap.instance().getLogger().addHandler(capture);
        ArtMap.instance().getEasels().clear();
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        when(ArtMap.instance().getArtistHandler()).thenReturn(new ArtistHandler());
        when(ArtMap.instance().isDBUpgradeNeeded()).thenReturn(false);
        ArtMap.instance().getLogger().removeHandler(capture);
        restoreConfig();
    }

    // ---- chunk unload ----------------------------------------------------------

    private static Chunk chunk(int x, int z) {
        Chunk c = mock(Chunk.class);
        when(c.getX()).thenReturn(x);
        when(c.getZ()).thenReturn(z);
        return c;
    }

    private static Easel easelIn(Chunk chunk) {
        Easel easel = mock(Easel.class);
        ChunkLocation location = chunk == null ? null : new ChunkLocation(chunk);
        when(easel.getChunk()).thenReturn(location);
        return easel;
    }

    @Test
    @DisplayName("unloading a chunk forgets the easels in it and keeps the rest")
    public void chunkUnloadForgetsEasels() {
        Location here = new Location(null, 1, 64, 1), there = new Location(null, 100, 64, 100), nowhere = new Location(null, 5, 64, 5);
        ArtMap.instance().getEasels().put(here, easelIn(chunk(0, 0)));
        ArtMap.instance().getEasels().put(there, easelIn(chunk(6, 6)));
        ArtMap.instance().getEasels().put(nowhere, easelIn(null));
        Chunk unloading = chunk(0, 0);
        ChunkUnloadEvent event = mock(ChunkUnloadEvent.class);
        when(event.getChunk()).thenReturn(unloading);

        new ChunkUnloadListener().onChunkUnload(event);

        assertFalse(ArtMap.instance().getEasels().containsKey(here));
        assertTrue(ArtMap.instance().getEasels().containsKey(there));
        assertTrue(ArtMap.instance().getEasels().containsKey(nowhere));

        ArtMap.instance().getEasels().clear();
        new ChunkUnloadListener().onChunkUnload(event); // nothing tracked: nothing scheduled
        assertTrue(ArtMap.instance().getEasels().isEmpty());
    }

    // ---- map initialise ----------------------------------------------------------

    private static MapInitializeEvent mapInit(int id) {
        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(id);
        MapInitializeEvent event = mock(MapInitializeEvent.class);
        when(event.getMap()).thenReturn(view);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Queue<Integer> mapQueue() throws Exception {
        java.lang.reflect.Field f = MapInitializeListener.class.getDeclaredField("mapQueue");
        f.setAccessible(true);
        return (Queue<Integer>) f.get(null);
    }

    @Test
    @DisplayName("initialised maps queue once each, and the tick restores those that are artworks")
    public void mapInitialiseQueuesAndRestores() throws Exception {
        mapQueue().clear();
        MapInitializeListener listener = new MapInitializeListener();
        listener.onMapInitialize(mapInit(1));
        listener.onMapInitialize(mapInit(1));
        listener.onMapInitialize(mapInit(2));
        listener.onMapInitialize(mapInit(3));
        assertEquals(3, mapQueue().size(), "duplicates are dropped");
        verify(ArtMap.instance().getScheduler().ASYNC).runTimer(org.mockito.ArgumentMatchers.eq(listener), anyLong(), anyLong());

        when(database.containsArtwork(1)).thenReturn(true);
        when(database.containsArtwork(2)).thenReturn(false);
        when(database.containsArtwork(3)).thenReturn(true);
        listener.run();
        verify(database).restoreMap(argThat((Map m) -> m.getMapId() == 1), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(false));
        verify(database).restoreMap(argThat((Map m) -> m.getMapId() == 3), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(false));
        verify(database, never()).restoreMap(argThat((Map m) -> m.getMapId() == 2), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        assertTrue(mapQueue().isEmpty(), "a vanilla map in the queue does not stall the artworks behind it");

        listener.onMapInitialize(mapInit(4));
        when(database.containsArtwork(4)).thenThrow(new SQLException("down"));
        listener.run();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Error with map restore")));
    }

    // ---- command blacklist ---------------------------------------------------------

    private PlayerCommandPreprocessEvent command(String message) {
        PlayerCommandPreprocessEvent event = mock(PlayerCommandPreprocessEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getMessage()).thenReturn(message);
        return event;
    }

    private void inArtKit(boolean inKit) {
        ArtistHandler handler = mock(ArtistHandler.class);
        ArtSession session = mock(ArtSession.class);
        when(session.isInArtKit()).thenReturn(inKit);
        when(handler.getCurrentSession(player)).thenReturn(session);
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);
    }

    @Test
    @DisplayName("blacklisted commands are blocked only while the art kit is out")
    public void commandBlacklist() throws Exception {
        configOverriding(new HashMap<>(java.util.Map.of("BLACKLIST", "tp, spawn")));
        PlayerCommandPreListener listener = new PlayerCommandPreListener();

        PlayerCommandPreprocessEvent tp = command("/TP home");
        listener.onCommandPre(tp);
        verify(tp, never()).setCancelled(true);

        inArtKit(false);
        listener.onCommandPre(tp);
        verify(tp, never()).setCancelled(true);

        inArtKit(true);
        listener.onCommandPre(tp);
        verify(tp).setCancelled(true);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("can't be used at an easel"));

        PlayerCommandPreprocessEvent art = command("/art save Sunset");
        listener.onCommandPre(art);
        verify(art, never()).setCancelled(true);
        PlayerCommandPreprocessEvent chat = command("spawn is that way");
        listener.onCommandPre(chat);
        verify(chat, never()).setCancelled(true);
    }

    @Test
    @DisplayName("an empty blacklist blocks nothing")
    public void emptyBlacklist() throws Exception {
        java.util.Map<String, Object> noList = new HashMap<>();
        noList.put("BLACKLIST", null);
        configOverriding(noList);
        inArtKit(true);
        PlayerCommandPreprocessEvent tp = command("/tp home");
        new PlayerCommandPreListener().onCommandPre(tp);
        verify(tp, never()).setCancelled(true);
    }

    // ---- join ----------------------------------------------------------------------

    @Test
    @DisplayName("joining refreshes the player's head and warns admins about an old database")
    public void join() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        PlayerJoinEventListener listener = new PlayerJoinEventListener();

        listener.onPlayerLogin(event);
        verify(ArtMap.instance().getHeadsCache()).updateCache(player.getUniqueId());
        verify(player, never()).sendMessage(anyString());

        when(ArtMap.instance().isDBUpgradeNeeded()).thenReturn(true);
        listener.onPlayerLogin(event);
        verify(player, never()).sendMessage(anyString());

        when(player.hasPermission("artmap.admin")).thenReturn(true);
        listener.onPlayerLogin(event);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("needs coverted"));
    }

    @Test
    @DisplayName("a chunk location exposes its coordinates and only equals another chunk location")
    public void chunkLocation() {
        ChunkLocation at = new ChunkLocation(chunk(3, -4));
        assertEquals(3, at.getX());
        assertEquals(-4, at.getZ());
        assertTrue(at.equals(new ChunkLocation(chunk(3, -4))));
        assertFalse(at.equals(new ChunkLocation(chunk(3, 4))));
        assertFalse(at.equals("3,-4"));
        at.hashCode();
    }
}
