package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Stops ArtMap artwork being duplicated through crafting / cartography, and nothing else (#377):
 *  a regular map must pass through both untouched, with no error logged or messaged. */
public class PlayerCraftListenerTest extends PluginTestSupport {

    private static final int MAP_ID = 42;

    private PlayerCraftListener listener;
    private Player player;
    private Player other;
    private Database database;
    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { logged.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    };

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new PlayerCraftListener();
        Player[] players = mocks.getRandomMockPlayers(2);
        player = preparePlayer(players[0]);
        other = preparePlayer(players[1]);
        // The player mocks are shared suite-wide; forget what earlier tests did with them.
        clearInvocations(player, other, player.getInventory(), other.getInventory());
        // A mocked database gives every branch a deterministic answer, including SQL failure.
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        // The plugin logger is real, so capture what the listener logs rather than mocking it.
        logged.clear();
        ArtMap.instance().getLogger().addHandler(capture);
    }

    @AfterEach
    public void restoreDatabase() {
        ArtMap.instance().getLogger().removeHandler(capture);
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
    }

    private boolean loggedSevere() {
        return logged.stream().anyMatch(r -> r.getLevel() == Level.SEVERE);
    }

    // ---- fixtures --------------------------------------------------------

    /** A filled map carrying a map view with the given id, as a real map item would. */
    private static ItemStack filledMap(int id) {
        ItemStack map = item(Material.FILLED_MAP);
        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(id);
        MapMeta meta = mock(MapMeta.class);
        when(meta.hasMapView()).thenReturn(true);
        when(meta.getMapView()).thenReturn(view);
        when(map.getItemMeta()).thenReturn(meta);
        return map;
    }

    /** A filled map with no map view at all (fresh from a creative menu, say). */
    private static ItemStack viewlessMap() {
        ItemStack map = item(Material.FILLED_MAP);
        MapMeta meta = mock(MapMeta.class);
        when(meta.hasMapView()).thenReturn(false);
        when(map.getItemMeta()).thenReturn(meta);
        return map;
    }

    /** Register an artwork for MAP_ID owned by `artist`; the item it hands out is a mock we can verify. */
    private ItemStack artworkBy(Player artist) throws SQLException {
        ItemStack artworkItem = item(Material.FILLED_MAP);
        ItemMeta artworkMeta = mock(MapMeta.class);
        when(artworkItem.getItemMeta()).thenReturn(artworkMeta);
        MapArt art = spy(new MapArt(MAP_ID, "Sunset", artist.getUniqueId(), artist.getName(), "01-01-2026"));
        doReturn(artworkItem).when(art).getMapItem();
        when(database.getArtwork(MAP_ID)).thenReturn(Optional.of(art));
        return artworkItem;
    }

    private static CraftItemEvent craft(ItemStack result, Player who, InventoryAction action) {
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getCurrentItem()).thenReturn(result);
        when(event.getWhoClicked()).thenReturn(who);
        when(event.getAction()).thenReturn(action);
        return event;
    }

    private static InventoryClickEvent cartographyClick(ItemStack current, Player who) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(mock(CartographyInventory.class));
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getWhoClicked()).thenReturn(who);
        return event;
    }

    private static void assertLeftToVanilla(Event event, Player who, ItemStack result) {
        verify(who, never()).sendMessage(anyString());
        if (event instanceof CraftItemEvent) {
            verify((CraftItemEvent) event, never()).setCancelled(true);
            verify((CraftItemEvent) event, never()).setResult(Event.Result.DENY);
        } else {
            verify((InventoryClickEvent) event, never()).setCancelled(true);
            verify((InventoryClickEvent) event, never()).setResult(Event.Result.DENY);
        }
        if (result != null) verify(result, never()).setItemMeta(any());
    }

    // ---- crafting table --------------------------------------------------

    @Test
    @DisplayName("a null craft result is tolerated (some forks send them)")
    public void nullCraftResultIsTolerated() {
        CraftItemEvent event = craft(null, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        assertLeftToVanilla(event, player, null);
    }

    @Test
    @DisplayName("crafting something that is not a map is left alone")
    public void nonMapCraftIsIgnored() {
        ItemStack stone = item(Material.STONE);
        CraftItemEvent event = craft(stone, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        assertLeftToVanilla(event, player, stone);
    }

    @Test
    @DisplayName("#377 copying a regular map is vanilla behaviour: no message, no log, no cancel")
    public void regularMapCraftIsLeftToVanilla() throws SQLException {
        ItemStack map = filledMap(MAP_ID);
        when(database.getArtwork(MAP_ID)).thenReturn(Optional.empty());
        CraftItemEvent event = craft(map, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        assertLeftToVanilla(event, player, map);
        assertFalse(loggedSevere(), "a regular map must not be logged as a database error");
    }

    @Test
    @DisplayName("a filled map with no map view never reaches the database")
    public void viewlessMapCraftSkipsTheDatabase() throws SQLException {
        ItemStack map = viewlessMap();
        CraftItemEvent event = craft(map, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        assertLeftToVanilla(event, player, map);
        verify(database, never()).getArtwork(anyInt());
    }

    @Test
    @DisplayName("the artist crafting a copy gets the real artwork item's meta on the result")
    public void artistCraftGetsArtworkMeta() throws SQLException {
        ItemStack artworkItem = artworkBy(player);
        ItemStack result = filledMap(MAP_ID);
        CraftItemEvent event = craft(result, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        verify(result).setItemMeta(artworkItem.getItemMeta());
        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("the artist shift-crafting gets one artwork per ingredient and the matrix is cleared")
    public void artistShiftCraftGivesWholeStack() throws SQLException {
        ItemStack artworkItem = artworkBy(player);
        ItemStack result = filledMap(MAP_ID);
        CraftItemEvent event = craft(result, player, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        ItemStack three = item(Material.FILLED_MAP);
        when(three.getAmount()).thenReturn(3);
        ItemStack two = item(Material.PAPER);
        when(two.getAmount()).thenReturn(2);
        CraftingInventory matrix = mock(CraftingInventory.class);
        when(matrix.getMatrix()).thenReturn(new ItemStack[] { three, null, two, null });
        when(event.getInventory()).thenReturn(matrix);
        when(player.getInventory().addItem(any())).thenReturn(new HashMap<>());

        listener.onPlayerCraftEvent(event);

        verify(event).setCancelled(true);
        verify(matrix).setMatrix(new ItemStack[4]);
        verify(artworkItem).setAmount(5);
        verify(player.getInventory()).addItem(artworkItem);
    }

    @Test
    @DisplayName("anyone else crafting a copy is refused")
    public void nonArtistCraftIsDenied() throws SQLException {
        artworkBy(other);
        ItemStack result = filledMap(MAP_ID);
        CraftItemEvent event = craft(result, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        verify(event).setCancelled(true);
        verify(event).setResult(Event.Result.DENY);
        verify(player).sendMessage(anyString());
        verify(result, never()).setItemMeta(any());
    }

    @Test
    @DisplayName("a database failure is logged and reported, and the craft still falls through to vanilla")
    public void databaseFailureIsReportedNotSwallowed() throws SQLException {
        ItemStack map = filledMap(MAP_ID);
        when(database.getArtwork(MAP_ID)).thenThrow(new SQLException("disk on fire"));
        CraftItemEvent event = craft(map, player, InventoryAction.PICKUP_ALL);

        listener.onPlayerCraftEvent(event);

        verify(player).sendMessage(contains("Error Retrieving Artwork"));
        assertTrue(logged.stream().anyMatch(r -> r.getLevel() == Level.SEVERE && r.getThrown() instanceof SQLException),
                "the SQL failure must be logged with its cause");
        verify(event, never()).setCancelled(true);
        verify(map, never()).setItemMeta(any());
    }

    // ---- cartography table -----------------------------------------------

    @Test
    @DisplayName("a non-cartography inventory click is left alone")
    public void nonCartographyClickIsIgnored() {
        Inventory inventory = mock(Inventory.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inventory);

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("an empty cartography slot is ignored")
    public void emptyCartographySlotIsIgnored() throws SQLException {
        InventoryClickEvent event = cartographyClick(null, player);

        listener.onInventoryClick(event);

        assertLeftToVanilla(event, player, null);
        verify(database, never()).getArtwork(anyInt());
    }

    @Test
    @DisplayName("a glass pane in the cartography table is ignored")
    public void nonMapCartographyClickIsIgnored() throws SQLException {
        ItemStack pane = item(Material.GLASS_PANE);
        InventoryClickEvent event = cartographyClick(pane, player);

        listener.onInventoryClick(event);

        assertLeftToVanilla(event, player, pane);
        verify(database, never()).getArtwork(anyInt());
    }

    @Test
    @DisplayName("#377 laminating a regular map is vanilla behaviour: no message, no log, no cancel")
    public void regularMapCartographyClickIsLeftToVanilla() throws SQLException {
        ItemStack map = filledMap(MAP_ID);
        when(database.getArtwork(MAP_ID)).thenReturn(Optional.empty());
        InventoryClickEvent event = cartographyClick(map, player);

        listener.onInventoryClick(event);

        assertLeftToVanilla(event, player, map);
        assertFalse(loggedSevere(), "a regular map must not be logged as a database error");
    }

    @Test
    @DisplayName("the artist laminating their artwork keeps the artwork's meta on the result")
    public void artistCartographyClickGetsArtworkMeta() throws SQLException {
        ItemStack artworkItem = artworkBy(player);
        ItemStack result = filledMap(MAP_ID);
        InventoryClickEvent event = cartographyClick(result, player);

        listener.onInventoryClick(event);

        verify(result).setItemMeta(artworkItem.getItemMeta());
        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("anyone else laminating an artwork is refused")
    public void nonArtistCartographyClickIsDenied() throws SQLException {
        artworkBy(other);
        ItemStack result = filledMap(MAP_ID);
        InventoryClickEvent event = cartographyClick(result, player);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(event).setResult(Event.Result.DENY);
        verify(player).sendMessage(anyString());
        verify(result, never()).setItemMeta(any());
    }

    @Test
    @DisplayName("a database failure in the cartography table is reported and falls through to vanilla")
    public void cartographyDatabaseFailureIsReported() throws SQLException {
        ItemStack map = filledMap(MAP_ID);
        when(database.getArtwork(MAP_ID)).thenThrow(new SQLException("disk on fire"));
        InventoryClickEvent event = cartographyClick(map, player);

        listener.onInventoryClick(event);

        verify(player).sendMessage(contains("Error Retrieving Artwork"));
        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("null events, pre-1.14 servers and maps without meta are left alone")
    public void cartographyGuards() throws Exception {
        listener.onInventoryClick(null);

        org.bukkit.event.inventory.InventoryClickEvent click = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(click.getInventory()).thenReturn(mock(org.bukkit.inventory.CartographyInventory.class));
        org.bukkit.inventory.ItemStack metaless = item(Material.FILLED_MAP);
        when(metaless.getItemMeta()).thenReturn(null);
        when(click.getCurrentItem()).thenReturn(metaless);
        when(click.getWhoClicked()).thenReturn(player);
        listener.onInventoryClick(click);
        verify(click, never()).setCancelled(true);

        me.Fupery.ArtMap.api.Utils.VersionHandler current = ArtMap.instance().getBukkitVersion();
        when(ArtMap.instance().getBukkitVersion()).thenReturn(new me.Fupery.ArtMap.api.Utils.VersionHandler(
                me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion.v1_13, mocks.getPluginMock()));
        try {
            listener.onInventoryClick(click);
        } finally {
            when(ArtMap.instance().getBukkitVersion()).thenReturn(current);
        }
        verify(database, never()).getArtwork(anyInt());
    }
}
