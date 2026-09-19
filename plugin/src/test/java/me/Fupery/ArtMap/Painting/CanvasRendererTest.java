package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The renderer's pixel buffer, dirty queue and the render pass that pushes it onto a Bukkit map. */
public class CanvasRendererTest extends PluginTestSupport {

    private static int nextMapId = 8000;
    private CanvasRenderer renderer;
    private int axis, resolution;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() {
        renderer = new CanvasRenderer(new Map(nextMapId++), 0);
        axis = renderer.getAxisLength();
        resolution = 128 / axis;
        // loadMap queues every pixel; drain it so tests start clean.
        renderer.render(null, mock(MapCanvas.class), null);
        assertFalse(renderer.isDirty());
    }

    @Test
    @DisplayName("a painted pixel is rendered as a resolution-sized block on the map")
    public void renderPushesDirtyPixelsScaled() {
        MapCanvas canvas = mock(MapCanvas.class);
        renderer.addPixel(3, 5, (byte) 42);
        assertTrue(renderer.isDirty());

        renderer.render(null, canvas, null);

        verify(canvas, times(resolution * resolution)).setPixel(anyInt(), anyInt(), anyByte());
        verify(canvas).setPixel(3 * resolution, 5 * resolution, (byte) 42);
        verify(canvas).setPixel(3 * resolution + resolution - 1, 5 * resolution + resolution - 1, (byte) 42);
        assertFalse(renderer.isDirty(), "rendering drains the queue");

        renderer.render(null, canvas, null);
        verify(canvas, times(resolution * resolution)).setPixel(anyInt(), anyInt(), anyByte());
    }

    @Test
    @DisplayName("a stopped renderer paints nothing more")
    public void stoppedRendererIsInert() {
        MapCanvas canvas = mock(MapCanvas.class);
        renderer.addPixel(1, 1, (byte) 7);
        renderer.stop();
        renderer.render(null, canvas, null);
        verify(canvas, never()).setPixel(anyInt(), anyInt(), anyByte());
    }

    @Test
    @DisplayName("clear resets every pixel to the palette default and marks them dirty")
    public void clearResetsToDefault() {
        byte def = ArtMap.instance().getDyePalette().getDefaultColour().getColour();
        renderer.addPixel(2, 2, (byte) (def + 4));
        renderer.clear();
        assertTrue(renderer.isDirty());
        for (int x = 0; x < axis; x++) for (int y = 0; y < axis; y++) assertEquals(def, renderer.getPixel(x, y));
    }

    @Test
    @DisplayName("getMap expands the buffer into 128x128 map colours and leaves the renderer active")
    public void getMapExpandsTheBuffer() {
        renderer.addPixel(4, 6, (byte) 9);
        byte[] colours = renderer.getMap();
        assertEquals(128 * 128, colours.length);
        assertEquals((byte) 9, colours[(4 * resolution) + (6 * resolution) * 128]);
        assertEquals((byte) 9, colours[(4 * resolution + resolution - 1) + (6 * resolution + resolution - 1) * 128]);
        renderer.addPixel(0, 0, (byte) 1);
        assertTrue(renderer.isDirty(), "still active after getMap");
    }

    @Test
    @DisplayName("pixel views write back through the renderer and the buffer reads back")
    public void pixelViewsAndBufferCopy() {
        renderer.getPixelAt(1, 2).setColour((byte) 5);
        assertEquals((byte) 5, renderer.getPixel(1, 2));
        assertEquals((byte) 5, renderer.getPixelBuffer()[1][2]);
        assertNotNull(renderer.getCurrentPixel(), "the cursor starts in the middle of the canvas");
        assertFalse(renderer.isOffCanvas());
        assertSame(renderer.getMapView(), ArtMap.getMap(nextMapId - 1));
    }
}
