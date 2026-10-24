package me.Fupery.ArtMap.Menu.Config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class MenuCommon {
    private final String header;
    private final String separator;
    private final String edge;
    private final MenuItemDefinition previous;
    private final MenuItemDefinition next;
    private final MenuItemDefinition previousDisabled;
    private final MenuItemDefinition nextDisabled;

    MenuCommon(FileConfiguration config) {
        this.header = config.getString("menu-header", "");
        this.separator = config.getString("pagination.separator", " :: ");
        this.edge = config.getString("pagination.edge", "*");
        ConfigurationSection pagination = config.getConfigurationSection("pagination");
        this.previous = item(pagination, "previous");
        this.next = item(pagination, "next");
        this.previousDisabled = item(pagination, "previous-disabled");
        this.nextDisabled = item(pagination, "next-disabled");
    }

    public static MenuCommon defaults() {
        return new MenuCommon(new org.bukkit.configuration.file.YamlConfiguration());
    }

    private static MenuItemDefinition item(ConfigurationSection parent, String key) {
        if (parent == null) return null;
        ConfigurationSection section = parent.getConfigurationSection(key);
        return section != null ? new MenuItemDefinition(section) : null;
    }

    public String getHeader() {
        return header;
    }

    public String getSeparator() {
        return separator;
    }

    public String getEdge() {
        return edge;
    }

    public MenuItemDefinition getPrevious() {
        return previous;
    }

    public MenuItemDefinition getNext() {
        return next;
    }

    public MenuItemDefinition getPreviousDisabled() {
        return previousDisabled;
    }

    public MenuItemDefinition getNextDisabled() {
        return nextDisabled;
    }
}
