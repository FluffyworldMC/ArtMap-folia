package me.Fupery.ArtMap.Recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Configuration;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** Parsing recipe.yml: the shapes people get wrong must produce a clear error, never a stack trace. */
public class RecipeLoaderTest extends PluginTestSupport {

    private static RecipeLoader loader;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        File folder = ArtMap.instance().getDataFolder();
        folder.mkdirs();
        Files.write(new File(folder, "recipe.yml").toPath(), String.join("\n",
                "GOOD_SHAPED:",
                "  MATERIALS:",
                "    s: STICK",
                "    r: {MATERIAL: REDSTONE, AMOUNT: 3}",
                "  SHAPE:",
                "    - \"sss\"",
                "    - \"srs\"",
                "    - \"sss\"",
                "GOOD_SHAPELESS:",
                "  MATERIALS:",
                "    s: STICK",
                "    r: REDSTONE",
                "TOO_FEW:",
                "  MATERIALS:",
                "    s: STICK",
                "NO_MATERIALS:",
                "  SHAPE:",
                "    - \"sss\"",
                "BAD_SHAPE_LINES:",
                "  MATERIALS: {s: STICK, r: REDSTONE}",
                "  SHAPE: [\"ss\", \"ss\"]",
                "BAD_SHAPE_WIDTH:",
                "  MATERIALS: {s: STICK, r: REDSTONE}",
                "  SHAPE: [\"ss\", \"sss\", \"sss\"]",
                "LONG_KEY:",
                "  MATERIALS: {ab: STICK, r: REDSTONE}",
                "BAD_MATERIAL:",
                "  MATERIALS: {s: UNOBTAINIUM, r: REDSTONE}",
                "").getBytes(StandardCharsets.UTF_8));
        // Custom recipes are read from the data folder; the flag is final, so set it on a mock.
        Configuration cfg = mock(Configuration.class);
        Field f = Configuration.class.getDeclaredField("CUSTOM_RECIPES");
        f.setAccessible(true);
        f.set(cfg, true);
        loader = new RecipeLoader(ArtMap.instance(), cfg);
    }

    @SuppressWarnings("unchecked")
    private static Map<Character, Ingredient> items(SimpleRecipe.Shaped shaped) throws Exception {
        Field f = SimpleRecipe.Shaped.class.getDeclaredField("items");
        f.setAccessible(true);
        return (Map<Character, Ingredient>) f.get(shaped);
    }

    @Test
    @DisplayName("an unknown recipe name is simply absent")
    public void unknownRecipeIsNull() throws Exception {
        assertNull(loader.getRecipe("NOPE"));
    }

    @Test
    @DisplayName("a shaped recipe keeps its shape and per-material amounts")
    public void shapedRecipeParses() throws Exception {
        SimpleRecipe recipe = loader.getRecipe("GOOD_SHAPED");
        assertTrue(recipe instanceof SimpleRecipe.Shaped);
        Map<Character, Ingredient> items = items((SimpleRecipe.Shaped) recipe);
        assertEquals(Material.STICK, items.get('s').getMaterial());
        assertEquals(1, items.get('s').getAmount());
        assertEquals(Material.REDSTONE, items.get('r').getMaterial());
        assertEquals(3, items.get('r').getAmount());
    }

    @Test
    @DisplayName("a recipe without a SHAPE section is shapeless")
    public void shapelessRecipeParses() throws Exception {
        assertTrue(loader.getRecipe("GOOD_SHAPELESS") instanceof SimpleRecipe.Shapeless);
    }

    @Test
    @DisplayName("malformed recipes are rejected with a message naming the recipe")
    public void malformedRecipesAreRejected() {
        for (String[] bad : new String[][] {
                { "TOO_FEW", "less than two materials" },
                { "NO_MATERIALS", "less than two materials" },
                { "BAD_SHAPE_LINES", "3 lines" },
                { "BAD_SHAPE_WIDTH", "3 characters" },
                { "LONG_KEY", "not a valid material key" },
                { "BAD_MATERIAL", "not a valid material" } }) {
            RecipeLoader.InvalidRecipeException e = assertThrows(RecipeLoader.InvalidRecipeException.class,
                    () -> loader.getRecipe(bad[0]), bad[0]);
            assertTrue(e.getMessage().contains(bad[0]), e.getMessage());
            assertTrue(e.getMessage().contains(bad[1]), e.getMessage());
        }
    }

    @Test
    @DisplayName("unloading removes only the recipes whose result is an ArtMap item")
    public void unloadRemovesOnlyArtMapRecipes() {
        Recipe easel = mock(Recipe.class);
        when(easel.getResult()).thenReturn(ArtMaterial.EASEL.getItem());
        Recipe stone = mock(Recipe.class);
        org.bukkit.inventory.ItemStack stoneStack = item(Material.STONE);
        when(stone.getResult()).thenReturn(stoneStack);
        List<Recipe> registered = new ArrayList<>(List.of(easel, stone));
        when(mocks.getMockServer().recipeIterator()).thenAnswer(i -> registered.iterator());

        loader.loadRecipes();
        loader.unloadRecipes();

        assertEquals(List.of(stone), registered);
    }
}
