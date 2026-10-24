package me.Fupery.ArtMap.Menu.HelpMenu;

import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.API.BasicMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Button.StaticButton;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Recipe.ArtMaterial;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class RecipePreview extends BasicMenu {

    private static final String MENU_ID = "recipe-preview";
    private final ArtMaterial recipe;

    public RecipePreview(ArtMaterial recipe) {
        super(title(recipe), definition().resolveType(new MenuType(InventoryType.DISPENSER)), true);
        this.recipe = recipe;
    }

    private static MenuDefinition definition() {
        return ArtMap.instance().getMenuConfig().get(MENU_ID);
    }

    private static String title(ArtMaterial recipe) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recipe", recipe.name().toLowerCase());
        return definition().getTitle("Recipe for " + recipe.name().toLowerCase(),
                placeholders, ArtMap.instance().getMenuConfig().getHeader());
    }

    @Override
    public void onMenuOpenEvent(Player viewer) {
        viewer.updateInventory();
    }

    @Override
    public Future<Button[]> getButtons() {
        FutureTask<Button[]> task = new FutureTask<> (()->{
            MenuDefinition definition = definition();
            MenuItemDefinition template = definition.getList() != null ? definition.getList().getItem() : null;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("recipe", recipe.name().toLowerCase());
            ItemStack[] preview = recipe.getPreview();
            Button[] buttons = new Button[preview.length];

            for (int i = 0; i < preview.length; i++) {
                if (preview[i] != null) {
                    StaticButton button = new StaticButton(preview[i]);
                    MenuItems.apply(button, template, placeholders);
                    buttons[i] = button;
                }
            }
            return buttons;
        });
        ArtMap.instance().getScheduler().ASYNC.run(task);
        return task;
    }
}
