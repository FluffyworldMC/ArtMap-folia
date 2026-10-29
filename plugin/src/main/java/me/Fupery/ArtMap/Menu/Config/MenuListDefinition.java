package me.Fupery.ArtMap.Menu.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public class MenuListDefinition {
    private final int startSlot;
    private final int endSlot;
    private final int pageSize;
    private final int previousSlot;
    private final int nextSlot;
    private final MenuItemDefinition previous;
    private final MenuItemDefinition next;
    private final MenuItemDefinition previousDisabled;
    private final MenuItemDefinition nextDisabled;
    private final MenuItemDefinition item;
    private final Map<String, MenuItemDefinition> overrides;
    private final Map<String, String> sortModes;
    private final String defaultSort;
    private final boolean pinViewer;

    MenuListDefinition(ConfigurationSection section) {
        this.startSlot = section != null ? section.getInt("start-slot", 1) : 1;
        this.endSlot = section != null ? section.getInt("end-slot", -1) : -1;
        this.pageSize = section != null ? section.getInt("page-size", 25) : 25;
        this.previousSlot = section != null ? section.getInt("previous-slot", 0) : 0;
        this.nextSlot = section != null ? section.getInt("next-slot", 26) : 26;
        this.previous = pageItem(section, "previous");
        this.next = pageItem(section, "next");
        this.previousDisabled = pageItem(section, "previous-disabled");
        this.nextDisabled = pageItem(section, "next-disabled");
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

        ConfigurationSection sortSection = section != null ? section.getConfigurationSection("sort") : null;
        Map<String, String> modes = new LinkedHashMap<>();
        ConfigurationSection modeSection = sortSection != null
                ? sortSection.getConfigurationSection("modes") : null;
        if (modeSection != null) {
            for (String key : modeSection.getKeys(false)) {
                modes.put(key.toLowerCase(Locale.ROOT), modeSection.getString(key, key));
            }
        }
        this.sortModes = Collections.unmodifiableMap(modes);
        String configuredDefault = sortSection != null ? sortSection.getString("default") : null;
        if (configuredDefault != null) configuredDefault = configuredDefault.toLowerCase(Locale.ROOT);
        if (configuredDefault == null || !modes.containsKey(configuredDefault)) {
            configuredDefault = modes.isEmpty() ? null : modes.keySet().iterator().next();
        }
        this.defaultSort = configuredDefault;
        this.pinViewer = sortSection == null || sortSection.getBoolean("pin-viewer", true);
    }

    public int getStartSlot() {
        return startSlot;
    }

    /** Last slot the list may use, or -1 when the list may run to the end of the inventory. */
    public int getEndSlot() {
        return endSlot;
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

    /**
     * Returns the page button configured for this menu, falling back to the shared common.yml
     * pagination items. A menu can therefore theme its own page buttons under "list".
     */
    public static MenuItemDefinition resolvePageItem(MenuListDefinition list, MenuCommon common,
                                                     boolean forward, boolean enabled) {
        MenuItemDefinition item = null;
        if (list != null) {
            item = forward
                    ? (enabled ? list.next : list.nextDisabled)
                    : (enabled ? list.previous : list.previousDisabled);
        }
        if (item != null || common == null) return item;
        return forward
                ? (enabled ? common.getNext() : common.getNextDisabled())
                : (enabled ? common.getPrevious() : common.getPreviousDisabled());
    }

    private static MenuItemDefinition pageItem(ConfigurationSection section, String key) {
        if (section == null) return null;
        ConfigurationSection itemSection = section.getConfigurationSection(key);
        return itemSection != null ? new MenuItemDefinition(itemSection) : null;
    }

    public MenuItemDefinition getItem() {
        return item;
    }

    public Map<String, String> getSortModes() {
        return sortModes;
    }

    public String getDefaultSort() {
        return defaultSort;
    }

    public boolean isPinViewer() {
        return pinViewer;
    }

    public MenuItemDefinition getOverride(String key) {
        return overrides.get(key.toUpperCase());
    }
}
