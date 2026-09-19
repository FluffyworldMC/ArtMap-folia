package me.Fupery.ArtMap.Listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.ArtMap;

/** Decides which clicks ArtMap swallows; #376 lived here, so the key property is that it must not cancel
 *  interactions unrelated to an easel. */
public class PlayerInteractEaselListenerTest extends PluginTestSupport {

    private PlayerInteractEaselListener listener;
    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new PlayerInteractEaselListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    private static Location somewhere() {
        Location location = mock(Location.class);
        when(location.getYaw()).thenReturn(0f);
        return location;
    }

    @Test
    @DisplayName("right-clicking an ordinary entity is left alone")
    public void ordinaryEntityIsNotCancelled() {
        Location where = somewhere();
        Cow cow = mock(Cow.class);
        // getPartType() switches on the entity type, so it must be set.
        when(cow.getType()).thenReturn(org.bukkit.entity.EntityType.COW);
        when(cow.getLocation()).thenReturn(where);

        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(cow);

        listener.onPlayerInteractEntity(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("breaking an unrelated block is left alone")
    public void unrelatedBlockBreakIsNotCancelled() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(org.bukkit.Material.DIRT);

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a player painting on an easel cannot break blocks")
    public void paintingPlayerCannotBreakBlocks() throws Exception {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(org.bukkit.Material.DIRT);

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);

        // checkIsPainting requires BOTH conditions: seated AND a live art session.
        when(player.isInsideVehicle()).thenReturn(true);
        org.bukkit.inventory.ItemStack dye = item(org.bukkit.Material.RED_DYE);
        me.Fupery.ArtMap.Easel.Easel easel = mock(me.Fupery.ArtMap.Easel.Easel.class);
        when(easel.seatUser(player)).thenReturn(true);
        player.getInventory().setItemInMainHand(dye);
        ArtMap.instance().getArtistHandler()
                .addPlayer(player, easel, new me.Fupery.ArtMap.IO.Database.Map(970), 0);

        listener.onBlockBreak(event);

        verify(event).setCancelled(true);
        ArtMap.instance().getArtistHandler().removePlayer(player);
    }

    @Test
    @DisplayName("merely being in a vehicle does not block breaking")
    public void vehicleAloneDoesNotCancel() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(org.bukkit.Material.DIRT);

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(player.isInsideVehicle()).thenReturn(true); // in a boat, not painting

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- easel parts, signs and the paint-brush menu ---------------------------

    private org.bukkit.World world;
    private org.bukkit.block.Block dirt;

    private Location partLocation() {
        world = mock(org.bukkit.World.class);
        dirt = mock(org.bukkit.block.Block.class);
        when(dirt.getType()).thenReturn(org.bukkit.Material.DIRT);
        // getEaselPos ends with getBlock().getLocation(); any consistent answer keys the registry.
        when(dirt.getLocation()).thenReturn(new Location(world, 10, 65, 10));
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(dirt);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(dirt);
        when(world.getNearbyEntities(org.mockito.ArgumentMatchers.any(Location.class), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(new java.util.ArrayList<>());
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(chunk);
        return new Location(world, 10.5, 65, 10.5, 0f, 0f);
    }

    private static org.bukkit.entity.ArmorStand standAt(Location loc, boolean visible) {
        org.bukkit.entity.ArmorStand stand = mock(org.bukkit.entity.ArmorStand.class);
        when(stand.getType()).thenReturn(org.bukkit.entity.EntityType.ARMOR_STAND);
        when(stand.isVisible()).thenReturn(visible);
        when(stand.isSmall()).thenReturn(false);
        when(stand.getLocation()).thenReturn(loc);
        return stand;
    }

    private static org.bukkit.entity.ItemFrame frameAt(Location loc) {
        org.bukkit.entity.ItemFrame frame = mock(org.bukkit.entity.ItemFrame.class);
        when(frame.getType()).thenReturn(org.bukkit.entity.EntityType.ITEM_FRAME);
        when(frame.getLocation()).thenReturn(loc);
        return frame;
    }

    /** Register a mock easel under the key Easel.getEasel derives from this part's location. */
    private static me.Fupery.ArtMap.Easel.Easel registerEasel(Location partLoc, me.Fupery.ArtMap.Easel.EaselPart part) {
        Location key = part.getEaselPos(partLoc, me.Fupery.ArtMap.Easel.EaselPart.getFacing(partLoc.getYaw()));
        me.Fupery.ArtMap.Easel.Easel easel = mock(me.Fupery.ArtMap.Easel.Easel.class);
        org.bukkit.inventory.ItemStack air = item(org.bukkit.Material.AIR);
        when(easel.getItem()).thenReturn(air);
        ArtMap.instance().getEasels().put(key, easel);
        return easel;
    }

    private org.bukkit.block.Block easelSign(Location loc) {
        org.bukkit.Material wallSign = ArtMap.instance().getBukkitVersion().getVersion().getWallSign();
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(block.getType()).thenReturn(wallSign);
        org.bukkit.block.Sign sign = mock(org.bukkit.block.Sign.class);
        when(sign.getLine(3)).thenReturn(me.Fupery.ArtMap.Easel.EaselPart.ARBITRARY_SIGN_ID);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(loc);
        ArtMap.instance().getEasels().put(loc, mock(me.Fupery.ArtMap.Easel.Easel.class));
        return block;
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanUp() throws Exception {
        ArtMap.instance().getEasels().clear();
        if (artists().containsPlayer(player)) artists().removePlayer(player);
        when(ArtMap.instance().getCompatManager().checkBuildAllowed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(ArtMap.instance().getCompatManager().checkInteractAllowed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(ArtMap.instance().getScheduler().SYNC.run(org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; });
    }

    @Test
    @DisplayName("right-clicking a registered easel stand is swallowed and handed to the easel")
    public void registeredStandClickIsHandled() {
        Location loc = partLocation();
        me.Fupery.ArtMap.Easel.Easel easel = registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.STAND);
        org.mockito.Mockito.clearInvocations(player);

        org.bukkit.event.player.PlayerInteractAtEntityEvent event = mock(org.bukkit.event.player.PlayerInteractAtEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        org.bukkit.entity.ArmorStand part1 = standAt(loc, true);
        when(event.getRightClicked()).thenReturn(part1);

        listener.onPlayerInteractAtEntity(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.anyString()); // no artmap.artist: told so by the easel
        verify(easel, never()).breakEasel();
    }

    @Test
    @DisplayName("a shift-click where building is not allowed is refused with a denied effect")
    public void shiftClickNeedsBuildRights() {
        Location loc = partLocation();
        me.Fupery.ArtMap.Easel.Easel easel = registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.STAND);
        when(player.isSneaking()).thenReturn(true);
        when(ArtMap.instance().getCompatManager().checkBuildAllowed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);

        org.bukkit.event.player.PlayerInteractAtEntityEvent event = mock(org.bukkit.event.player.PlayerInteractAtEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        org.bukkit.entity.ArmorStand part2 = standAt(loc, true);
        when(event.getRightClicked()).thenReturn(part2);

        listener.onPlayerInteractAtEntity(event);

        verify(event).setCancelled(true);
        verify(easel).playEffect(me.Fupery.ArtMap.Easel.EaselEffect.USE_DENIED);
        verify(easel, never()).breakEasel();
        when(player.isSneaking()).thenReturn(false);
    }

    @Test
    @DisplayName("the invisible seat and an unregistered stand are left alone")
    public void seatAndStrayStandAreIgnored() {
        Location loc = partLocation();
        registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.STAND);

        org.bukkit.event.player.PlayerInteractAtEntityEvent seat = mock(org.bukkit.event.player.PlayerInteractAtEntityEvent.class);
        when(seat.getPlayer()).thenReturn(player);
        org.bukkit.entity.ArmorStand part3 = standAt(loc, false);
        when(seat.getRightClicked()).thenReturn(part3);
        listener.onPlayerInteractAtEntity(seat);
        verify(seat, never()).setCancelled(true);

        ArtMap.instance().getEasels().clear();
        org.bukkit.event.player.PlayerInteractAtEntityEvent stray = mock(org.bukkit.event.player.PlayerInteractAtEntityEvent.class);
        when(stray.getPlayer()).thenReturn(player);
        org.bukkit.entity.ArmorStand part4 = standAt(loc, true);
        when(stray.getRightClicked()).thenReturn(part4);
        listener.onPlayerInteractAtEntity(stray);
        verify(stray, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a mob hitting the easel is blocked without involving the easel")
    public void mobDamageIsBlocked() {
        Location loc = partLocation();
        me.Fupery.ArtMap.Easel.Easel easel = registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.STAND);
        org.bukkit.event.entity.EntityDamageByEntityEvent event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(mock(org.bukkit.entity.Zombie.class));
        org.bukkit.entity.ArmorStand part5 = standAt(loc, true);
        when(event.getEntity()).thenReturn(part5);

        listener.onEntityDamageByEntity(event);

        verify(event).setCancelled(true);
        verify(easel, never()).playEffect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a seated painter's clicks on the easel are swallowed without acting on it")
    public void paintingPlayerClicksAreSwallowed() throws Exception {
        Location loc = partLocation();
        me.Fupery.ArtMap.Easel.Easel easel = registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.STAND);
        when(player.hasPermission("artmap.artist")).thenReturn(true);
        when(player.isInsideVehicle()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        me.Fupery.ArtMap.Easel.Easel seatEasel = mock(me.Fupery.ArtMap.Easel.Easel.class);
        when(seatEasel.seatUser(player)).thenReturn(true);
        player.getInventory().setItemInMainHand(item(org.bukkit.Material.RED_DYE));
        artists().addPlayer(player, seatEasel, new me.Fupery.ArtMap.IO.Database.Map(978), 0);

        org.bukkit.event.player.PlayerArmorStandManipulateEvent event = mock(org.bukkit.event.player.PlayerArmorStandManipulateEvent.class);
        when(event.getPlayer()).thenReturn(player);
        org.bukkit.entity.ArmorStand part6 = standAt(loc, true);
        when(event.getRightClicked()).thenReturn(part6);
        listener.onPlayerArmorStandManipulate(event);

        verify(event, org.mockito.Mockito.atLeastOnce()).setCancelled(true); // cancelled by both guards
        verify(easel, never()).breakEasel();
        when(player.isSneaking()).thenReturn(false);
        when(player.isInsideVehicle()).thenReturn(false);
    }

    @Test
    @DisplayName("the easel's item frame cannot be broken by a player, but physics is not our business")
    public void frameBreakByPlayerIsBlocked() {
        Location loc = partLocation();
        registerEasel(loc, me.Fupery.ArtMap.Easel.EaselPart.FRAME);
        org.bukkit.event.hanging.HangingBreakByEntityEvent event = mock(org.bukkit.event.hanging.HangingBreakByEntityEvent.class);
        when(event.getCause()).thenReturn(org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.ENTITY);
        when(event.getRemover()).thenReturn(player);
        org.bukkit.entity.ItemFrame part7 = frameAt(loc);
        when(event.getEntity()).thenReturn(part7);
        listener.onHangingBreakByEntity(event);
        verify(event).setCancelled(true);

        org.bukkit.event.hanging.HangingBreakByEntityEvent physics = mock(org.bukkit.event.hanging.HangingBreakByEntityEvent.class);
        when(physics.getCause()).thenReturn(org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.PHYSICS);
        listener.onHangingBreakByEntity(physics);
        verify(physics, never()).setCancelled(true);
    }

    @Test
    @DisplayName("the easel sign cannot be clicked, broken or updated by physics")
    public void easelSignIsProtected() {
        Location loc = partLocation();
        org.bukkit.block.Block sign = easelSign(loc);

        org.bukkit.event.player.PlayerInteractEvent click = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(click.getPlayer()).thenReturn(player);
        when(click.getClickedBlock()).thenReturn(sign);
        when(click.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
        listener.onRightClick(click);
        verify(click).setCancelled(true);

        BlockBreakEvent brk = mock(BlockBreakEvent.class);
        when(brk.getBlock()).thenReturn(sign);
        when(brk.getPlayer()).thenReturn(player);
        listener.onBlockBreak(brk);
        verify(brk).setCancelled(true);

        org.bukkit.event.block.BlockPhysicsEvent physics = mock(org.bukkit.event.block.BlockPhysicsEvent.class);
        when(physics.getBlock()).thenReturn(sign);
        listener.onBlockPhysics(physics);
        verify(physics).setCancelled(true);

        org.bukkit.event.block.BlockPhysicsEvent other = mock(org.bukkit.event.block.BlockPhysicsEvent.class);
        when(other.getBlock()).thenReturn(dirt);
        listener.onBlockPhysics(other);
        verify(other, never()).setCancelled(true);
    }

    @Test
    @DisplayName("right-clicking the air with a paint brush schedules the help menu")
    public void paintBrushOpensTheMenu() {
        // The menu needs a real inventory GUI; capture the scheduled task instead of running it.
        when(ArtMap.instance().getScheduler().SYNC.run(org.mockito.ArgumentMatchers.any(Runnable.class))).thenReturn(null);
        org.mockito.Mockito.clearInvocations(ArtMap.instance().getScheduler().SYNC);
        org.bukkit.event.player.PlayerInteractEvent event = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(me.Fupery.ArtMap.Recipe.ArtMaterial.PAINT_BRUSH.getItem());

        listener.onRightClick(event);

        verify(ArtMap.instance().getScheduler().SYNC).run(org.mockito.ArgumentMatchers.any(Runnable.class));
    }
}
