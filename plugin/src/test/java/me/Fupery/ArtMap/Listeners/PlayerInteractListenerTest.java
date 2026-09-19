package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.Recipe.ArtMaterial;

/** Easel placement and the "don't plant your paint brush in the ground" rule. */
public class PlayerInteractListenerTest extends PluginTestSupport {

    private PlayerInteractListener listener;
    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new PlayerInteractListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    private static BlockFace facingFor(float yaw) throws Exception {
        Player p = mock(Player.class);
        Location loc = mock(Location.class);
        when(loc.getYaw()).thenReturn(yaw);
        when(p.getLocation()).thenReturn(loc);
        Method m = PlayerInteractListener.class.getDeclaredMethod("getFacing", Player.class);
        m.setAccessible(true);
        return (BlockFace) m.invoke(null, p);
    }

    /** getFacing returns the easel's facing, the opposite of the player's look: yaw 0 looks south, easel faces north. */
    @Test
    @DisplayName("getFacing maps yaw to the easel facing (opposite the player)")
    public void getFacingMapsAllQuadrants() throws Exception {
        assertEquals(BlockFace.NORTH, facingFor(0f), "yaw 0 (player looks south)");
        assertEquals(BlockFace.EAST, facingFor(90f), "yaw 90");
        assertEquals(BlockFace.SOUTH, facingFor(180f), "yaw 180");
        assertEquals(BlockFace.WEST, facingFor(270f), "yaw 270");
        assertEquals(BlockFace.NORTH, facingFor(359f), "yaw 359 wraps back to NORTH");
    }

    @Test
    @DisplayName("negative and >360 yaws normalise instead of falling through")
    public void getFacingNormalisesOutOfRangeYaw() throws Exception {
        // Bukkit hands out negative yaws routinely, and they must land in the same
        // quadrant as their positive equivalent.
        assertEquals(facingFor(90f), facingFor(-270f), "-270 is the same heading as 90");
        assertEquals(facingFor(180f), facingFor(-180f), "-180 is the same heading as 180");
        assertEquals(facingFor(0f), facingFor(720f), "720 wraps to 0");
        assertEquals(facingFor(270f), facingFor(-90f), "-90 is the same heading as 270");
    }

    @Test
    @DisplayName("right-clicking a block with a paint brush is refused")
    public void paintBrushCannotBePlaced() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(ArtMaterial.PAINT_BRUSH.getItem());

        listener.onPlayerInteractEvent(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("an ordinary item is not intercepted")
    public void ordinaryItemIsIgnored() {
        org.bukkit.inventory.ItemStack stone = item(Material.STONE);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(stone);

        listener.onPlayerInteractEvent(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("left-clicking with a paint brush is not intercepted")
    public void leftClickWithBrushIsIgnored() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(ArtMaterial.PAINT_BRUSH.getItem());

        listener.onPlayerInteractEvent(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("an empty-handed click is tolerated")
    public void emptyHandIsTolerated() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(null);

        listener.onPlayerInteractEvent(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- easel placement, hand bookkeeping and creative map fix-up ------------------

    private org.bukkit.World world;
    private org.bukkit.block.Block block;

    /** A world where every block answers `isEmpty` as configured and nothing is nearby. */
    private void worldWhereBlocksAre(boolean empty) {
        world = mock(org.bukkit.World.class);
        when(world.getName()).thenReturn("world");
        block = mock(org.bukkit.block.Block.class);
        when(block.isEmpty()).thenReturn(empty);
        when(block.getType()).thenReturn(empty ? Material.AIR : Material.STONE);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(block.getBlockData()).thenReturn(mock(org.bukkit.block.data.type.WallSign.class));
        when(block.getState()).thenReturn(mock(org.bukkit.block.Sign.class));
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(block);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        when(world.getNearbyEntities(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(new java.util.ArrayList<>());
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(chunk);
        org.bukkit.entity.ArmorStand stand = mock(org.bukkit.entity.ArmorStand.class);
        when(stand.getType()).thenReturn(org.bukkit.entity.EntityType.ARMOR_STAND);
        when(stand.isVisible()).thenReturn(true);
        when(stand.isValid()).thenReturn(true);
        org.bukkit.entity.ItemFrame frame = mock(org.bukkit.entity.ItemFrame.class);
        when(frame.getType()).thenReturn(org.bukkit.entity.EntityType.ITEM_FRAME);
        when(frame.isValid()).thenReturn(true);
        when(world.spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.eq(org.bukkit.entity.EntityType.ARMOR_STAND))).thenReturn(stand);
        when(world.spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.eq(org.bukkit.entity.EntityType.ITEM_FRAME))).thenReturn(frame);
    }

    private PlayerInteractEvent easelClick(BlockFace face) {
        return easelClick(player, face);
    }

    private PlayerInteractEvent easelClick(Player who, BlockFace face) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(who);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(ArtMaterial.EASEL.getItem());
        when(event.getBlockFace()).thenReturn(face);
        when(event.getClickedBlock()).thenReturn(block);
        Location loc = mock(Location.class);
        when(loc.getYaw()).thenReturn(0f);
        when(who.getLocation()).thenReturn(loc);
        return event;
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanUp() {
        ArtMap.instance().getEasels().clear();
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
        when(ArtMap.instance().getCompatManager().checkBuildAllowed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    @Test
    @DisplayName("an easel clicked onto the side of a block is swallowed but not placed")
    public void sideFaceIsSwallowed() {
        worldWhereBlocksAre(true);
        PlayerInteractEvent event = easelClick(BlockFace.NORTH);
        listener.onPlayerInteractEvent(event);
        verify(event).setCancelled(true);
        verify(world, never()).spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("without artmap.artist, or where building is not allowed, no easel is placed")
    public void placementNeedsPermissionAndBuildRights() {
        worldWhereBlocksAre(true);
        listener.onPlayerInteractEvent(easelClick(BlockFace.UP));
        verify(world, never()).spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.any());

        when(player.hasPermission("artmap.artist")).thenReturn(true);
        when(ArtMap.instance().getCompatManager().checkBuildAllowed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);
        listener.onPlayerInteractEvent(easelClick(BlockFace.UP));
        verify(world, never()).spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("an obstructed spot refuses the easel")
    public void obstructedSpotIsRefused() {
        worldWhereBlocksAre(false);
        when(player.hasPermission("artmap.artist")).thenReturn(true);
        listener.onPlayerInteractEvent(easelClick(BlockFace.UP));
        verify(world, never()).spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a clear spot with permission attempts to spawn the easel parts")
    public void clearSpotSpawnsTheEasel() {
        worldWhereBlocksAre(true);
        // A stateful player: the shared mock inventory answers an empty hand with a delegate-less AIR stack.
        Player placer = statefulPlayer("Placer");
        when(placer.hasPermission("artmap.artist")).thenReturn(true);
        placer.getInventory().setItemInMainHand(easelStack(2));

        PlayerInteractEvent event = easelClick(placer, BlockFace.UP);
        listener.onPlayerInteractEvent(event);

        verify(event).setCancelled(true);
        verify(world, org.mockito.Mockito.atLeastOnce()).spawnEntity(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.eq(org.bukkit.entity.EntityType.ARMOR_STAND));
    }

    private static void removeEaselFromHand(Player p) throws Exception {
        Method m = PlayerInteractListener.class.getDeclaredMethod("removeEaselFromHandle", Player.class);
        m.setAccessible(true);
        m.invoke(new PlayerInteractListener(), p);
    }

    private static org.bukkit.inventory.ItemStack easelStack(int amount) {
        org.bukkit.inventory.ItemStack real = ArtMaterial.EASEL.getItem();
        org.bukkit.inventory.ItemStack stack = item(Material.ARMOR_STAND);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(real.getItemMeta());
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    @Test
    @DisplayName("placing consumes one easel from the main hand, or the off hand, and nothing else")
    public void placingConsumesOneEasel() throws Exception {
        Player player = statefulPlayer("Placer");
        org.bukkit.inventory.ItemStack two = easelStack(2);
        player.getInventory().setItemInMainHand(two);
        removeEaselFromHand(player);
        verify(two).setAmount(1);
        org.junit.jupiter.api.Assertions.assertSame(two, player.getInventory().getItemInMainHand());

        org.bukkit.inventory.ItemStack one = easelStack(1);
        player.getInventory().setItemInMainHand(one);
        removeEaselFromHand(player);
        org.junit.jupiter.api.Assertions.assertNull(player.getInventory().getItemInMainHand());

        player.getInventory().setItemInMainHand(item(Material.STONE));
        org.bukkit.inventory.ItemStack offOne = easelStack(1);
        player.getInventory().setItemInOffHand(offOne);
        removeEaselFromHand(player);
        org.junit.jupiter.api.Assertions.assertNull(player.getInventory().getItemInOffHand());
        org.junit.jupiter.api.Assertions.assertEquals(Material.STONE, player.getInventory().getItemInMainHand().getType());

        org.bukkit.inventory.ItemStack offTwo = easelStack(2);
        player.getInventory().setItemInOffHand(offTwo);
        removeEaselFromHand(player);
        verify(offTwo).setAmount(1);

        player.getInventory().setItemInOffHand(item(Material.STONE));
        removeEaselFromHand(player);
        org.junit.jupiter.api.Assertions.assertEquals(Material.STONE, player.getInventory().getItemInOffHand().getType());
    }

    // ---- creative-mode map fix-up ----------------------------------------------

    private static org.bukkit.inventory.ItemStack creativeMap(Integer mapId, boolean hasLore) {
        org.bukkit.inventory.ItemStack map = item(Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta meta = mock(org.bukkit.inventory.meta.MapMeta.class);
        when(meta.hasLore()).thenReturn(hasLore);
        when(meta.hasMapView()).thenReturn(mapId != null);
        if (mapId != null) {
            org.bukkit.map.MapView view = mock(org.bukkit.map.MapView.class);
            when(view.getId()).thenReturn(mapId);
            when(meta.getMapView()).thenReturn(view);
        }
        when(map.getItemMeta()).thenReturn(meta);
        return map;
    }

    private org.bukkit.event.inventory.InventoryCreativeEvent creativeClick(org.bukkit.inventory.ItemStack cursor,
            org.bukkit.event.inventory.ClickType click, org.bukkit.inventory.Inventory clicked) {
        org.bukkit.event.inventory.InventoryCreativeEvent event = mock(org.bukkit.event.inventory.InventoryCreativeEvent.class);
        when(event.getCursor()).thenReturn(cursor);
        when(event.getClick()).thenReturn(click);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getSlot()).thenReturn(5);
        when(event.getWhoClicked()).thenReturn(player);
        return event;
    }

    @Test
    @DisplayName("a creative-menu map that is a known artwork gets the artwork's lore written back")
    public void creativeMapGetsArtworkLore() throws Exception {
        me.Fupery.ArtMap.IO.Database.Database database = mock(me.Fupery.ArtMap.IO.Database.Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        me.Fupery.ArtMap.IO.MapArt art = new me.Fupery.ArtMap.IO.MapArt(61, "Sunset", player.getUniqueId(), player.getName(), "01-01-2026");
        when(database.getArtwork(61)).thenReturn(java.util.Optional.of(art));
        org.bukkit.inventory.Inventory clicked = mock(org.bukkit.inventory.Inventory.class);

        listener.onInventoryCreativeEvent(creativeClick(creativeMap(61, false), org.bukkit.event.inventory.ClickType.CREATIVE, clicked));

        verify(clicked).setItem(org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.any(org.bukkit.inventory.ItemStack.class));
    }

    @Test
    @DisplayName("maps that already carry lore, have no view, or are unknown are left alone")
    public void creativeMapLeftAloneWhenNotOurs() throws Exception {
        me.Fupery.ArtMap.IO.Database.Database database = mock(me.Fupery.ArtMap.IO.Database.Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        when(database.getArtwork(org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.Optional.empty());
        org.bukkit.inventory.Inventory clicked = mock(org.bukkit.inventory.Inventory.class);

        listener.onInventoryCreativeEvent(creativeClick(creativeMap(61, true), org.bukkit.event.inventory.ClickType.CREATIVE, clicked));
        listener.onInventoryCreativeEvent(creativeClick(creativeMap(null, false), org.bukkit.event.inventory.ClickType.CREATIVE, clicked));
        listener.onInventoryCreativeEvent(creativeClick(creativeMap(62, false), org.bukkit.event.inventory.ClickType.CREATIVE, clicked));
        listener.onInventoryCreativeEvent(creativeClick(creativeMap(61, false), org.bukkit.event.inventory.ClickType.LEFT, clicked));
        listener.onInventoryCreativeEvent(creativeClick(creativeMap(61, false), org.bukkit.event.inventory.ClickType.CREATIVE, null));
        listener.onInventoryCreativeEvent(creativeClick(item(Material.STONE), org.bukkit.event.inventory.ClickType.CREATIVE, clicked));

        verify(clicked, never()).setItem(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
        verify(database, org.mockito.Mockito.times(1)).getArtwork(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("a database failure during the creative fix-up is reported to the player")
    public void creativeDatabaseFailureIsReported() throws Exception {
        me.Fupery.ArtMap.IO.Database.Database database = mock(me.Fupery.ArtMap.IO.Database.Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        when(database.getArtwork(61)).thenThrow(new java.sql.SQLException("down"));
        org.mockito.Mockito.clearInvocations(player);

        listener.onInventoryCreativeEvent(creativeClick(creativeMap(61, false), org.bukkit.event.inventory.ClickType.CREATIVE, mock(org.bukkit.inventory.Inventory.class)));

        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Error Retrieving Artwork"));
    }

    @Test
    @DisplayName("west-facing yaw and the wrap-around both resolve")
    public void facingWestAndWrap() throws Exception {
        assertEquals(BlockFace.WEST, facingFor(270f));
        assertEquals(BlockFace.NORTH, facingFor(359f));
    }

    @Test
    @DisplayName("an easel held in the off hand is consumed from there")
    public void easelTakenFromOffHand() throws Exception {
        Player p = statefulPlayer("OffHand");
        p.getInventory().setItemInMainHand(item(Material.STONE));
        org.bukkit.inventory.ItemStack two = easelStack(2);
        p.getInventory().setItemInOffHand(two);
        removeEaselFromHand(p);
        verify(two).setAmount(1);

        p.getInventory().setItemInOffHand(easelStack(1));
        removeEaselFromHand(p);
        assertNull(p.getInventory().getItemInOffHand());
    }
}
