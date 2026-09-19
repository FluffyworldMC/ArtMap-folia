package me.Fupery.ArtMap.Listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Stops ArtMap artwork being duplicated through crafting / cartography. */
public class PlayerCraftListenerTest extends PluginTestSupport {

    private PlayerCraftListener listener;
    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new PlayerCraftListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    @Test
    @DisplayName("a null craft result is tolerated (some forks send them)")
    public void nullCraftResultIsTolerated() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getCurrentItem()).thenReturn(null);

        listener.onPlayerCraftEvent(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("crafting something that is not a map is left alone")
    public void nonMapCraftIsIgnored() {
        org.bukkit.inventory.ItemStack stone = item(Material.STONE);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getCurrentItem()).thenReturn(stone);

        listener.onPlayerCraftEvent(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a map with no ArtMap id reports an error instead of throwing")
    public void unknownMapIsReportedNotThrown() {
        org.bukkit.inventory.ItemStack map = item(Material.FILLED_MAP);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getCurrentItem()).thenReturn(map);
        when(event.getWhoClicked()).thenReturn(player);

        listener.onPlayerCraftEvent(event);

        // No artwork resolved -> the handler must bail out, not cancel or crash.
        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a non-cartography inventory click is left alone")
    public void nonCartographyClickIsIgnored() {
        Inventory inventory = mock(Inventory.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inventory);

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }
}
