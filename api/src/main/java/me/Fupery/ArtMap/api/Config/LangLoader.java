package me.Fupery.ArtMap.api.Config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Fupery.ArtMap.api.IArtMap;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.annotation.Nonnull;

/**
 * Loads the editable language file from the plugin data folder. The bundled English file is only
 * used as the source for a new file and as a fallback for keys an older local file does not have
 * yet.
 */
class LangLoader {
    private final IArtMap plugin;
    private final File langFile;
    private final FileConfiguration defaults;
    private final FileConfiguration lang;
    private final LinkedHashMap<String, Object> missingStrings = new LinkedHashMap<>();

    LangLoader(IArtMap plugin) {
        this.plugin = plugin;
        defaults = YamlConfiguration.loadConfiguration(plugin.getTextResourceFile("lang.yml"));
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.writeResource("lang.yml", langFile);
        }
        lang = YamlConfiguration.loadConfiguration(langFile);
        plugin.getLogger().info("Loading language file: " + langFile.getName());
    }

    /** Appends keys added by a plugin update so admins can translate them without losing their file. */
    void save() {
        if (missingStrings.isEmpty()) return;
        StringBuilder text = new StringBuilder();
        text.append(System.lineSeparator())
                .append("# New ArtMap keys added by an update.").append(System.lineSeparator());
        YamlConfiguration addition = new YamlConfiguration();
        for (Entry<String, Object> entry : missingStrings.entrySet()) {
            addition.set(entry.getKey(), entry.getValue());
        }
        text.append(addition.saveToString());
        try {
            Files.write(langFile.toPath(), text.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            plugin.getLogger().info("Added " + missingStrings.size() + " new key(s) to lang.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot save new keys to lang.yml.", e);
        }
    }

    String loadString(String key) {
        if (!lang.contains(key)) {
            if (defaults == null || !defaults.contains(key)) {
                return null;
            }
            Object value = defaults.get(key);
            missingStrings.put(key, value);
            logLangError(String.format("Missing key in lang.yml: '%s'", key));
            return LangColors.translate(defaults.getString(key));
        }
        return LangColors.translate(lang.getString(key));
    }

    @Nonnull
    String[] loadArray(String key) {
        if (lang.contains(key)) {
            List<String> messages = lang.getStringList(key);
            if (!messages.isEmpty()) {
                return translated(messages);
            }
            String single = lang.getString(key);
            if (single != null) {
                return new String[]{LangColors.translate(single)};
            }
        }
        if (defaults == null || !defaults.contains(key)) {
            return new String[0];
        }
        Object value = defaults.get(key);
        missingStrings.put(key, value);
        List<String> messages = defaults.getStringList(key);
        if (!messages.isEmpty()) {
            return translated(messages);
        }
        String single = defaults.getString(key);
        return single != null ? new String[]{LangColors.translate(single)} : new String[0];
    }

    private static String[] translated(List<String> messages) {
        String[] result = new String[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            result[i] = LangColors.translate(messages.get(i));
        }
        return result;
    }

    String[] loadRegex(String key) {
        if (lang.contains(key)) {
            List<String> msg = lang.getStringList(key);
            if (!msg.isEmpty()) return msg.toArray(new String[0]);
        }
        if (defaults != null && defaults.contains(key)) {
            Object value = defaults.get(key);
            missingStrings.put(key, value);
            List<String> msg = defaults.getStringList(key);
            return msg.toArray(new String[0]);
        }
        return Collections.emptyList().toArray(new String[0]);
    }

    private void logLangError(String reason) {
        plugin.getLogger().warning(reason + " Default values will be used.");
    }
}
