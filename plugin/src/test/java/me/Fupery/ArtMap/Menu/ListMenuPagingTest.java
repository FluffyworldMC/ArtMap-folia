package me.Fupery.ArtMap.Menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.Fupery.ArtMap.Menu.API.ListMenu;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.api.Config.Lang;

// Paging through long list menus (an artist with 70+ pages of artworks). Players on 26.2 reported paging stopping
// after 64 pages: the arrows carried the page number as their stack size, which overflowed the max stack size.
public class ListMenuPagingTest {

    private static final int PAGE_SIZE = 25;
    private static final int BACK_SLOT = 0;
    private static final int NEXT_SLOT = PAGE_SIZE + 1;

    private static Player player;

    @BeforeAll
    public static void setup() {
        MockBukkit.mock();
        // A clicked arrow only plays a sound on the player; MockBukkit's PlayerMock needs a newer Guava than the
        // test classpath has.
        player = mock(Player.class);
    }

    @AfterAll
    public static void teardown() {
        MockBukkit.unmock();
    }

    private static class Item extends Button {
        Item() {
            super(Material.FILLED_MAP);
        }

        @Override
        public void onClick(Player player, ClickType clickType) {
        }
    }

    // Stands in for ArtistArtworksMenu without the database or scheduler.
    private static class StubListMenu extends ListMenu {
        private final List<Button> items;

        StubListMenu(int itemCount) {
            // A parent gives page 0 a back button instead of CloseButton, which needs Lang loaded.
            super("stub", viewer -> null, 0);
            items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) items.add(new Item());
        }

        @Override
        protected Future<List<Button>> getListItems() {
            return CompletableFuture.completedFuture(items);
        }

        // Redrawing the open inventory goes through ArtMap's scheduler; the page model is what is under test.
        @Override
        protected void refresh(Player player) {
        }

        Button[] buttons() throws Exception {
            return getButtons().get();
        }

        void click(int slot, ClickType click) throws Exception {
            Button button = buttons()[slot];
            assertNotNull(button, "no button in slot " + slot + " on page " + (page + 1));
            button.onClick(player, click);
        }

        int page() {
            return page;
        }
    }

    private static String name(Button button) {
        return button.getItemMeta().getDisplayName();
    }

    private static String pageOf(int page, int pages) {
        return " " + page + Lang.SEARCH_PAGE_SEPERATOR.get() + pages;
    }

    @Test
    public void nextArrowReachesEveryPage() throws Exception {
        int pages = 70;
        StubListMenu menu = new StubListMenu(PAGE_SIZE * pages);
        for (int p = 0; p < pages - 1; p++) {
            menu.click(NEXT_SLOT, ClickType.LEFT);
            assertEquals(p + 1, menu.page(), "next on page " + (p + 1));
        }
        assertNull(menu.buttons()[NEXT_SLOT], "Last page should not have a next arrow");
    }

    @Test
    public void arrowStackSizesStayWithinMaxStackSizePastPage64() throws Exception {
        int pages = 70;
        StubListMenu menu = new StubListMenu(PAGE_SIZE * pages);
        for (int p = 0; p < pages; p++) {
            Button[] buttons = menu.buttons();
            for (int slot : new int[] { BACK_SLOT, NEXT_SLOT }) {
                Button arrow = buttons[slot];
                if (arrow == null) continue;
                int max = arrow.getMaxStackSize();
                assertTrue(arrow.getAmount() >= 1 && arrow.getAmount() <= max,
                        "Page " + (p + 1) + " slot " + slot + " has stack size " + arrow.getAmount()
                                + " (max " + max + ")");
            }
            if (p < pages - 1) menu.click(NEXT_SLOT, ClickType.LEFT);
        }
    }

    @Test
    public void arrowsAreNamedAfterThePageTheyLeadTo() throws Exception {
        int pages = 78;
        StubListMenu menu = new StubListMenu(PAGE_SIZE * pages);
        for (int p = 0; p < 65; p++) menu.click(NEXT_SLOT, ClickType.LEFT);
        Button[] buttons = menu.buttons();
        // Page 66: the stack sizes stop at 64, the names keep counting.
        assertTrue(name(buttons[BACK_SLOT]).contains(pageOf(65, pages)), name(buttons[BACK_SLOT]));
        assertTrue(name(buttons[NEXT_SLOT]).contains(pageOf(67, pages)), name(buttons[NEXT_SLOT]));
        assertTrue(buttons[NEXT_SLOT].getItemMeta().hasLore(), "the shift-click hint is in the lore");
    }

    @Test
    public void shiftClickJumpsTenPagesAndStopsAtTheEnds() throws Exception {
        int pages = 70;
        StubListMenu menu = new StubListMenu(PAGE_SIZE * pages);
        menu.click(NEXT_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(10, menu.page());
        menu.click(NEXT_SLOT, ClickType.LEFT);
        assertEquals(11, menu.page(), "a plain click still moves one page");
        for (int i = 0; i < 5; i++) menu.click(NEXT_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(61, menu.page());
        menu.click(NEXT_SLOT, ClickType.SHIFT_RIGHT);
        assertEquals(pages - 1, menu.page(), "a jump past the end lands on the last page");

        menu.click(BACK_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(pages - 11, menu.page());
        for (int i = 0; i < 5; i++) menu.click(BACK_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(9, menu.page());
        menu.click(BACK_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(0, menu.page(), "a jump past the start lands on the first page");
    }

    @Test
    public void lastPageShowsRemainingItemsPastPage64() throws Exception {
        int pages = 70;
        int remainder = 7;
        StubListMenu menu = new StubListMenu(PAGE_SIZE * (pages - 1) + remainder);
        menu.click(NEXT_SLOT, ClickType.SHIFT_LEFT);
        for (int i = 0; i < 6; i++) menu.click(NEXT_SLOT, ClickType.SHIFT_LEFT);
        assertEquals(pages - 1, menu.page());
        Button[] buttons = menu.buttons();
        assertTrue(name(buttons[BACK_SLOT]).contains(pageOf(pages - 1, pages)), name(buttons[BACK_SLOT]));
        int shown = 0;
        for (int slot = 1; slot <= PAGE_SIZE; slot++) if (buttons[slot] != null) shown++;
        assertEquals(remainder, shown, "Page " + pages + " should show the leftover artworks");
    }
}
