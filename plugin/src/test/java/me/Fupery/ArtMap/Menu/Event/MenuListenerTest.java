package me.Fupery.ArtMap.Menu.Event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.Menu.MenuTestSupport;
import me.Fupery.ArtMap.Recipe.ArtItem;

/** MenuListener turns inventory events into menu events for players the handler tracks, and leaves the rest alone.
 *  A tracked click is not driven here: it reads player.getOpenInventory(), which cannot run on this classpath (see
 *  MenuTestSupport); the integration feature suite clicks through every menu instead. */
public class MenuListenerTest extends MenuTestSupport {

    private MenuListener listener;
    private Player tracked;
    private Player untracked;

    @BeforeEach
    public void fresh() {
        clearInvocations(menus);
        listener = new MenuListener(menus, listenerPlugin());
        tracked = player(false);
        untracked = player(false);
        when(menus.isTrackingPlayer(tracked)).thenReturn(true);
        when(menus.isTrackingPlayer(untracked)).thenReturn(false);
    }

    @Test
    public void clicksOutsideAMenuAreLeftAlone() {
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(untracked);
        listener.onMenuInteract(click);
        verify(click, never()).setCancelled(true);
    }

    @Test
    public void dragsAreDeniedOnlyInsideAMenu() {
        InventoryDragEvent outside = mock(InventoryDragEvent.class);
        when(outside.getWhoClicked()).thenReturn(untracked);
        listener.onItemDrag(outside);
        verify(outside, never()).setCancelled(true);

        InventoryDragEvent inside = mock(InventoryDragEvent.class);
        when(inside.getWhoClicked()).thenReturn(tracked);
        listener.onItemDrag(inside);
        verify(inside).setResult(Event.Result.DENY);
        verify(inside).setCancelled(true);
    }

    @Test
    public void closingTheInventoryClosesTheMenu() {
        InventoryCloseEvent outside = mock(InventoryCloseEvent.class);
        when(outside.getPlayer()).thenReturn(untracked);
        listener.onMenuClose(outside);
        verify(menus, never()).closeMenu(any(), any());

        InventoryCloseEvent inside = mock(InventoryCloseEvent.class);
        when(inside.getPlayer()).thenReturn(tracked);
        listener.onMenuClose(inside);
        verify(menus).closeMenu(tracked, MenuCloseReason.CLIENT);
    }

    @Test
    public void quittingWithAMenuOpenClosesIt() {
        PlayerQuitEvent outside = mock(PlayerQuitEvent.class);
        when(outside.getPlayer()).thenReturn(untracked);
        listener.onPlayerQuit(outside);
        verify(menus, never()).closeMenu(any(), any());

        PlayerQuitEvent inside = mock(PlayerQuitEvent.class);
        when(inside.getPlayer()).thenReturn(tracked);
        listener.onPlayerQuit(inside);
        verify(menus).closeMenu(tracked, MenuCloseReason.QUIT);
    }

    @Test
    public void dyingWithAMenuOpenClosesItAndDropsNoPreviewMap() {
        ItemStack preview = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = preview.getItemMeta();
        meta.setLore(List.of(ArtItem.PREVIEW_KEY));
        preview.setItemMeta(meta);
        ItemStack stone = new ItemStack(Material.STONE);
        List<ItemStack> drops = new ArrayList<>(List.of(preview, stone));

        PlayerDeathEvent outside = mock(PlayerDeathEvent.class);
        when(outside.getEntity()).thenReturn(untracked);
        listener.onPlayerDeath(outside);
        verify(menus, never()).closeMenu(any(), any());

        PlayerDeathEvent inside = mock(PlayerDeathEvent.class);
        when(inside.getEntity()).thenReturn(tracked);
        when(inside.getDrops()).thenReturn(drops);
        listener.onPlayerDeath(inside);
        verify(menus).closeMenu(tracked, MenuCloseReason.DEATH);
        assertEquals(Material.AIR, preview.getType(), "the preview map is not dropped");
        assertEquals(Material.STONE, stone.getType());
    }

    @Test
    public void handSwapsAreRefusedOnlyInsideAMenu() {
        PlayerSwapHandItemsEvent outside = mock(PlayerSwapHandItemsEvent.class);
        when(outside.getPlayer()).thenReturn(untracked);
        listener.onPlayerSwapHandItems(outside);
        verify(outside, never()).setCancelled(true);

        PlayerSwapHandItemsEvent inside = mock(PlayerSwapHandItemsEvent.class);
        when(inside.getPlayer()).thenReturn(tracked);
        listener.onPlayerSwapHandItems(inside);
        verify(inside).setCancelled(true);
    }

    @Test
    public void menuEventsCarryWhatHappened() {
        Inventory inventory = mock(Inventory.class);
        MenuEvent.MenuCloseEvent close = new MenuEvent.MenuCloseEvent(tracked, inventory, MenuCloseReason.BACK);
        assertSame(tracked, close.getPlayer());
        assertSame(inventory, close.getInventory());
        assertEquals(MenuCloseReason.BACK, close.getReason());
        MenuEvent.MenuInteractEvent click = new MenuEvent.MenuInteractEvent(tracked, inventory, 4, ClickType.RIGHT);
        assertEquals(4, click.getSlot());
        assertEquals(ClickType.RIGHT, click.getClick());
    }
}
