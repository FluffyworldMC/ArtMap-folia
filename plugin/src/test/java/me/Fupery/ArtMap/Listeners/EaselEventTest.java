package me.Fupery.ArtMap.Listeners;

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

import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Easel.EaselEffect;
import me.Fupery.ArtMap.Easel.EaselEvent;
import me.Fupery.ArtMap.api.Easel.ClickType;

/**
 * Guard rails on the easel's click state machine - the gate every mount, break
 * and canvas action goes through.
 */
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
}
