package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.Painting.Brush.BrushAction;
import me.Fupery.ArtMap.Painting.Brushes.Dropper;
import me.Fupery.ArtMap.Painting.Brushes.Dye;
import me.Fupery.ArtMap.Painting.Brushes.Fill;
import me.Fupery.ArtMap.Painting.Brushes.Flip;
import me.Fupery.ArtMap.api.Colour.ArtDye;
import me.Fupery.ArtMap.api.Colour.Palette;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The four brushes against a real renderer and palette, with the cursor pinned per test so
 *  every stroke lands on a known pixel. */
public class BrushesTest extends PluginTestSupport {

    private static int nextMapId = 7000;
    private static Palette palette;
    private static ArtDye red, blue, coal, feather;

    private Player painter;
    private CanvasRenderer renderer;
    private int axis;
    private final byte[] cursor = new byte[2];
    private boolean offCanvas;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        palette = ArtMap.instance().getDyePalette();
        red = palette.getDye(item(Material.RED_DYE));
        blue = palette.getDye(item(Material.LAPIS_LAZULI));
        coal = palette.getDye(item(Material.COAL));
        feather = palette.getDye(item(Material.FEATHER));
        // Dropper announces the picked colour through the Bukkit scheduler; run it inline.
        when(Bukkit.getScheduler().runTask(any(), any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; });
    }

    @BeforeEach
    public void freshCanvas() {
        painter = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(painter);
        painter.getInventory().setItemInOffHand(item(Material.AIR));
        renderer = spy(new CanvasRenderer(new Map(nextMapId++), 0));
        doAnswer(i -> offCanvas ? null : new byte[] { cursor[0], cursor[1] }).when(renderer).getCurrentPixel();
        axis = renderer.getAxisLength();
        offCanvas = false;
        aim(axis / 2, axis / 2);
    }

    private void aim(int x, int y) {
        cursor[0] = (byte) x;
        cursor[1] = (byte) y;
    }

    private byte at(int x, int y) {
        return renderer.getPixel(x, y);
    }

    private int countPixels(byte colour) {
        int n = 0;
        for (int x = 0; x < axis; x++) for (int y = 0; y < axis; y++) if (at(x, y) == colour) n++;
        return n;
    }

    // ---- Dye ---------------------------------------------------------------

    @Nested
    class DyeBrush {
        private Dye dye;

        @BeforeEach
        void brush() { dye = new Dye(renderer, painter); }

        @Test
        @DisplayName("only palette materials count as a dye")
        void checkMaterial() {
            assertTrue(dye.checkMaterial(item(Material.RED_DYE)));
            assertTrue(dye.checkMaterial(item(Material.COAL)));
            assertFalse(dye.checkMaterial(item(Material.STONE)));
        }

        @Test
        @DisplayName("an unknown item paints nothing")
        void unknownItemPaintsNothing() {
            byte before = at(cursor[0], cursor[1]);
            assertTrue(dye.paint(BrushAction.LEFT_CLICK, item(Material.STONE), 0).isEmpty());
            assertEquals(before, at(cursor[0], cursor[1]));
        }

        @Test
        @DisplayName("a left click dots the pixel under the cursor and keeps no flow history")
        void leftClickDotsThePixel() {
            List<CachedPixel> out = dye.paint(BrushAction.LEFT_CLICK, item(Material.RED_DYE), 0);
            assertEquals(red.getColour(), at(cursor[0], cursor[1]));
            assertTrue(out.isEmpty());
            assertEquals(1, countPixels(red.getColour()));
        }

        @Test
        @DisplayName("off the canvas nothing is painted for either click")
        void offCanvasPaintsNothing() {
            offCanvas = true;
            assertTrue(dye.paint(BrushAction.LEFT_CLICK, item(Material.RED_DYE), 0).isEmpty());
            assertTrue(dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 0).isEmpty());
            assertEquals(0, countPixels(red.getColour()));
        }

        @Test
        @DisplayName("a held right click flows a continuous line between successive cursor positions")
        void rightClickFlowsALine() {
            aim(2, 2);
            dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 0);
            aim(12, 6); // shallow line, +x +y
            List<CachedPixel> out = dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 50);
            assertEquals(2, out.size());
            assertEquals(red.getColour(), at(2, 2));
            assertEquals(red.getColour(), at(12, 6));
            assertEquals(11, countPixels(red.getColour()), "one pixel per column along a 10-wide line");

            aim(6, 20); // steep line, -x +y
            dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 50);
            assertEquals(red.getColour(), at(6, 20));
            assertEquals(11 + 14, countPixels(red.getColour()), "one pixel per row along a 14-tall line");

            aim(6, 20); // no movement: the flow must not blow up on a zero-length line
            assertEquals(4, dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 50).size());
            aim(1, 9); // -x -y
            dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 50);
            assertEquals(red.getColour(), at(1, 9));
        }

        @Test
        @DisplayName("changing colour or pausing the stroke starts a fresh flow")
        void colourChangeOrPauseResetsTheFlow() {
            aim(3, 3);
            dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 0);
            aim(3, 4);
            assertEquals(2, dye.paint(BrushAction.RIGHT_CLICK, item(Material.RED_DYE), 10).size());
            aim(3, 5);
            assertEquals(1, dye.paint(BrushAction.RIGHT_CLICK, item(Material.LAPIS_LAZULI), 10).size(), "new colour, new flow");
            assertEquals(blue.getColour(), at(3, 5));
            aim(3, 6);
            assertEquals(1, dye.paint(BrushAction.RIGHT_CLICK, item(Material.LAPIS_LAZULI), 300).size(), "a pause ends the flow");
            dye.clean();
            aim(3, 7);
            assertEquals(1, dye.paint(BrushAction.RIGHT_CLICK, item(Material.LAPIS_LAZULI), 10).size());
        }

        @Test
        @DisplayName("coal darkens and feather lightens the existing pixel instead of replacing it")
        void shadingToolsShiftTheExistingColour() {
            dye.paint(BrushAction.LEFT_CLICK, item(Material.RED_DYE), 0);
            byte base = at(cursor[0], cursor[1]);
            dye.paint(BrushAction.LEFT_CLICK, item(Material.COAL), 0);
            assertEquals(coal.getDyeColour(base), at(cursor[0], cursor[1]));
            assertNotEquals(base, at(cursor[0], cursor[1]));
            dye.paint(BrushAction.LEFT_CLICK, item(Material.FEATHER), 0);
            assertEquals(base, at(cursor[0], cursor[1]), "lightening undoes one darkening step");
            assertEquals(feather.getDyeColour(coal.getDyeColour(base)), base);
        }
    }

    // ---- Dropper -----------------------------------------------------------

    @Nested
    class DropperBrush {
        private Dropper dropper;

        @BeforeEach
        void brush() { dropper = new Dropper(renderer, painter); }

        @Test
        @DisplayName("only a sponge is a dropper")
        void checkMaterial() {
            assertTrue(dropper.checkMaterial(item(Material.SPONGE)));
            assertFalse(dropper.checkMaterial(item(Material.STONE)));
        }

        @Test
        @DisplayName("before anything is picked a right click paints nothing")
        void nothingPickedPaintsNothing() {
            assertNull(dropper.getColour());
            assertTrue(dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 0).isEmpty());
        }

        @Test
        @DisplayName("a left click picks the colour under the cursor and tells the painter")
        void leftClickPicksAndAnnounces() {
            renderer.addPixel(cursor[0], cursor[1], red.getColour());
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);
            assertEquals(red.getColour(), dropper.getColour());
            verify(painter).sendMessage(contains(red.name()));
        }

        @Test
        @DisplayName("off canvas a left click picks nothing")
        void offCanvasPicksNothing() {
            offCanvas = true;
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);
            assertNull(dropper.getColour());
            verify(painter, never()).sendMessage(any(String.class));
            assertTrue(dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 0).isEmpty());
        }

        @Test
        @DisplayName("a right click paints the picked colour and flows like a dye")
        void rightClickPaintsThePickedColour() {
            renderer.addPixel(0, 0, blue.getColour());
            aim(0, 0);
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);

            aim(4, 4);
            assertEquals(1, dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 0).size());
            assertEquals(blue.getColour(), at(4, 4));
            aim(9, 4);
            assertEquals(2, dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 20).size());
            assertEquals(1 + 6, countPixels(blue.getColour()), "picked pixel plus a 6-wide line");
            aim(9, 12);
            dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 20);
            assertEquals(blue.getColour(), at(9, 12));
            aim(9, 12);
            dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 20);
            aim(9, 13);
            assertEquals(1, dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 400).size(), "a pause ends the flow");

            // Picking a different colour mid-flow starts a new one.
            renderer.addPixel(20, 20, red.getColour());
            aim(20, 20);
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);
            aim(21, 20);
            assertEquals(1, dropper.paint(BrushAction.RIGHT_CLICK, item(Material.SPONGE), 20).size());
            assertEquals(red.getColour(), at(21, 20));
        }
    }

    // ---- Fill --------------------------------------------------------------

    @Nested
    class FillBrush {
        private Dropper dropper;
        private Fill fill;

        @BeforeEach
        void brush() {
            dropper = new Dropper(renderer, painter);
            fill = new Fill(renderer, painter, dropper);
        }

        /** A vertical red wall down the middle so a fill from the left cannot reach the right. */
        private void wall() {
            for (int y = 0; y < axis; y++) renderer.addPixel(axis / 2, y, red.getColour());
        }

        @Test
        @DisplayName("only a bucket is a fill brush")
        void checkMaterial() {
            assertTrue(fill.checkMaterial(item(Material.BUCKET)));
            assertFalse(fill.checkMaterial(item(Material.STONE)));
        }

        @Test
        @DisplayName("filling with a dye in the off hand floods the contiguous region only")
        void floodsTheContiguousRegion() {
            wall();
            painter.getInventory().setItemInOffHand(item(Material.LAPIS_LAZULI));
            aim(1, 1);
            List<CachedPixel> out = fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0);
            int left = (axis / 2) * axis;
            assertEquals(left, out.size(), "every filled pixel is remembered for undo");
            assertEquals(left, countPixels(blue.getColour()));
            assertEquals(axis, countPixels(red.getColour()), "the wall is untouched");
            assertNotEquals(blue.getColour(), at(axis - 1, 1), "the far side is untouched");
        }

        @Test
        @DisplayName("a right click undoes the last fill")
        void rightClickUndoes() {
            wall();
            byte original = at(1, 1);
            painter.getInventory().setItemInOffHand(item(Material.LAPIS_LAZULI));
            aim(1, 1);
            fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0);
            fill.paint(BrushAction.RIGHT_CLICK, item(Material.BUCKET), 0);
            assertEquals(0, countPixels(blue.getColour()));
            assertEquals(original, at(1, 1));
        }

        @Test
        @DisplayName("with nothing to undo a right click does nothing")
        void rightClickWithoutFillIsNoop() {
            assertTrue(fill.paint(BrushAction.RIGHT_CLICK, item(Material.BUCKET), 0).isEmpty());
        }

        @Test
        @DisplayName("an empty off hand fills nothing")
        void emptyOffHandFillsNothing() {
            assertTrue(fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0).isEmpty());
            assertEquals(0, countPixels(blue.getColour()));
        }

        @Test
        @DisplayName("a sponge in the off hand fills with the picked colour, or nothing if none was picked")
        void spongeOffHandUsesThePickedColour() {
            painter.getInventory().setItemInOffHand(item(Material.SPONGE));
            assertTrue(fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0).isEmpty(), "nothing picked yet");

            renderer.addPixel(0, 0, red.getColour());
            aim(0, 0);
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);
            aim(5, 5);
            fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0);
            assertEquals(axis * axis, countPixels(red.getColour()), "the whole blank canvas is red");
        }

        @Test
        @DisplayName("off the canvas a fill does nothing")
        void offCanvasFillsNothing() {
            offCanvas = true;
            painter.getInventory().setItemInOffHand(item(Material.LAPIS_LAZULI));
            assertTrue(fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0).isEmpty());
            painter.getInventory().setItemInOffHand(item(Material.SPONGE));
            renderer.addPixel(0, 0, red.getColour());
            offCanvas = false; aim(0, 0);
            dropper.paint(BrushAction.LEFT_CLICK, item(Material.SPONGE), 0);
            offCanvas = true;
            assertTrue(fill.paint(BrushAction.LEFT_CLICK, item(Material.BUCKET), 0).isEmpty());
            assertEquals(1, countPixels(red.getColour()));
        }
    }

    // ---- Flip --------------------------------------------------------------

    @Nested
    class FlipBrush {
        private Flip flip;

        @BeforeEach
        void brush() { flip = new Flip(renderer, painter); }

        @Test
        @DisplayName("only a compass is a flip brush")
        void checkMaterial() {
            assertTrue(flip.checkMaterial(item(Material.COMPASS)));
            assertFalse(flip.checkMaterial(item(Material.STONE)));
        }

        @Test
        @DisplayName("a left click mirrors the canvas horizontally and a right click vertically")
        void mirrorsTheCanvas() {
            renderer.addPixel(0, 1, red.getColour());
            byte blank = at(axis - 1, 1);

            assertTrue(flip.paint(BrushAction.LEFT_CLICK, item(Material.COMPASS), 0).isEmpty(), "flips are their own undo");
            assertEquals(red.getColour(), at(axis - 1, 1));
            assertEquals(blank, at(0, 1));
            assertEquals(1, countPixels(red.getColour()));

            flip.paint(BrushAction.RIGHT_CLICK, item(Material.COMPASS), 0);
            assertEquals(red.getColour(), at(axis - 1, axis - 2));
            assertEquals(blank, at(axis - 1, 1));
            assertEquals(1, countPixels(red.getColour()));
        }
    }
}
