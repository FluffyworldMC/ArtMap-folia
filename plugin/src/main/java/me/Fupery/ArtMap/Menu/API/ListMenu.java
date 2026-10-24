package me.Fupery.ArtMap.Menu.API;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Menu.Button.ActionButton;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Button.CloseButton;
import me.Fupery.ArtMap.Menu.Button.LinkedButton;
import me.Fupery.ArtMap.Menu.Config.MenuCommon;
import me.Fupery.ArtMap.Menu.Config.MenuConfigManager;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuButtons;
import me.Fupery.ArtMap.Menu.Config.MenuListDefinition;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Event.MenuFactory;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;

public abstract class ListMenu extends CacheableMenu {

    private static final char LEFT_ARROW = '\u2B05';
    private static final char RIGHT_ARROW = '\u27A1';
    private static final int DEFAULT_PAGE_SIZE = 25;

    protected int page;
    protected List<Button> cachedButtons;
    protected Optional<MenuFactory> parent = Optional.empty();

    protected ListMenu(String heading, int page) {
        super(heading, InventoryType.CHEST);
        this.page = page;
        this.cachedButtons = null;
    }

    protected ListMenu(String heading, MenuFactory parent, int page) {
        super(heading, InventoryType.CHEST);
        this.page = page;
        this.cachedButtons = null;
        this.parent = Optional.of(parent);
    }

    protected ListMenu(String heading, MenuType type, int page, boolean fullHeading) {
        super(heading, type, fullHeading);
        this.page = page;
        this.cachedButtons = null;
    }

    protected ListMenu(String heading, MenuType type, MenuFactory parent, int page, boolean fullHeading) {
        super(heading, type, fullHeading);
        this.page = page;
        this.cachedButtons = null;
        this.parent = Optional.of(parent);
    }

    /** The menu/*.yml id backing this list. */
    protected String getMenuId() {
        return null;
    }

    public String getHeading() {
        return this.heading;
    }

    @Override
    public void onMenuOpenEvent(Player viewer) {
    }

    @Override
    public void onMenuRefreshEvent(Player viewer) {
    }

    @Override
    public void onMenuClickEvent(Player viewer, int slot, ClickType click) {
    }

    @Override
    public void onMenuCloseEvent(Player viewer, MenuCloseReason reason) {
    }

    @Override
    public Future<Button[]> getButtons() {
        ArtMap artMap = ArtMap.instance();
        MenuConfigManager manager = artMap != null ? artMap.getMenuConfig() : null;
        MenuDefinition definition = manager != null && getMenuId() != null
                ? manager.get(getMenuId()) : MenuDefinition.empty(getMenuId());
        MenuListDefinition list = definition.getList();
        int start = list != null ? list.getStartSlot() : 1;
        int pageSize = list != null && list.getPageSize() > 0 ? list.getPageSize() : DEFAULT_PAGE_SIZE;
        int previousSlot = list != null ? list.getPreviousSlot() : 0;
        int nextSlot = list != null ? list.getNextSlot() : 26;
        int size = definition.resolveType(new MenuType(InventoryType.CHEST)).getDefaultSize();
        if (size <= 0) size = 27;

        Button[] buttons = new Button[size];
        Map<String, String> placeholders = basePlaceholders();

        // Frame items configured under "items".
        for (MenuItemDefinition item : definition.getItems()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= size) continue;
            Button button = MenuButtons.build(item, viewer, placeholders);
            if (button != null) buttons[slot] = button;
        }

        List<Button> listItems;
        try {
            listItems = getListItems().get();
        } catch (InterruptedException | ExecutionException e) {
            ArtMap.instance().getLogger().log(Level.SEVERE, "Interrupted creating menu buttons!", e);
            return CompletableFuture.completedFuture(buttons);
        }
        cachedButtons = listItems;

        int lastPage = lastPage(listItems.size(), pageSize);
        page = Math.max(0, Math.min(page, lastPage));
        int startIndex = page * pageSize;
        int placed = 0;
        for (int i = startIndex; i < listItems.size() && placed < pageSize; i++) {
            int slot = start + placed;
            while (slot < size && buttons[slot] != null && slot != previousSlot && slot != nextSlot) {
                slot++;
            }
            if (slot >= size) break;
            Button item = listItems.get(i);
            if (item != null) {
                buttons[slot] = decorateListItem(item, i, placeholders);
            }
            placed++;
        }

        placePageButton(buttons, previousSlot, size, page > 0, false, page - 1,
                lastPage, placeholders);
        if (page == 0) {
            if (parent.isPresent()) {
                buttons[previousSlot] = new LinkedButton(parent.get(), Material.MAGENTA_GLAZED_TERRACOTTA,
                        new String[]{ChatColor.RED.toString() + ChatColor.BOLD + LEFT_ARROW});
            } else {
                buttons[previousSlot] = new CloseButton();
            }
        }
        placePageButton(buttons, nextSlot, size, page < lastPage, true, page + 1,
                lastPage, placeholders);

        return CompletableFuture.completedFuture(buttons);
    }

    /** Subclasses may override to decorate generated list entries with config values. */
    protected Button decorateListItem(Button item, int index, Map<String, String> placeholders) {
        return item;
    }

    protected Map<String, String> basePlaceholders() {
        return new HashMap<>();
    }

    private void placePageButton(Button[] buttons, int slot, int size, boolean enabled,
                                 boolean forward, int targetPage, int lastPage,
                                 Map<String, String> placeholders) {
        if (slot < 0 || slot >= size) return;
        ArtMap artMap = ArtMap.instance();
        MenuCommon common = artMap != null && artMap.getMenuConfig() != null
                ? artMap.getMenuConfig().getCommon() : MenuCommon.defaults();
        MenuItemDefinition item = forward
                ? (enabled ? common.getNext() : common.getNextDisabled())
                : (enabled ? common.getPrevious() : common.getPreviousDisabled());
        if (item == null) {
            if (enabled) {
                ItemStack stack = new ItemStack(forward ? Material.MAGENTA_GLAZED_TERRACOTTA : Material.BARRIER);
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    String arrow = forward ? "\u27A1" : "\u2B05";
                    meta.setDisplayName(ChatColor.GREEN + arrow + " " + Lang.SEARCH_PAGE.get() + " "
                            + (targetPage + 1) + Lang.SEARCH_PAGE_SEPERATOR.get() + (lastPage + 1));
                    meta.setLore(Collections.singletonList(String.format(
                            Lang.MENU_PAGE_JUMP.get() != null ? Lang.MENU_PAGE_JUMP.get() : "Shift-click to jump %s pages", 10)));
                    stack.setItemMeta(meta);
                }
                stack.setAmount(Math.max(1, Math.min(targetPage + 1, stack.getMaxStackSize())));
                buttons[slot] = new ActionButton(stack, (player, click) ->
                        changePage(player, forward, click != null && click.isShiftClick() ? 10 : 1));
            }
            return;
        }
        Map<String, String> pagePlaceholders = new HashMap<>(placeholders);
        pagePlaceholders.put("page", String.valueOf(page + 1));
        pagePlaceholders.put("pages", String.valueOf(lastPage + 1));
        pagePlaceholders.put("target_page", String.valueOf(targetPage + 1));
        ItemStack stack = MenuItems.build(item, new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA), pagePlaceholders);
        buttons[slot] = enabled
                ? new ActionButton(stack, (player, click) ->
                        changePage(player, forward, click != null && click.isShiftClick() ? 10 : 1))
                : new ActionButton(stack, (player, click) -> { });
    }

    private int lastPage(int itemCount, int pageSize) {
        if (itemCount <= 0) return 0;
        return (itemCount - 1) / Math.max(1, pageSize);
    }

    protected void changePage(Player player, boolean forward) {
        changePage(player, forward, 1);
    }

    protected void changePage(Player player, boolean forward, int pages) {
        int total = cachedButtons != null ? cachedButtons.size() : 0;
        int pageSize = DEFAULT_PAGE_SIZE;
        ArtMap artMap = ArtMap.instance();
        MenuConfigManager manager = artMap != null ? artMap.getMenuConfig() : null;
        MenuDefinition definition = manager != null && getMenuId() != null
                ? manager.get(getMenuId()) : MenuDefinition.empty(getMenuId());
        MenuListDefinition list = definition.getList();
        if (list != null && list.getPageSize() > 0) pageSize = list.getPageSize();
        int last = lastPage(total, pageSize);
        int target = forward ? page + pages : page - pages;
        page = Math.max(0, Math.min(target, last));
        refresh(player);
    }

    protected abstract Future<List<Button>> getListItems();
}
