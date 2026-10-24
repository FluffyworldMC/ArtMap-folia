package me.Fupery.ArtMap.Painting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The paint-brush (save) gesture and the handler's bookkeeping around sessions. */
public class ArtistHandlerTest extends PluginTestSupport {

    private static int nextMapId = 9500;
    private static Configuration defaultConfig;

    private ArtistHandler handler;
    private Player painter;
    private Easel easel;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        defaultConfig = ArtMap.instance().getConfiguration();
    }

    @BeforeEach
    public void fresh() throws Exception {
        handler = new ArtistHandler();
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);
        painter = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(painter);
        easel = mock(Easel.class);
        when(easel.seatUser(any(Player.class))).thenReturn(true);
        handler.addPlayer(painter, easel, new Map(nextMapId++), 0);
        painter.getInventory().setItemInMainHand(paintBrush());
    }

    @AfterEach
    public void restore() throws Exception {
        when(ArtMap.instance().getConfiguration()).thenReturn(defaultConfig);
        handler.removePlayer(painter);
        when(ArtMap.instance().getScheduler().SYNC.run(any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; });
    }

    private static ItemStack paintBrush() {
        ItemStack brush = item(Material.REDSTONE_TORCH);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasLore()).thenReturn(true);
        // Craftable kit items are identified by the kit key on the first lore line, not their name.
        when(meta.getLore()).thenReturn(Arrays.asList(ArtItem.KIT_KEY, ArtItem.PAINT_BRUSH));
        when(brush.hasItemMeta()).thenReturn(true);
        when(brush.getItemMeta()).thenReturn(meta);
        return brush;
    }

    private static void disablePaintbrush() throws Exception {
        Configuration cfg = mock(Configuration.class);
        Field f = Configuration.class.getDeclaredField("DISABLE_PAINTBRUSH");
        f.setAccessible(true);
        f.set(cfg, true);
        when(ArtMap.instance().getConfiguration()).thenReturn(cfg);
    }

    private static ArtistPacket click() {
        return new ArtistPacket.PacketInteract(InteractType.ATTACK);
    }

    @Test
    @DisplayName("with the paint brush disabled the click is refused and the painter told why")
    public void disabledBrushIsRefused() throws Exception {
        disablePaintbrush();
        assertFalse(handler.handlePacket(painter, click()));
        verify(painter).sendMessage(anyString());
    }

    @Test
    @DisplayName("a paint brush click while not seated does nothing")
    public void unseatedBrushClickIsIgnored() {
        when(painter.isInsideVehicle()).thenReturn(false);
        assertFalse(handler.handlePacket(painter, click()));
        verify(painter, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("a seated paint brush click opens the save dialog on the next tick")
    public void seatedBrushClickSchedulesTheSaveDialog() {
        when(painter.isInsideVehicle()).thenReturn(true);
        // The dialog is an AnvilGUI that needs a real server; capture the scheduled task instead of running it.
        when(ArtMap.instance().getScheduler().SYNC.run(any(Player.class), any(Runnable.class))).thenReturn(null);
        assertTrue(handler.handlePacket(painter, click()));
        verify(ArtMap.instance().getScheduler().SYNC).run(any(Player.class), any(Runnable.class));
    }

    @Test
    @DisplayName("the handler knows which easel a painter is on, and nothing about anyone else")
    public void easelLookup() {
        assertSame(easel, handler.getEasel(painter));
        assertTrue(handler.containsPlayer(painter.getUniqueId()));
        assertFalse(handler.containsPlayer((UUID) null));
        assertFalse(handler.containsPlayer((Player) null));
        Player[] two = mocks.getRandomMockPlayers(2); // one shuffle, so "other" can never be the painter
        Player other = two[0] == painter ? two[1] : two[0];
        assertNull(handler.getEasel(other));
        assertFalse(handler.containsPlayer(other.getUniqueId()));
        assertTrue(handler.getArtists().contains(painter.getUniqueId()));
    }

    @Test
    @DisplayName("stopping the handler ends every session and closes the packet receiver")
    public void stopEndsEverySession() {
        handler.stop();
        assertFalse(handler.containsPlayer(painter));
        verify(ArtMap.instance().getProtocolManager().getPacketReceiver()).close();
    }
}
