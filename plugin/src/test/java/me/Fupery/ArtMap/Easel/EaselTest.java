package me.Fupery.ArtMap.Easel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.api.Exception.ArtMapException;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** An easel over a stateful mock world: spawning its parts, finding it again, seating, items and breaking. */
public class EaselTest extends PluginTestSupport {

    private static ArtistHandler realHandler;
    private static Material wallSign;

    private World world;
    private Block block;
    private Material blockType;
    private String[] signLines;
    private boolean blocksEmpty;
    private final List<Entity> entities = new ArrayList<>();
    private Location origin;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        realHandler = ArtMap.instance().getArtistHandler();
        wallSign = ArtMap.instance().getBukkitVersion().getVersion().getWallSign();
    }

    @BeforeEach
    public void freshWorld() {
        entities.clear();
        blocksEmpty = true;
        blockType = Material.AIR;
        signLines = new String[4];
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        block = mock(Block.class);
        when(block.isEmpty()).thenAnswer(i -> blocksEmpty);
        when(block.getType()).thenAnswer(i -> blockType);
        doAnswer(i -> { blockType = i.getArgument(0); return null; }).when(block).setType(any(Material.class));
        when(block.getBlockData()).thenReturn(mock(WallSign.class));
        Sign sign = mock(Sign.class);
        when(sign.getLine(anyInt())).thenAnswer(i -> signLines[(int) i.getArgument(0)]);
        doAnswer(i -> { signLines[(int) i.getArgument(0)] = i.getArgument(1); return null; }).when(sign).setLine(anyInt(), any());
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenAnswer(i -> new Location(world, 10, 65, 10));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(i -> new ArrayList<>(entities));
        when(world.spawnEntity(any(Location.class), eq(EntityType.ARMOR_STAND))).thenAnswer(i -> stand(i.getArgument(0)));
        when(world.spawnEntity(any(Location.class), eq(EntityType.ITEM_FRAME))).thenAnswer(i -> frame(i.getArgument(0)));
        origin = new Location(world, 10, 65, 10);
    }

    @AfterEach
    public void cleanUp() {
        ArtMap.instance().getEasels().clear();
        when(ArtMap.instance().getArtistHandler()).thenReturn(realHandler);
    }

    /** An armour stand that remembers visibility, size and removal, like a real one. */
    private ArmorStand stand(Location at) {
        ArmorStand s = mock(ArmorStand.class);
        boolean[] state = { true, false, true }; // visible, small, valid
        when(s.getType()).thenReturn(EntityType.ARMOR_STAND);
        when(s.getLocation()).thenReturn(at.clone());
        when(s.isVisible()).thenAnswer(i -> state[0]);
        doAnswer(i -> { state[0] = i.getArgument(0); return null; }).when(s).setVisible(any(boolean.class));
        when(s.isSmall()).thenAnswer(i -> state[1]);
        doAnswer(i -> { state[1] = i.getArgument(0); return null; }).when(s).setSmall(any(boolean.class));
        when(s.isValid()).thenAnswer(i -> state[2]);
        doAnswer(i -> { state[2] = false; entities.remove(s); return null; }).when(s).remove();
        List<Entity> passengers = new ArrayList<>();
        when(s.addPassenger(any(Entity.class))).thenAnswer(i -> passengers.add(i.getArgument(0)));
        when(s.getPassengers()).thenAnswer(i -> new ArrayList<>(passengers));
        entities.add(s);
        return s;
    }

    private ItemFrame frame(Location at) {
        ItemFrame f = mock(ItemFrame.class);
        ItemStack air = item(Material.AIR);
        when(air.clone()).thenReturn(air); // removeItem clones the mounted item before inspecting it
        Object[] state = { true, BlockFace.NORTH, air };
        when(f.getType()).thenReturn(EntityType.ITEM_FRAME);
        when(f.getLocation()).thenReturn(at.clone());
        when(f.isValid()).thenAnswer(i -> state[0]);
        doAnswer(i -> { state[0] = false; entities.remove(f); return null; }).when(f).remove();
        when(f.setFacingDirection(any(BlockFace.class), any(boolean.class))).thenAnswer(i -> { state[1] = i.getArgument(0); return true; });
        when(f.getFacing()).thenAnswer(i -> state[1]);
        when(f.getItem()).thenAnswer(i -> state[2]);
        doAnswer(i -> { state[2] = i.getArgument(0); return null; }).when(f).setItem(any());
        entities.add(f);
        return f;
    }

    private ArmorStand theStand() {
        return (ArmorStand) entities.stream().filter(e -> e instanceof ArmorStand && ((ArmorStand) e).isVisible()).findFirst().orElse(null);
    }

    private ItemFrame theFrame() {
        return (ItemFrame) entities.stream().filter(e -> e instanceof ItemFrame).findFirst().orElse(null);
    }

    private static ItemStack unfinishedMap(int id) {
        ItemStack map = item(Material.FILLED_MAP);
        MapMeta meta = mock(MapMeta.class);
        org.bukkit.map.MapView view = mock(org.bukkit.map.MapView.class);
        when(view.getId()).thenReturn(id);
        when(meta.hasMapView()).thenReturn(true);
        when(meta.getMapView()).thenReturn(view);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Arrays.asList(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.ITALIC + "Unfinished Artwork", "by Someone"));
        when(map.hasItemMeta()).thenReturn(true);
        when(map.getItemMeta()).thenReturn(meta);
        when(map.clone()).thenReturn(map);
        return map;
    }

    // ---- spawning and finding ------------------------------------------------

    @Test
    @DisplayName("spawning places a sign, a stand and a frame and registers the easel")
    public void spawnPlacesAllParts() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);

        assertNotNull(easel);
        assertEquals(wallSign, blockType, "the sign block was placed");
        assertEquals(EaselPart.ARBITRARY_SIGN_ID, signLines[3]);
        assertNotNull(theStand());
        assertNotNull(theFrame());
        assertEquals(BlockFace.NORTH, easel.getFacing());
        assertSame(easel, ArtMap.instance().getEasels().get(origin));
        assertTrue(Easel.checkForEasel(origin));
        assertFalse(easel.hasItem());
        assertFalse(easel.isBeingUsed());
        assertNotNull(easel.getChunk());
    }

    @Test
    @DisplayName("an obstructed stand position makes the spawn fail and clean up")
    public void obstructedSpawnFails() {
        blocksEmpty = false;
        assertNull(Easel.spawnEasel(origin, BlockFace.EAST));
        assertFalse(ArtMap.instance().getEasels().containsKey(origin));
    }

    @Test
    @DisplayName("an easel is found again from any of its parts, from the registry or from the world")
    public void foundFromParts() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.SOUTH);
        Location standLoc = theStand().getLocation();
        Location frameLoc = theFrame().getLocation();

        assertSame(easel, Easel.getEasel(standLoc, EaselPart.STAND));
        assertSame(easel, Easel.getEasel(frameLoc, EaselPart.FRAME));

        ArtMap.instance().getEasels().clear();
        Easel rebuilt = Easel.getEasel(standLoc, EaselPart.STAND);
        assertNotNull(rebuilt, "rediscovered from the sign and the entities");
        assertEquals(origin, rebuilt.getLocation());
        assertSame(rebuilt, ArtMap.instance().getEasels().get(origin));

        ArtMap.instance().getEasels().clear();
        blockType = Material.DIRT;
        assertNull(Easel.getEasel(standLoc, EaselPart.STAND), "no sign, no easel");
    }

    @Test
    @DisplayName("checkForEasel forgets a registered easel whose parts are gone")
    public void checkForEaselDropsStaleEntries() {
        Easel.spawnEasel(origin, BlockFace.WEST);
        entities.clear();
        assertFalse(Easel.checkForEasel(origin));
        assertFalse(ArtMap.instance().getEasels().containsKey(origin));
    }

    @Test
    @DisplayName("every part's spawn position maps back to the easel block for every facing")
    public void partPositionsRoundTrip() {
        for (BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            freshWorld();
            Easel.spawnEasel(origin, facing);
            for (Entity e : new ArrayList<>(entities)) {
                EaselPart part = EaselPart.getPartType(e);
                Location back = part.getEaselPos(e.getLocation(), EaselPart.getFacing(e.getLocation().getYaw()));
                assertEquals(origin, back, part + " facing " + facing);
            }
            ArtMap.instance().getEasels().clear();
        }
    }

    // ---- seating -------------------------------------------------------------

    @Test
    @DisplayName("seating spawns a seat and marker, and the easel is in use while the handler knows the painter")
    public void seatingAndLeaving() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);
        Player painter = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(painter.getEyeLocation()).thenReturn(origin.clone().add(0, 1.6, 0));
        ArtistHandler handler = mock(ArtistHandler.class);
        when(handler.containsPlayer(any(UUID.class))).thenReturn(true);
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);

        assertTrue(easel.seatUser(painter));
        long stands = entities.stream().filter(e -> e instanceof ArmorStand).count();
        assertEquals(3, stands, "stand, seat and marker");
        assertTrue(easel.isBeingUsed());

        easel.removeUser();
        assertFalse(easel.isBeingUsed());
        assertEquals(1, entities.stream().filter(e -> e instanceof ArmorStand).count(), "seat and marker removed");

        // A painter the handler no longer tracks is not really seated.
        assertTrue(easel.seatUser(painter));
        when(handler.containsPlayer(any(UUID.class))).thenReturn(false);
        assertFalse(easel.isBeingUsed());
    }

    @Test
    @DisplayName("seating fails when the seat will not take the passenger")
    public void seatingFailsWithoutPassenger() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);
        Player painter = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(painter.getEyeLocation()).thenReturn(origin.clone());
        when(world.spawnEntity(any(Location.class), eq(EntityType.ARMOR_STAND))).thenAnswer(i -> {
            ArmorStand s = stand(i.getArgument(0));
            when(s.addPassenger(any(Entity.class))).thenReturn(false);
            return s;
        });
        assertFalse(easel.seatUser(painter));
        assertFalse(easel.isBeingUsed());
    }

    // ---- items ----------------------------------------------------------------

    @Test
    @DisplayName("mounting a canvas puts its item on the frame; removing a canvas drops it back")
    public void mountAndRemoveCanvas() throws Exception {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);
        Canvas canvas = mock(Canvas.class);
        ItemStack canvasItem = unfinishedMap(4242);
        when(canvas.getEaselItem()).thenReturn(canvasItem);

        easel.mountCanvas(canvas);
        assertSame(canvasItem, easel.getItem());
        assertTrue(easel.hasItem());

        easel.removeItem();
        verify(world).dropItem(eq(origin), any(ItemStack.class));
        // The frame now holds a real AIR stack, which has no delegate under the mocks, so compare identity.
        org.junit.jupiter.api.Assertions.assertNotSame(canvasItem, easel.getItem());
    }

    @Test
    @DisplayName("a foreign item on the frame is thrown out, and reported")
    public void foreignItemIsEjected() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);
        ItemStack stone = item(Material.STONE);
        when(stone.clone()).thenReturn(stone);
        easel.setItem(stone);
        assertTrue(easel.hasItem());

        assertThrows(ArtMapException.class, easel::removeItem);
        verify(world).dropItemNaturally(eq(origin), eq(stone));
        verify(theFrame(), org.mockito.Mockito.times(2)).setItem(any()); // the stone, then air
    }

    @Test
    @DisplayName("without a frame there is no facing, no item and the centre is the easel block")
    public void noFrameFallbacks() throws Exception {
        java.lang.reflect.Constructor<Easel> ctor = Easel.class.getDeclaredConstructor(Location.class, boolean.class);
        ctor.setAccessible(true);
        Easel bare = ctor.newInstance(origin, true);
        assertNull(bare.getFacing());
        assertFalse(bare.hasItem());
        bare.setItem(item(Material.STONE)); // no frame: silently ignored
        assertFalse(bare.hasItem());
        bare.playEffect(EaselEffect.USE_DENIED); // centre falls back to the block itself
    }

    // ---- breaking ---------------------------------------------------------------

    @Test
    @DisplayName("breaking removes the parts, clears the sign, drops the easel item and de-registers it")
    public void breakingCleansUp() {
        Easel easel = Easel.spawnEasel(origin, BlockFace.NORTH);
        easel.breakEasel();

        assertEquals(Material.AIR, blockType);
        assertTrue(entities.isEmpty());
        verify(world).dropItemNaturally(eq(origin), any(ItemStack.class));
        assertFalse(ArtMap.instance().getEasels().containsKey(origin));

        easel.breakEasel(); // already broken: a no-op
        verify(world).dropItemNaturally(eq(origin), any(ItemStack.class));
    }

    // ---- EaselPart ---------------------------------------------------------------

    @Test
    @DisplayName("part types are told apart by entity type, visibility and size")
    public void partTypes() {
        ArmorStand visible = stand(origin);
        assertEquals(EaselPart.STAND, EaselPart.getPartType(visible));
        visible.setVisible(false);
        assertEquals(EaselPart.SEAT, EaselPart.getPartType(visible));
        visible.setSmall(true);
        assertEquals(EaselPart.MARKER, EaselPart.getPartType(visible));
        assertEquals(EaselPart.FRAME, EaselPart.getPartType(frame(origin)));
        Cow cow = mock(Cow.class);
        when(cow.getType()).thenReturn(EntityType.COW);
        assertNull(EaselPart.getPartType(cow));
    }

    @Test
    @DisplayName("facing and yaw offsets cover the four directions and default sanely")
    public void facingAndYaw() {
        assertEquals(BlockFace.SOUTH, EaselPart.getFacing(0));
        assertEquals(BlockFace.WEST, EaselPart.getFacing(90));
        assertEquals(BlockFace.NORTH, EaselPart.getFacing(180));
        assertEquals(BlockFace.EAST, EaselPart.getFacing(270));
        assertEquals(BlockFace.SOUTH, EaselPart.getFacing(45));
        assertEquals(180, EaselPart.getYawOffset(BlockFace.SOUTH));
        assertEquals(90, EaselPart.getYawOffset(BlockFace.WEST));
        assertEquals(90, EaselPart.getYawOffset(BlockFace.EAST));
        assertEquals(0, EaselPart.getYawOffset(BlockFace.NORTH));
        assertEquals(0, EaselPart.getYawOffset(BlockFace.UP));
        verify(block, never()).setType(Material.DIRT);
    }
}
