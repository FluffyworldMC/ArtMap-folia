package me.Fupery.ArtMap.Menu.HelpMenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.MenuTestSupport;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Preview.ArtPreview;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Exception.HeadFetchException;

/** The help menus' buttons for admins, artists and plain players, and their database and head-fetch failures. */
public class HelpMenusTest extends MenuTestSupport {

    private Configuration defaults;

    @BeforeEach
    public void fresh() {
        reset(database);
        clearInvocations(menus, ArtMap.instance().getPreviewManager());
        reset(ArtMap.instance().getHeadsCache());
        defaults = ArtMap.instance().getConfiguration();
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getConfiguration()).thenReturn(defaults);
    }

    private static MapArt art(String title, UUID artist, String artistName) {
        return new MapArt(7, title, artist, artistName, "01-01-2026");
    }

    /** For the artworks list, whose buttons set the map's location name: MockBukkit's MapMeta does not implement
     *  setLocationName, so the artwork hands out a map whose meta ignores it. */
    private static MapArt listed(String title, UUID artist, String artistName) {
        MapArt art = Mockito.spy(art(title, artist, artistName));
        ItemStack real = art(title, artist, artistName == null ? "?" : artistName).getMapItem();
        MapMeta meta = Mockito.spy((MapMeta) real.getItemMeta());
        Mockito.doNothing().when(meta).setLocationName(any());
        ItemStack item = mock(ItemStack.class);
        when(item.getItemMeta()).thenReturn(meta);
        doReturn(item).when(art).getMapItem();
        return art;
    }

    private static Button[] buttons(me.Fupery.ArtMap.Menu.Handler.CacheableMenu menu) throws Exception {
        return menu.getButtons().get();
    }

    private static int buttonCount(Button[] buttons) {
        int count = 0;
        for (Button button : buttons) if (button != null) count++;
        return count;
    }

    private static List<String> lore(ItemStack item) {
        return item.getItemMeta().getLore();
    }

    /** Configuration is immutable: install a copy with the named fields replaced. */
    private void configOverriding(Map<String, Object> overrides) throws Exception {
        Configuration cfg = mock(Configuration.class);
        for (Field field : Configuration.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(cfg, overrides.containsKey(field.getName()) ? overrides.get(field.getName()) : field.get(defaults));
        }
        when(ArtMap.instance().getConfiguration()).thenReturn(cfg);
    }

    /** `player` is the artwork's artist: MapArt compares Bukkit.getOfflinePlayer(artist) with the clicking player. */
    private static MockedStatic<Bukkit> asArtist(Player player) {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS);
        bukkit.when(() -> Bukkit.getOfflinePlayer(player.getUniqueId())).thenReturn(player);
        return bukkit;
    }

    private static ItemStack previewItem() {
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = map.getItemMeta();
        meta.setLore(List.of(ArtItem.PREVIEW_KEY));
        map.setItemMeta(meta);
        return map;
    }

    // ---- recipes ------------------------------------------------------------------------

    @Test
    public void theBrushRecipeIsHiddenWhenThePaintbrushIsDisabled() throws Exception {
        assertEquals(6, buttonCount(buttons(new RecipeMenu(false))));
        configOverriding(Map.of("DISABLE_PAINTBRUSH", true));
        Button[] disabled = buttons(new RecipeMenu(false));
        assertEquals(5, buttonCount(disabled));
        assertNotNull(disabled[0], "the back arrow is still there");
        assertNotNull(new RecipeMenu(false).getParent(player(false)));
    }

    @Test
    public void recipeButtonsPreviewForEveryoneAndHandItemsToAdmins() throws Exception {
        Player admin = player(true);
        Button easel = buttons(new RecipeMenu(true))[3];
        assertTrue(lore(easel).contains(org.bukkit.ChatColor.GOLD + Lang.ADMIN_RECIPE.get()));
        easel.onClick(admin, ClickType.RIGHT);
        verify(admin.getInventory()).addItem(any(ItemStack.class));
        easel.onClick(admin, ClickType.LEFT);
        verify(menus).openMenu(eq(admin), isA(RecipePreview.class));
        clearInvocations(menus);
        easel.onClick(admin, ClickType.SHIFT_LEFT);
        verifyNoInteractions(menus);

        Player plain = player(false);
        Button plainEasel = buttons(new RecipeMenu(false))[3];
        plainEasel.onClick(plain, ClickType.RIGHT);
        verify(plain.getInventory(), never()).addItem(any(ItemStack.class));
        verify(menus).openMenu(eq(plain), isA(RecipePreview.class));
    }

    // ---- artist list --------------------------------------------------------------------

    private static ItemStack head(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName(name);
        head.setItemMeta(meta);
        return head;
    }

    private static void resetWarning() throws Exception {
        Field warned = ArtistMenu.class.getDeclaredField("haveWarnedUser");
        warned.setAccessible(true);
        warned.set(null, false);
    }

    @Test
    public void theArtistListPutsTheViewerFirstThenArtistsByName() throws Exception {
        Player viewer = player(false);
        UUID zed = UUID.randomUUID(), amy = UUID.randomUUID();
        when(database.listArtists(viewer.getUniqueId())).thenReturn(List.of(viewer.getUniqueId(), zed, amy));
        when(database.listArtistStats()).thenReturn(Collections.emptyMap());
        when(ArtMap.instance().getHeadsCache().getHead(viewer.getUniqueId())).thenReturn(head("Viewer"));
        when(ArtMap.instance().getHeadsCache().getHead(zed)).thenReturn(head("zed"));
        when(ArtMap.instance().getHeadsCache().getHead(amy)).thenReturn(head("Amy"));
        when(ArtMap.instance().getHeadsCache().getCacheSize()).thenReturn(3);

        Button[] list = buttons(new ArtistMenu(viewer));
        assertEquals("Viewer", list[1].getItemMeta().getDisplayName());
        assertEquals("Amy", list[2].getItemMeta().getDisplayName());
        assertEquals("zed", list[3].getItemMeta().getDisplayName());
        verify(viewer, never()).sendMessage(any(String.class));

        list[2].onClick(viewer, ClickType.LEFT);
        verify(menus).openMenu(eq(viewer), isA(ArtistArtworksMenu.class));
    }

    @Test
    public void artistsWhoseHeadCannotBeFetchedAreListedByTheirId() throws Exception {
        resetWarning();
        Player viewer = player(false);
        UUID artist = UUID.randomUUID(), other = UUID.randomUUID();
        when(database.listArtists(viewer.getUniqueId())).thenReturn(List.of(artist, other));
        when(ArtMap.instance().getHeadsCache().getHead(any(UUID.class))).thenThrow(new HeadFetchException("offline"));
        when(ArtMap.instance().getHeadsCache().getCacheSize()).thenReturn(0);

        ArtistMenu menu = new ArtistMenu(viewer);
        Button[] list = buttons(menu);
        assertEquals(viewer.getUniqueId().toString(), list[1].getItemMeta().getDisplayName());
        assertTrue(List.of(artist.toString(), other.toString()).contains(list[2].getItemMeta().getDisplayName()));
        verify(viewer).sendMessage(org.mockito.ArgumentMatchers.contains("2 artist(s) currently not cached"));

        clearInvocations(viewer);
        buttons(new ArtistMenu(viewer));
        verify(viewer, never()).sendMessage(any(String.class)); // warned once per server run

        assertNotNull(menu.getParent(viewer));
        assertNull(menu.getViewer(), "the mocked viewer is not online on the MockBukkit server");
    }

    @Test
    public void aDatabaseFailureShowsAnEmptyArtistList() throws Exception {
        Player viewer = player(false);
        when(database.listArtists(any())).thenThrow(new SQLException("down"));
        Button[] list = buttons(new ArtistMenu(viewer));
        assertNotNull(list[45], "the configured back button is still shown");
        assertNull(list[1]);
    }

    // ---- an artist's artworks -----------------------------------------------------------

    private static ArtistArtworksMenu artworks(Player viewer, UUID artist, String name, boolean admin, MapArt... art)
            throws Exception {
        when(database.listMapArt(artist)).thenReturn(List.of(art));
        return new ArtistArtworksMenu(new ArtistMenu(viewer), artist, name, admin, 0);
    }

    @Test
    public void theArtworksTitleFitsTheThirtyTwoCharacterLimit() throws Exception {
        Player viewer = player(false);
        UUID artist = UUID.randomUUID();
        String heading = artworks(viewer, artist, "Bob", false).getHeading();
        assertTrue(heading.length() <= 32, heading);
        assertTrue(heading.contains("Bob"), heading);
        String longName = "ABCDEFGHIJKLMNOPQRSTUVWXYZabc";
        assertTrue(artworks(viewer, artist, longName, false).getHeading().length() <= 32,
                "the inventory title is capped at 32 characters");
        String tooLong = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefgh";
        assertTrue(artworks(viewer, artist, tooLong, false).getHeading().length() <= 32);
    }

    @Test
    public void onlyAPreviewMapCountsAsAPreview() {
        assertTrue(ArtistArtworksMenu.isPreviewItem(previewItem()));
        assertFalse(ArtistArtworksMenu.isPreviewItem(null));
        assertFalse(ArtistArtworksMenu.isPreviewItem(new ItemStack(Material.STONE)));
        assertFalse(ArtistArtworksMenu.isPreviewItem(new ItemStack(Material.FILLED_MAP)), "no lore");
        ItemStack other = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = other.getItemMeta();
        meta.setLore(List.of("something else"));
        other.setItemMeta(meta);
        assertFalse(ArtistArtworksMenu.isPreviewItem(other));
        assertTrue(ArtPieceMenu.isPreviewItem(previewItem()));
        assertFalse(ArtPieceMenu.isPreviewItem(other));
        assertTrue(DeleteConfirmationMenu.isPreviewItem(previewItem()));
        assertFalse(DeleteConfirmationMenu.isPreviewItem(null));
    }

    @Test
    public void closingTheArtworksListEndsAPreviewUnlessItIsOpeningOne() throws Exception {
        Player viewer = player(false);
        ArtistArtworksMenu menu = artworks(viewer, UUID.randomUUID(), "Bob", false);
        when(viewer.getInventory().getItemInOffHand()).thenReturn(previewItem());
        menu.onMenuCloseEvent(viewer, MenuCloseReason.SPECIAL);
        verify(viewer.getInventory(), never()).setItemInOffHand(any());
        menu.onMenuCloseEvent(viewer, MenuCloseReason.CLIENT);
        verify(viewer.getInventory()).setItemInOffHand(any());

        Player holding = player(false);
        when(holding.getInventory().getItemInOffHand()).thenReturn(new ItemStack(Material.STONE));
        menu.onMenuCloseEvent(holding, MenuCloseReason.CLIENT);
        verify(holding.getInventory(), never()).setItemInOffHand(any());
        assertNotNull(menu.getParent(viewer));
    }

    @Test
    public void artworksWithoutAnArtistNameTakeTheMenusAndADatabaseFailureListsNothing() throws Exception {
        Player viewer = player(false);
        UUID artist = UUID.randomUUID();
        MapArt nameless = listed("Nameless", artist, null);
        MapArt named = listed("Nameless", artist, "Bob");
        doReturn(named).when(nameless).setAristName("Bob");
        assertNotNull(buttons(artworks(viewer, artist, "Bob", false, nameless))[1]);
        verify(nameless).setAristName("Bob");

        when(database.listMapArt(artist)).thenThrow(new SQLException("down"));
        assertNull(buttons(new ArtistArtworksMenu(new ArtistMenu(viewer), artist, "Bob", false, 0))[1]);
    }

    @Test
    public void artworkListClicksOpenThePageOrHandACopyToAdmins() throws Exception {
        UUID artist = UUID.randomUUID();
        Player admin = player(true);
        Button piece = buttons(artworks(admin, artist, "Bob", true, listed("Sunset", artist, "Bob")))[1];
        assertTrue(lore(piece).contains(org.bukkit.ChatColor.GOLD + Lang.ADMIN_RECIPE.get()));
        piece.onClick(admin, ClickType.LEFT);
        verify(menus).closeMenu(admin, MenuCloseReason.SWITCH);
        verify(menus).openMenu(eq(admin), isA(ArtPieceMenu.class));
        piece.onClick(admin, ClickType.RIGHT);
        verify(admin.getInventory()).addItem(any(ItemStack.class));

        // A list opened by an admin, clicked by someone who is not (one any longer).
        Player demoted = player(false);
        piece.onClick(demoted, ClickType.RIGHT);
        verify(demoted).sendMessage(Lang.NO_PERM.get());
        verify(demoted.getInventory(), never()).addItem(any(ItemStack.class));

        Player plain = player(false);
        Button plainPiece = buttons(artworks(plain, artist, "Bob", false, listed("Sunset", artist, "Bob")))[1];
        plainPiece.onClick(plain, ClickType.RIGHT);
        verify(plain, never()).sendMessage(any(String.class));
        plainPiece.onClick(plain, ClickType.SHIFT_LEFT);
        verify(menus, never()).openMenu(eq(plain), any());
    }

    // ---- an artwork's page --------------------------------------------------------------

    private static ArtPieceMenu page(Player viewer, MapArt art) throws Exception {
        ArtistArtworksMenu parent = artworks(viewer, art.getArtist(), art.getArtistName(), viewer.hasPermission("artmap.admin"), art);
        return new ArtPieceMenu(parent, art, viewer);
    }

    @Test
    public void renameAndDeleteAreOnlyOfferedToTheArtistAndAdmins() throws Exception {
        Player plain = player(false);
        MapArt someoneElses = art("Theirs", UUID.randomUUID(), "Them");
        Button[] seen = buttons(page(plain, someoneElses));
        assertEquals(Material.FILLED_MAP, seen[1].getType());
        assertNull(seen[2]);

        Player admin = player(true);
        assertEquals(Material.REDSTONE, buttons(page(admin, someoneElses))[2].getType());

        Player artist = player(false);
        Button[] own = buttons(page(artist, art("Mine", artist.getUniqueId(), "Me")));
        assertEquals(Material.REDSTONE, own[2].getType());
        assertEquals(Material.WRITABLE_BOOK, own[3].getType());
    }

    @Test
    public void previewNeedsAFreeOffHand() throws Exception {
        Player viewer = player(false);
        ArtPieceMenu menu = page(viewer, art("Sky", UUID.randomUUID(), "Them"));
        Button preview = buttons(menu)[1];

        preview.onClick(viewer, ClickType.LEFT);
        verify(menus).closeMenu(viewer, MenuCloseReason.SPECIAL);
        verify(ArtMap.instance().getPreviewManager()).startPreview(eq(viewer), isA(ArtPreview.class));
        verify(menus).openMenu(viewer, menu);

        when(viewer.getInventory().getItemInOffHand()).thenReturn(previewItem());
        preview.onClick(viewer, ClickType.LEFT);
        verify(ArtMap.instance().getPreviewManager(), Mockito.times(2)).startPreview(eq(viewer), any());

        when(viewer.getInventory().getItemInOffHand()).thenReturn(new ItemStack(Material.STONE));
        preview.onClick(viewer, ClickType.LEFT);
        verify(viewer).sendMessage(Lang.EMPTY_HAND_PREVIEW.get());
        verify(ArtMap.instance().getPreviewManager(), Mockito.times(2)).startPreview(eq(viewer), any());
    }

    @Test
    public void aRightClickOnThePreviewCopiesForAdminsAndTheArtistOnly() throws Exception {
        Player admin = player(true);
        buttons(page(admin, art("Sky", UUID.randomUUID(), "Them")))[1].onClick(admin, ClickType.RIGHT);
        verify(admin.getInventory()).addItem(any(ItemStack.class));

        Player plain = player(false);
        buttons(page(plain, art("Sky", UUID.randomUUID(), "Them")))[1].onClick(plain, ClickType.RIGHT);
        verify(plain).sendMessage(Lang.NO_PERM.get());
        verify(plain.getInventory(), never()).addItem(any(ItemStack.class));

        Player artist = player(false);
        MapArt own = art("Mine", artist.getUniqueId(), "Me");
        try (MockedStatic<Bukkit> bukkit = asArtist(artist)) {
            Button preview = buttons(page(artist, own))[1];
            assertTrue(lore(preview).contains(org.bukkit.ChatColor.GOLD + Lang.RECIPE_PLAYER_MAP_COPY.get()));

            preview.onClick(artist, ClickType.RIGHT);
            verify(artist).sendMessage(Lang.RECIPE_PLAYER_MAP_COPY_MISSING.get());
            verify(artist.getInventory(), never()).addItem(any(ItemStack.class));

            ItemStack maps = new ItemStack(Material.MAP, 2);
            HashMap<Integer, ItemStack> found = new HashMap<>(Map.of(4, maps));
            PlayerInventory inventory = artist.getInventory();
            when(inventory.contains(Material.MAP)).thenReturn(true);
            doReturn(found).when(inventory).all(Material.MAP);
            preview.onClick(artist, ClickType.RIGHT);
            assertEquals(1, maps.getAmount(), "the copy uses one empty map");
            verify(artist.getInventory()).addItem(any(ItemStack.class));
        }
    }

    @Test
    public void closingTheArtworkPageEndsAPreviewUnlessItIsOpeningOne() throws Exception {
        Player viewer = player(false);
        ArtPieceMenu menu = page(viewer, art("Sky", UUID.randomUUID(), "Them"));
        when(viewer.getInventory().getItemInOffHand()).thenReturn(previewItem());
        menu.onMenuCloseEvent(viewer, MenuCloseReason.SPECIAL);
        verify(viewer.getInventory(), never()).setItemInOffHand(any());
        menu.onMenuCloseEvent(viewer, MenuCloseReason.BACK);
        verify(viewer.getInventory()).setItemInOffHand(any());
        when(viewer.getInventory().getItemInOffHand()).thenReturn(new ItemStack(Material.STONE));
        menu.onMenuCloseEvent(viewer, MenuCloseReason.CLIENT);
        verify(viewer.getInventory()).setItemInOffHand(any());
        assertNotNull(menu.getParent(viewer));
    }

    @Test
    public void deleteOpensTheConfirmationOnALeftClick() throws Exception {
        Player admin = player(true);
        Button delete = buttons(page(admin, art("Sky", UUID.randomUUID(), "Them")))[2];
        delete.onClick(admin, ClickType.RIGHT);
        verifyNoInteractions(menus);
        delete.onClick(admin, ClickType.LEFT);
        verify(menus).closeMenu(admin, MenuCloseReason.DONE);
        verify(menus).openMenu(eq(admin), isA(DeleteConfirmationMenu.class));
    }

    @Test
    public void renameRefusesSomeoneWhoIsNeitherTheArtistNorAnAdmin() throws Exception {
        Player admin = player(true);
        Button rename = buttons(page(admin, art("Sky", UUID.randomUUID(), "Them")))[3];
        rename.onClick(admin, ClickType.RIGHT);
        verifyNoInteractions(menus);
        Player plain = player(false);
        rename.onClick(plain, ClickType.LEFT);
        verify(menus).closeMenu(plain, MenuCloseReason.DONE);
        verify(plain).sendMessage(org.mockito.ArgumentMatchers.startsWith(Lang.NO_PERM.get()));
    }

    // ---- delete confirmation ------------------------------------------------------------

    private static Button accept(Player viewer, MapArt art) throws Exception {
        ArtistArtworksMenu parent = artworks(viewer, art.getArtist(), art.getArtistName(), false, art);
        DeleteConfirmationMenu menu = new DeleteConfirmationMenu(parent, art, false);
        assertSame(parent, menu.getParent(viewer));
        return buttons(menu)[1];
    }

    @Test
    public void theArtistDeletesTheirArtworkWithALeftClick() throws Exception {
        Player artist = player(false);
        MapArt own = art("Mine", artist.getUniqueId(), "Me");
        Button accept = accept(artist, own);
        accept.onClick(artist, ClickType.RIGHT);
        verify(database, never()).deleteArtwork(any());
        accept.onClick(artist, ClickType.LEFT);
        verify(menus).closeMenu(artist, MenuCloseReason.DONE);
        verify(database).deleteArtwork(own);
        verify(artist).sendMessage(String.format(Lang.DELETED.get(), "Mine"));
        verify(menus).openMenu(eq(artist), isA(ArtistArtworksMenu.class));
    }

    @Test
    public void anAdminDeletesAnyArtworkAndOthersAreRefused() throws Exception {
        MapArt theirs = art("Theirs", UUID.randomUUID(), "Them");
        Player admin = player(true);
        accept(admin, theirs).onClick(admin, ClickType.LEFT);
        verify(database).deleteArtwork(theirs);

        Player plain = player(false);
        accept(plain, theirs).onClick(plain, ClickType.LEFT);
        verify(plain).sendMessage(Lang.NO_PERM.get());
        verify(database, Mockito.times(1)).deleteArtwork(theirs);
        verify(menus, never()).openMenu(eq(plain), any());
    }

    @Test
    public void aFailedDeleteIsReportedAndTheListIsNotReopened() throws Exception {
        Player artist = player(false);
        MapArt own = art("Mine", artist.getUniqueId(), "Me");
        doThrow(new SQLException("down")).when(database).deleteArtwork(own);
        accept(artist, own).onClick(artist, ClickType.LEFT);
        verify(artist).sendMessage("Error deleting Artwork check logs.");
        verify(menus, never()).openMenu(eq(artist), any());
    }
}
