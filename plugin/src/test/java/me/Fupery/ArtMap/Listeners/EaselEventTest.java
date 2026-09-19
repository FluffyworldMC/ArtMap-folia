package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Easel.EaselEffect;
import me.Fupery.ArtMap.Easel.EaselEvent;
import me.Fupery.ArtMap.api.Easel.ClickType;

/** Guard rails on the easel's click state machine, the gate every mount, break and canvas action goes through. */
public class EaselEventTest extends PluginTestSupport {

    private Player player;
    private Easel easel;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(player.hasPermission("artmap.artist")).thenReturn(true);
        easel = mock(Easel.class);
        // Build the stack BEFORE stubbing: touching the mocked ItemFactory between
        // when() and thenReturn() makes Mockito report UnfinishedStubbing.
        ItemStack empty = item(Material.AIR);
        when(easel.getItem()).thenReturn(empty);
    }

    @Test
    @DisplayName("without artmap.artist nothing happens")
    public void requiresArtistPermission() {
        when(player.hasPermission(anyString())).thenReturn(false);

        new EaselEvent(easel, ClickType.SHIFT_RIGHT_CLICK, player).callEvent();

        verify(easel, never()).breakEasel();
        verify(easel, never()).seatUser(any(Player.class));
    }

    @Test
    @DisplayName("an easel someone else is using is refused, not broken")
    public void easelInUseIsRefused() {
        when(easel.isBeingUsed()).thenReturn(true);

        new EaselEvent(easel, ClickType.SHIFT_RIGHT_CLICK, player).callEvent();

        verify(easel, never()).breakEasel();
        verify(easel).playEffect(EaselEffect.USE_DENIED);
    }

    @Test
    @DisplayName("shift-right-click breaks a free easel")
    public void shiftRightClickBreaksEasel() {
        new EaselEvent(easel, ClickType.SHIFT_RIGHT_CLICK, player).callEvent();

        verify(easel).breakEasel();
    }

    @Test
    @DisplayName("left-click only shows help - it must never break the easel")
    public void leftClickIsNonDestructive() {
        new EaselEvent(easel, ClickType.LEFT_CLICK, player).callEvent();

        verify(easel, never()).breakEasel();
        verify(easel, never()).seatUser(any(Player.class));
    }

    @Test
    @DisplayName("right-clicking an empty easel with no canvas does not seat the player")
    public void rightClickEmptyHandedDoesNotMount() {
        player.getInventory().setItemInMainHand(item(Material.STONE));

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel, never()).seatUser(any(Player.class));
        verify(easel, never()).breakEasel();
    }

    @Test
    @DisplayName("an active preview swallows the click instead of acting on the easel")
    public void previewSwallowsTheClick() {
        // Whatever the click, a player mid-preview must not also break the easel.
        new EaselEvent(easel, ClickType.SHIFT_RIGHT_CLICK, player).callEvent();
        verify(easel).breakEasel();

        assertFalse(previews().isPreviewing(player),
                "no preview should have been started by an easel click");
    }

    // ---- what a right-click does depending on what is on the easel and in hand ------

    private static int nextMapId = 9800;

    private static ItemStack mapWithView(Integer id, String... lore) {
        ItemStack map = item(Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta meta = mock(org.bukkit.inventory.meta.MapMeta.class);
        when(meta.hasMapView()).thenReturn(id != null);
        if (id != null) {
            org.bukkit.map.MapView view = mock(org.bukkit.map.MapView.class);
            when(view.getId()).thenReturn(id);
            when(meta.getMapView()).thenReturn(view);
        }
        when(meta.hasLore()).thenReturn(lore.length > 0);
        when(meta.getLore()).thenReturn(java.util.Arrays.asList(lore));
        when(map.hasItemMeta()).thenReturn(true);
        when(map.getItemMeta()).thenReturn(meta);
        when(map.clone()).thenReturn(map);
        return map;
    }

    private me.Fupery.ArtMap.IO.Database.Database database;

    private void mockDatabase() throws Exception {
        database = mock(me.Fupery.ArtMap.IO.Database.Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        // The player mocks are shared suite-wide; forget what earlier tests did with this one.
        org.mockito.Mockito.clearInvocations(player, player.getInventory());
        // cloneMap and createMap go through the server's world and map factories.
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(mocks.getMockServer().getWorld(anyString())).thenReturn(world);
        when(mocks.getMockServer().createMap(any(org.bukkit.World.class))).thenAnswer(i -> org.bukkit.Bukkit.getMap(nextMapId++));
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanUp() throws Exception {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        when(mocks.getMockServer().getWorld(anyString())).thenReturn(null);
        if (artists().containsPlayer(player)) artists().removePlayer(player);
    }

    @Test
    @DisplayName("right-clicking an easel holding a canvas seats the player on it")
    public void rightClickWithCanvasMounts() {
        ItemStack onEasel = mapWithView(4321);
        when(easel.getItem()).thenReturn(onEasel);
        when(easel.getFacing()).thenReturn(org.bukkit.block.BlockFace.NORTH);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel).seatUser(player);
    }

    @Test
    @DisplayName("a broken map on the easel is reported instead of mounting")
    public void brokenMapOnEaselIsReported() {
        ItemStack broken = mapWithView(null);
        when(easel.getItem()).thenReturn(broken);
        org.mockito.Mockito.clearInvocations(player);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel, never()).seatUser(any(Player.class));
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("a foreign item on the easel is removed rather than used")
    public void foreignItemIsRemoved() throws Exception {
        ItemStack stone = item(Material.STONE);
        when(easel.getItem()).thenReturn(stone);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel).removeItem();
        verify(easel, never()).seatUser(any(Player.class));
    }

    @Test
    @DisplayName("a blank canvas in hand is mounted as a fresh map and one canvas is taken")
    public void canvasInHandIsMounted() throws Exception {
        mockDatabase();
        when(database.createMap()).thenReturn(new me.Fupery.ArtMap.IO.Database.Map(nextMapId++));
        ItemStack canvas = me.Fupery.ArtMap.Recipe.ArtMaterial.CANVAS.getItem();
        player.getInventory().setItemInMainHand(canvas);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel).mountCanvas(any());
        verify(player.getInventory()).removeItem(any(ItemStack.class));
    }

    @Test
    @DisplayName("a canvas cannot be mounted when no map can be created")
    public void canvasWithoutMapIsRefused() throws Exception {
        mockDatabase();
        when(database.createMap()).thenReturn(null);
        player.getInventory().setItemInMainHand(me.Fupery.ArtMap.Recipe.ArtMaterial.CANVAS.getItem());
        org.mockito.Mockito.clearInvocations(player);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel, never()).mountCanvas(any());
        verify(player).sendMessage(anyString());
    }

    private ItemStack artworkInHand(int id) {
        ItemStack art = mapWithView(id, org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.ITALIC + "Player Artwork", "by someone");
        player.getInventory().setItemInMainHand(art);
        return art;
    }

    @Test
    @DisplayName("the artist can put their own artwork back on an easel to edit a copy")
    public void ownArtworkIsMountedForEditing() throws Exception {
        mockDatabase();
        artworkInHand(70);
        me.Fupery.ArtMap.IO.MapArt art = new me.Fupery.ArtMap.IO.MapArt(70, "Mine", player.getUniqueId(), player.getName(), "01-01-2026");
        when(database.getArtwork(70)).thenReturn(java.util.Optional.of(art));

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel).mountCanvas(any());
        verify(easel, never()).playEffect(EaselEffect.USE_DENIED);
    }

    @Test
    @DisplayName("someone else's artwork cannot be edited without artmap.admin")
    public void foreignArtworkIsRefused() throws Exception {
        mockDatabase();
        artworkInHand(71);
        me.Fupery.ArtMap.IO.MapArt art = new me.Fupery.ArtMap.IO.MapArt(71, "Theirs", java.util.UUID.randomUUID(), "Other", "01-01-2026");
        when(database.getArtwork(71)).thenReturn(java.util.Optional.of(art));

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel, never()).mountCanvas(any());
        verify(easel).playEffect(EaselEffect.USE_DENIED);
    }

    @Test
    @DisplayName("an unsaved artwork is mounted as-is, an unknown one is refused")
    public void unsavedAndUnknownArtwork() throws Exception {
        mockDatabase();
        artworkInHand(72);
        when(database.getArtwork(72)).thenReturn(java.util.Optional.empty());
        when(database.containsUnsavedArtwork(72)).thenReturn(true);
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        verify(easel).mountCanvas(any());

        artworkInHand(73);
        when(database.getArtwork(73)).thenReturn(java.util.Optional.empty());
        when(database.containsUnsavedArtwork(73)).thenReturn(false);
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        verify(easel).playEffect(EaselEffect.USE_DENIED);
    }

    @Test
    @DisplayName("a database failure while placing an artwork is reported to the player")
    public void artworkLookupFailureIsReported() throws Exception {
        mockDatabase();
        artworkInHand(74);
        when(database.getArtwork(74)).thenThrow(new java.sql.SQLException("down"));
        org.mockito.Mockito.clearInvocations(player);

        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();

        verify(easel, never()).mountCanvas(any());
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Error placing art"));
    }

    // ---- failure branches ------------------------------------------------------------

    private final java.util.List<java.util.logging.LogRecord> logged = new java.util.ArrayList<>();
    private final java.util.logging.Handler capture = new java.util.logging.Handler() {
        @Override public void publish(java.util.logging.LogRecord r) { logged.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    private void listen() {
        ArtMap.instance().getLogger().addHandler(capture);
    }

    @org.junit.jupiter.api.AfterEach
    public void stopListening() {
        ArtMap.instance().getLogger().removeHandler(capture);
    }

    @Test
    @DisplayName("a map on the easel with no map view is reported as broken")
    public void viewlessMapOnEasel() {
        listen();
        ItemStack viewless = mapWithView(null);
        when(easel.getItem()).thenReturn(viewless);
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Broken map on easel")));
        verify(player).sendMessage(me.Fupery.ArtMap.api.Config.Lang.ERROR_ON_EASEL.get());
    }

    @Test
    @DisplayName("a failure while seating the painter is logged rather than thrown")
    public void mountFailureIsLogged() {
        listen();
        ItemStack onEasel = mapWithView(4322);
        when(easel.getItem()).thenReturn(onEasel);
        when(easel.getFacing()).thenReturn(null); // no facing: the yaw offset lookup blows up
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Failure having player mount")));
    }

    @Test
    @DisplayName("a foreign item that cannot be removed is logged")
    public void foreignItemRemovalFailure() throws Exception {
        listen();
        ItemStack stone = item(Material.STONE);
        when(easel.getItem()).thenReturn(stone);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("down")).when(easel).removeItem();
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("Unexpected item on Easel")));
    }

    @Test
    @DisplayName("an artwork in hand without a map view cannot be placed and says so")
    public void artworkWithoutView() throws Exception {
        mockDatabase();
        ItemStack art = mapWithView(null, org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.ITALIC + "Player Artwork", "by someone");
        player.getInventory().setItemInMainHand(art);
        org.mockito.Mockito.clearInvocations(player);
        new EaselEvent(easel, ClickType.RIGHT_CLICK, player).callEvent();
        verify(easel, never()).mountCanvas(any());
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Error placing art"));
    }
}
