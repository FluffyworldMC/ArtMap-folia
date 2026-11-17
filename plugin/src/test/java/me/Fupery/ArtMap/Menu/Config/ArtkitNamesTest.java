package me.Fupery.ArtMap.Menu.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import me.Fupery.ArtMap.ArtMap;

public class ArtkitNamesTest {
    @Test
    public void artkitNamesMigrateWithoutOverwritingCustomMenus(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        java.nio.file.Path menus = java.nio.file.Files.createDirectories(folder.resolve("menu"));
        java.nio.file.Path artkit = menus.resolve("artkit.yml");
        String custom = "# Keep my menu\ntitle: Custom palette\nrows: 3\ntool_names:\n  DYE: Custom point\n";
        java.nio.file.Files.writeString(artkit, custom);
        ArtMap localPlugin = org.mockito.Mockito.mock(ArtMap.class);
        org.mockito.Mockito.when(localPlugin.getDataFolder()).thenReturn(folder.toFile());
        org.mockito.Mockito.when(localPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("ArtkitNamesTest"));
        org.mockito.Mockito.when(localPlugin.getTextResourceFile(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new java.io.InputStreamReader(getClass().getResourceAsStream("/" + invocation.getArgument(0)), java.nio.charset.StandardCharsets.UTF_8));
        MenuConfigManager manager = new MenuConfigManager(localPlugin);
        manager.reload();
        String migrated = java.nio.file.Files.readString(artkit);
        assertTrue(migrated.startsWith(custom), "existing comments and layout stay untouched");
        assertEquals("Grass", manager.get("artkit").getColorName(1));
        assertEquals("Dark", manager.get("artkit").getShadeName(0));
        assertEquals("Custom point", manager.get("artkit").getToolName("DYE", "fallback"));
        manager.reload();
        assertEquals(migrated, java.nio.file.Files.readString(artkit), "reload must not duplicate sections");

        YamlConfiguration edited = YamlConfiguration.loadConfiguration(artkit.toFile());
        edited.set("color_names.1", "&aCustom grass");
        edited.set("shade_names.0", "Custom shadow");
        edited.set("color_names.2", null);
        edited.save(artkit.toFile());
        manager.reload();
        assertEquals("§aCustom grass", manager.get("artkit").getColorName(1));
        assertEquals("Custom shadow", manager.get("artkit").getShadeName(0));
        assertEquals("Sand", manager.get("artkit").getColorName(2), "a missing entry uses the bundled name");
    }
}
