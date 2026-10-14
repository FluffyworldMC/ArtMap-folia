package me.Fupery.ArtMap.Menu.API;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.Menu.MenuTestSupport;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Button.LinkedButton;
import me.Fupery.ArtMap.Menu.Button.StaticButton;
import me.Fupery.ArtMap.Menu.Event.MenuFactory;

/** The menu building blocks: MenuType, ListMenu's page model and arrows, and the button constructors. */
public class MenuApiTest extends MenuTestSupport {

    private static final int NEXT = 26;

    private static class Item extends Button {
        Item() {
            super(Material.FILLED_MAP);
        }

        @Override
        public void onClick(Player player, ClickType clickType) {
        }
    }

    private static class StubList extends ListMenu {
        private final List<Button> items = new ArrayList<>();
        int refreshes;

        StubList(int items, int page) {
            super("stub", page);
            for (int i = 0; i < items; i++) this.items.add(new Item());
        }

        @Override
        protected Future<List<Button>> getListItems() {
            return CompletableFuture.completedFuture(items);
        }

        @Override
        protected void refresh(Player player) {
            refreshes++;
        }
    }

    // ---- MenuType -----------------------------------------------------------------

    @Test
    public void menuTypeSizesAndInventories() {
        Player viewer = player(false);
        MenuType chest = new MenuType(InventoryType.CHEST);
        assertEquals(27, chest.getDefaultSize());
        assertNotNull(chest.createInventory(viewer, "chest"));

        MenuType sized = new MenuType(9);
        assertEquals(9, sized.getDefaultSize());
        assertEquals(9, sized.createInventory(viewer, "nine").getSize());

        MenuType none = new MenuType((InventoryType) null);
        assertEquals(-1, none.getDefaultSize());
        assertNull(none.createInventory(viewer, "none"), "no type and no size makes no inventory");
        assertNull(new MenuType(0).createInventory(viewer, "empty"));
    }

    // ---- ListMenu -------------------------------------------------------------------

    @Test
    public void headingCarriesTheMenuHeader() {
        StubList menu = new StubList(1, 0);
        assertTrue(menu.getHeading().endsWith("stub"), menu.getHeading());
    }

    @Test
    public void anEmptyListHasOnePageAndNoNextArrow() throws Exception {
        StubList menu = new StubList(0, 0);
        Button[] buttons = menu.getButtons().get();
        assertNull(buttons[NEXT]);
        menu.changePage(player(false), true);
        assertEquals(0, menu.page, "there is nowhere to page to");
        assertEquals(1, menu.refreshes);
    }

    @Test
    public void singleStepPagingStaysInRange() throws Exception {
        StubList menu = new StubList(60, 0);
        menu.getButtons().get();
        Player viewer = player(false);
        menu.changePage(viewer, false);
        assertEquals(0, menu.page, "back from the first page stays there");
        menu.changePage(viewer, true);
        menu.changePage(viewer, true);
        menu.changePage(viewer, true);
        assertEquals(2, menu.page, "next from the last page stays there");
    }

    @Test
    public void pageArrowsAreEqualOnlyToTheSameDirectionOfTheSameMenu() throws Exception {
        StubList menu = new StubList(75, 1);
        Button[] first = menu.getButtons().get();
        Button[] again = menu.getButtons().get();
        Button back = first[0], next = first[NEXT];

        // MockBukkit never finds two separately built items similar (and a clone is its own ItemStackMock), so the
        // same-direction comparison past ItemStack.equals is out of reach here; these cases are not.
        assertEquals(back, back);
        assertEquals(back.hashCode(), back.hashCode());
        assertNotEquals(back, again[0].clone(), "a copy is not an arrow");
        assertNotEquals(back, next, "back and next are different buttons");
        assertNotEquals(back.hashCode(), next.hashCode());

        StubList other = new StubList(75, 1);
        assertNotEquals(back, other.getButtons().get()[0], "arrows of different menus differ");
        assertNotEquals(next, first[1], "an arrow is not a list item");
        assertNotEquals(next, new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA), "nor a plain item");
        assertFalse(next.equals(null));
    }

    @Test
    public void clickingAnArrowPagesTheMenu() throws Exception {
        StubList menu = new StubList(75, 1);
        Player viewer = player(false);
        menu.getButtons().get()[NEXT].onClick(viewer, ClickType.LEFT);
        assertEquals(2, menu.page);
        menu.getButtons().get()[0].onClick(viewer, ClickType.SHIFT_RIGHT);
        assertEquals(0, menu.page);
    }

    // ---- buttons --------------------------------------------------------------------

    private static String name(ItemStack item) {
        return item.getItemMeta().getDisplayName();
    }

    @Test
    public void buttonConstructorsSetNameAndLore() {
        // (material, name, lore...) and (material, text...) overlap; an explicit lore array picks the former.
        StaticButton named = new StaticButton(Material.STONE, "Name", new String[] { "one", "two" });
        assertEquals("Name", name(named));
        assertEquals(List.of("one", "two"), named.getItemMeta().getLore());

        StaticButton nothing = new StaticButton(Material.STONE, (String) null, new String[0]);
        assertFalse(nothing.getItemMeta().hasDisplayName() || nothing.getItemMeta().hasLore(), "a null name and no lore set nothing");

        StaticButton fromText = new StaticButton(Material.STONE, new String[] { "Title", "lore" });
        assertEquals("Title", name(fromText));
        assertEquals(List.of("lore"), fromText.getItemMeta().getLore());
        assertFalse(new StaticButton(Material.STONE, new String[0]).getItemMeta().hasDisplayName());

        StaticButton plain = new StaticButton(Material.STONE);
        assertEquals(Material.STONE, plain.getType());

        ItemStack source = new ItemStack(Material.PAPER, 3);
        ItemMeta meta = source.getItemMeta();
        meta.setDisplayName("Copied");
        source.setItemMeta(meta);
        StaticButton copy = new StaticButton(source);
        assertEquals(3, copy.getAmount());
        assertEquals("Copied", name(copy));
        copy.onClick(player(true), ClickType.LEFT); // a static button does nothing
    }

    @Test
    public void linkedButtonsOpenTheirMenu() {
        StubList target = new StubList(1, 0);
        MenuFactory factory = viewer -> target;
        LinkedButton bare = new LinkedButton(factory, Material.BOOK);
        assertEquals(Material.BOOK, bare.getType());
        LinkedButton named = new LinkedButton(factory, Material.BOOK, "Go", new String[] { "there" });
        assertEquals("Go", name(named));

        Player viewer = player(false);
        named.onClick(viewer, ClickType.LEFT);
        verify(menus).openMenu(viewer, target);
    }
}
