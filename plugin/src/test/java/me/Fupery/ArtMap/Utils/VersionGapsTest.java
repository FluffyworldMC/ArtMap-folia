package me.Fupery.ArtMap.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Utils.Version;
import me.Fupery.ArtMap.api.Utils.VersionHandler;
import me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The newer version brackets, the enum helpers and the comparison operators. */
public class VersionGapsTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @AfterEach
    public void restoreServerVersion() {
        when(mocks.getMockServer().getBukkitVersion()).thenReturn("1.21.4-R0.1-SNAPSHOT");
    }

    private static BukkitVersion bracketFor(String bukkitVersion) {
        when(mocks.getMockServer().getBukkitVersion()).thenReturn(bukkitVersion);
        return VersionHandler.checkVersion();
    }

    @Test
    @DisplayName("1.17, 1.18 through 1.20.1, 1.20.2 through 26.2, and everything from 26.3 up land in their brackets")
    public void newerBrackets() {
        assertEquals(BukkitVersion.v1_17, bracketFor("1.17.1-R0.1-SNAPSHOT"));
        assertEquals(BukkitVersion.v1_18, bracketFor("1.18.2-R0.1-SNAPSHOT"));
        assertEquals(BukkitVersion.v1_18, bracketFor("1.20.1-R0.1-SNAPSHOT"));
        assertEquals(BukkitVersion.v1_18, bracketFor("1.20-R0.1-SNAPSHOT"), "the 1.20.0 release string has two segments");
        assertEquals(BukkitVersion.v1_20_2, bracketFor("1.20.4-R0.1-SNAPSHOT"));
        assertEquals(BukkitVersion.v1_20_2, bracketFor("26.1.2.build.72-stable"));
        assertEquals(BukkitVersion.v1_20_2, bracketFor("26.2.build.1-stable"));
        assertEquals(BukkitVersion.v26_3, bracketFor("26.3.build.181-stable"), "26.3 swapped the swing packet for a punch");
        assertEquals(BukkitVersion.v26_3, bracketFor("27.1.build.3-stable"));
        restoreServerVersion();
        VersionHandler handler = new VersionHandler(mocks.getPluginMock());
        assertEquals(BukkitVersion.v1_20_2, handler.getVersion());
        assertEquals("v1_20_2", handler.toString());
    }

    @Test
    @DisplayName("bracket comparisons follow declaration order")
    public void bracketComparisons() {
        assertTrue(BukkitVersion.v1_14.isGreaterThan(BukkitVersion.v1_13));
        assertTrue(BukkitVersion.v1_14.isGreaterOrEqualTo(BukkitVersion.v1_14));
        assertTrue(BukkitVersion.v1_14.isEqualTo(BukkitVersion.v1_14));
        assertTrue(BukkitVersion.v1_14.isLessOrEqualTo(BukkitVersion.v1_15));
        assertFalse(BukkitVersion.v1_14.isLessThan(BukkitVersion.v1_13));
    }

    public static class FakePacket {
        public float a(float f) { return 12f; }
        public float b(float f) { return -3f; }
    }

    @Test
    @DisplayName("yaw and pitch are read off a packet through its obfuscated accessors")
    public void eulerValues() throws Exception {
        assertEquals(12f, BukkitVersion.v1_18.getYaw(new FakePacket()));
        assertEquals(-3f, BukkitVersion.v1_18.getPitch(new FakePacket()));
    }

    @Test
    @DisplayName("seat offsets and sign materials depend on the bracket, with bedrock when a name is unknown")
    public void offsetsAndSigns() {
        assertEquals(1.219, BukkitVersion.v1_18.getSeatXOffset());
        assertEquals(-2.24979, BukkitVersion.v1_18.getSeatYOffset());
        assertEquals(-2.49979, BukkitVersion.v1_20_2.getSeatYOffset());
        assertEquals(-2.49979, BukkitVersion.v26_3.getSeatYOffset(), "the 1.20.2 seat height carries on");
        assertEquals(Material.OAK_WALL_SIGN, BukkitVersion.v1_18.getWallSign());
        assertEquals(Material.OAK_SIGN, BukkitVersion.v1_18.getSign());
        // The 1.13 names no longer exist in the API on the test classpath.
        assertEquals(Material.BEDROCK, BukkitVersion.v1_13.getWallSign());
        assertEquals(Material.BEDROCK, BukkitVersion.v1_13.getSign());
    }

    @Test
    @DisplayName("version comparisons compare the shared leading segments")
    public void versionComparisons() {
        Version v = new Version(1, 14);
        assertTrue(v.isGreaterThan(1, 13));
        assertFalse(v.isGreaterThan(1, 14));
        assertTrue(v.isGreaterOrEqualTo(1, 14, 1), "prefix compare: only the shared segments count");
        // Server versions are padded to three segments so a bare 1.20 is 1.20.0, below 1.20.2.
        assertTrue(Version.getBukkitVersion("1.20-R0.1-SNAPSHOT").isLessThan(1, 20, 2));
        assertTrue(Version.getBukkitVersion("1.20-R0.1-SNAPSHOT").isEqualTo(1, 20));
        assertEquals("1.20.0", Version.getBukkitVersion("1.20-R0.1-SNAPSHOT").toString());
        assertEquals("0", Version.getBukkitVersion("garbage").toString(), "nothing numeric stays empty");
        assertTrue(v.isLessOrEqualTo(1, 14));
        assertTrue(v.isLessThan(1, 15));
        assertEquals(-1, v.compareTo(new Version(1, 15)));
        assertEquals(1, v.compareTo(new Version(1, 13, 2)));
        assertFalse(v.equals("1.14"));
        assertTrue(v.equals(new Version(1, 14)));
        assertNotNull(new Version(ArtMap.instance()));
    }
}
