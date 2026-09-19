package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Event.PlayerMountEaselEvent;
import me.Fupery.ArtMap.Utils.Reflection;
import me.Fupery.ArtMap.Utils.Scheduler;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.api.Colour.DyeType;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** A painter's session: mounting, the art kit and its paging, strokes, persistence and dismount. */
public class ArtSessionTest extends PluginTestSupport {

    private static int nextMapId = 9000;
    private static PluginManager pluginManager;
    private static Reflection reflection;

    private Player player;
    private Easel easel;
    private Map map;
    private ArtSession session;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        reflection = ArtMap.instance().getReflection();
        // start() defers the action bar; run deferred tasks inline so the branch is exercised.
        when(ArtMap.instance().getScheduler().SYNC.runLater(any(Runnable.class), anyInt()))
                .thenAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; });
        // The scheduler is a mock, so runSafely (used by Map.update) must be told to run inline too.
        Scheduler scheduler = ArtMap.instance().getScheduler();
        doAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; }).when(scheduler).runSafely(any(Runnable.class));
        pluginManager = Bukkit.getServer().getPluginManager();
    }

    @BeforeEach
    public void fresh() throws Exception {
        player = statefulPlayer("KitTester");
        easel = mock(Easel.class);
        when(easel.seatUser(any(Player.class))).thenReturn(true);
        map = new Map(nextMapId++);
        session = new ArtSession(player, easel, map, 0);
        resetKitCache();
    }

    @AfterEach
    public void restore() {
        restoreConfig();
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        doNothing().when(pluginManager).callEvent(any());
        when(ArtMap.instance().getReflection()).thenReturn(reflection);
        ArtSession.clearHotbar(player);
    }

    // ---- fixtures --------------------------------------------------------

    /** The kit is cached in a static weak reference; drop it so a config change is visible. */
    private static void resetKitCache() throws Exception {
        Field f = ArtSession.class.getDeclaredField("kitReference");
        f.setAccessible(true);
        f.set(null, new WeakReference<>(new LinkedList<ItemStack[]>()));
    }

    private static Material typeAt(ItemStack[] slots, int i) {
        return slots[i] == null ? null : slots[i].getType();
    }

    private static int pages() {
        return (int) Math.ceil(ArtMap.instance().getDyePalette().getDyes(DyeType.DYE).length / 18d);
    }

    /** Under the mocks the AIR filler is a Paper ItemStack with no delegate, so getType throws for it. */
    private static boolean isAirOrNull(ItemStack s) {
        if (s == null) return true;
        try { return s.getType() == Material.AIR; } catch (NullPointerException noDelegate) { return true; }
    }

    // ---- start -----------------------------------------------------------

    @Test
    @DisplayName("a cancelled mount event stops the session before the easel is touched")
    public void cancelledMountEventStopsStart() throws Exception {
        doAnswer(i -> { ((PlayerMountEaselEvent) i.getArgument(0)).setCancelled(true); return null; })
                .when(pluginManager).callEvent(any());
        assertFalse(session.start(player));
        verify(easel, never()).seatUser(any());
    }

    @Test
    @DisplayName("an easel that will not seat the player stops the session")
    public void unseatedPlayerStopsStart() throws Exception {
        when(easel.seatUser(any(Player.class))).thenReturn(false);
        assertFalse(session.start(player));
        assertFalse(session.isInArtKit());
    }

    @Test
    @DisplayName("a failure restoring the map is reported and the mount is refused")
    public void restoreFailureRefusesTheMount() throws Exception {
        // The checked failure comes from the NMS reflection layer when the map data is written back.
        Reflection broken = mock(Reflection.class);
        when(broken.getMap(any(MapView.class))).thenAnswer(i -> reflection.getMap(i.getArgument(0)));
        doThrow(new NoSuchFieldException("colors")).when(broken).setWorldMap(any(MapView.class), any(byte[].class));
        when(ArtMap.instance().getReflection()).thenReturn(broken);
        assertFalse(session.start(player));
        verify(player).sendMessage(contains("Error restoring painting"));
    }

    @Test
    @DisplayName("a normal start seats the player, tells them they are painting, and leaves their inventory alone")
    public void normalStart() throws Exception {
        when(player.getVehicle()).thenReturn(mock(Entity.class));
        player.getInventory().setItem(3, item(Material.STONE));
        assertTrue(session.start(player));
        verify(easel).seatUser(player);
        assertFalse(session.isInArtKit());
        assertEquals(Material.STONE, player.getInventory().getItem(3).getType());
        assertSame(easel, session.getEasel());
    }

    // ---- art kit ---------------------------------------------------------

    @Test
    @DisplayName("with the kit forced, starting swaps the inventory for page one of the kit")
    public void forcedKitIsHandedOut() throws Exception {
        configWith(true, false);
        when(player.hasPermission("artmap.artkit")).thenReturn(true);
        player.getInventory().setItem(3, item(Material.STONE));
        player.getInventory().setItemInOffHand(item(Material.SHIELD));

        assertTrue(session.start(player));

        assertTrue(session.isInArtKit());
        ItemStack[] kit = player.getInventory().getStorageContents();
        assertNotNull(kit[9], "dyes start in the first storage row");
        assertNotEquals(Material.AIR, kit[9].getType());
        assertEquals(Material.FEATHER, typeAt(kit, 29));
        assertEquals(Material.COAL, typeAt(kit, 30));
        assertEquals(Material.COMPASS, typeAt(kit, 31));
        assertEquals(Material.BUCKET, typeAt(kit, 32));
        assertEquals(Material.SPONGE, typeAt(kit, 33));
        assertEquals(Material.REDSTONE_TORCH, typeAt(kit, 34), "the paint brush is in the kit");
        assertTrue(isAirOrNull(kit[27]), "no back button on the first page");
        assertEquals(pages() > 1, typeAt(kit, 35) == Material.MAGENTA_GLAZED_TERRACOTTA, "next button only if there is a next page");
        assertNull(player.getInventory().getItemInOffHand(), "the off hand is emptied");
    }

    @Test
    @DisplayName("with the paint brush disabled the kit has no brush")
    public void disabledPaintbrushLeavesTheKitSlotEmpty() throws Exception {
        configWith(true, true);
        when(player.hasPermission("artmap.artkit")).thenReturn(true);
        session.start(player);
        assertTrue(isAirOrNull(player.getInventory().getStorageContents()[34]));
    }

    @Test
    @DisplayName("without the permission the forced kit is not handed out")
    public void forcedKitNeedsPermission() throws Exception {
        configWith(true, false);
        when(player.hasPermission("artmap.artkit")).thenReturn(false);
        session.start(player);
        assertFalse(session.isInArtKit());
    }

    @Test
    @DisplayName("a remembered hotbar and off hand come back with the next kit, until cleared")
    public void rememberedHotbarIsRestored() throws Exception {
        // removeKit() itself cannot run here: it calls InventoryView.setCursor, which the plugin compiles
        // against as a class while the test classpath has the 1.21 interface (Paper rewrites this at runtime).
        // Seed what removeKit would have saved and check addKit's restore branch directly.
        configWith(true, false);
        when(player.hasPermission("artmap.artkit")).thenReturn(true);
        assertFalse(session.removeKit(player), "nothing to remove before the kit is given");

        ItemStack favourite = item(Material.LAPIS_LAZULI);
        ItemStack shield = item(Material.SHIELD);
        ItemStack[] saved = new ItemStack[10];
        saved[2] = favourite;
        saved[9] = shield;
        savedHotbars().put(player.getUniqueId(), saved);

        session.start(player);
        assertSame(favourite, player.getInventory().getItem(2));
        assertSame(shield, player.getInventory().getItemInOffHand());

        ArtSession.clearHotbar(player);
        ArtSession again = new ArtSession(player, easel, map, 0);
        again.start(player);
        assertTrue(isAirOrNull(player.getInventory().getItem(2)));
        assertNull(player.getInventory().getItemInOffHand());
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, ItemStack[]> savedHotbars() throws Exception {
        Field f = ArtSession.class.getDeclaredField("artkitHotbars");
        f.setAccessible(true);
        return (java.util.Map<UUID, ItemStack[]>) f.get(null);
    }

    @Test
    @DisplayName("kit pages step forward and back within bounds and keep the hotbar")
    public void kitPaging() throws Exception {
        configWith(true, false);
        when(player.hasPermission("artmap.artkit")).thenReturn(true);
        session.start(player);
        int pages = pages();
        assertTrue(pages > 1, "the palette needs more than one page for this test to mean anything");
        ItemStack held = item(Material.RED_DYE);
        player.getInventory().setItem(4, held);

        session.prevKitPage(player);
        assertTrue(isAirOrNull(player.getInventory().getStorageContents()[27]), "cannot page before the first");

        session.nextKitPage(player);
        ItemStack[] second = player.getInventory().getStorageContents();
        assertEquals(Material.MAGENTA_GLAZED_TERRACOTTA, typeAt(second, 27), "back button on page two");
        assertEquals(pages > 2, typeAt(second, 35) == Material.MAGENTA_GLAZED_TERRACOTTA);
        assertSame(held, player.getInventory().getItem(4), "paging keeps the hotbar");

        for (int i = 0; i < pages + 2; i++) session.nextKitPage(player);
        ItemStack[] last = player.getInventory().getStorageContents();
        assertEquals(Material.MAGENTA_GLAZED_TERRACOTTA, typeAt(last, 27));
        assertTrue(isAirOrNull(last[35]), "no next button on the last page");

        for (int i = 0; i < pages + 2; i++) session.prevKitPage(player);
        assertTrue(isAirOrNull(player.getInventory().getStorageContents()[27]));
        assertSame(held, player.getInventory().getItem(4));
    }

    // ---- strokes and persistence ---------------------------------------------

    /** The cursor starts on pixel (limit/2, limit/2) where limit is axis-1. */
    private byte centrePixel() {
        int res = ArtMap.instance().getPixelTable().getResolutionFactor();
        int mid = ((128 / res) - 1) / 2;
        return map.readData()[(mid * res) + (mid * res) * 128];
    }

    @Test
    @DisplayName("a stroke after the cooldown paints, persists on demand, and marks the session dirty")
    public void strokePaintsAndPersists() throws Exception {
        session.start(player);
        byte red = ArtMap.instance().getDyePalette().getDye(item(Material.RED_DYE)).getColour();
        session.setDirty(false);
        session.sendMap(player);
        verify(player, never()).sendMap(any(MapView.class));

        session.paint(item(Material.RED_DYE), Brush.BrushAction.LEFT_CLICK);
        assertNotEquals(red, centrePixel(), "inside the cooldown the stroke is dropped");
        Thread.sleep(200);
        session.paint(item(Material.RED_DYE), Brush.BrushAction.LEFT_CLICK);
        session.sendMap(player);
        verify(player).sendMap(any(MapView.class));
        session.persistMap(false);
        assertEquals(red, centrePixel());
        assertTrue(session.lastPaintActionWithin(5, TimeUnit.SECONDS));
        assertFalse(session.lastPaintActionWithin(0, TimeUnit.SECONDS));

        Thread.sleep(200);
        session.paint(item(Material.STONE), Brush.BrushAction.LEFT_CLICK);
        Thread.sleep(400);
        session.paint(item(Material.BUCKET), Brush.BrushAction.RIGHT_CLICK);
        session.updatePosition(90f, 0f);
        Thread.sleep(400);
        session.paint(item(Material.RED_DYE), Brush.BrushAction.LEFT_CLICK);
        session.persistMap(false);
        assertEquals(red, centrePixel(), "off the canvas nothing changes");

        session.clearMap();
        assertEquals(ArtMap.instance().getDyePalette().getDefaultColour().getColour(), centrePixel());
    }

    @Test
    @DisplayName("ending the session dismounts the painter, frees the easel and saves the painting")
    public void endDismountsAndSaves() throws Exception {
        player.getInventory().setItem(3, item(Material.STONE));
        session.start(player);
        session.setActive(true);
        assertTrue(session.isActive());

        session.end(player);

        assertFalse(session.isActive());
        assertFalse(session.isInArtKit());
        assertEquals(Material.STONE, player.getInventory().getItem(3).getType());
        verify(player).teleport(any(Location.class));
        verify(easel).removeUser();
        verify(player, never()).sendMessage(anyString());
        assertTrue(db.getArtworkCompressedMap(map.getMapId()).isPresent(), "the painting was persisted");
    }
}
