package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The five ways an art session can end; #376 was one of them silently skipping cleanup, so each entry point
 *  is checked on its own rather than trusting the shared helper. */
public class PlayerQuitListenerTest extends PluginTestSupport {

    private PlayerQuitListener listener;
    private Player player;
    private int nextMapId = 940;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new PlayerQuitListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        when(player.getType()).thenReturn(EntityType.PLAYER);
    }

    private void startSession() throws Exception {
        ItemStack dye = item(Material.RED_DYE);
        Easel easel = mock(Easel.class);
        when(easel.seatUser(player)).thenReturn(true);
        player.getInventory().setItemInMainHand(dye);
        ArtMap.instance().getArtistHandler().addPlayer(player, easel, new Map(nextMapId++), 0);
        assertTrue(ArtMap.instance().getArtistHandler().containsPlayer(player),
                "precondition: the player should be painting");
    }

    private void assertSessionEnded(String how) {
        assertFalse(ArtMap.instance().getArtistHandler().containsPlayer(player),
                how + " must end the art session");
    }

    @Test
    @DisplayName("quitting ends the art session")
    public void quitEndsSession() throws Exception {
        startSession();
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerQuit(event);

        assertSessionEnded("quitting");
    }

    @Test
    @DisplayName("being kicked ends the art session")
    public void kickEndsSession() throws Exception {
        startSession();
        PlayerKickEvent event = mock(PlayerKickEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerKick(event);

        assertSessionEnded("being kicked");
    }

    @Test
    @DisplayName("dying ends the art session")
    public void deathEndsSession() throws Exception {
        startSession();
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerDeath(event);

        assertSessionEnded("dying");
    }

    @Test
    @DisplayName("taking damage ends the art session")
    public void damageEndsSession() throws Exception {
        startSession();
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerDamage(event);

        assertSessionEnded("taking damage");
    }

    @Test
    @DisplayName("teleporting ends the art session")
    public void teleportEndsSession() throws Exception {
        startSession();
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerTeleport(event);

        assertSessionEnded("teleporting");
    }

    @Test
    @DisplayName("a damaged mob is ignored, not cast to Player")
    public void mobDamageIsIgnored() {
        Zombie zombie = mock(Zombie.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(zombie);

        // The guard exists precisely to avoid a ClassCastException here.
        listener.onPlayerDamage(event);
    }

    @Test
    @DisplayName("ending a session for a player who was never painting is harmless")
    public void quitWithoutSessionIsHarmless() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerQuit(event);

        assertFalse(ArtMap.instance().getArtistHandler().containsPlayer(player));
    }

    @Test
    @DisplayName("a quitting previewer has the preview taken back")
    public void previewEndsOnQuit() {
        me.Fupery.ArtMap.Preview.Preview preview = new me.Fupery.ArtMap.Preview.Preview() {
            @Override public boolean start(Player p) { return true; }
            @Override public boolean end(Player p) { return true; }
            @Override public boolean isEventAllowed(java.util.UUID p, org.bukkit.event.Event e) { return true; }
        };
        assertTrue(ArtMap.instance().getPreviewManager().startPreview(player, preview));
        player.getInventory().setItemInMainHand(item(Material.FILLED_MAP));
        PlayerQuitListener.endPlayerArtSession(player);
        assertFalse(ArtMap.instance().getPreviewManager().isPreviewing(player));
    }
}
