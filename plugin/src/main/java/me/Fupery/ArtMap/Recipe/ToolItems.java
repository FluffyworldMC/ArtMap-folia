package me.Fupery.ArtMap.Recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;

public final class ToolItems {

    private ToolItems() {
    }

    public static ItemStack build(ArtMaterial material, Material fallback) {
        MenuDefinition definition = ArtMap.instance().getMenuConfig().get("tool-items");
        MenuItemDefinition item = definition.getItem(material.name());
        return MenuItems.build(item, new ItemStack(fallback), null);
    }
}
