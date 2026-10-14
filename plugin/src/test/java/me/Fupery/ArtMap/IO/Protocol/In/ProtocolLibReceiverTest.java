package me.Fupery.ArtMap.IO.Protocol.In;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.comphenix.protocol.PacketType;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The receiver listens for the left-click packet CompatibilityManager names (the swing, or 26.3's punch), looked
 *  up by name on the installed ProtocolLib. (Reading a packet is not driven here: PacketContainer cannot be built or
 *  mocked without a server's NMS classes.) */
public class ProtocolLibReceiverTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap(); // ProtocolLib's packet tables read the server's Minecraft version
    }

    @Test
    public void punchIsLookedUpByNameOnTheRunningProtocolLib() {
        assertSame(PacketType.Play.Client.LOOK, ProtocolLibReceiver.clientType("LOOK"));
        assertSame(PacketType.Play.Client.ARM_ANIMATION, ProtocolLibReceiver.clientType("ARM_ANIMATION"));
        // The test classpath has ProtocolLib 5.3.0, which predates 26.3's punch packet.
        assertNull(ProtocolLibReceiver.clientType("PUNCH"));
        assertNull(ProtocolLibReceiver.clientType("NOT_A_PACKET"));
    }
}
