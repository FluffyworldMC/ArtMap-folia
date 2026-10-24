package me.Fupery.ArtMap.Heads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.api.Compatability.IHeadsRetriever;
import me.Fupery.ArtMap.api.Compatability.IHeadsRetriever.HeadCacheType;
import me.Fupery.ArtMap.api.Compatability.IHeadsRetriever.TextureData;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The startup prefetch: disk cache, server-side textures and the give-up path, with the Mojang fetch off. */
public class HeadsCachePrefetchTest extends PluginTestSupport {

    private File cacheFile;
    private Database database;
    private IHeadsRetriever retriever;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() throws Exception {
        ArtMap.instance().getDataFolder().mkdirs();
        cacheFile = new File(ArtMap.instance().getDataFolder(), "heads_cache.json");
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        retriever = mock(IHeadsRetriever.class);
        when(ArtMap.instance().getCompatManager().getHeadsRetriever()).thenReturn(retriever);
        // No network in a unit test, and no ten-second pauses between artists.
        configOverriding(Map.of("HEAD_FETCH_MOJANG", false, "HEAD_PREFETCH_PERIOD", 1L, "HEAD_PREFETCH_DELAY", 0L));
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        cacheFile.delete();
    }

    /** Builds the cache with prefetch on and runs the async task it scheduled, inline. */
    private HeadsCache prefetchingCache() {
        org.mockito.Mockito.clearInvocations(ArtMap.instance().getScheduler().ASYNC); // shared mock
        HeadsCache cache = new HeadsCache(ArtMap.instance(), true);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(ArtMap.instance().getScheduler().ASYNC).runLater(task.capture(), anyLong());
        task.getValue().run();
        return cache;
    }

    @Test
    @DisplayName("prefetch counts disk-cached artists, fetches textures the server has and gives up on the rest")
    public void prefetchCoversEveryOutcome() throws Exception {
        UUID cached = UUID.randomUUID();
        Files.write(cacheFile.toPath(), ("{\"" + cached + "\":{\"name\":\"Cached\",\"texture\":\"abc\",\"type\":\"URL\"}}")
                .getBytes(StandardCharsets.UTF_8));
        Player known = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(known.hasPlayedBefore()).thenReturn(true);
        Optional<TextureData> texture = Optional.of(new TextureData(known.getName(), "tex", HeadCacheType.URL));
        when(retriever.getTextureData(known)).thenReturn(texture);
        UUID stranger = UUID.randomUUID();
        List<UUID> artists = List.of(cached, known.getUniqueId(), stranger);
        when(database.listArtists(any(UUID.class))).thenReturn(artists);

        HeadsCache cache = prefetchingCache();

        assertTrue(cache.isHeadCached(cached), "loaded from the disk cache");
        assertEquals("Cached", cache.getPlayerName(cached));
        assertTrue(cache.isHeadCached(known.getUniqueId()), "fetched from the server profile");
        assertEquals(known.getName(), cache.getPlayerName(known.getUniqueId()));
        assertEquals(Optional.of(known.getUniqueId()), cache.getPlayerUUID(known.getName()));
        assertTrue(cache.searchCache(known.getName().substring(0, 3)).contains(known.getName()));
        assertFalse(cache.isHeadCached(stranger), "nothing to fetch without the Mojang API");
        assertEquals(null, cache.getPlayerName(stranger));
        assertTrue(cacheFile.length() > 0 && new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8)
                .contains(known.getUniqueId().toString()), "the new texture was written back to disk");
    }

    @Test
    @DisplayName("a corrupt cache file is reported and the cache starts empty")
    public void corruptCacheFileIsSkipped() throws Exception {
        Files.write(cacheFile.toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        when(database.listArtists(any(UUID.class))).thenReturn(List.of());
        HeadsCache cache = prefetchingCache();
        assertFalse(cache.isHeadCached(UUID.randomUUID()));
    }

    @Test
    @DisplayName("a database failure during prefetch is logged, not thrown")
    public void databaseFailureIsContained() throws Exception {
        when(database.listArtists(any(UUID.class))).thenThrow(new java.sql.SQLException("down"));
        prefetchingCache();
    }

    @Test
    @DisplayName("updateCache refreshes one player on demand")
    public void updateOnDemand() throws Exception {
        Player known = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(known.hasPlayedBefore()).thenReturn(true);
        Optional<TextureData> texture = Optional.of(new TextureData(known.getName(), "tex", HeadCacheType.URL));
        when(retriever.getTextureData(known)).thenReturn(texture);
        HeadsCache cache = new HeadsCache(ArtMap.instance(), false);
        cache.updateCache(known.getUniqueId());
        assertTrue(cache.isHeadCached(known.getUniqueId()));
    }
}
