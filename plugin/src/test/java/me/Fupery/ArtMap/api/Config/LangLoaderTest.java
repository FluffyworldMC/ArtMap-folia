package me.Fupery.ArtMap.api.Config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.IArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Language file resolution: bundled, translated, custom, and the fallbacks when keys are missing. */
public class LangLoaderTest extends PluginTestSupport {

    private static IArtMap plugin;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        plugin = ArtMap.instance();
        // doReturn: the shared stub for this method opens the resource eagerly, which a when() would trigger.
        doReturn(new StringReader("NO_PERM: Nein\n")).when(plugin).getTextResourceFile("lang/german.yml");
        doReturn(null).when(plugin).getTextResourceFile("lang/klingon.yml");
    }

    private static Configuration language(String language) throws Exception {
        Configuration cfg = mock(Configuration.class);
        Field f = Configuration.class.getDeclaredField("LANGUAGE");
        f.setAccessible(true);
        f.set(cfg, language);
        return cfg;
    }

    @Test
    @DisplayName("the bundled english file answers every key and falls back sanely for unknown ones")
    public void englishDefaults() throws Exception {
        LangLoader loader = new LangLoader(plugin, language("english"));
        assertNotNull(loader.loadString("NO_PERM"));
        assertNull(loader.loadString("NOT_A_REAL_KEY"));
        assertTrue(loader.loadArray("RECIPE_EASEL").length > 0, "a list key loads as a list");
        assertArrayEquals(new String[] { loader.loadString("NO_PERM") }, loader.loadArray("NO_PERM"), "a scalar key loads as a one-line list");
        assertArrayEquals(new String[] { "[NOT_A_REAL_KEY] NOT FOUND" }, loader.loadArray("NOT_A_REAL_KEY"));
        assertTrue(loader.loadRegex("ILLEGAL_EXPRESSIONS").length > 0);
        loader.save(); // not custom: a no-op
    }

    @Test
    @DisplayName("a translation overrides what it defines and falls back to english for the rest")
    public void translationOverridesAndFallsBack() throws Exception {
        LangLoader loader = new LangLoader(plugin, language("german"));
        assertEquals("Nein", loader.loadString("NO_PERM"));
        LangLoader english = new LangLoader(plugin, language("english"));
        assertEquals(english.loadString("PREFIX"), loader.loadString("PREFIX"));
        assertArrayEquals(english.loadArray("RECIPE_EASEL"), loader.loadArray("RECIPE_EASEL"));
    }

    @Test
    @DisplayName("an unknown language name falls back to english entirely")
    public void unknownLanguageUsesEnglish() throws Exception {
        LangLoader loader = new LangLoader(plugin, language("klingon"));
        LangLoader english = new LangLoader(plugin, language("english"));
        assertEquals(english.loadString("NO_PERM"), loader.loadString("NO_PERM"));
    }

    @Test
    @DisplayName("a custom language file is created and missing keys are appended on save")
    public void customLanguageSavesMissingKeys() throws Exception {
        File custom = new File(plugin.getDataFolder(), "lang.yml");
        plugin.getDataFolder().mkdirs();
        Files.write(custom.toPath(), "PREFIX: \"[Custom] \"\n".getBytes(StandardCharsets.UTF_8));
        try {
            LangLoader loader = new LangLoader(plugin, language("custom"));
            assertEquals("[Custom] ", loader.loadString("PREFIX"));
            assertNotNull(loader.loadString("NO_PERM"), "missing keys come from the defaults");
            loader.save();
            String saved = new String(Files.readAllBytes(custom.toPath()), StandardCharsets.UTF_8);
            assertTrue(saved.contains("NO_PERM:"), "the missing key was appended so admins can translate it");
        } finally {
            custom.delete();
        }
    }
}
