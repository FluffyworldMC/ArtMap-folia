package me.Fupery.ArtMap.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import me.Fupery.ArtMap.api.Exception.ArtMapException;

/** Lore-key tagging, map-id extraction and the give-or-drop helper. */
public class ItemUtilsTest {

    private static ItemStack item(Material material, ItemMeta meta) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(meta != null);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    private static ItemMeta meta(List<String> lore) {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasLore()).thenReturn(lore != null);
        when(meta.getLore()).thenReturn(lore);
        return meta;
    }

    // ---- giveItem ----------------------------------------------------------

    private static Player playerWithInventory(HashMap<Integer, ItemStack> leftover) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any())).thenReturn(leftover);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        return player;
    }

    @Test
    @DisplayName("an item that fits is not dropped")
    public void giveItemThatFits() {
        Player player = playerWithInventory(new HashMap<>());
        ItemStack gift = item(Material.PAPER, null);
        ItemUtils.giveItem(player, gift);
        verify(player.getInventory()).addItem(gift);
        verify(player.getWorld(), never()).dropItemNaturally(any(), any());
    }

    @Test
    @DisplayName("what does not fit is dropped at the player's feet")
    public void giveItemOverflowIsDropped() {
        ItemStack leftover = item(Material.PAPER, null);
        when(leftover.getAmount()).thenReturn(3);
        HashMap<Integer, ItemStack> map = new HashMap<>();
        map.put(0, leftover);
        Player player = playerWithInventory(map);
        ItemUtils.giveItem(player, item(Material.PAPER, null));
        verify(player.getWorld()).dropItemNaturally(player.getLocation(), leftover);
    }

    @Test
    @DisplayName("an empty leftover stack is not dropped")
    public void giveItemEmptyLeftoverIsIgnored() {
        ItemStack leftover = item(Material.PAPER, null);
        when(leftover.getAmount()).thenReturn(0);
        HashMap<Integer, ItemStack> map = new HashMap<>();
        map.put(0, leftover);
        Player player = playerWithInventory(map);
        ItemUtils.giveItem(player, item(Material.PAPER, null));
        verify(player.getWorld(), never()).dropItemNaturally(any(), any());
    }

    // ---- hasKey ------------------------------------------------------------

    @Test
    @DisplayName("hasKey is false for null, meta-less, lore-less and unrelated items")
    public void hasKeyNegatives() {
        assertFalse(ItemUtils.hasKey(null, "k"));
        assertFalse(ItemUtils.hasKey(item(Material.PAPER, null), "k"));
        assertFalse(ItemUtils.hasKey(item(Material.PAPER, meta(null)), "k"));
        assertFalse(ItemUtils.hasKey(item(Material.PAPER, meta(Arrays.asList("other"))), "k"));
    }

    @Test
    @DisplayName("hasKey finds the key anywhere in the lore")
    public void hasKeyPositive() {
        assertTrue(ItemUtils.hasKey(item(Material.PAPER, meta(Arrays.asList("first", "k"))), "k"));
    }

    // ---- addKey ------------------------------------------------------------

    @Test
    @DisplayName("addKey returns null for an item with no meta")
    public void addKeyWithoutMeta() {
        assertNull(ItemUtils.addKey(item(Material.PAPER, null), "k"));
    }

    @Test
    @DisplayName("addKey starts a lore list when there is none")
    public void addKeyCreatesLore() {
        ItemMeta meta = meta(null);
        ItemStack stack = item(Material.PAPER, meta);
        assertSame(stack, ItemUtils.addKey(stack, "k"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> lore = ArgumentCaptor.forClass(List.class);
        verify(meta).setLore(lore.capture());
        assertEquals(Arrays.asList("k"), lore.getValue());
        verify(stack).setItemMeta(meta);
    }

    @Test
    @DisplayName("addKey appends to existing lore and never duplicates a key")
    public void addKeyAppendsOnce() {
        ItemMeta meta = meta(new ArrayList<>(Arrays.asList("first")));
        ItemStack stack = item(Material.PAPER, meta);
        ItemUtils.addKey(stack, "k");
        verify(meta).setLore(Arrays.asList("first", "k"));

        ItemMeta tagged = meta(new ArrayList<>(Arrays.asList("first", "k")));
        ItemStack already = item(Material.PAPER, tagged);
        assertSame(already, ItemUtils.addKey(already, "k"));
        verify(tagged, never()).setLore(any());
        verify(already, never()).setItemMeta(any());
    }

    // ---- getMapID ----------------------------------------------------------

    @Test
    @DisplayName("getMapID refuses anything that is not a filled map")
    public void getMapIdRejectsNonMaps() {
        assertThrows(ArtMapException.class, () -> ItemUtils.getMapID(item(Material.PAPER, null)));
        assertThrows(ArtMapException.class, () -> ItemUtils.getMapID(item(Material.FILLED_MAP, null)));
    }

    @Test
    @DisplayName("getMapID is empty without a map view and carries the id with one")
    public void getMapIdReadsTheView() throws ArtMapException {
        MapMeta noView = mock(MapMeta.class);
        when(noView.hasMapView()).thenReturn(false);
        assertEquals(Optional.empty(), ItemUtils.getMapID(item(Material.FILLED_MAP, noView)));

        MapMeta nullView = mock(MapMeta.class);
        when(nullView.hasMapView()).thenReturn(true);
        when(nullView.getMapView()).thenReturn(null);
        assertEquals(Optional.empty(), ItemUtils.getMapID(item(Material.FILLED_MAP, nullView)));

        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(1234);
        MapMeta withView = mock(MapMeta.class);
        when(withView.hasMapView()).thenReturn(true);
        when(withView.getMapView()).thenReturn(view);
        assertEquals(Optional.of(1234), ItemUtils.getMapID(item(Material.FILLED_MAP, withView)));
    }
}
