package me.Fupery.ArtMap.Menu.Handler;

import java.util.concurrent.ExecutionException;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.API.MenuTemplate;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuPlaceholders;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.api.Config.Lang;

public abstract class CacheableMenu implements MenuTemplate {

    protected String heading;
    protected MenuType type;
    protected Player viewer;
    private Button[] buttons;
    private boolean open = false;

    protected CacheableMenu(String heading, InventoryType type) {
        this(heading, new MenuType(type), false);
    }

    protected CacheableMenu(String heading, MenuType type) {
        this(heading, type, false);
    }

    /**
     * @param fullHeading true when the caller already supplied the complete title, including the
     *                    configured menu header (used by menu/*.yml driven menus).
     */
    protected CacheableMenu(String heading, MenuType type, boolean fullHeading) {
        this.heading = heading == null ? "" : heading;
        this.type = type;
    }

    protected CacheableMenu(String heading, InventoryType type, boolean fullHeading) {
        this(heading, new MenuType(type), fullHeading);
    }

    private Button[] buildButtons() {
        try {
            return getButtons().get();
        } catch (InterruptedException | ExecutionException e) {
            ArtMap.instance().getLogger().warning("Failure loading menu butttons.  Please submit a gitlab issue!");
            return new Button[0];
        }
    }

    private void loadButtons(Inventory inventory, Button[] loaded) {
        buttons = loaded;
        for (int slot = 0; slot < buttons.length && slot < inventory.getSize(); slot++) {
            if (buttons[slot] != null) inventory.setItem(slot, buttons[slot]);
            else inventory.setItem(slot, new ItemStack(Material.AIR));
        }
    }

    Inventory open(Player player) {
        ArtMap.instance();
        this.viewer = player;
        String inventoryTitle = MenuItems.translate(MenuPlaceholders.apply(player, heading));
        if (inventoryTitle != null && inventoryTitle.length() > 32) {
            inventoryTitle = inventoryTitle.substring(0, 32);
        }
        Inventory inventory = this.type.createInventory(player, inventoryTitle);
		ArtMap.instance().getScheduler().ASYNC.run(() -> {
			Button[] loaded = buildButtons();
			ArtMap.instance().getScheduler().SYNC.run(player, () -> {
				loadButtons(inventory, loaded);
				player.openInventory(inventory);
				onMenuOpenEvent(player);
				this.open = true;
			});
        });
        return inventory;
    }

    protected void refresh(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        ArtMap.instance().getScheduler().ASYNC.run(() -> {
			Button[] loaded = buildButtons();
			ArtMap.instance().getScheduler().SYNC.run(player, () -> {
                loadButtons(inventory, loaded);
                player.updateInventory();
                onMenuRefreshEvent(player);
            });
        });
    }

    void click(Player player, int slot, ClickType clickType) {
        if (slot >= 0 && slot < buttons.length && buttons[slot] != null)
            buttons[slot].onClick(player, clickType);
        onMenuClickEvent(player, slot, clickType);
    }

    void close(Player player, MenuCloseReason reason) {
        if (reason.shouldCloseInventory()) player.closeInventory();
        onMenuCloseEvent(player, reason);
        this.open = false;
    }

    boolean isOpen() {
        return open;
    }
}
