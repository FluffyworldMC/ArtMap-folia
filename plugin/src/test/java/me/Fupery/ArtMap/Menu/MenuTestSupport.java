package me.Fupery.ArtMap.Menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.Menu.Handler.MenuHandler;
import me.Fupery.ArtMap.Preview.PreviewManager;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Utils.VersionHandler;
import me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion;
import me.Fupery.ArtMap.mocks.MockUtil;

/** Menus build real ItemStacks (MockBukkit's server) and call into ArtMap.instance() (MockUtil's mocked plugin), so
 *  menu tests run on both: MockBukkit owns Bukkit.getServer(), the mocked ArtMap is the plugin instance, Lang is the
 *  shipped lang.yml and the MenuHandler is real. Players are Mockito mocks: MockBukkit's PlayerMock needs a newer
 *  Guava than the test classpath has. */
public abstract class MenuTestSupport {

    protected static ServerMock server;
    protected static ArtMap plugin;
    protected static MenuHandler menus;
    protected static Database database;

    @BeforeAll
    public static void bootMenus() throws Exception {
        server = MockBukkit.mock();
        MockUtil util = new MockUtil();
        util.mockArtMap().mockDataFolder(new File("target/plugins/Artmap/"));
        plugin = util.getArtmapMock();
        Lang.load(plugin, new Configuration(plugin));
        when(plugin.getBukkitVersion()).thenReturn(new VersionHandler(BukkitVersion.v1_20_2, plugin));
        when(plugin.getDyePalette()).thenReturn(new me.Fupery.ArtMap.Compatibility.impl.Palette_1_18());
        when(plugin.getRecipeLoader()).thenReturn(mock(me.Fupery.ArtMap.Recipe.RecipeLoader.class));
        me.Fupery.ArtMap.Recipe.ArtMaterial.setupRecipes();
        database = mock(Database.class);
        when(plugin.getArtDatabase()).thenReturn(database);
        when(plugin.getPreviewManager()).thenReturn(mock(PreviewManager.class));
        // MenuHandler reads player.getOpenInventory(): InventoryView is a class in the API ArtMap compiles against
        // and an interface on the test classpath (Paper rewrites the call at runtime), so a real handler cannot run
        // here. A mock with the real menu list stands in; tests verify what the menus ask it to do.
        menus = mock(MenuHandler.class);
        Field list = MenuHandler.class.getDeclaredField("MENU");
        list.setAccessible(true);
        list.set(menus, new MenuHandler.MenuList());
        when(plugin.getMenuHandler()).thenReturn(menus);
    }

    @AfterAll
    public static void unmock() {
        MockBukkit.unmock();
    }

    /** A player with an empty off hand who is handed whatever they are given. */
    protected static Player player(boolean admin) {
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn("Player" + id.toString().substring(0, 4));
        when(p.hasPermission("artmap.admin")).thenReturn(admin);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any())).thenReturn(new HashMap<>());
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(p.getInventory()).thenReturn(inventory);
        return p;
    }
}
