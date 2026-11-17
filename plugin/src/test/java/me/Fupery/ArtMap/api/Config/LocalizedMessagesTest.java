package me.Fupery.ArtMap.api.Config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.Fupery.ArtMap.api.IArtMap;

/** Uses the production language resource without booting a Minecraft registry. */
public class LocalizedMessagesTest {
    @Test
    public void upgradesKeepOverridesAndReloadNewMessages(@TempDir Path folder) throws Exception {
        Path resource = Path.of("src/main/resources/lang.yml");
        Path local = folder.resolve("lang.yml");
        String custom = "# Local translations\nARTIST_HEADS_LOADING: 'Heads remaining: %s'\n"
                + "EXPORT_BEGIN: 'Exporting %s pictures'\n";
        Files.writeString(local, custom);
        IArtMap plugin = mock(IArtMap.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocalizedMessagesTest"));
        when(plugin.getTextResourceFile(anyString())).thenAnswer(i -> Files.newBufferedReader(resource));

        Lang.load(plugin);
        assertEquals("Heads remaining: 3", String.format(Lang.ARTIST_HEADS_LOADING.get(), 3));
        assertEquals("Exporting 8 pictures", String.format(Lang.EXPORT_BEGIN.get(), 8));
        String upgraded = Files.readString(local);
        assertTrue(upgraded.startsWith(custom), "existing translations and comments are preserved");
        assertTrue(upgraded.contains("REPAIR_ID_NOT_FOUND:"));
        assertTrue(upgraded.contains("CONVERT_EXPORT_COMPLETE:"));

        YamlConfiguration source = YamlConfiguration.loadConfiguration(resource.toFile());
        for (Lang key : Lang.values()) {
            if (key.ordinal() >= Lang.ARTIST_HEADS_LOADING.ordinal()) {
                assertNotNull(source.getString(key.name()), "production default for " + key);
                assertFalse(key.get().contains("NOT FOUND"), "loaded language key " + key);
            }
        }
        // A second load must not duplicate the appended keys.
        Lang.load(plugin);
        assertEquals(upgraded, Files.readString(local));

        YamlConfiguration edited = YamlConfiguration.loadConfiguration(local.toFile());
        edited.set("ARTIST_HEADS_LOADING", "&aLoaded after reload: %s");
        edited.set("EXPORT_MAP_MISSING", "%2$s / map %1$s");
        edited.save(local.toFile());
        Lang.load(plugin);
        assertEquals("§aLoaded after reload: 2", String.format(Lang.ARTIST_HEADS_LOADING.get(), 2));
        assertEquals("Sunset / map 42", String.format(Lang.EXPORT_MAP_MISSING.get(), 42, "Sunset"));
    }
}
