package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.PixelTableManager;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.api.Painting.ICanvasRenderer;
import me.Fupery.ArtMap.api.Painting.Pixel;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Pixel value objects, the plain map renderer, and a renderer built without pixel tables. */
public class SmallPaintingGapsTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @Test
    @DisplayName("a pixel writes through to its canvas and compares by position and colour")
    public void pixel() {
        ICanvasRenderer canvas = mock(ICanvasRenderer.class);
        Pixel pixel = new Pixel(canvas, 1, 2, (byte) 3);
        assertEquals(1, pixel.getX());
        assertEquals(2, pixel.getY());
        assertEquals(3, pixel.getColour());
        pixel.setColour((byte) 4);
        verify(canvas).addPixel(1, 2, (byte) 4);
        assertEquals(4, pixel.getColour());
        assertTrue(pixel.equals(new Pixel(canvas, 1, 2, (byte) 4)));
        assertFalse(pixel.equals(new Pixel(canvas, 1, 3, (byte) 4)));
        assertFalse(pixel.equals("pixel"));
        pixel.hashCode();
    }

    @Test
    @DisplayName("a cached pixel compares by position and dye")
    public void cachedPixel() {
        CachedPixel a = new CachedPixel(1, 2, (byte) 3);
        assertTrue(a.equals(new CachedPixel(1, 2, (byte) 3)));
        assertFalse(a.equals(new CachedPixel(1, 2, (byte) 4)));
        assertFalse(a.equals("x"));
        a.hashCode();
    }

    @Test
    @DisplayName("the generic renderer paints a full map exactly once and ignores malformed data")
    public void genericRenderer() {
        byte[] map = new byte[Map.Size.MAX.value];
        map[5] = 7;
        MapCanvas canvas = mock(MapCanvas.class);
        GenericMapRenderer renderer = new GenericMapRenderer(map);
        renderer.render(mock(MapView.class), canvas, null);
        verify(canvas, times(Map.Size.MAX.value)).setPixel(anyInt(), anyInt(), anyByte());
        verify(canvas).setPixel(5, 0, (byte) 7);
        renderer.render(mock(MapView.class), canvas, null);
        verify(canvas, times(Map.Size.MAX.value)).setPixel(anyInt(), anyInt(), anyByte());

        MapCanvas untouched = mock(MapCanvas.class);
        new GenericMapRenderer(new byte[3]).render(mock(MapView.class), untouched, null);
        verify(untouched, org.mockito.Mockito.never()).setPixel(anyInt(), anyInt(), anyByte());
    }

    @Test
    @DisplayName("without pixel tables the canvas renderer is inert rather than broken")
    public void rendererWithoutTables() {
        PixelTableManager tables = ArtMap.instance().getPixelTable();
        when(ArtMap.instance().getPixelTable()).thenReturn(null);
        try {
            CanvasRenderer renderer = new CanvasRenderer(new Map(1), 0);
            assertEquals(0, renderer.getAxisLength());
        } finally {
            when(ArtMap.instance().getPixelTable()).thenReturn(tables);
        }
    }
}
