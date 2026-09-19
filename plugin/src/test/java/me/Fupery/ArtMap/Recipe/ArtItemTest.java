package me.Fupery.ArtMap.Recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The lore tags that tell ArtMap's maps apart from every other filled map on the server. */
public class ArtItemTest {

    private static final String TAG = ChatColor.AQUA.toString() + ChatColor.ITALIC;

    private static ItemStack item(Material material) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        return stack;
    }

    private static ItemStack mapWithLore(String... lore) {
        ItemStack map = item(Material.FILLED_MAP);
        ItemMeta meta = mock(ItemMeta.class);
        when(map.hasItemMeta()).thenReturn(true);
        when(map.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(lore.length > 0);
        when(meta.getLore()).thenReturn(lore.length > 0 ? Arrays.asList(lore) : null);
        return map;
    }

    private static void assertNoneMatch(ItemStack stack) {
        assertFalse(ArtItem.isArtwork(stack));
        assertFalse(ArtItem.isOriginalArtwork(stack));
        assertFalse(ArtItem.isUnfinishedArtwork(stack));
        assertFalse(ArtItem.isCopyArtwork(stack));
    }

    @Test
    @DisplayName("null, non-map, meta-less and lore-less items are never artwork")
    public void nonArtworkShapes() {
        assertNoneMatch(null);
        assertNoneMatch(item(Material.STONE));
        ItemStack bare = item(Material.FILLED_MAP);
        when(bare.hasItemMeta()).thenReturn(false);
        assertNoneMatch(bare);
        assertNoneMatch(mapWithLore());
        assertNoneMatch(mapWithLore("Just a map", "by nobody"));
    }

    @Test
    @DisplayName("a finished artwork is an artwork and an original, nothing else")
    public void originalArtwork() {
        ItemStack map = mapWithLore(TAG + "Player Artwork", "by Someone");
        assertTrue(ArtItem.isArtwork(map));
        assertTrue(ArtItem.isOriginalArtwork(map));
        assertFalse(ArtItem.isUnfinishedArtwork(map));
        assertFalse(ArtItem.isCopyArtwork(map));
    }

    @Test
    @DisplayName("an unfinished artwork is an artwork and unfinished, nothing else")
    public void unfinishedArtwork() {
        ItemStack map = mapWithLore(TAG + "Unfinished Artwork");
        assertTrue(ArtItem.isArtwork(map));
        assertTrue(ArtItem.isUnfinishedArtwork(map));
        assertFalse(ArtItem.isOriginalArtwork(map));
        assertFalse(ArtItem.isCopyArtwork(map));
    }

    @Test
    @DisplayName("a copy is an artwork and a copy, nothing else")
    public void copyArtwork() {
        ItemStack map = mapWithLore(TAG + "Artwork Copy");
        assertTrue(ArtItem.isArtwork(map));
        assertTrue(ArtItem.isCopyArtwork(map));
        assertFalse(ArtItem.isOriginalArtwork(map));
        assertFalse(ArtItem.isUnfinishedArtwork(map));
    }

    @Test
    @DisplayName("only the first lore line identifies the item")
    public void tagMustBeOnTheFirstLine() {
        assertNoneMatch(mapWithLore("by Someone", TAG + "Player Artwork"));
    }
}
