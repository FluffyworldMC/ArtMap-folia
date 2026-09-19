package me.Fupery.ArtMap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.IO.TitleFilter;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.IArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Configuration defaults, the menu title strings and the swear filter. */
public class ConfigGapsTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @AfterEach
    public void restore() {
        restoreConfig();
    }

    private static Configuration configFrom(String yaml) throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.loadFromString(yaml);
        IArtMap plugin = mock(IArtMap.class);
        when(plugin.getConfig()).thenReturn(file);
        return new Configuration(plugin);
    }

    @Test
    @DisplayName("missing or silly values fall back: no world, a too-short autosave, ink uses off")
    public void configurationDefaults() throws Exception {
        Configuration cfg = configFrom("language: english\nartworkAutoSave: 5\ninkUses: 3\n");
        assertEquals("world", cfg.WORLD);
        assertEquals(300, cfg.ARTWORK_AUTO_SAVE);
        assertTrue(cfg.LIMITED_INK_USES);
        assertEquals(3, cfg.INK_USES);

        Configuration explicit = configFrom("world: art\nartworkAutoSave: 60\ninkUses: 0\n");
        assertEquals("art", explicit.WORLD);
        assertEquals(60, explicit.ARTWORK_AUTO_SAVE);
        assertFalse(explicit.LIMITED_INK_USES);
    }

    @Test
    @DisplayName("menu titles and the filter set can be read and sent")
    public void menuTitlesAndFilter() {
        Player player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        assertNotNull(Lang.MenuTitle.MENU_HELP.get());
        Lang.MenuTitle.MENU_HELP.send(player);
        verify(player).sendMessage(Lang.MenuTitle.MENU_HELP.get());
        Lang.Filter.ILLEGAL_EXPRESSIONS.send(player);
        assertTrue(Lang.Filter.ILLEGAL_EXPRESSIONS.get().length > 0);
    }

    @Test
    @DisplayName("the swear filter only applies to English with the option on")
    public void swearFilter() throws Exception {
        TitleFilter filter = new TitleFilter(Lang.Filter.ILLEGAL_EXPRESSIONS.get());
        configOverriding(Map.of("SWEAR_FILTER", true, "LANGUAGE", "english"));
        assertFalse(filter.check("shitpost"));
        assertTrue(filter.check("Sunset"));
        configOverriding(Map.of("SWEAR_FILTER", true, "LANGUAGE", "german"));
        assertTrue(filter.check("shitpost"));
        configOverriding(Map.of("SWEAR_FILTER", false, "LANGUAGE", "english"));
        assertTrue(filter.check("shitpost"));
    }
}
