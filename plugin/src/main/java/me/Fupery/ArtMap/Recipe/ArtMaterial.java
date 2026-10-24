package me.Fupery.ArtMap.Recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;

public enum ArtMaterial {

	EASEL, CANVAS, MAP_ART, PAINT_BRUSH, COMPASS, COAL, FEATHER, PAINTBUCKET, SPONGE;

    private CustomItem artItem;

    public static void setupRecipes() {
        setupConfigured(EASEL, Material.OAK_FENCE, "EASEL");
        setupConfigured(CANVAS, Material.PAPER, "CANVAS");
        MAP_ART.artItem = new ArtItem.ArtworkItem(-1, "Artwork", null, null);
        setupConfigured(PAINT_BRUSH, Material.LEATHER_HORSE_ARMOR, "PAINT_BRUSH");
        setupConfigured(COMPASS, Material.COMPASS, null);
        setupConfigured(COAL, Material.COAL, null);
        setupConfigured(FEATHER, Material.FEATHER, null);
        setupConfigured(PAINTBUCKET, Material.BUCKET, null);
        setupConfigured(SPONGE, Material.SPONGE, null);

        for (ArtMaterial material : values()) material.artItem.addRecipe();
    }

    private static void setupConfigured(ArtMaterial material, Material fallback, String recipe) {
        ItemStack stack = ToolItems.build(material, fallback);
        ArtItem.ConfiguredItem item = new ArtItem.ConfiguredItem(stack, ArtItem.KIT_KEY);
        if (recipe != null) {
            try {
                item.recipe(ArtMap.instance().getRecipeLoader().getRecipe(recipe));
            } catch (RecipeLoader.InvalidRecipeException e) {
                ArtMap.instance().getLogger().log(java.util.logging.Level.SEVERE, "Failure!", e);
            }
        }
        material.artItem = item;
    }

    public static ArtMaterial getCraftItemType(ItemStack item) {
        for (ArtMaterial material : values()) {
            if (material.artItem.checkItem(item)) return material;
        }
        return null;
    }

    public static ArtItem.ArtworkItem getMapArt(int id, String title, String playerName, String date) {
        return new ArtItem.ArtworkItem(id, title, playerName, date);
    }

    public Material getType() {
        return artItem.getMaterial();
    }

    public ItemStack getItem() {
        return artItem.toItemStack();
    }

    public boolean isValidMaterial(ItemStack item) {
        return artItem.checkItem(item);
    }

    public Recipe getRecipe() {
        return artItem.getBukkitRecipe();
    }

    public ItemStack[] getPreview() {
        // A recipe that failed to load (see CraftableItem) leaves null here; show an empty grid, not an NPE.
        return artItem.getRecipe() == null ? new ItemStack[9] : artItem.getRecipe().getPreview();
    }
}
