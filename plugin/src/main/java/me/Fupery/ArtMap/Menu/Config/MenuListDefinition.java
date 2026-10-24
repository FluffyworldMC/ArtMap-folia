package me.Fupery.ArtMap.Menu.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public class MenuListDefinition {
    private final int startSlot;
    private final int pageSize;
    private final int previousSlot;
    private final int nextSlot;
    private final MenuItemDefinition item;
    private final Map<String, MenuItemDefinition> overrides;

    MenuListDefinition(ConfigurationSection section) {
        this.startSlot = section != null ? section.getInt("start-slot", 1) : 1;
        this.pageSize = section != null ? section.getInt("page-size", 25) : 25;
        this.previousSlot = section != null ? section.getInt("previous-slot", 0) : 0;
        this.nextSlot = section != null ? section.getInt("next-slot", 26) : 26;
        ConfigurationSection itemSection = section != null ? section.getConfigurationSection("item") : null;
        this.item = itemSection != null ? new MenuItemDefinition(itemSection) : null;
        Map<String, MenuItemDefinition> parsed = new HashMap<>();
        ConfigurationSection overrideSection = section != null ? section.getConfigurationSection("overrides") : null;
        if (overrideSection != null) {
            for (String key : overrideSection.getKeys(false)) {
                ConfigurationSection entry = overrideSection.getConfigurationSection(key);
                if (entry != null) parsed.put(key.toUpperCase(), new MenuItemDefinition(entry));
            }
        }
        this.overrides = Collections.unmodifiableMap(parsed);
    }

    public int getStartSlot() {
        return startSlot;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getPreviousSlot() {
        return previousSlot;
    }

    public int getNextSlot() {
        return nextSlot;
    }

    public MenuItemDefinition getItem() {
        return item;
    }

    public MenuItemDefinition getOverride(String key) {
        return overrides.get(key.toUpperCase());
    }
}
