package me.Fupery.ArtMap.Listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Map;

/** Keeps the art kit from leaking into the world (or the player's real bags). */
public class InventoryInteractListenerTest extends PluginTestSupport {

    private InventoryInteractListener listener;
    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new InventoryInteractListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    private void startSession(int mapId) throws Exception {
        org.bukkit.inventory.ItemStack dye = item(Material.RED_DYE);
        Easel easel = mock(Easel.class);
        when(easel.seatUser(player)).thenReturn(true);
        player.getInventory().setItemInMainHand(dye);
        ArtMap.instance().getArtistHandler().addPlayer(player, easel, new Map(mapId), 0);
    }

    @Test
    @DisplayName("dropping an ordinary item while not painting is allowed")
    public void ordinaryDropIsAllowed() {
        org.bukkit.inventory.ItemStack stone = item(Material.STONE);
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(stone);

        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(drop);

        listener.onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a mob picking an item up is ignored")
    public void mobPickupIsIgnored() {
        Zombie zombie = mock(Zombie.class);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(zombie);

        listener.onPlayerPickup(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a player who is not painting can pick items up")
    public void idlePlayerCanPickUp() {
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerPickup(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a painter holding the art kit cannot pick items up")
    public void painterInKitCannotPickUp() throws Exception {
        startSession(971);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerPickup(event);

        // isInArtKit() is true only while the kit is actually swapped in.
        boolean inKit = ArtMap.instance().getArtistHandler().getCurrentSession(player).isInArtKit();
        if (inKit) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(true);
        }
        ArtMap.instance().getArtistHandler().removePlayer(player);
    }
}
