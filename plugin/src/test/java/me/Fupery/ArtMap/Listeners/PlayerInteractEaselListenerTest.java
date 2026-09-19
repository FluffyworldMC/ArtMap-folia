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
}
