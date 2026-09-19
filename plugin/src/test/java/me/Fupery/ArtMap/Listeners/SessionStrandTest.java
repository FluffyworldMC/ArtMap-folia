package me.Fupery.ArtMap.Listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spigotmc.event.entity.EntityDismountEvent;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.IO.Protocol.ProtocolHandler;
import me.Fupery.ArtMap.IO.Protocol.In.PacketReceiver;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket.PacketInteract.InteractType;
import me.Fupery.ArtMap.Painting.ArtSession;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.Preview.PreviewManager;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.api.Utils.VersionHandler;
import me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.MockUtil;

/** #376: a player left in the artists map has every USE_ENTITY packet cancelled, so item frames silently stop
 *  working until restart. Each test asserts handlePacket(player, INTERACT) is true once painting has ended. */
public class SessionStrandTest {

    private static MockUtil mocks;
    private static Database db;
    private static Player[] players;
    private ArtistHandler handler;
    private int nextMapId = 900;

    @BeforeAll
    public static void setupOnce() throws Exception {
        mocks = new MockUtil();
        mocks.mockServer("1.21.4-R0.1-SNAPSHOT").mockArtMap();
        mocks.mockDataFolder(new File("target/plugins/Artmap/")).mockLogger();
        ArtMap plugin = mocks.getArtmapMock();

        Lang.load(plugin, new Configuration(plugin));
        when(plugin.getRecipeLoader()).thenReturn(mock(me.Fupery.ArtMap.Recipe.RecipeLoader.class));
        me.Fupery.ArtMap.Recipe.ArtMaterial.setupRecipes();
        when(plugin.getDyePalette()).thenReturn(new me.Fupery.ArtMap.Compatibility.impl.Palette_1_18());

        db = new Database(plugin);
        when(plugin.getArtDatabase()).thenReturn(db);

        // The network layer we are asserting about; the receiver itself is inert.
        ProtocolHandler protocol = mock(ProtocolHandler.class);
        when(protocol.getPacketReceiver()).thenReturn(mock(PacketReceiver.class));
        when(plugin.getProtocolManager()).thenReturn(protocol);

        // endPlayerArtSession also touches previews; a real one with nothing in it.
        when(plugin.getPreviewManager()).thenReturn(new PreviewManager());

        // EaselPart's static initialiser reads the seat offsets off the version
        // handler, so it has to resolve before any Easel is constructed.
        when(plugin.getBukkitVersion())
                .thenReturn(new VersionHandler(BukkitVersion.v1_20_2, mocks.getPluginMock()));
        // breakEasel de-registers the easel from this map.
        when(plugin.getEasels()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        players = mocks.getRandomMockPlayers(6);
    }

    /** Fresh handler per test - {@code artists} is long-lived state in production. */
    @BeforeEach
    public void freshHandler() {
        handler = new ArtistHandler();
        when(mocks.getArtmapMock().getArtistHandler()).thenReturn(handler);
    }

    private static Easel mockEasel() {
        Easel easel = mock(Easel.class);
        when(easel.seatUser(any(Player.class))).thenReturn(true);
        return easel;
    }

    /** True when ArtMap would let this player's item-frame interaction through. */
    private boolean itemFrameInteractionAllowed(Player player) {
        return handler.handlePacket(player, new ArtistPacket.PacketInteract(InteractType.INTERACT));
    }

    private void startSession(Player player) throws Exception {
        // handlePacket reads the held item; a bare ItemStack(AIR) has no craft delegate outside a real server.
        player.getInventory().setItemInMainHand(new ItemStack(Material.RED_DYE));
        handler.addPlayer(player, mockEasel(), new Map(nextMapId++), 0);
        assertTrue(handler.containsPlayer(player), "precondition: session should be registered");
    }

    // ── controls ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a player with no art session can use item frames")
    public void noSessionMeansPacketsPassThrough() {
        players[0].getInventory().setItemInMainHand(new ItemStack(Material.RED_DYE));
        assertTrue(itemFrameInteractionAllowed(players[0]),
                "USE_ENTITY must not be cancelled for a player who is not painting");
    }

    @Test
    @DisplayName("a player actively painting has entity interactions cancelled (by design)")
    public void activeSessionCancelsInteractions() throws Exception {
        startSession(players[1]);
        assertFalse(itemFrameInteractionAllowed(players[1]),
                "while painting, USE_ENTITY is consumed as a brush stroke - this is correct");
    }

    /** #376 follow-up: pin that left- and right-click on frames are treated alike here (the video suggested
     *  otherwise). Only the handlePacket decision; USE_ENTITY decoding upstream is version-specific. */
    @Test
    @DisplayName("#376 probe: ATTACK and INTERACT are treated identically while painting")
    public void attackAndInteractAreBothCancelledWhilePainting() throws Exception {
        Player player = players[0];
        startSession(player);

        boolean interactAllowed = handler.handlePacket(player,
                new ArtistPacket.PacketInteract(InteractType.INTERACT));
        boolean attackAllowed = handler.handlePacket(player,
                new ArtistPacket.PacketInteract(InteractType.ATTACK));

        assertFalse(interactAllowed, "right-click (INTERACT) is consumed as a brush stroke");
        assertFalse(attackAllowed,
                "left-click (ATTACK) is consumed too - PacketInteract always carries "
                + "PacketType.INTERACT, so handlePacket cannot distinguish them");
    }

    @Test
    @DisplayName("#376 probe: both pass through once the session has ended")
    public void attackAndInteractBothRecoverAfterSessionEnds() throws Exception {
        Player player = players[1];
        startSession(player);
        handler.removePlayer(player);

        assertTrue(handler.handlePacket(player,
                new ArtistPacket.PacketInteract(InteractType.INTERACT)),
                "right-click must work again");
        assertTrue(handler.handlePacket(player,
                new ArtistPacket.PacketInteract(InteractType.ATTACK)),
                "left-click must work again");
    }

    @Test
    @DisplayName("#376 probe: a bare arm swing is never cancelled")
    public void armSwingIsNeverCancelled() throws Exception {
        Player player = players[2];
        startSession(player);

        assertTrue(handler.handlePacket(player, new ArtistPacket.PacketArmSwing()),
                "ARM_ANIMATION is painted with but passed through, so mounted swings "
                + "still reach the server (see the 26.1 client change, issue #373)");
        handler.removePlayer(player);
    }

    // ── the four defects ─────────────────────────────────────────────────────

    /** removePlayer used to bail on !isActive() before removing the map entry, so the session was never cleared. */
    @Test
    @DisplayName("#376: removePlayer clears the map even when the session is already inactive")
    public void removePlayerClearsInactiveSession() throws Exception {
        Player player = players[2];
        startSession(player);

        // Still in the map but inactive: ArtSession.end() sets this, and addPlayer has a window before setActive(true).
        ArtSession session = handler.getCurrentSession(player);
        assertNotNull(session, "precondition: session present");
        Method setActive = ArtSession.class.getDeclaredMethod("setActive", boolean.class);
        setActive.setAccessible(true);
        setActive.invoke(session, false);

        handler.removePlayer(player);

        assertFalse(handler.containsPlayer(player),
                "an inactive session must still be removed, or the player is stranded forever");
        assertTrue(itemFrameInteractionAllowed(player),
                "#376: item frames must work again once the session is gone");
    }

    /** What made #376 permanent: cleanup ran only if (player.isInsideVehicle()), so once the seat was gone
     *  quit, kick, death and relogging all skipped it. */
    @Test
    @DisplayName("#376: quitting clears the session even when the player is not in a vehicle")
    public void quitClearsSessionWithoutVehicle() throws Exception {
        Player player = players[3];
        startSession(player);

        // The easel (and its seat) is gone - this is what made it unrecoverable.
        when(player.isInsideVehicle()).thenReturn(false);

        PlayerQuitListener.endPlayerArtSession(player);

        assertFalse(handler.containsPlayer(player),
                "quit cleanup must not be gated on the player still sitting on the easel");
        assertTrue(itemFrameInteractionAllowed(player),
                "#376: rejoining must recover a player whose easel was destroyed");
    }

    /** Breaking an easel removed its parts, seat included, without ending the painter's session. */
    @Test
    @DisplayName("#376: breaking an easel ends the occupant's art session")
    public void breakEaselEndsOccupantSession() throws Exception {
        Player player = players[4];
        startSession(player);

        Easel easel = newRealEasel();
        setEaselUser(easel, player.getUniqueId());

        easel.breakEasel();

        assertFalse(handler.containsPlayer(player),
                "breaking an easel must not leave its painter stranded in the artists map");
        assertTrue(itemFrameInteractionAllowed(player),
                "#376: item frames must work after the easel you were using is broken");
    }

    /** The #292 dismount cancel cannot re-seat a player whose seat is being destroyed; returning early stranded them. */
    @Test
    @DisplayName("#376: the #292 paint guard does not strand a player whose seat is gone")
    public void dismountGuardDoesNotStrandWhenSeatInvalid() throws Exception {
        Player player = players[5];
        when(player.getType()).thenReturn(EntityType.PLAYER);
        startSession(player);
        // A stroke just landed, so the #292 guard is live.
        handler.handlePacket(player, new ArtistPacket.PacketInteract(InteractType.ATTACK));

        // The seat entity is being removed, so it is no longer valid.
        Entity deadSeat = mock(Entity.class);
        when(deadSeat.isValid()).thenReturn(false);

        EntityDismountEvent event = mock(EntityDismountEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDismounted()).thenReturn(deadSeat);

        new PlayerDismountListener().onPlayerDismount(event);

        verify(event, never()).setCancelled(true);
        assertFalse(handler.containsPlayer(player),
                "a dismount caused by the seat being destroyed must end the session");
        assertTrue(itemFrameInteractionAllowed(player),
                "#376: item frames must work after the easel is destroyed mid-stroke");
    }

    @Test
    @DisplayName("#292 still honoured: a recent stroke on a live seat cancels the dismount")
    public void dismountGuardStillProtectsLiveSeat() throws Exception {
        Player player = players[5];
        when(player.getType()).thenReturn(EntityType.PLAYER);
        startSession(player);
        handler.handlePacket(player, new ArtistPacket.PacketInteract(InteractType.ATTACK));

        Entity liveSeat = mock(Entity.class);
        when(liveSeat.isValid()).thenReturn(true);

        EntityDismountEvent event = mock(EntityDismountEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDismounted()).thenReturn(liveSeat);

        new PlayerDismountListener().onPlayerDismount(event);

        verify(event).setCancelled(true);
        assertTrue(handler.containsPlayer(player),
                "#292: a player who just painted must stay seated on a live easel");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Easel's constructor is private and needs a chunk; build one over mocks so breakEasel() runs for real. */
    private static Easel newRealEasel() throws Exception {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("test-world");

        // breakEasel's scheduled body clears the sign and drops the item; give it enough world to run quietly.
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);

        Location location = mock(Location.class);
        when(location.getChunk()).thenReturn(chunk);
        when(location.getWorld()).thenReturn(world);
        when(location.clone()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(location.add(any(Double.class), any(Double.class), any(Double.class))).thenReturn(location);

        Constructor<Easel> ctor = Easel.class.getDeclaredConstructor(Location.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(location, true);
    }

    /** seatUser() spawns entities, so set the occupant directly. */
    private static void setEaselUser(Easel easel, UUID user) throws Exception {
        Field field = Easel.class.getDeclaredField("user");
        field.setAccessible(true);
        field.set(easel, user);
    }
}
