package me.Fupery.ArtMap.Menu.HelpMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Menu.API.BasicMenu;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuButtons;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Recipe.ArtMaterial;
import me.Fupery.ArtMap.Utils.ItemUtils;

public class RecipeMenu extends BasicMenu implements ChildMenu {

    private static final String MENU_ID = "recipes";
    private boolean adminMenu;

    public RecipeMenu(boolean adminMenu) {
        super(definition().getTitle(Lang.MenuTitle.MENU_RECIPE.get(), null, ArtMap.instance().getMenuConfig().getHeader()),
                definition().resolveType(new MenuType(InventoryType.CHEST)), true);
        this.adminMenu = adminMenu;
    }

    private static MenuDefinition definition() {
        return ArtMap.instance().getMenuConfig().get(MENU_ID);
    }

    @Override
    public Future<Button[]> getButtons() {
        MenuDefinition definition = definition();
        int size = Math.max(9, definition.resolveType(new MenuType(InventoryType.CHEST)).getDefaultSize());
        Button[] buttons = new Button[size];
        for (MenuItemDefinition item : definition.getItems()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= size) continue;
            if (item.getAction() == me.Fupery.ArtMap.Menu.Config.MenuAction.RECIPE) {
                ArtMaterial material = material(item.getRecipe());
                if (material != null) {
                    if (material == ArtMaterial.PAINT_BRUSH
                            && ArtMap.instance().getConfiguration().DISABLE_PAINTBRUSH) {
                        continue;
                    }
                    buttons[slot] = new RecipeButton(material, item);
                }
            } else {
                buttons[slot] = MenuButtons.build(item, viewer, null);
            }
        }
        return CompletableFuture.completedFuture(buttons);
    }

    private static ArtMaterial material(String name) {
        if (name == null) return null;
        try {
            return ArtMaterial.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CacheableMenu getParent(Player viewer) {
        return null;
    }

    private class RecipeButton extends Button {

        final ArtMaterial recipe;

        RecipeButton(ArtMaterial material, MenuItemDefinition item) {
            super(material.getType());
            this.recipe = material;
            ItemMeta meta = material.getItem().getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            meta.setLore(lore);
            setItemMeta(meta);
            MenuItems.applyInPlace(this, item, null);
        }

        @Override
        public void onClick(Player player, ClickType clickType) {
            if (adminMenu) {
                if (clickType == ClickType.LEFT) {
                    openRecipePreview(player);
                } else if (clickType == ClickType.RIGHT) {
                    ArtMap.instance().getScheduler().SYNC.run(player, () -> ItemUtils.giveItem(player, recipe.getItem()));
                }
            } else {
                openRecipePreview(player);
            }
        }

        private void openRecipePreview(Player player) {
            ArtMap.instance().getMenuHandler().openMenu(player, new RecipePreview(recipe));
        }
    }
}
