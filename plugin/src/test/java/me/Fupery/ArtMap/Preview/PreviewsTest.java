package me.Fupery.ArtMap.Preview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Utils.Scheduler;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The artwork and recipe previews and the timeout that ends them. */
public class PreviewsTest extends PluginTestSupport {

    private Player viewer;
    private ItemStack previewItem;
    private ArtPreview preview;
    private Scheduler.TaskHandler timeoutHandler;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() {
        viewer = statefulPlayer("Viewer");
        viewer.getInventory().setItemInMainHand(item(Material.AIR));
        viewer.getInventory().setItemInOffHand(item(Material.AIR));
        ItemStack cursor = item(Material.AIR);
        when(viewer.getItemOnCursor()).thenReturn(cursor);
        timeoutHandler = mock(Scheduler.TaskHandler.class);
        when(ArtMap.instance().getScheduler().getTaskHandler(any(Player.class), any(Runnable.class)))
                .thenReturn(timeoutHandler);
        previewItem = item(Material.FILLED_MAP);
        MapArt art = spy(new MapArt(1, "Sunset", UUID.randomUUID(), "Artist", "01-01-2026"));
        doReturn(previewItem).when(art).getMapItem();
        preview = new ArtPreview(art);
        when(ArtMap.instance().getPreviewManager()).thenReturn(new PreviewManager());
    }

    @Test
    @DisplayName("an artwork preview goes into an empty off hand and schedules its timeout")
    public void artPreviewStarts() {
        assertTrue(preview.start(viewer));
        assertTrue(viewer.getInventory().getItemInOffHand() == previewItem);
        verify(timeoutHandler).runLater(false, 300);
        assertFalse(preview.isEventAllowed(viewer.getUniqueId(), mock(Event.class)));
    }

    @Test
    @DisplayName("a full off hand refuses the preview and tells the viewer")
    public void artPreviewNeedsEmptyOffHand() {
        viewer.getInventory().setItemInOffHand(item(Material.STONE));
        assertFalse(preview.start(viewer));
        verify(viewer).sendMessage(Lang.EMPTY_HAND_PREVIEW.get());
    }

    @Test
    @DisplayName("ending the preview takes the map back from the off hand, the cursor, or the inventory")
    public void artPreviewEnds() {
        preview.start(viewer);
        assertTrue(preview.end(viewer));
        verify(timeoutHandler).cancel();
        assertNull(viewer.getInventory().getItemInOffHand());

        viewer.getInventory().setItemInOffHand(item(Material.AIR));
        preview.start(viewer);
        when(viewer.getItemOnCursor()).thenReturn(previewItem);
        assertTrue(preview.end(viewer));
        verify(viewer).setItemOnCursor(null);

        ItemStack cursor = item(Material.AIR);
        when(viewer.getItemOnCursor()).thenReturn(cursor);
        preview.start(viewer);
        viewer.getInventory().setItemInOffHand(item(Material.AIR)); // moved elsewhere by the player
        assertTrue(preview.end(viewer));
        verify(viewer.getInventory()).removeItem(previewItem);
    }

    @Test
    @DisplayName("the timeout ends the preview through the manager, and does nothing once it is already over")
    public void timeoutEndsThroughManager() {
        Player known = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        ItemStack air = item(Material.AIR), cursor = item(Material.AIR);
        known.getInventory().setItemInOffHand(air);
        when(known.getItemOnCursor()).thenReturn(cursor);
        assertTrue(previews().startPreview(known, preview));
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(ArtMap.instance().getScheduler()).getTaskHandler(org.mockito.ArgumentMatchers.eq(known), timeout.capture());

        timeout.getValue().run();
        assertFalse(previews().isPreviewing(known));

        timeout.getValue().run();
        assertFalse(previews().isPreviewing(known));
    }

    @Test
    @DisplayName("a recipe preview opens its inventory and clears it again on end")
    public void recipePreview() {
        Inventory inventory = mock(Inventory.class);
        RecipePreview recipe = new RecipePreview(inventory);
        assertTrue(recipe.start(viewer));
        verify(viewer).openInventory(inventory);
        assertFalse(recipe.isEventAllowed(viewer.getUniqueId(), mock(Event.class)));
        assertTrue(recipe.end(viewer));
        verify(inventory).clear();
        verify(viewer).closeInventory();
        verify(viewer, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("checking an event for someone who is not previewing does nothing")
    public void checkAllowedWithoutPreview() {
        PreviewManager manager = new PreviewManager();
        assertFalse(manager.isPreviewing(viewer));
        manager.checkAllowed(viewer, mock(Event.class));
        assertFalse(manager.isPreviewing(viewer.getUniqueId()));
    }
}
