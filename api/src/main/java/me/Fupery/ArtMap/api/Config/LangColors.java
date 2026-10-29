package me.Fupery.ArtMap.api.Config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

/**
 * Translates the colour codes accepted in configuration files:
 * {@code &a}, {@code &x&r&r&g&g&b&b} and {@code &#rrggbb}.
 */
public final class LangColors {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern HEX_SEQUENCE = Pattern.compile("&x((?:&[0-9A-Fa-f]){6})");

    private LangColors() {
    }

    public static String translate(String text) {
        if (text == null) return null;
        String result = replaceHex(text);
        result = replaceHexSequence(result);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /** Turns &#rrggbb into the section-sign sequence used by Minecraft 1.16+. */
    private static String replaceHex(String text) {
        Matcher matcher = HEX_COLOR.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase();
            StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                replacement.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /** Accepts the &x&r&r&g&g&b&b form as well. */
    private static String replaceHexSequence(String text) {
        Matcher matcher = HEX_SEQUENCE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String sequence = matcher.group(1).replace('&', '§');
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("§x" + sequence));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
