package me.Fupery.ArtMap.Menu.Config;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.API.BasicMenu;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;

/** A static menu rendered entirely from menu/&lt;id&gt;.yml. */
public class ConfiguredMenu extends BasicMenu implements ChildMenu {
    private final MenuDefinition definition;

    public ConfiguredMenu(String id) {
        this(ArtMap.instance().getMenuConfig().get(id));
    }

    public ConfiguredMenu(MenuDefinition definition) {
        super(definition.getTitle("", null, ArtMap.instance().getMenuConfig().getHeader()),
                definition.resolveType(new MenuType(InventoryType.CHEST)), true);
        this.definition = definition;
    }

    @Override
    public Future<Button[]> getButtons() {
        int size = Math.max(9, type.getDefaultSize());
        Button[] buttons = new Button[size];
        for (MenuItemDefinition item : definition.getItems()) {
            if (item.getSlot() < 0 || item.getSlot() >= size) continue;
            Button button = buildButton(item);
            if (button != null) buttons[item.getSlot()] = button;
        }
        return CompletableFuture.completedFuture(buttons);
    }

    private Button buildButton(MenuItemDefinition item) {
        return MenuButtons.build(item, viewer, null);
    }

    @Override
    public CacheableMenu getParent(Player viewer) {
        String parent = definition.getParent();
        return parent != null ? ArtMap.instance().getMenuHandler().createMenu(parent, viewer) : null;
    }

    public MenuDefinition getDefinition() {
        return definition;
    }

    public Map<Integer, MenuItemDefinition> getConfiguredItems() {
        return definition.getItemsBySlot();
    }
}
