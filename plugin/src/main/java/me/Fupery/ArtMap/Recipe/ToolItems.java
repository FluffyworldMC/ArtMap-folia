package me.Fupery.ArtMap.Recipe;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        ItemStack stack = MenuItems.build(item, new ItemStack(fallback), null);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.removeIf(line -> line.contains(ArtItem.KIT_KEY));
            lore.add(0, ArtItem.KIT_KEY);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
