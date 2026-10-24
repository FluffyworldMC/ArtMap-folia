package me.Fupery.ArtMap.Menu.Config;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Resolves PlaceholderAPI placeholders without a hard runtime dependency. */
public final class MenuPlaceholders {

    private MenuPlaceholders() {
    }

    public static String apply(Player player, String text) {
        if (player == null || text == null || text.indexOf('%') < 0) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            Object resolved = method.invoke(null, player, text);
            return resolved instanceof String ? (String) resolved : text;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return text;
        }
    }
}
