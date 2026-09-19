package me.Fupery.ArtMap.api.Colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.api.Painting.ICanvasRenderer;
import me.Fupery.ArtMap.api.Painting.Pixel;

/** Map colours come in groups of four shades; coal and feather step through them and wrap at the ends. */
public class ShadingDyeTest {

    private final ShadingDye darken = new ShadingDye("Coal", "COAL", true, ChatColor.BLACK, Material.COAL);
    private final ShadingDye lighten = new ShadingDye("Feather", "FEATHER", false, ChatColor.WHITE, Material.FEATHER);

    @Test
    @DisplayName("darkening steps down a shade and wraps from the lightest to the darkest")
    public void darkenSteps() {
        assertEquals((byte) 19, darken.getDyeColour((byte) 16), "shade 0 wraps to shade 3");
        assertEquals((byte) 16, darken.getDyeColour((byte) 17), "shade 1 -> 0");
        assertEquals((byte) 17, darken.getDyeColour((byte) 18), "shade 2 -> 1");
        assertEquals((byte) 19, darken.getDyeColour((byte) 19), "shade 3 is already darkest");
        assertEquals((byte) 0xFF, darken.getDyeColour((byte) 0xFF), "high bytes are treated unsigned");
    }

    @Test
    @DisplayName("lightening steps up a shade and wraps from the darkest to the lightest")
    public void lightenSteps() {
        assertEquals((byte) 17, lighten.getDyeColour((byte) 16), "shade 0 -> 1");
        assertEquals((byte) 18, lighten.getDyeColour((byte) 17), "shade 1 -> 2");
        assertEquals((byte) 18, lighten.getDyeColour((byte) 18), "shade 2 is already lightest");
        assertEquals((byte) 16, lighten.getDyeColour((byte) 19), "shade 3 wraps to shade 0");
    }

    @Test
    @DisplayName("applying a shade writes the shifted colour back through the canvas")
    public void applyWritesThroughTheCanvas() {
        ICanvasRenderer canvas = mock(ICanvasRenderer.class);
        Pixel pixel = new Pixel(canvas, 3, 4, (byte) 17);
        darken.apply(pixel);
        verify(canvas).addPixel(3, 4, (byte) 16);
        assertEquals((byte) 16, pixel.getColour());
    }

    @Test
    @DisplayName("shading dyes are equal by direction only and have no colour of their own")
    public void identity() {
        assertTrue(darken.equals(new ShadingDye("x", "X", true, ChatColor.GRAY, Material.CHARCOAL)));
        assertFalse(darken.equals(lighten));
        assertFalse(darken.equals("coal"));
        assertEquals(0, darken.getColour());
        darken.hashCode();
    }
}
