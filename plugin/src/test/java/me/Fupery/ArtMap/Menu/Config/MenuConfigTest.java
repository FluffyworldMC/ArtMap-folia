package me.Fupery.ArtMap.Menu.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.MenuTestSupport;

/** The menu/*.yml defaults and the item metadata they can add. */
public class MenuConfigTest extends MenuTestSupport {

    @Test
    public void bundledMenusLoad() {
        MenuDefinition recipes = ArtMap.instance().getMenuConfig().get("recipes");
        assertEquals("recipes", recipes.getId());
        assertNotNull(recipes.getItem("easel"));
        assertEquals(MenuAction.RECIPE, recipes.getItem("easel").getAction());
        assertEquals("EASEL", recipes.getItem("easel").getRecipe());
        assertNotNull(recipes.getItem("back"));
    }

    @Test
    public void configuredTileAndLoreOverrideLangDefaults() {
        MenuDefinition artists = ArtMap.instance().getMenuConfig().get("artists");
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("artist", "Bob");
        ItemStack item = MenuItems.build(artists.getList().getItem(), new ItemStack(Material.PLAYER_HEAD),
                placeholders);
        assertEquals("Bob", item.getItemMeta().getDisplayName());
    }

    @Test
    public void customModelDataAndItemModelAreAccepted() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("slot", 3);
        yaml.set("material", "DIAMOND");
        yaml.set("name", "&aCustom");
        yaml.set("lore", Arrays.asList("one", "two"));
        yaml.set("custom_model_data", 4321);
        yaml.set("item_model", "artmap:menu/custom");
        MenuItemDefinition item = new MenuItemDefinition(yaml);

        ItemStack stack = MenuItems.build(item, new ItemStack(Material.STONE), new HashMap<>());
        assertEquals(Material.DIAMOND, stack.getType());
        assertEquals("§aCustom", stack.getItemMeta().getDisplayName());
        assertEquals(Arrays.asList("one", "two"), stack.getItemMeta().getLore());
        assertTrue(stack.getItemMeta().hasCustomModelData());
        assertEquals(4321, stack.getItemMeta().getCustomModelData());
    }

    @Test
    public void configuredTitleIsUsedInsteadOfTheFallback() {
        MenuDefinition recipes = ArtMap.instance().getMenuConfig().get("recipes");
        String title = recipes.getTitle("fallback", new HashMap<>(), ArtMap.instance().getMenuConfig().getHeader());
        assertEquals("Choose a Recipe", title);
    }

    @Test
    public void displayNameAliasIsAccepted() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("slot", 1);
        yaml.set("material", "STONE");
        yaml.set("display_name", "&aShown");
        MenuItemDefinition item = new MenuItemDefinition(yaml);
        ItemStack stack = MenuItems.build(item, new ItemStack(Material.AIR), new HashMap<>());
        assertEquals("§aShown", stack.getItemMeta().getDisplayName());
    }

    @Test
    public void hexColoursAreTranslated() {
        assertEquals("§x§f§f§0§0§0§0Red", MenuItems.translate("&#ff0000Red"));
        assertEquals("§x§1§2§3§4§5§6Green", MenuItems.translate("&x&1&2&3&4&5&6Green"));
    }

    @Test
    public void menusAreChestSized() {
        MenuDefinition recipes = ArtMap.instance().getMenuConfig().get("recipes");
        assertEquals(9, recipes.resolveType(null).getDefaultSize(), "size: 9 becomes a one-row chest");
        MenuDefinition artkit = ArtMap.instance().getMenuConfig().get("artkit");
        assertEquals(54, artkit.resolveType(null).getDefaultSize());
    }

    @Test
    public void bundledArtistAndArtworkMenusExposeBackAndSortOptions() {
        MenuDefinition artists = ArtMap.instance().getMenuConfig().get("artists");
        assertNotNull(artists.getItem("back"), "artists menu back button comes from YAML");
        assertNotNull(artists.getItem("sort"), "artists menu sort switch comes from YAML");
        MenuListDefinition artistList = artists.getList();
        assertNotNull(artistList);
        assertEquals("name", artistList.getDefaultSort());
        assertTrue(artistList.isPinViewer());
        assertEquals(6, artistList.getSortModes().size(), artistList.getSortModes().keySet().toString());
        assertTrue(artistList.getSortModes().containsKey("newest"));
        assertTrue(artistList.getSortModes().containsKey("oldest"));

        MenuDefinition artworks = ArtMap.instance().getMenuConfig().get("artworks");
        assertNotNull(artworks.getItem("back"));
        assertNotNull(artworks.getItem("sort"));
        MenuListDefinition artworkList = artworks.getList();
        assertNotNull(artworkList);
        assertEquals(4, artworkList.getSortModes().size());
        assertTrue(artworkList.getSortModes().containsKey("title-desc"));
    }

    @Test
    public void commonPaginationProvidesADefaultBackButton() {
        assertNotNull(ArtMap.instance().getMenuConfig().getCommon().getBack());
    }
}
