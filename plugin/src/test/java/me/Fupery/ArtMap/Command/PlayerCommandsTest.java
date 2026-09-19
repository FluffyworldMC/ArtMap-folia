package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Canvas;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Easel.EaselEffect;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.Preview.PreviewManager;
import me.Fupery.ArtMap.Utils.Scheduler;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Exception.DuplicateArtworkException;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The player-facing commands: save, clear, delete and preview. */
public class PlayerCommandsTest extends PluginTestSupport {

    private static final String UNFINISHED_TAG = ChatColor.AQUA.toString() + ChatColor.ITALIC + "Unfinished Artwork";
    private static int nextMapId = 8700;

    private Player player;
    private Database database;
    private ArtistHandler handler;
    private Easel easel;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void fresh() throws Exception {
        mocks.resetMockPlayerPermissions();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(player, player.getInventory());
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        handler = new ArtistHandler();
        when(ArtMap.instance().getArtistHandler()).thenReturn(handler);
        when(ArtMap.instance().getPreviewManager()).thenReturn(new PreviewManager());
        easel = mock(Easel.class);
        when(easel.seatUser(any(Player.class))).thenReturn(true);
    }

    @AfterEach
    public void restore() throws Exception {
        restoreConfig();
        handler.removePlayer(player);
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
    }

    private ReturnMessage run(AsyncCommand command, CommandSender sender, String... args) {
        ReturnMessage msg = new ReturnMessage(sender, null);
        command.runCommand(sender, args, msg);
        return msg;
    }

    private void seat() throws Exception {
        handler.addPlayer(player, easel, new me.Fupery.ArtMap.IO.Database.Map(nextMapId++), 0);
        assertTrue(handler.containsPlayer(player), "precondition: painting");
    }

    /** An unfinished-artwork map item, the thing an easel holds while someone paints on it. */
    private static ItemStack unfinishedMap(int id) {
        ItemStack map = item(Material.FILLED_MAP);
        MapView view = mock(MapView.class);
        when(view.getId()).thenReturn(id);
        MapMeta meta = mock(MapMeta.class);
        when(meta.hasMapView()).thenReturn(true);
        when(meta.getMapView()).thenReturn(view);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Arrays.asList(UNFINISHED_TAG, "by someone"));
        when(map.hasItemMeta()).thenReturn(true);
        when(map.getItemMeta()).thenReturn(meta);
        return map;
    }

    // ---- save --------------------------------------------------------------

    @Test
    @DisplayName("save while seated stores the canvas, ends the session and hands back the artwork")
    public void saveStoresTheCanvas() throws Exception {
        seat();
        ItemStack canvasItem = unfinishedMap(77);
        when(easel.getItem()).thenReturn(canvasItem);
        MapArt saved = new MapArt(77, "Sunset", player.getUniqueId(), player.getName(), "01-01-2026");
        when(database.saveArtwork(any(Canvas.class), eq("Sunset"), eq(player))).thenReturn(saved);

        run(new CommandSave(), player, "save", "Sunset");

        verify(database).saveArtwork(any(Canvas.class), eq("Sunset"), eq(player));
        assertFalse(handler.containsPlayer(player), "the session ends on save");
        verify(easel).playEffect(EaselEffect.SAVE_ARTWORK);
        verify(easel).setItem(any(ItemStack.class));
        verify(player.getInventory()).addItem(any(ItemStack.class));
        verify(player).sendMessage(contains("Sunset"));
    }

    @Test
    @DisplayName("save refuses when the GUI is forced, the title is missing or bad, or nobody is painting")
    public void saveGuards() throws Exception {
        configOverriding(Map.of("FORCE_GUI", true));
        run(new CommandSave(), player, "save", "Sunset");
        verify(player).sendMessage(Lang.PAINTBRUSH_FORCED.get());
        restoreConfig();

        run(new CommandSave(), player, "save");
        verify(database, never()).saveArtwork(any(Canvas.class), anyString(), any(Player.class));

        assertEquals(Lang.BAD_TITLE.get(), run(new CommandSave(), player, "save", "a").message, "too short");
        assertEquals(Lang.BAD_TITLE.get(), run(new CommandSave(), player, "save", "Sun$et").message, "illegal character");

        assertNull(run(new CommandSave(), player, "save", "Sunset").message);
        verify(player).sendMessage(Lang.NOT_RIDING_EASEL.get());
    }

    @Test
    @DisplayName("a duplicate title is reported to the painter and the session continues")
    public void saveDuplicateTitleKeepsPainting() throws Exception {
        seat();
        ItemStack canvasItem = unfinishedMap(78);
        when(easel.getItem()).thenReturn(canvasItem);
        when(database.saveArtwork(any(Canvas.class), anyString(), any(Player.class)))
                .thenThrow(new DuplicateArtworkException("taken"));

        run(new CommandSave(), player, "save", "Sunset");

        verify(player).sendMessage("taken");
        assertTrue(handler.containsPlayer(player), "a refused save leaves the painter seated");
    }

    @Test
    @DisplayName("a save that cannot read the canvas reports the failure and logs it")
    public void saveWithoutCanvasFails() throws Exception {
        seat();
        when(easel.getItem()).thenReturn(null);

        ReturnMessage msg = run(new CommandSave(), player, "save", "Sunset");

        assertEquals(String.format(Lang.PREFIX + Lang.SAVE_FAILURE.get(), "Sunset"), msg.message);
        assertTrue(handler.containsPlayer(player));
    }

    // ---- clear -------------------------------------------------------------

    @Test
    @DisplayName("clear wipes the canvas and persists the blank map")
    public void clearPersistsBlankMap() throws Exception {
        seat();
        clearInvocations(database);

        run(new CommandClear(), player, "clear");

        verify(database).saveInProgressArt(any(me.Fupery.ArtMap.IO.Database.Map.class), any(byte[].class));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("clear while not painting says so, and a database failure is reported")
    public void clearGuards() throws Exception {
        run(new CommandClear(), player, "clear");
        verify(player).sendMessage(Lang.NOT_RIDING_EASEL.get());

        seat();
        org.mockito.Mockito.doThrow(new SQLException("down")).when(database)
                .saveInProgressArt(any(me.Fupery.ArtMap.IO.Database.Map.class), any(byte[].class));
        run(new CommandClear(), player, "clear");
        verify(player).sendMessage(contains("Failure breaking the easel"));
    }

    // ---- delete ------------------------------------------------------------

    private MapArt artworkBy(UUID artist) throws SQLException {
        MapArt art = new MapArt(nextMapId++, "Sunset", artist, "Artist", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(art));
        return art;
    }

    @Test
    @DisplayName("the artist, an admin and the console may delete; anyone else may not")
    public void deletePermissions() throws Exception {
        MapArt own = artworkBy(player.getUniqueId());
        assertEquals(String.format(Lang.DELETED.get(), "Sunset"), run(new CommandDelete(), player, "delete", "Sunset").message);
        verify(database).deleteArtwork(own);

        MapArt other = artworkBy(UUID.randomUUID());
        assertEquals(Lang.NO_PERM.get(), run(new CommandDelete(), player, "delete", "Sunset").message);
        verify(database, never()).deleteArtwork(other);

        when(player.hasPermission("artmap.admin")).thenReturn(true);
        assertEquals(String.format(Lang.DELETED.get(), "Sunset"), run(new CommandDelete(), player, "delete", "Sunset").message);

        CommandSender console = mock(CommandSender.class);
        assertEquals(String.format(Lang.DELETED.get(), "Sunset"), run(new CommandDelete(), console, "delete", "Sunset").message);
    }

    @Test
    @DisplayName("delete refuses a forced GUI, a missing title, an unknown artwork and reports a database error")
    public void deleteGuards() throws Exception {
        configOverriding(Map.of("FORCE_GUI", true));
        run(new CommandDelete(), player, "delete", "Sunset");
        verify(player).sendMessage(contains("Paint Brush"));
        restoreConfig();

        assertEquals(Lang.MISSING_TITLE.get(), run(new CommandDelete(), player, "delete").message);

        when(database.getArtwork("Nope")).thenReturn(Optional.empty());
        assertEquals(String.format(Lang.MAP_NOT_FOUND.get(), "Nope"), run(new CommandDelete(), player, "delete", "Nope").message);

        when(database.getArtwork("Boom")).thenThrow(new SQLException("down"));
        assertNull(run(new CommandDelete(), player, "delete", "Boom").message);
        verify(player).sendMessage(contains("Failure deleting art"));
    }

    // ---- preview -----------------------------------------------------------

    @Test
    @DisplayName("preview with empty hands puts the artwork in the off hand and starts a timed preview")
    public void previewStartsWithEmptyHands() throws Exception {
        Player viewer = statefulPlayer("Viewer");
        viewer.getInventory().setItemInMainHand(item(Material.AIR));
        viewer.getInventory().setItemInOffHand(item(Material.AIR));
        Scheduler.TaskHandler task = mock(Scheduler.TaskHandler.class);
        when(ArtMap.instance().getScheduler().getTaskHandler(any())).thenReturn(task);
        MapArt art = new MapArt(nextMapId++, "Sunset", UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(art));

        ReturnMessage msg = run(new CommandPreview(), viewer, "preview", "Sunset");

        assertEquals(String.format(Lang.PREVIEWING.get(), "Sunset"), msg.message);
        assertTrue(previews().isPreviewing(viewer));
        assertEquals(Material.FILLED_MAP, viewer.getInventory().getItemInOffHand().getType());
        verify(task).runLater(false, 300);
    }

    @Test
    @DisplayName("preview needs a title, a known artwork, an empty main hand and no forced GUI")
    public void previewGuards() throws Exception {
        assertEquals(Lang.COMMAND_PREVIEW.get(), run(new CommandPreview(), player, "preview").message);

        when(database.getArtwork("Nope")).thenReturn(Optional.empty());
        assertEquals(String.format(Lang.MAP_NOT_FOUND.get(), "Nope"), run(new CommandPreview(), player, "preview", "Nope").message);

        when(database.getArtwork("Boom")).thenThrow(new SQLException("down"));
        assertNull(run(new CommandPreview(), player, "preview", "Boom").message);
        verify(player).sendMessage("Error loading preview!");

        MapArt art = new MapArt(nextMapId++, "Sunset", UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(art));
        player.getInventory().setItemInMainHand(item(Material.STONE));
        assertEquals(Lang.EMPTY_HAND_PREVIEW.get(), run(new CommandPreview(), player, "preview", "Sunset").message);
        assertFalse(previews().isPreviewing(player));

        configOverriding(Map.of("FORCE_GUI", true));
        assertEquals(Lang.EMPTY_HAND_PREVIEW.get(), run(new CommandPreview(), player, "preview", "Sunset").message);
        verify(player).sendMessage(contains("Paint Brush"));
    }

    @Test
    @DisplayName("the command's sender is the message target")
    public void returnMessageGoesToSender() {
        ReturnMessage msg = new ReturnMessage(player, "hello");
        msg.run();
        verify(player).sendMessage("hello");
    }
}
