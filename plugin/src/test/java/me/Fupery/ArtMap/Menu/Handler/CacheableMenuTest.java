package me.Fupery.ArtMap.Menu.Handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.Menu.MenuTestSupport;
import me.Fupery.ArtMap.Menu.API.BasicMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.api.Config.Lang;

/** CacheableMenu's own lifecycle (open, click, close) and the MenuHandler's title key. Refreshing and the handler's
 *  tracking read player.getOpenInventory(), which cannot run on this classpath (see MenuTestSupport). */
public class CacheableMenuTest extends MenuTestSupport {

    private static class Counter extends Button {
        int clicks;

        Counter() {
            super(Material.STONE);
        }

        @Override
        public void onClick(Player player, ClickType clickType) {
            clicks++;
        }
    }

    private static class Menu extends BasicMenu {
        final Counter button = new Counter();
        int menuClicks;

        Menu(String heading) {
            super(heading, new MenuType(9));
        }

        @Override
        public Future<Button[]> getButtons() {
            return CompletableFuture.completedFuture(new Button[] { button, null });
        }

        @Override
        public void onMenuClickEvent(Player viewer, int slot, ClickType click) {
            menuClicks++;
        }
    }

    @Test
    public void headingsAreCutToTheThirtyTwoCharacterTitleLimit() {
        assertEquals(Lang.MenuTitle.MENU_HEADER.get() + "short", new Menu("short").heading);
        String heading = new Menu("a heading far too long to fit in an inventory title").heading;
        assertEquals(32, heading.length());
        assertTrue(heading.startsWith(Lang.MenuTitle.MENU_HEADER.get()));
    }

    @Test
    public void openingFillsTheInventoryAndClicksReachTheButtons() {
        Player viewer = player(false);
        Menu menu = new Menu("menu");
        // open/click/close are package-private on CacheableMenu, which BasicMenu (another package) does not pass on.
        CacheableMenu cacheable = menu;
        assertFalse(cacheable.isOpen());
        Inventory inventory = cacheable.open(viewer);
        verify(viewer).openInventory(inventory);
        assertTrue(cacheable.isOpen());
        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertTrue(inventory.getItem(1) == null || inventory.getItem(1).getType() == Material.AIR, "an empty slot is air");

        cacheable.click(viewer, 0, ClickType.LEFT);
        cacheable.click(viewer, 1, ClickType.LEFT);  // nothing there
        cacheable.click(viewer, 7, ClickType.LEFT);  // past the buttons
        cacheable.click(viewer, -1, ClickType.LEFT); // outside the inventory
        assertEquals(1, menu.button.clicks);
        assertEquals(4, menu.menuClicks, "the menu hears every click");
    }

    @Test
    public void closingForAReasonThatOwnsTheInventoryClosesIt() {
        Player viewer = player(false);
        CacheableMenu menu = new Menu("menu");
        menu.open(viewer);
        menu.close(viewer, MenuCloseReason.CLIENT);
        verify(viewer, never()).closeInventory();
        assertFalse(menu.isOpen());
        menu.close(viewer, MenuCloseReason.DONE);
        verify(viewer).closeInventory();
    }

    @Test
    public void theTitleKeyDropsATrailingColourCode() throws Exception {
        Field message = Lang.MenuTitle.class.getDeclaredField("message");
        message.setAccessible(true);
        String original = (String) message.get(Lang.MenuTitle.MENU_HEADER);
        try {
            message.set(Lang.MenuTitle.MENU_HEADER, "§1[Art] §7");
            MenuHandler handler = new MenuHandler(listenerPlugin());
            Field key = MenuHandler.class.getDeclaredField("menuKey");
            key.setAccessible(true);
            assertEquals("$1[art]", key.get(handler), "a menu title may start with its own colour code");
            handler.closeAll(); // nothing open: nothing to close
        } finally {
            message.set(Lang.MenuTitle.MENU_HEADER, original);
        }
    }
}
