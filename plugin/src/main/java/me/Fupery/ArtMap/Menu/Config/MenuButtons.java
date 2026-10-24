package me.Fupery.ArtMap.Menu.Config;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.Button.ActionButton;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;

/** Shared factory for the built-in configured actions. */
public final class MenuButtons {

    private MenuButtons() {
    }

    public static boolean isAllowed(MenuItemDefinition item, Player player) {
        return item.getPermission() == null || player.hasPermission(item.getPermission());
    }

    public static Button build(MenuItemDefinition item, Player player, Map<String, String> placeholders) {
        return build(item, new ItemStack(Material.STONE), player, placeholders);
    }

    public static Button build(MenuItemDefinition item, ItemStack fallback, Player player,
                               Map<String, String> placeholders) {
        if (item == null || !isAllowed(item, player)) return null;
        ItemStack stack = MenuItems.build(item, fallback, placeholders);
        switch (item.getAction()) {
            case OPEN:
                if (item.getTargetMenu() == null) return null;
                return new ActionButton(stack, (viewer, click) ->
                        ArtMap.instance().getMenuHandler().openMenuById(viewer, item.getTargetMenu()));
            case BACK:
                return new ActionButton(stack, (viewer, click) ->
                        ArtMap.instance().getMenuHandler().closeMenu(viewer, MenuCloseReason.BACK));
            case CLOSE:
                return new ActionButton(stack, (viewer, click) ->
                        ArtMap.instance().getMenuHandler().closeMenu(viewer, MenuCloseReason.DONE));
            case NONE:
            case PREVIOUS_PAGE:
            case NEXT_PAGE:
            case RECIPE:
            default:
                return new ActionButton(stack, (viewer, click) -> { });
        }
    }
}
