package me.Fupery.ArtMap.Menu.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class MenuItemDefinition {
    private final int slot;
    private final String material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final String artItem;
    private final String itemModel;
    private final Integer customModelData;
    private final String customModelDataRaw;
    private final MenuAction action;
    private final String targetMenu;
    private final String recipe;
    private final String tool;
    private final String permission;
    private final String condition;
    private final boolean hideAttributes;

    MenuItemDefinition(ConfigurationSection section) {
        this.slot = section != null ? section.getInt("slot", -1) : -1;
        this.material = section != null ? section.getString("material") : null;
        this.amount = section != null ? section.getInt("amount", 1) : 1;
        String configuredName = section != null ? section.getString("name") : null;
        if (configuredName == null && section != null) {
            configuredName = section.getString("display_name");
        }
        this.name = configuredName;
        this.lore = section != null ? new ArrayList<>(section.getStringList("lore")) : Collections.emptyList();
        this.artItem = section != null ? section.getString("art_item") : null;
        String configuredItemModel = section != null ? section.getString("item_model") : null;
        if (configuredItemModel == null && section != null) {
            configuredItemModel = section.getString("item-model");
        }
        this.itemModel = configuredItemModel;
        this.customModelDataRaw = section != null ? section.getString("custom_model_data") : null;
        this.customModelData = section != null && section.isInt("custom_model_data")
                ? section.getInt("custom_model_data") : null;
        this.action = MenuAction.parse(section != null ? section.getString("action") : null);
        this.targetMenu = section != null ? section.getString("menu") : null;
        this.recipe = section != null ? section.getString("recipe") : null;
        this.tool = section != null ? section.getString("tool") : null;
        this.permission = section != null ? section.getString("permission") : null;
        this.condition = section != null ? section.getString("condition") : null;
        this.hideAttributes = section != null && section.getBoolean("hide_attributes", false);
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial(Material fallback) {
        Material parsed = MenuItems.matchMaterial(material);
        return parsed != null ? parsed : fallback;
    }

    public int getAmount() {
        return amount;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getArtItem() {
        return artItem;
    }

    public String getItemModel() {
        return itemModel;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getCustomModelDataRaw() {
        return customModelDataRaw;
    }

    public String getTool() {
        return tool;
    }

    public MenuAction getAction() {
        return action;
    }

    public String getTargetMenu() {
        return targetMenu;
    }

    public String getRecipe() {
        return recipe;
    }

    public String getPermission() {
        return permission;
    }

    public String getCondition() {
        return condition;
    }

    public boolean isHideAttributes() {
        return hideAttributes;
    }

    /** Applies the configured name, lore and model data to an already built item. */
    public ItemStack apply(ItemStack stack, Map<String, String> placeholders) {
        return MenuItems.apply(stack, this, placeholders);
    }
}
