package me.Fupery.ArtMap.Listeners;

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

    /**
     * getFacing returns the direction the *easel* should face, which is the
     * opposite of where the player is looking - the easel turns to face them.
     * Minecraft yaw 0 means looking south, and the easel then faces north.
     */
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
}
