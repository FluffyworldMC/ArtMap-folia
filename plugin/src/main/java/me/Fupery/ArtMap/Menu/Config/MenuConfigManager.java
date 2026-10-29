package me.Fupery.ArtMap.Menu.Config;

import java.io.File;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;

import me.Fupery.ArtMap.ArtMap;

/** Loads and validates plugins/ArtMap/menu/*.yml. */
public class MenuConfigManager {
    private static final List<String> MENU_IDS = Arrays.asList(
            "tools", "recipes", "artists", "artworks", "artwork", "delete", "recipe-preview", "artkit", "tool-items");
    private static final String COMMON_FILE = "common.yml";

    private final ArtMap plugin;
    private final Map<String, MenuDefinition> definitions = new LinkedHashMap<>();
    private MenuCommon common = MenuCommon.defaults();

    public MenuConfigManager(ArtMap plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File folder = new File(plugin.getDataFolder(), "menu");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create menu folder: " + folder.getAbsolutePath());
        }
        ensure(COMMON_FILE);
        for (String id : MENU_IDS) ensure(id + ".yml");

        common = loadCommon(new File(folder, COMMON_FILE));
        definitions.clear();
        for (String id : MENU_IDS) {
            definitions.put(id, loadMenu(id, new File(folder, id + ".yml")));
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " menu configuration(s).");
    }

    private void ensure(String fileName) {
        File file = new File(new File(plugin.getDataFolder(), "menu"), fileName);
        if (!file.exists()) {
            plugin.writeResource("menu/" + fileName, file);
        }
    }

    private YamlConfiguration read(File file, String resourcePath) {
        YamlConfiguration config = new YamlConfiguration();
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        }
        // Menu files are chest GUIs now. Ignore an old "type" file and use the bundled default.
        if (config.contains("type") || config.getKeys(false).isEmpty()) {
            Reader reader = plugin.getTextResourceFile(resourcePath);
            if (reader != null) {
                config = YamlConfiguration.loadConfiguration(reader);
            }
        }
        return config;
    }

    private MenuCommon loadCommon(File file) {
        try {
            return new MenuCommon(read(file, "menu/" + COMMON_FILE));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + file.getName() + "; using defaults.", e);
            return MenuCommon.defaults();
        }
    }

    private MenuDefinition loadMenu(String id, File file) {
        try {
            return new MenuDefinition(id, read(file, "menu/" + id + ".yml"));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read menu/" + file.getName() + "; using defaults.", e);
            return MenuDefinition.empty(id);
        }
    }

    public MenuDefinition get(String id) {
        MenuDefinition definition = definitions.get(id);
        return definition != null ? definition : MenuDefinition.empty(id);
    }

    public MenuCommon getCommon() {
        return common;
    }

    public String getHeader() {
        return common.getHeader();
    }

    public List<String> getMenuIds() {
        return Collections.unmodifiableList(MENU_IDS);
    }
}
