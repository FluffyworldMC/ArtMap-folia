package me.Fupery.ArtMap.Menu.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import me.Fupery.ArtMap.Menu.API.MenuType;

public class MenuDefinition {
    private static final int DEFAULT_CHEST_SIZE = 54;
    private final String id;
    private final String title;
    private final int size;
    private final String parent;
    private final Map<Integer, MenuItemDefinition> itemsBySlot;
    private final Map<String, MenuItemDefinition> itemsByKey;
    private final MenuListDefinition list;
    private final MenuItemDefinition brush;
    private final Map<String, String> toolNames;

    MenuDefinition(String id, FileConfiguration config) {
        this.id = id;
        this.title = config.getString("title");
        this.parent = config.getString("parent");
        int configuredSize = config.getInt("size", 0);
        if (configuredSize <= 0 && config.contains("rows")) {
            configuredSize = Math.max(1, Math.min(6, config.getInt("rows", 6))) * 9;
        }
        if (configuredSize < 9 || configuredSize > 54 || configuredSize % 9 != 0) {
            configuredSize = DEFAULT_CHEST_SIZE;
        }
        this.size = configuredSize;
        Map<Integer, MenuItemDefinition> slots = new LinkedHashMap<>();
        Map<String, MenuItemDefinition> keys = new LinkedHashMap<>();
        ConfigurationSection itemSection = config.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                ConfigurationSection entry = itemSection.getConfigurationSection(key);
                if (entry == null) continue;
                MenuItemDefinition item = new MenuItemDefinition(entry);
                keys.put(key, item);
                if (item.getSlot() >= 0) slots.put(item.getSlot(), item);
            }
        }
        this.itemsBySlot = Collections.unmodifiableMap(slots);
        this.itemsByKey = Collections.unmodifiableMap(keys);
        this.list = config.isConfigurationSection("list") ? new MenuListDefinition(config.getConfigurationSection("list")) : null;
        this.brush = config.isConfigurationSection("brush")
                ? new MenuItemDefinition(config.getConfigurationSection("brush")) : null;

        Map<String, String> names = new LinkedHashMap<>();
        ConfigurationSection toolNameSection = config.getConfigurationSection("tool_names");
        if (toolNameSection != null) {
            for (String key : toolNameSection.getKeys(false)) {
                names.put(key.toUpperCase(), toolNameSection.getString(key));
            }
        }
        this.toolNames = Collections.unmodifiableMap(names);
    }

    public static MenuDefinition empty(String id) {
        return new MenuDefinition(id, new org.bukkit.configuration.file.YamlConfiguration());
    }

    public String getId() {
        return id;
    }

    public String getTitle(String fallback, Map<String, String> placeholders, String header) {
        String raw = title != null ? title : fallback;
        if (raw == null) raw = "";
        return MenuItems.translate(MenuItems.replace(raw, placeholders));
    }

    public MenuType resolveType(MenuType fallback) {
        return new MenuType(size > 0 ? size : DEFAULT_CHEST_SIZE);
    }

    public String getParent() {
        return parent;
    }

    public MenuItemDefinition getItem(int slot) {
        return itemsBySlot.get(slot);
    }

    public MenuItemDefinition getItem(String key) {
        return itemsByKey.get(key);
    }

    public Map<Integer, MenuItemDefinition> getItemsBySlot() {
        return itemsBySlot;
    }

    public Map<String, MenuItemDefinition> getItemsByKey() {
        return itemsByKey;
    }

    public MenuListDefinition getList() {
        return list;
    }

    public MenuItemDefinition getBrush() {
        return brush;
    }

    public String getToolName(String tool, String fallback) {
        if (tool == null) return fallback;
        String name = toolNames.get(tool.toUpperCase());
        return name != null ? name : fallback;
    }

    public List<MenuItemDefinition> getItems() {
        return new ArrayList<>(itemsByKey.values());
    }
}
