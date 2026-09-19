package me.Fupery.ArtMap.mocks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Database;
import me.Fupery.ArtMap.IO.Protocol.ProtocolHandler;
import me.Fupery.ArtMap.IO.Protocol.In.PacketReceiver;
import me.Fupery.ArtMap.Painting.ArtistHandler;
import me.Fupery.ArtMap.Preview.PreviewManager;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Utils.VersionHandler;
import me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion;

/** Shared bootstrap: a mocked server + ArtMap instance wired far enough for event handlers to run.
 *  MockUtil guards its re-initialisation, so several test classes can call bootstrap() in @BeforeAll. */
public abstract class PluginTestSupport {

    protected static MockUtil mocks;
    protected static Database db;
    protected static Configuration defaultConfig;

    protected static void bootstrap() throws Exception {
        if (mocks != null) {
            return;
        }
        MockUtil util = new MockUtil();
        util.mockServer("1.21.4-R0.1-SNAPSHOT").mockArtMap();
        util.mockDataFolder(new File("target/plugins/Artmap/")).mockLogger();
        ArtMap plugin = util.getArtmapMock();

        Lang.load(plugin, new Configuration(plugin));
        when(plugin.getRecipeLoader()).thenReturn(mock(me.Fupery.ArtMap.Recipe.RecipeLoader.class));
        me.Fupery.ArtMap.Recipe.ArtMaterial.setupRecipes();
        when(plugin.getDyePalette()).thenReturn(new me.Fupery.ArtMap.Compatibility.impl.Palette_1_18());

        db = new Database(plugin);
        when(plugin.getArtDatabase()).thenReturn(db);
        defaultConfig = plugin.getConfiguration();

        ProtocolHandler protocol = mock(ProtocolHandler.class);
        when(protocol.getPacketReceiver()).thenReturn(mock(PacketReceiver.class));
        when(plugin.getProtocolManager()).thenReturn(protocol);

        when(plugin.getPreviewManager()).thenReturn(new PreviewManager());
        when(plugin.getArtistHandler()).thenReturn(new ArtistHandler());

        // EaselPart's static initialiser reads seat offsets off the version handler,
        // so it must resolve before any Easel/EaselPart is touched.
        when(plugin.getBukkitVersion())
                .thenReturn(new VersionHandler(BukkitVersion.v1_20_2, util.getPluginMock()));
        when(plugin.getEasels()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        // Compat checks default to "allowed" unless a test says otherwise.
        when(plugin.getCompatManager().checkBuildAllowed(any(), any())).thenReturn(true);
        when(plugin.getCompatManager().checkInteractAllowed(any(), any(), any())).thenReturn(true);

        mocks = util;
    }

    /** Lang.ActionBar sends through player.spigot(), which the shared mocks leave null; call for any messaged player. */
    protected static org.bukkit.entity.Player preparePlayer(org.bukkit.entity.Player player) {
        org.bukkit.entity.Player.Spigot spigot = mock(org.bukkit.entity.Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        return player;
    }

    /** A stand-in ItemStack: new ItemStack(mat) has no craft delegate outside a server, so getType() blows up. */
    protected static org.bukkit.inventory.ItemStack item(org.bukkit.Material material) {
        org.bukkit.inventory.ItemStack stack = mock(org.bukkit.inventory.ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(1);
        return stack;
    }

    /** A player whose inventory really stores items: 36 storage slots, 4 armour slots, off hand at 40. */
    protected static org.bukkit.entity.Player statefulPlayer(String name) {
        org.bukkit.entity.Player p = mock(org.bukkit.entity.Player.class);
        when(p.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(p.getName()).thenReturn(name);
        when(p.spigot()).thenReturn(mock(org.bukkit.entity.Player.Spigot.class));
        when(p.getLocation()).thenAnswer(i -> new org.bukkit.Location(null, 0, 64, 0));
        when(p.getOpenInventory()).thenReturn(mock(org.bukkit.inventory.InventoryView.class));
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[41];
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(inv.getContents()).thenAnswer(i -> contents.clone());
        org.mockito.Mockito.doAnswer(i -> { org.bukkit.inventory.ItemStack[] c = i.getArgument(0);
                System.arraycopy(c, 0, contents, 0, Math.min(c.length, contents.length)); return null; })
                .when(inv).setContents(any());
        when(inv.getStorageContents()).thenAnswer(i -> java.util.Arrays.copyOf(contents, 36));
        org.mockito.Mockito.doAnswer(i -> { org.bukkit.inventory.ItemStack[] c = i.getArgument(0);
                System.arraycopy(c, 0, contents, 0, Math.min(c.length, 36)); return null; })
                .when(inv).setStorageContents(any());
        when(inv.getItem(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> contents[(int) i.getArgument(0)]);
        org.mockito.Mockito.doAnswer(i -> { contents[(int) i.getArgument(0)] = i.getArgument(1); return null; })
                .when(inv).setItem(org.mockito.ArgumentMatchers.anyInt(), any());
        when(inv.getItemInOffHand()).thenAnswer(i -> contents[40]);
        org.mockito.Mockito.doAnswer(i -> { contents[40] = i.getArgument(0); return null; }).when(inv).setItemInOffHand(any());
        when(inv.getItemInMainHand()).thenAnswer(i -> contents[0]);
        org.mockito.Mockito.doAnswer(i -> { contents[0] = i.getArgument(0); return null; }).when(inv).setItemInMainHand(any());
        when(p.getInventory()).thenReturn(inv);
        return p;
    }

    /** Configuration is immutable, so build one with the flags the session and listeners read, and install it. */
    protected static Configuration configWith(boolean forceArtKit, boolean disablePaintbrush) throws Exception {
        return configOverriding(java.util.Map.of("FORCE_ART_KIT", forceArtKit, "DISABLE_PAINTBRUSH", disablePaintbrush));
    }

    /** A copy of the default Configuration with the named fields replaced, installed as the plugin's config. */
    protected static Configuration configOverriding(java.util.Map<String, Object> overrides) throws Exception {
        Configuration cfg = mock(Configuration.class);
        for (java.lang.reflect.Field field : Configuration.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(cfg, overrides.containsKey(field.getName()) ? overrides.get(field.getName()) : field.get(defaultConfig));
        }
        when(ArtMap.instance().getConfiguration()).thenReturn(cfg);
        return cfg;
    }

    protected static void restoreConfig() {
        when(ArtMap.instance().getConfiguration()).thenReturn(defaultConfig);
    }

    /** End a session whose art kit is swapped in. removeKit() calls InventoryView.setCursor, which the plugin
     *  compiles against as a class while the test classpath has the 1.21 interface (Paper rewrites this at
     *  runtime), so the kit is dropped reflectively first. */
    protected static void endKitSessionQuietly(org.bukkit.entity.Player player) throws Exception {
        me.Fupery.ArtMap.Painting.ArtSession session = artists().getCurrentSession(player);
        if (session != null) {
            java.lang.reflect.Field f = me.Fupery.ArtMap.Painting.ArtSession.class.getDeclaredField("inventory");
            f.setAccessible(true);
            f.set(session, null);
        }
        artists().removePlayer(player);
    }

    protected static ArtistHandler artists() {
        return ArtMap.instance().getArtistHandler();
    }

    protected static PreviewManager previews() {
        return ArtMap.instance().getPreviewManager();
    }
}
