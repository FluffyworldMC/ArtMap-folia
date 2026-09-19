package me.Fupery.ArtMap.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.CompressedMap;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.Menu.API.TextPagination;
import me.Fupery.ArtMap.Menu.Event.MenuFactory;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Menu.Handler.MenuHandler;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/** The command dispatcher, its shared permission gate, and the admin commands defined inline in it. */
public class AdminCommandsTest extends PluginTestSupport {

    private static int nextMapId = 8800;

    private Player admin;
    private Database database;
    private CommandHandler handler;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        // Both commands hand work to the Bukkit scheduler; run it inline.
        when(Bukkit.getScheduler().runTaskAsynchronously(any(), any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; });
        when(Bukkit.getScheduler().runTask(any(), any(Runnable.class)))
                .thenAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; });
        // /art give looks players up by name.
        when(mocks.getMockServer().getPlayer(anyString())).thenAnswer(i -> {
            for (Player p : mocks.getRandomMockPlayers(10)) if (p.getName().equals(i.getArgument(0))) return p;
            return null;
        });
    }

    @BeforeEach
    public void fresh() throws Exception {
        mocks.resetMockPlayerPermissions();
        admin = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
        clearInvocations(admin, admin.getInventory());
        when(admin.hasPermission("artmap.admin")).thenReturn(true);
        database = mock(Database.class);
        when(ArtMap.instance().getArtDatabase()).thenReturn(database);
        handler = new CommandHandler();
    }

    @AfterEach
    public void restore() {
        when(ArtMap.instance().getArtDatabase()).thenReturn(db);
    }

    private void dispatch(CommandSender sender, String... args) {
        handler.onCommand(sender, mock(org.bukkit.command.Command.class), "art", args);
    }

    // ---- dispatch and the shared gate -------------------------------------

    @Test
    @DisplayName("a known sub command runs and its reply reaches the sender")
    public void dispatchRunsCommand() {
        dispatch(admin, "delete");
        verify(admin).sendMessage(Lang.MISSING_TITLE.get());
    }

    @Test
    @DisplayName("an unknown sub command answers with help, and no arguments opens the help menu")
    public void dispatchUnknownAndEmpty() throws Exception {
        dispatch(admin, "frobnicate");
        verify(admin).sendMessage(Lang.HELP.get());

        MenuHandler menus = mock(MenuHandler.class);
        MenuHandler.MenuList list = mock(MenuHandler.MenuList.class);
        java.lang.reflect.Field menuField = MenuHandler.class.getDeclaredField("MENU");
        menuField.setAccessible(true);
        menuField.set(menus, list);
        CacheableMenu help = mock(CacheableMenu.class);
        MenuFactory helpFactory = mock(MenuFactory.class);
        when(helpFactory.get(admin)).thenReturn(help);
        list.HELP = helpFactory;
        when(ArtMap.instance().getMenuHandler()).thenReturn(menus);
        when(admin.hasPermission("artmap.menu")).thenReturn(true);

        dispatch(admin);
        verify(menus).openMenu(admin, help);
    }

    @Test
    @DisplayName("help with an argument, or from the console, prints the text help")
    public void helpText() {
        when(admin.hasPermission("artmap.menu")).thenReturn(true);
        dispatch(admin, "help");
        verify(admin).sendMessage(Lang.Array.CONSOLE_HELP.get());

        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("artmap.menu")).thenReturn(true);
        dispatch(console);
        verify(console).sendMessage(Lang.Array.CONSOLE_HELP.get());
    }

    @Test
    @DisplayName("the gate refuses a missing permission and a console on a player-only command")
    public void gateRefuses() {
        when(admin.hasPermission("artmap.admin")).thenReturn(false);
        dispatch(admin, "convert");
        verify(admin).sendMessage(Lang.NO_PERM.get());

        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("artmap.artist")).thenReturn(true);
        dispatch(console, "clear");
        verify(console).sendMessage(Lang.NO_CONSOLE.get());
    }

    // ---- give ------------------------------------------------------------------

    private ItemStack given() {
        org.mockito.ArgumentCaptor<ItemStack> item = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(admin.getInventory()).addItem(item.capture());
        return item.getValue();
    }

    @Test
    @DisplayName("give hands out the craftable items, with an optional amount")
    public void giveCraftables() {
        dispatch(admin, "give", admin.getName(), "easel");
        assertEquals(Material.ARMOR_STAND, given().getType());
        clearInvocations(admin.getInventory());

        dispatch(admin, "give", admin.getName(), "canvas", "5");
        ItemStack canvas = given();
        assertEquals(Material.PAPER, canvas.getType());
        assertEquals(5, canvas.getAmount());
        clearInvocations(admin.getInventory());

        dispatch(admin, "give", admin.getName(), "paintbrush", "1");
        assertEquals(Material.REDSTONE_TORCH, given().getType());
    }

    @Test
    @DisplayName("give looks up unsaved and saved artworks in the database")
    public void giveArtworks() throws Exception {
        when(database.containsUnsavedArtwork(41)).thenReturn(true);
        dispatch(admin, "give", admin.getName(), "unsaved:41");
        assertEquals(Material.FILLED_MAP, given().getType());
        clearInvocations(admin.getInventory());

        when(database.containsUnsavedArtwork(42)).thenReturn(false);
        dispatch(admin, "give", admin.getName(), "unsaved:42");
        verify(admin).sendMessage(contains("No unsaved artwork"));

        MapArt art = new MapArt(nextMapId++, "Sunset", UUID.randomUUID(), "Artist", "01-01-2026");
        when(database.getArtwork("Sunset")).thenReturn(Optional.of(art));
        dispatch(admin, "give", admin.getName(), "artwork:Sunset");
        assertEquals(Material.FILLED_MAP, given().getType());

        when(database.getArtwork("Nope")).thenReturn(Optional.empty());
        dispatch(admin, "give", admin.getName(), "artwork:Nope");
        verify(admin).sendMessage(contains("could not be found"));

        when(database.getArtwork("Boom")).thenThrow(new java.sql.SQLException("down"));
        dispatch(admin, "give", admin.getName(), "artwork:Boom");
        verify(admin).sendMessage(contains("Error retrieving art"));
    }

    @Test
    @DisplayName("give reports an unknown player or item")
    public void giveGuards() {
        dispatch(admin, "give", "NoSuchPlayer", "easel");
        verify(admin).sendMessage(contains("NoSuchPlayer"));

        dispatch(admin, "give", admin.getName(), "spaceship");
        verify(admin).sendMessage(contains("/art give"));
        verify(admin.getInventory(), never()).addItem(any(ItemStack.class));
    }

    // ---- palette ---------------------------------------------------------------

    @Test
    @DisplayName("palette hands the admin a map whose renderer paints every colour once")
    public void paletteMap() {
        List<MapRenderer> renderers = new ArrayList<>();
        MapView view = mock(MapView.class);
        when(view.getRenderers()).thenReturn(renderers);
        org.mockito.Mockito.doAnswer(i -> renderers.add(i.getArgument(0))).when(view).addRenderer(any());
        when(mocks.getMockServer().createMap(any())).thenReturn(view);

        dispatch(admin, "palette");

        verify(admin.getInventory()).setItemInMainHand(argThat((ItemStack i) -> i.getType() == Material.FILLED_MAP));
        assertEquals(1, renderers.size());
        MapCanvas canvas = mock(MapCanvas.class);
        renderers.get(0).render(view, canvas, admin);
        verify(canvas, times(128 * 128)).setPixel(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyByte());
        verify(canvas).setPixel(0, 5, (byte) 5);
        verify(canvas).setPixel(64, 5, (byte) (5 + 128));
        renderers.get(0).render(view, canvas, admin);
        verify(canvas, times(128 * 128)).setPixel(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyByte());
    }

    // ---- convert -----------------------------------------------------------------

    private static File legacy(String name) {
        return new File(ArtMap.instance().getDataFolder(), name);
    }

    @Test
    @DisplayName("convert with nothing to convert says so, and --force picks up disabled legacy files")
    public void convert() throws Exception {
        for (String f : new String[] { "mapList.yml", "mapList.yml.off", "ArtMap.db", "ArtMap.db.off" }) legacy(f).delete();
        dispatch(admin, "convert");
        verify(admin).sendMessage(contains("not needed or failed"));

        legacy("mapList.yml.off").createNewFile();
        try {
            dispatch(admin, "convert", "--force");
            verify(admin).sendMessage(contains("Flat DB conversion being forced"));
            verify(admin).sendMessage(contains("No artwork to export"));
            verify(admin).sendMessage(contains("import -all"));
        } finally {
            for (File f : ArtMap.instance().getDataFolder().listFiles()) if (f.getName().startsWith("mapList.yml")) f.delete();
        }
    }

    // ---- test --------------------------------------------------------------------

    @Test
    @DisplayName("test -create makes the requested number of artworks")
    public void testCreate() throws Exception {
        when(database.createMap()).thenAnswer(i -> new Map(nextMapId++));
        dispatch(admin, "test", "-create", "3");
        verify(database, times(3)).saveArtwork(any(MapArt.class), any(CompressedMap.class));

        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandTest(), "test").message);
        assertEquals(Lang.COMMAND_EXPORT.get(), run(new CommandTest(), "test", "-nonsense").message);
        when(admin.hasPermission("artmap.admin")).thenReturn(false);
        assertEquals(Lang.NO_PERM.get(), run(new CommandTest(), "test", "-create", "1").message);
    }

    private ReturnMessage run(AsyncCommand command, String... args) {
        ReturnMessage msg = new ReturnMessage(admin, null);
        command.runCommand(admin, args, msg);
        return msg;
    }

    // ---- page ---------------------------------------------------------------------

    private static TextComponent[] lines(int n) {
        TextComponent[] t = new TextComponent[n];
        for (int i = 0; i < n; i++) t[i] = new TextComponent("line " + i);
        return t;
    }

    @Test
    @DisplayName("page walks a started pagination and refuses bad or expired requests")
    public void page() {
        Player.Spigot spigot = admin.spigot();
        clearInvocations(spigot);
        TextPagination pages = new TextPagination(lines(25), "Results", admin.getUniqueId());
        Page.startPagination(admin, pages);
        verify(spigot, times(20)).sendMessage(any(BaseComponent.class));

        clearInvocations(spigot);
        assertNull(run(new Page(), "page", admin.getUniqueId().toString(), "2").message);
        verify(spigot, times(7)).sendMessage(any(BaseComponent.class));

        assertEquals("page syntax incorrect!", run(new Page(), "page").message);
        assertEquals("Player not found.", run(new Page(), "page", "not-a-uuid", "1").message);
        assertEquals("Player not found.", run(new Page(), "page", UUID.randomUUID().toString(), "1").message);
        Player other = mocks.getRandomMockPlayers(10)[0] == admin ? mocks.getRandomMockPlayers(10)[1] : mocks.getRandomMockPlayers(10)[0];
        assertEquals("Pagination for this player has expired.", run(new Page(), "page", other.getUniqueId().toString(), "1").message);
    }

    @Test
    @DisplayName("pagination headers carry a back arrow after page one and a forward arrow before the last")
    public void paginationArrows() {
        TextPagination pages = new TextPagination(lines(40), "Results", admin.getUniqueId());
        Player.Spigot spigot = admin.spigot();
        pages.sendPlayerPage(admin, 1);
        assertTrue(pages.buildHeader().toLegacyText().contains(">>"));
        pages.sendPlayerNextPage(admin);
        String middle = pages.buildHeader().toLegacyText();
        assertTrue(middle.contains("<<") && middle.contains(">>"));
        pages.sendPlayerNextPage(admin);
        assertTrue(pages.buildHeader().toLegacyText().contains("<<"));
        pages.sendPlayerNextPage(admin);
        pages.sendPlayerPrevPage(admin);
        pages.sendPlayerPrevPage(admin);
        pages.sendPlayerPrevPage(admin);
        assertTrue(pages.buildHeader().toLegacyText().contains(">>"));
        assertEquals(admin.getUniqueId(), pages.getId());
        verify(spigot, org.mockito.Mockito.atLeast(5)).sendMessage(any(BaseComponent.class));
    }
}
