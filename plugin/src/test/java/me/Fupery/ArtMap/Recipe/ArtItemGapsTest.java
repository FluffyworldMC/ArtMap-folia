package me.Fupery.ArtMap.Recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.YamlReader;
import me.Fupery.ArtMap.Utils.ItemUtils;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Leftover item, material and resource-loading branches. */
public class ArtItemGapsTest extends PluginTestSupport {

    private final List<LogRecord> logged = new java.util.ArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord r) { logged.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void listen() {
        ArtMap.instance().getLogger().addHandler(capture);
    }

    @AfterEach
    public void restore() {
        ArtMap.instance().getLogger().removeHandler(capture);
        restoreConfig();
    }

    @Test
    @DisplayName("a copy artwork item is a tagged filled map carrying the original's title and artist")
    public void copyArtworkItem() {
        ItemStack copy = new ArtItem.CopyArtworkItem(5, "Sunset", "Alice", "01-01-2026").toItemStack();
        assertEquals(Material.FILLED_MAP, copy.getType());
        assertTrue(ArtItem.isCopyArtwork(copy));
        assertTrue(ArtItem.isArtwork(copy));
        assertEquals("Sunset", copy.getItemMeta().getDisplayName());
    }

    @Test
    @DisplayName("a craftable item whose recipe fails to load is logged and left without one")
    public void craftableWithBadRecipe() throws Exception {
        RecipeLoader loader = ArtMap.instance().getRecipeLoader();
        java.lang.reflect.Constructor<RecipeLoader.InvalidRecipeException> ctor =
                RecipeLoader.InvalidRecipeException.class.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        when(loader.getRecipe("BROKEN")).thenThrow(ctor.newInstance("BROKEN", "bad"));
        ArtItem.CraftableItem item = new ArtItem.CraftableItem("broken", Material.STICK, "key");
        assertNull(item.getRecipe());
        assertNull(item.getBukkitRecipe(), "no recipe, no Bukkit recipe, no NPE");
        assertTrue(logged.stream().anyMatch(r -> r.getThrown() instanceof RecipeLoader.InvalidRecipeException));
    }

    @Test
    @DisplayName("art materials expose their type and build artwork items")
    public void artMaterialAccessors() {
        assertEquals(Material.ARMOR_STAND, ArtMaterial.EASEL.getType());
        assertEquals(Material.PAPER, ArtMaterial.CANVAS.getType());
        assertEquals(Material.FILLED_MAP, ArtMaterial.getMapArt(1, "T", "P", "D").toItemStack().getType());
        // The mocked loader gives the materials no recipe, exactly the state a bad recipe.yml leaves behind.
        assertNull(ArtMaterial.CANVAS.getRecipe());
        assertEquals(9, ArtMaterial.CANVAS.getPreview().length);
        assertNull(ArtMaterial.CANVAS.getPreview()[0]);
    }

    @Test
    @DisplayName("with custom recipes off the loader reads the bundled recipe file")
    public void recipesFromResources() throws Exception {
        doReturn(new FileReader(new File("target/classes/recipe.yml"))).when(ArtMap.instance()).getTextResourceFile("recipe.yml");
        Configuration cfg = configOverriding(Map.of("CUSTOM_RECIPES", false));
        RecipeLoader loader = new RecipeLoader(ArtMap.instance(), cfg);
        assertNotNull(loader.getRecipe("EASEL"));
    }

    @Test
    @DisplayName("the yaml reader reads bundled resources, and the data folder only when the file exists")
    public void yamlReader() {
        YamlReader reader = new YamlReader(ArtMap.instance(), "lang.yml");
        assertTrue(reader.readFromResources().contains("NO_PERM"));
        assertNull(new YamlReader("nothere.yml").readFromDataFolder());
        assertTrue(reader.tryDataFolder().contains("NO_PERM"));
    }

    @Test
    @DisplayName("the static utils class can still be extended")
    public void utilsConstructor() {
        assertNotNull(new ItemUtils() { });
    }
}
