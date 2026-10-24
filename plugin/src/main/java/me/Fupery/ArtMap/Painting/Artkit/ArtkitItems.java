package me.Fupery.ArtMap.Painting.Artkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Recipe.ArtMaterial;

public final class ArtkitItems {
    public static final String COLOR_KEY = "Artkit:Color:";
    public static final String TOOL_KEY = "Artkit:Tool:";

    private ArtkitItems() {
    }

    public static boolean isBrush(ItemStack item) {
        return ArtMaterial.getCraftItemType(item) == ArtMaterial.PAINT_BRUSH;
    }

    public static ArtkitState read(ItemStack item) {
        ArtkitState state = new ArtkitState();
        if (item == null || !item.hasItemMeta()) return state;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return state;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Integer index = data.get(colorKey(), PersistentDataType.INTEGER);
        if (index != null) {
            state.setColor(ArtkitPalette.get(index));
        }
        String tool = data.get(toolKey(), PersistentDataType.STRING);
        if (tool != null) {
            state.setTool(ArtkitTool.parse(tool, state.getTool()));
        }
        if (!meta.hasLore()) return state;
        for (String line : meta.getLore()) {
            if (line.startsWith(COLOR_KEY) || line.contains("§r" + COLOR_KEY)) {
                int marker = line.indexOf(COLOR_KEY);
                if (marker >= 0) {
                    try {
                        state.setColor(ArtkitPalette.get(Integer.parseInt(line.substring(marker + COLOR_KEY.length()).trim())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (line.startsWith(TOOL_KEY) || line.contains("§r" + TOOL_KEY)) {
                int marker = line.indexOf(TOOL_KEY);
                if (marker >= 0) {
                    state.setTool(ArtkitTool.parse(line.substring(marker + TOOL_KEY.length()), state.getTool()));
                }
            }
        }
        return state;
    }

    public static void updateBrush(Player player, ArtkitState state) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isBrush(hand)) return;

        ItemStack brush = hand.clone();
        MenuDefinition artkit = ArtMap.instance().getMenuConfig().get("artkit");
        MenuItemDefinition brushDefinition = artkit != null ? artkit.getBrush() : null;
        Map<String, String> placeholders = placeholders(state);
        if (brushDefinition != null) {
            brush = MenuItems.apply(brush, brushDefinition, placeholders);
        }
        MenuDefinition toolItems = ArtMap.instance().getMenuConfig().get("tool-items");
        MenuItemDefinition toolDefinition = toolItems != null ? toolItems.getItem("PAINT_BRUSH") : null;
        if (toolDefinition != null) {
            brush = MenuItems.applyModelData(brush, toolDefinition, placeholders);
        }
        ItemMeta meta = brush.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains(COLOR_KEY) || line.contains(TOOL_KEY));
        if (lore.isEmpty() || !lore.get(0).contains(ArtItem.KIT_KEY)) {
            lore.add(0, ArtItem.KIT_KEY);
        }
        meta.setLore(lore);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(colorKey(), PersistentDataType.INTEGER, state.getColor().getIndex());
        data.set(toolKey(), PersistentDataType.STRING, state.getTool().name());
        brush.setItemMeta(meta);
        MenuItems.applyDyedColor(brush, placeholders);
        player.getInventory().setItemInMainHand(brush);
    }

    private static NamespacedKey colorKey() {
        return new NamespacedKey(ArtMap.instance(), "artkit_color");
    }

    private static NamespacedKey toolKey() {
        return new NamespacedKey(ArtMap.instance(), "artkit_tool");
    }

    public static Map<String, String> placeholders(ArtkitState state) {
        Map<String, String> values = new HashMap<>();
        ArtkitColor color = state.getColor();
        values.put("hex", color.getHex());
        values.put("hex_code", "#" + color.getHex());
        values.put("color_index", String.valueOf(color.getIndex()));
        values.put("color_name", color.getName());
        values.put("base", String.valueOf(color.getBase()));
        values.put("shade", String.valueOf(color.getShade()));
        values.put("shade_name", color.getShadeName());
        MenuDefinition artkit = ArtMap.instance().getMenuConfig().get("artkit");
        String tool = state.getTool().name();
        if (artkit != null) {
            tool = artkit.getToolName(tool, state.getTool().getDisplayName());
            tool = MenuItems.translate(tool);
        }
        values.put("tool", tool);
        return values;
    }
}
