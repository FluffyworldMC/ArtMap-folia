package me.Fupery.ArtMap.Painting.Artkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.API.BasicMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.ActionButton;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuAction;
import me.Fupery.ArtMap.Menu.Config.MenuCommon;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuListDefinition;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Painting.ArtSession;

public class ArtKitMenu extends BasicMenu {
    private final Player viewer;
    private int page;
    private final MenuDefinition definition;

    public ArtKitMenu(Player viewer, int page) {
        super(title(page), definition().resolveType(new MenuType(54)), true);
        this.viewer = viewer;
        this.page = Math.max(0, page);
        this.definition = definition();
        ArtSession session = ArtMap.instance().getArtistHandler().getCurrentSession(viewer);
        if (session != null) {
            ArtkitItems.updateBrush(viewer, session.getArtkit());
        }
    }

    private static MenuDefinition definition() {
        return ArtMap.instance().getMenuConfig().get("artkit");
    }

    private static String title(int page) {
        return definition().getTitle("ArtKit", null, ArtMap.instance().getMenuConfig().getHeader());
    }

    @Override
    public Future<Button[]> getButtons() {
        MenuType type = definition.resolveType(new MenuType(54));
        int size = Math.max(9, type.getDefaultSize());
        Button[] buttons = new Button[size];
        ArtSession session = ArtMap.instance().getArtistHandler().getCurrentSession(viewer);
        if (session == null) return CompletableFuture.completedFuture(buttons);
        ArtkitState state = session.getArtkit();

        for (MenuItemDefinition item : definition.getItems()) {
            if (item.getSlot() < 0 || item.getSlot() >= size) continue;
            Button button = toolButton(item, session, state);
            if (button != null) buttons[item.getSlot()] = button;
        }

        MenuListDefinition list = definition.getList();
        int start = list != null ? list.getStartSlot() : 9;
        int pageSize = list != null && list.getPageSize() > 0 ? list.getPageSize() : 45;
        int previousSlot = list != null ? list.getPreviousSlot() : 45;
        int nextSlot = list != null ? list.getNextSlot() : 53;
        MenuItemDefinition template = list != null ? list.getItem() : null;
        List<ArtkitColor> colors = ArtkitPalette.colors();
        int lastPage = Math.max(0, (colors.size() - 1) / pageSize);
        int current = Math.min(page, lastPage);
        int startIndex = current * pageSize;
        for (int i = 0; i < pageSize && startIndex + i < colors.size(); i++) {
            ArtkitColor color = colors.get(startIndex + i);
            Map<String, String> placeholders = ArtkitItems.placeholders(state);
            placeholders.put("hex", color.getHex());
            placeholders.put("hex_code", "#" + color.getHex());
            placeholders.put("color_index", String.valueOf(color.getIndex()));
            placeholders.put("color_name", color.getName());
            placeholders.put("base", String.valueOf(color.getBase()));
            placeholders.put("shade", String.valueOf(color.getShade()));
            placeholders.put("shade_name", color.getShadeName());
            ItemStack stack = MenuItems.build(template, new ItemStack(Material.LEATHER_HORSE_ARMOR), placeholders);
            if (stack.getType() != Material.LEATHER_HORSE_ARMOR) {
                ItemStack leather = new ItemStack(Material.LEATHER_HORSE_ARMOR);
                leather.setItemMeta(stack.getItemMeta());
                MenuItems.copyDyedColor(stack, leather);
                stack = leather;
            }
            int slot = start + i;
            if (slot >= size) break;
            buttons[slot] = new ActionButton(stack, (player, click) -> {
                ArtSession active = ArtMap.instance().getArtistHandler().getCurrentSession(player);
                if (active == null) return;
                ArtkitTool tool = active.getArtkit().getTool();
                active.getArtkit().setColor(color);
                if (!tool.isDrawingTool()) {
                    active.getArtkit().setTool(ArtkitTool.DYE);
                }
                ArtkitItems.updateBrush(player, active.getArtkit());
                ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);
            });
        }

        MenuCommon common = ArtMap.instance().getMenuConfig().getCommon();
        buttons[previousSlot] = pageButton(MenuListDefinition.resolvePageItem(list, common, false, current > 0),
                current > 0, current - 1, current, lastPage);
        buttons[nextSlot] = pageButton(MenuListDefinition.resolvePageItem(list, common, true, current < lastPage),
                current < lastPage, current + 1, current, lastPage);
        return CompletableFuture.completedFuture(buttons);
    }

    private Button toolButton(MenuItemDefinition item, ArtSession session, ArtkitState state) {
        Map<String, String> placeholders = ArtkitItems.placeholders(state);
        ItemStack stack = MenuItems.build(item, new ItemStack(Material.STONE), placeholders);
        MenuAction action = item.getAction();
        if (action == MenuAction.TOOL) {
            ArtkitTool tool = ArtkitTool.parse(item.getTool(), state.getTool());
            return new ActionButton(stack, (player, click) -> {
                ArtSession active = ArtMap.instance().getArtistHandler().getCurrentSession(player);
                if (active == null) return;
                active.getArtkit().setTool(tool);
                ArtkitItems.updateBrush(player, active.getArtkit());
                ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);
            });
        }
        if (action == MenuAction.SAVE) {
            return new ActionButton(stack, (player, click) -> {
                ArtSession active = ArtMap.instance().getArtistHandler().getCurrentSession(player);
                if (active == null) return;
                ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);
                ArtMap.instance().getArtistHandler().openSaveDialog(player, active);
            });
        }
        if (action == MenuAction.CLOSE) {
            return new ActionButton(stack, (player, click) ->
                    ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE));
        }
        return new ActionButton(stack, (player, click) -> { });
    }

    private Button pageButton(MenuItemDefinition item, boolean hasPage, int target, int current, int lastPage) {
        Map<String, String> placeholders = ArtkitItems.placeholders(
                ArtMap.instance().getArtistHandler().getCurrentSession(viewer).getArtkit());
        placeholders.put("page", String.valueOf(current + 1));
        placeholders.put("pages", String.valueOf(lastPage + 1));
        placeholders.put("target_page", String.valueOf(target + 1));
        ItemStack stack = MenuItems.build(item, new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA), placeholders);
        if (!hasPage) return new ActionButton(stack, (player, click) -> { });
        return new ActionButton(stack, (player, click) -> refresh(player, target));
    }

    private void refresh(Player player, int target) {
        this.page = Math.max(0, target);
        ArtMap.instance().getMenuHandler().refreshMenu(player);
    }
}
