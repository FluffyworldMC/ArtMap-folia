package me.Fupery.ArtMap.api.Colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The plain dye value object. */
public class DyeGapsTest {

    @Test
    @DisplayName("a basic dye reports its names and colour, and compares by map colour")
    public void basicDye() {
        BasicDye red = new BasicDye("Rojo", "red", 1, ChatColor.RED, Material.RED_DYE);
        assertEquals(ChatColor.RED + "Rojo", red.name());
        assertEquals("Rojo", red.rawName());
        assertEquals("RED", red.englishName());
        assertEquals(ChatColor.RED, red.getDisplayColour());
        assertEquals(Material.RED_DYE, red.getMaterial());
        assertTrue(red.equals(new BasicDye("Red", "red", 1, ChatColor.RED, Material.RED_DYE)));
        assertFalse(red.equals(new BasicDye("Blue", "blue", 2, ChatColor.BLUE, Material.LAPIS_LAZULI)));
        assertFalse(red.equals("red"));
        red.hashCode();
    }
}
