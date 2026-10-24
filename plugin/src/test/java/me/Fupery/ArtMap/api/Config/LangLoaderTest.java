package me.Fupery.ArtMap.api.Config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.IArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The local lang.yml is the source of truth; bundled English only supplies missing keys. */
public class LangLoaderTest extends PluginTestSupport {

    private static IArtMap plugin;
    private File langFile;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        plugin = ArtMap.instance();
    }

    @BeforeEach
    public void fresh() throws Exception {
        plugin.getDataFolder().mkdirs();
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        langFile.delete();
    }

    @AfterEach
    public void clean() {
        if (langFile != null) langFile.delete();
    }

    @Test
    @DisplayName("the bundled english file answers every key and falls back sanely for unknown ones")
    public void englishDefaults() {
        LangLoader loader = new LangLoader(plugin);
        assertNotNull(loader.loadString("NO_PERM"));
        assertNull(loader.loadString("NOT_A_REAL_KEY"));
        assertTrue(loader.loadArray("RECIPE_EASEL").length > 0, "a list key loads as a list");
        assertArrayEquals(new String[]{loader.loadString("NO_PERM")}, loader.loadArray("NO_PERM"),
                "a scalar key loads as a one-line list");
        assertArrayEquals(new String[]{"[NOT_A_REAL_KEY] NOT FOUND"}, loader.loadArray("NOT_A_REAL_KEY"));
        assertTrue(loader.loadRegex("ILLEGAL_EXPRESSIONS").length > 0);
    }

    @Test
    @DisplayName("a local value overrides the bundled file and missing keys are appended")
    public void localOverridesAndMissingKeysAreAppended() throws Exception {
        Files.write(langFile.toPath(), "PREFIX: \"[Local] \"\n".getBytes(StandardCharsets.UTF_8));
        LangLoader loader = new LangLoader(plugin);

        assertEquals("[Local] ", loader.loadString("PREFIX"));
        assertNotNull(loader.loadString("NO_PERM"));
        assertEquals("§cYou don't have permission to do this.", loader.loadString("NO_PERM"));
        assertTrue(loader.loadArray("RECIPE_EASEL").length > 0);
        loader.save();

        String saved = new String(Files.readAllBytes(langFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("NO_PERM:"), "missing scalar keys are appended");
        assertTrue(saved.contains("RECIPE_EASEL:"), "missing list keys are appended");
        assertTrue(saved.contains("[Local] "), "existing user text is preserved");
    }

    @Test
    @DisplayName("local list and scalar entries both load")
    public void localListAndScalar() throws Exception {
        Files.write(langFile.toPath(), ("NO_PERM: \"Nein\"\n"
                + "RECIPE_EASEL:\n"
                + "  - \"One\"\n"
                + "  - \"Two\"\n").getBytes(StandardCharsets.UTF_8));
        LangLoader loader = new LangLoader(plugin);
        assertEquals("Nein", loader.loadString("NO_PERM"));
        assertArrayEquals(new String[]{"Nein"}, loader.loadArray("NO_PERM"));
        assertArrayEquals(new String[]{"One", "Two"}, loader.loadArray("RECIPE_EASEL"));
    }

    @Test
    @DisplayName("the prefix is read from lang.yml")
    public void prefixComesFromLang() throws Exception {
        Files.write(langFile.toPath(), "PREFIX: \"[Brand] \"\n".getBytes(StandardCharsets.UTF_8));
        LangLoader loader = new LangLoader(plugin);
        assertEquals("[Brand] ", loader.loadString("PREFIX"));
    }
}
