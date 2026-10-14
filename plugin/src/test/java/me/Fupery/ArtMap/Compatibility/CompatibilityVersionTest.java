package me.Fupery.ArtMap.Compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.Compatibility.impl.HeadRetrieval_1_20_2;
import me.Fupery.ArtMap.Compatibility.impl.Palette_1_18;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The version hooks CompatibilityManager picks for the 26.x servers: only the canvas left-click packet changes
 *  at 26.3, where the client's arm swing became a punch. */
public class CompatibilityVersionTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @AfterEach
    public void restoreServerVersion() {
        when(mocks.getMockServer().getBukkitVersion()).thenReturn("1.21.4-R0.1-SNAPSHOT");
    }

    private static CompatibilityManager on(String bukkitVersion) {
        when(mocks.getMockServer().getBukkitVersion()).thenReturn(bukkitVersion);
        return new CompatibilityManager(mocks.getArtmapMock());
    }

    @Test
    public void upTo26_2TheLeftClickIsTheArmSwing() {
        assertEquals("ARM_ANIMATION", on("1.20.4-R0.1-SNAPSHOT").getLeftClickPacket());
        assertEquals("ARM_ANIMATION", on("26.2.build.1-stable").getLeftClickPacket());
    }

    @Test
    public void from26_3TheLeftClickIsThePunchAndTheRestCarriesOn() {
        CompatibilityManager compat = on("26.3.build.181-stable");
        assertEquals("PUNCH", compat.getLeftClickPacket());
        assertTrue(compat.getPalette() instanceof Palette_1_18);
        assertTrue(compat.getHeadsRetriever() instanceof HeadRetrieval_1_20_2);
    }
}
