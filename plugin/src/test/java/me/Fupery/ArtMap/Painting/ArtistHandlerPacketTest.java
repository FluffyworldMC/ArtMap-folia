package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket.PacketInteract.InteractType;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The packet routes through the handler other than the paint brush: look, swing, click and strangers. */
public class ArtistHandlerPacketTest extends PluginTestSupport {

    private static int nextMapId = 9600;

    private ArtistHandler handler;
    private Player painter;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() throws Exception {
        handler = new ArtistHandler();
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);
        painter = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        Easel easel = mock(Easel.class);
        when(easel.seatUser(any(Player.class))).thenReturn(true);
        handler.addPlayer(painter, easel, new Map(nextMapId++), 0);
        painter.getInventory().setItemInMainHand(item(Material.RED_DYE));
        Thread.sleep(60); // past the brush cooldown measured from the session start
    }

    @AfterEach
    public void endSession() throws Exception {
        handler.removePlayer(painter);
    }

    @Test
    @DisplayName("a look packet moves the cursor and is swallowed")
    public void look() {
        assertTrue(handler.handlePacket(painter, new ArtistPacket.PacketLook(12f, -4f)));
    }

    @Test
    @DisplayName("an arm swing with a dye paints and is swallowed; a click paints and passes through")
    public void swingAndClickPaint() {
        assertTrue(handler.handlePacket(painter, new ArtistPacket.PacketArmSwing()));
        assertFalse(handler.handlePacket(painter, new ArtistPacket.PacketInteract(InteractType.ATTACK)));
        assertFalse(handler.handlePacket(painter, new ArtistPacket.PacketInteract(InteractType.INTERACT)));
        assertTrue(handler.getCurrentSession(painter).lastPaintActionWithin(5, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("a packet from someone not painting is passed through and any stale session dropped")
    public void strangerPassesThrough() throws Exception {
        Player[] two = mocks.getRandomMockPlayers(2);
        Player stranger = two[0] == painter ? two[1] : two[0];
        org.mockito.Mockito.clearInvocations(ArtMap.instance().getProtocolManager().getPacketReceiver()); // shared mock
        assertTrue(handler.handlePacket(stranger, new ArtistPacket.PacketArmSwing()));
        assertFalse(handler.containsPlayer(stranger));
        verify(ArtMap.instance().getProtocolManager().getPacketReceiver(), org.mockito.Mockito.never()).uninjectPlayer(stranger);
    }

    @Test
    @DisplayName("an object that is no known packet is typed as invalid")
    public void unknownPacketType() {
        org.junit.jupiter.api.Assertions.assertEquals(me.Fupery.ArtMap.IO.Protocol.In.Packet.PacketType.INVALID,
                me.Fupery.ArtMap.IO.Protocol.In.Packet.PacketType.getPacketType("not a packet"));
    }

    @Test
    @DisplayName("null packets and senders are ignored")
    public void nulls() {
        assertTrue(handler.handlePacket(painter, null));
        assertTrue(handler.handlePacket(null, new ArtistPacket.PacketArmSwing()));
    }
}
