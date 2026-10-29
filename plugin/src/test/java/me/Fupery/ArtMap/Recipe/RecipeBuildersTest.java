package me.Fupery.ArtMap.Recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.mocks.PluginTestSupport;

/** The recipe and custom item builders: pure ItemStack assembly with no server behind it. */
public class RecipeBuildersTest extends PluginTestSupport {

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
        // NamespacedKey derives its namespace from the plugin name.
        when(ArtMap.instance().getName()).thenReturn("artmap");
    }

    // ---- shaped -------------------------------------------------------------------

    @Test
    @DisplayName("a shaped recipe needs three rows of three and maps its keys onto a 3x3 preview")
    public void shaped() {
        SimpleRecipe.Shaped recipe = new SimpleRecipe.Shaped("shaped_test")
                .shape("sps", "s s", "sps")
                .set('s', Material.STICK)
                .set('p', new Ingredient.WrappedItem(new ItemStack(Material.PAPER, 2)));
        assertEquals("shaped_test", recipe.getName());

        ItemStack[] preview = recipe.getPreview();
        assertEquals(9, preview.length);
        assertEquals(Material.STICK, preview[0].getType());
        assertEquals(Material.PAPER, preview[1].getType());
        assertEquals(2, preview[1].getAmount());
        assertNull(preview[4], "an unmapped key leaves the slot empty");

        Recipe bukkit = recipe.toBukkitRecipe(new ItemStack(Material.ARMOR_STAND));
        assertTrue(bukkit instanceof ShapedRecipe);
        ShapedRecipe shaped = (ShapedRecipe) bukkit;
        assertArrayEquals(new String[] { "sps", "s s", "sps" }, shaped.getShape());
        assertEquals(2, shaped.getIngredientMap().size());
        assertEquals("artmap", shaped.getKey().getNamespace());
    }

    private static void assertArrayEquals(String[] a, String[] b) {
        assertTrue(Arrays.equals(a, b), Arrays.toString(b));
    }

    @Test
    @DisplayName("a malformed shape is rejected up front")
    public void badShape() {
        assertThrows(RuntimeException.class, () -> new SimpleRecipe.Shaped("bad").shape("ab", "cd"));
        assertThrows(RuntimeException.class, () -> new SimpleRecipe.Shaped("bad").shape("abcd", "efg", "hij"));
    }

    // ---- shapeless ----------------------------------------------------------------

    @Test
    @DisplayName("a shapeless recipe lists its ingredients in order, with amounts")
    public void shapeless() {
        SimpleRecipe.Shapeless recipe = new SimpleRecipe.Shapeless("shapeless_test")
                .add(Material.PAPER)
                .add(Material.STICK, -1)
                .add(Material.COAL, -1, 3)
                .add(new Ingredient.WrappedItem(new ItemStack(Material.FEATHER)));

        ItemStack[] preview = recipe.getPreview();
        assertEquals(Material.PAPER, preview[0].getType());
        assertEquals(3, preview[2].getAmount());
        assertEquals(Material.FEATHER, preview[3].getType());
        assertNull(preview[4]);

        Recipe bukkit = recipe.toBukkitRecipe(new ItemStack(Material.MAP));
        assertTrue(bukkit instanceof ShapelessRecipe);
        assertEquals(6, ((ShapelessRecipe) bukkit).getIngredientList().size(), "three coal count three times");
    }

    // ---- custom item --------------------------------------------------------------

    private static ItemMeta metaOf(ItemStack stack) {
        return stack.getItemMeta();
    }

    @Test
    @DisplayName("a custom item writes its key as the first lore line, then name, tooltip, flags and amount")
    public void customItemAssembly() {
        CustomItem item = new CustomItem(Material.FEATHER, "KEY", "Feather", new String[] { "line one", "line two" })
                .amount(4)
                .flag(ItemFlag.HIDE_ATTRIBUTES);
        ItemStack stack = item.toItemStack();
        assertEquals(Material.FEATHER, stack.getType());
        assertEquals(4, stack.getAmount());
        assertEquals(4, item.getAmount());
        assertEquals(Material.FEATHER, item.getMaterial());
        ItemMeta meta = metaOf(stack);
        assertEquals("Feather", meta.getDisplayName());
        assertEquals(List.of("KEY", "line one", "line two"), meta.getLore());
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));

        assertEquals(item, new CustomItem(Material.STONE, "KEY"));
        assertEquals(item.hashCode(), new CustomItem(Material.STONE, "KEY").hashCode());
        assertNotEquals(item, new CustomItem(Material.FEATHER, "OTHER"));
        assertFalse(item.equals("KEY"));
    }

    @Test
    @DisplayName("names and tooltips can come from the language file")
    public void customItemFromLang() {
        CustomItem item = new CustomItem(Material.COAL, "KEY", "tip")
                .name(Lang.RECIPE_EASEL_NAME)
                .tooltip("Tip one", "Tip two");
        ItemMeta meta = metaOf(item.toItemStack());
        assertEquals(Lang.RECIPE_EASEL_NAME.get(), meta.getDisplayName());
        assertEquals(List.of("KEY", "Tip one", "Tip two"), meta.getLore());

        CustomItem renamed = new CustomItem(Material.COAL, "KEY").name("Plain").tooltip("a", "b");
        assertEquals(List.of("KEY", "a", "b"), metaOf(renamed.toItemStack()).getLore());
        CustomItem tipped = new CustomItem(Material.COAL, "KEY", new String[] { "only tip" });
        assertEquals(List.of("KEY", "only tip"), metaOf(tipped.toItemStack()).getLore());
    }

    @Test
    @DisplayName("an item wrapped around an existing stack keeps that stack, and one without meta yields nothing")
    public void customItemAroundStack() {
        ItemStack existing = new ItemStack(Material.SPONGE);
        CustomItem wrapped = new CustomItem(existing, "KEY");
        assertSame(existing, wrapped.toItemStack());
        assertEquals(Material.SPONGE, wrapped.getMaterial());

        ItemStack bare = item(Material.SPONGE);
        when(bare.getItemMeta()).thenReturn(null);
        assertNull(new CustomItem(bare, "KEY").toItemStack());
    }

    @Test
    @DisplayName("checkItem wants the right material and the key on the first lore line")
    public void checkItem() {
        CustomItem item = new CustomItem(Material.FEATHER, "KEY");
        assertFalse(item.checkItem(null));
        assertFalse(item.checkItem(item(Material.STONE)));
        ItemStack noMeta = item(Material.FEATHER);
        assertFalse(item.checkItem(noMeta));

        ItemStack tagged = item(Material.FEATHER);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("KEY", "more"));
        when(tagged.hasItemMeta()).thenReturn(true);
        when(tagged.getItemMeta()).thenReturn(meta);
        assertTrue(item.checkItem(tagged));

        when(meta.getLore()).thenReturn(List.of("OTHER"));
        assertFalse(item.checkItem(tagged));
        when(meta.hasLore()).thenReturn(false);
        assertFalse(item.checkItem(tagged));
    }

    @Test
    @DisplayName("an item with a recipe can build and register it; one without registers nothing")
    public void customItemRecipe() {
        CustomItem plain = new CustomItem(Material.FEATHER, "KEY");
        assertNull(plain.getRecipe());
        plain.addRecipe();
        verify(mocks.getMockServer(), org.mockito.Mockito.never()).addRecipe(any());

        CustomItem crafted = new CustomItem(Material.FEATHER, "KEY")
                .recipe(new SimpleRecipe.Shapeless("feather_test").add(Material.STRING));
        Recipe recipe = crafted.getBukkitRecipe();
        assertNotNull(recipe);
        assertEquals(Material.FEATHER, recipe.getResult().getType());
        crafted.addRecipe();
        verify(mocks.getMockServer()).addRecipe(any(Recipe.class));
    }
}
