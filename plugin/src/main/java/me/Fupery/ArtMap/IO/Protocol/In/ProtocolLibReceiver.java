package me.Fupery.ArtMap.IO.Protocol.In;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.EnumWrappers;
import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket;
import me.Fupery.ArtMap.Painting.ArtistHandler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket.PacketInteract;
import static me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket.PacketInteract.InteractType;

public class ProtocolLibReceiver extends PacketReceiver {

    // The client's left-click on the canvas: the arm swing, or from 26.3 the punch (CompatibilityManager picks it).
    // Null when this ProtocolLib build has no such packet type, e.g. one older than the server's Minecraft version.
    private final PacketType leftClick;

    public ProtocolLibReceiver() {
        String name = ArtMap.instance().getCompatManager().getLeftClickPacket();
        leftClick = clientType(name);
        if (leftClick == null) {
            ArtMap.instance().getLogger().warning("This ProtocolLib build has no " + name + " packet for this "
                    + "Minecraft version, so left-click painting is disabled. Update ProtocolLib.");
        }
        registerListeners(ArtMap.instance());
    }

    /** A client packet type by its PacketType.Play.Client field name, or null on a ProtocolLib that predates it.
     *  ArtMap builds against ProtocolLib 5.3.0, which has no PUNCH (added for 26.3). */
    static PacketType clientType(String name) {
        try {
            return (PacketType) PacketType.Play.Client.class.getField(name).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private void registerListeners(JavaPlugin plugin) {
        PacketAdapter.AdapterParameteters options = new PacketAdapter.AdapterParameteters();
        options.plugin(plugin);
        options.optionAsync();
        options.connectionSide(ConnectionSide.CLIENT_SIDE);
        options.listenerPriority(ListenerPriority.HIGH);
        List<PacketType> types = new ArrayList<>();
        if (leftClick != null) types.add(leftClick);
        types.add(PacketType.Play.Client.LOOK);
        types.add(PacketType.Play.Client.USE_ENTITY);
        options.types(types.toArray(new PacketType[0]));
        ProtocolLibrary.getProtocolManager().addPacketListener(new DefaultPacketAdapter(options));
    }

    static ArtistPacket getPacketType(PacketContainer packet, PacketType leftClick) {
        if (packet.getType() == PacketType.Play.Client.LOOK) {
            float yaw = packet.getFloat().read(0);
            float pitch = packet.getFloat().read(1);
            return new ArtistPacket.PacketLook(yaw, pitch);

        } else if (packet.getType() == leftClick) {
            return new ArtistPacket.PacketArmSwing();

        } else if (packet.getType() == PacketType.Play.Client.USE_ENTITY) {
            try {
                EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
            return new PacketInteract(
                action == EnumWrappers.EntityUseAction.ATTACK ? InteractType.ATTACK : InteractType.INTERACT);
            } catch (Exception e) {
                //Then we must be on 1.17+
                try {
                    Object enumEntityUseActionObject = packet.getModifier().read(1);
                    // 26.3 moved attacks to their own packet: the interact packet holds the hand here, not an action
                    if (enumEntityUseActionObject instanceof Enum) {
                        return new PacketInteract(InteractType.INTERACT);
                    }
                    Method method = enumEntityUseActionObject.getClass().getMethod("a");
                    method.setAccessible(true);
                    Object nmsAction = method.invoke(enumEntityUseActionObject);
                    return new PacketInteract(
						nmsAction.toString().equals("ATTACK") ||	// 1.17.1
						nmsAction.toString().equals("b") ?			// 1.17
							InteractType.ATTACK : InteractType.INTERACT
					);
                } catch (Exception e1) {
                    ArtMap.instance().getLogger().log(Level.SEVERE, "Error reading USE_ENTITY packet!", e1);
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(ArtMap.instance());
    }

    class DefaultPacketAdapter extends PacketAdapter {
        DefaultPacketAdapter(AdapterParameteters options) {
            super(options);
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            ArtistHandler handler = ArtMap.instance().getArtistHandler();
            try {
                if (!handler.containsPlayer(event.getPlayer()))
                    return;
                ArtistPacket packet = getPacketType(event.getPacket(), leftClick);
                if (packet == null)
                    return;
                if (!onPacketPlayIn(handler, event.getPlayer(), packet))
                    event.setCancelled(true);
            } catch (UnsupportedOperationException e) {
                //ProtocolLib sometimes passes a fake player here.  Do nothing if that happens.
            }
        }
    }

    @Override
    public void injectPlayer(Player player) throws ReflectiveOperationException {}

    @Override
    public void uninjectPlayer(Player player) {}
}
