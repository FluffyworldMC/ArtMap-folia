package me.Fupery.ArtMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.Fupery.ArtMap.mocks.MockUtil;

public class ArtMapTest {

    private MockUtil mocks;

    @BeforeEach
    public void setup() throws Exception {
        this.mocks = new MockUtil();
        // Must be a modern version: the test classpath carries the 1.21 paper-api,
        // so onEnable has to pick the modern palette/head retriever to load cleanly.
        this.mocks.mockServer("1.21.4-R0.1-SNAPSHOT");
    }

    private static final File DATA_FOLDER = new File("./target/plugins/Artmap/");

    /** A real plugin object over the mocked server, with /art bound to a mock command. */
    private ArtMap realPlugin() throws FileNotFoundException, InvalidDescriptionException {
        Server mockServer = this.mocks.getMockServer();
        @SuppressWarnings( "deprecation" )
        JavaPluginLoader loader = new JavaPluginLoader(mockServer);
        File pluginYml = new File(getClass().getResource("/plugin.yml").getPath());
        PluginDescriptionFile desc = new PluginDescriptionFile(new FileInputStream(pluginYml));
        DATA_FOLDER.mkdirs();
        ArtMap artmap = Mockito.spy(new ArtMap(loader, desc, DATA_FOLDER, null));
        Mockito.doReturn(mock(PluginCommand.class)).when(artmap).getCommand(any(String.class));
        return artmap;
    }

    @Test
    public void testArtMapPlugin() throws FileNotFoundException, InvalidDescriptionException {
        ArtMap artmap = realPlugin();
        Assertions.assertNotNull(artmap, "Artmap instnace null!");
        artmap.onEnable();
        Assertions.assertNotNull(ArtMap.instance(), "Artmap failed to enable.");
        Assertions.assertFalse(artmap.isDBUpgradeNeeded(), "a fresh data folder needs no conversion");
    }

    /** The private startup check, called directly: onEnable stops at the ProtocolLib hook in this harness. */
    private static boolean upgradeNeeded(ArtMap artmap) throws Exception {
        ArtMap.setInstance(artmap);
        java.lang.reflect.Method check = ArtMap.class.getDeclaredMethod("checkIfDatabaseUpgradeNeeded");
        check.setAccessible(true);
        return (boolean) check.invoke(artmap);
    }

    @Test
    public void legacyDatabasesAreFlaggedOnEnable() throws Exception {
        File flat = new File(DATA_FOLDER, "mapList.yml");
        File v2 = new File(DATA_FOLDER, "ArtMap.db");
        try {
            ArtMap artmap = realPlugin();
            Assertions.assertFalse(upgradeNeeded(artmap), "a fresh data folder needs no conversion");
            Assertions.assertTrue(flat.createNewFile());
            Assertions.assertTrue(upgradeNeeded(artmap), "a flat-file database asks for /art convert");
            Assertions.assertTrue(flat.delete());
            Assertions.assertTrue(v2.createNewFile());
            Assertions.assertTrue(upgradeNeeded(artmap), "a V2 database asks for /art convert");
        } finally {
            flat.delete();
            v2.delete();
        }
    }

    @Test
    public void writeResourceCopiesOnceAndReportsFailures() throws Exception {
        ArtMap artmap = realPlugin();
        File target = new File(DATA_FOLDER, "copied-lang.yml");
        target.delete();
        try {
            Assertions.assertTrue(artmap.writeResource("lang.yml", target));
            Assertions.assertTrue(target.length() > 0, "the bundled resource was copied");
            long size = target.length();
            Assertions.assertTrue(artmap.writeResource("lang.yml", target), "an existing file is left alone");
            Assertions.assertEquals(size, target.length());
            File unwritable = new File(DATA_FOLDER, "no/such/dir/lang.yml");
            Assertions.assertFalse(artmap.writeResource("lang.yml", unwritable), "a missing parent directory is reported");
        } finally {
            target.delete();
        }
    }

    //Test artwork recycle does not delete map that is a completed artwork
}