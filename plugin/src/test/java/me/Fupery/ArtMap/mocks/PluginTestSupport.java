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

    protected static ArtistHandler artists() {
        return ArtMap.instance().getArtistHandler();
    }

    protected static PreviewManager previews() {
        return ArtMap.instance().getPreviewManager();
    }
}
