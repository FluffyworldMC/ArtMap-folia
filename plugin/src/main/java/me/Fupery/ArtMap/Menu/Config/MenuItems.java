package me.Fupery.ArtMap.Menu.Config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import net.md_5.bungee.api.chat.BaseComponent;

import me.Fupery.ArtMap.Recipe.ArtMaterial;

/** Builds menu items and applies the optional 1.14/1.21 item metadata through reflection. */
public final class MenuItems {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern HEX_SEQUENCE = Pattern.compile("&x((?:&[0-9A-Fa-f]){6})");

    private MenuItems() {
    }

    public static Material matchMaterial(String name) {
        if (name == null || name.isEmpty()) return null;
        return Material.matchMaterial(name);
    }

    public static ItemStack build(MenuItemDefinition definition, ItemStack fallback, Map<String, String> placeholders) {
        ItemStack stack = fallback != null ? fallback.clone() : null;
        if (definition != null && definition.getArtItem() != null) {
            try {
                stack = ArtMaterial.valueOf(definition.getArtItem().toUpperCase()).getItem();
            } catch (IllegalArgumentException ignored) {
                // Keep the fallback item when the art_item key is invalid.
            }
        }
        if (definition != null) {
            Material material = definition.getMaterial(stack != null ? stack.getType() : Material.STONE);
            if (stack == null) {
                stack = new ItemStack(material);
            } else if (material != stack.getType()) {
                ItemStack replacement = new ItemStack(material);
                if (stack.hasItemMeta()) replacement.setItemMeta(stack.getItemMeta());
                stack = replacement;
            }
        }
        if (stack == null) stack = new ItemStack(Material.STONE);
        return apply(stack, definition, placeholders);
    }

    public static ItemStack apply(ItemStack stack, MenuItemDefinition definition, Map<String, String> placeholders) {
        if (stack == null || definition == null) return stack;
        ItemStack result = stack.clone();
        if (definition.getAmount() > 0) {
            result.setAmount(Math.max(1, Math.min(64, definition.getAmount())));
        }
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        String name = definition.getName();
        List<String> lore = definition.getLore().isEmpty()
                ? Collections.emptyList() : new ArrayList<>(definition.getLore());

        if (name != null) {
            meta.setDisplayName(translate(replace(name, placeholders)));
        }
        if (!lore.isEmpty()) {
            List<String> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(translate(replace(line, placeholders)));
            }
            meta.setLore(lines);
        }

        applyCustomModelData(meta, definition.getCustomModelDataRaw(), definition.getCustomModelData(), placeholders);
        applyItemModel(meta, MenuItems.replace(definition.getItemModel(), placeholders));
        if (definition.isHideAttributes() || result.getType() == Material.LEATHER_HORSE_ARMOR) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        removeItalic(meta);
        result.setItemMeta(meta);
        applyDyedColor(result, placeholders);
        return result;
    }

    /**
     * Applies the configured metadata directly to the given stack. Custom buttons extend
     * ItemStack, so the copy returned by {@link #apply(ItemStack, MenuItemDefinition, Map)}
     * would otherwise be discarded and the configured name/lore would never show.
     */
    public static void applyInPlace(ItemStack stack, MenuItemDefinition definition,
                                    Map<String, String> placeholders) {
        if (stack == null || definition == null) return;
        ItemStack result = apply(stack, definition, placeholders);
        if (result == null || result == stack) return;
        stack.setType(result.getType());
        stack.setAmount(result.getAmount());
        stack.setItemMeta(result.getItemMeta());
        copyDyedColor(result, stack);
    }

    /** Applies only item_model/custom_model_data, leaving the name and lore alone. */
    public static ItemStack applyModelData(ItemStack stack, MenuItemDefinition definition,
                                           Map<String, String> placeholders) {
        if (stack == null || definition == null) return stack;
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        applyCustomModelData(meta, definition.getCustomModelDataRaw(), definition.getCustomModelData(), placeholders);
        applyItemModel(meta, replace(definition.getItemModel(), placeholders));
        result.setItemMeta(meta);
        return result;
    }

    public static void applyDyedColor(ItemStack stack, Map<String, String> placeholders) {
        if (stack == null || placeholders == null || stack.getType() != Material.LEATHER_HORSE_ARMOR) return;
        String hex = placeholders.get("hex");
        if (hex == null || hex.length() != 6) return;
        try {
            int rgb = Integer.parseInt(hex, 16);
            stack.setData(DataComponentTypes.DYED_COLOR,
                    DyedItemColor.dyedItemColor(Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)));
        } catch (RuntimeException ignored) {
        }
    }

    /** Copies the dyed colour component, which is not part of ItemMeta on modern Paper. */
    public static void copyDyedColor(ItemStack from, ItemStack to) {
        if (from == null || to == null) return;
        try {
            DyedItemColor color = from.getData(DataComponentTypes.DYED_COLOR);
            if (color != null) to.setData(DataComponentTypes.DYED_COLOR, color);
        } catch (RuntimeException ignored) {
        }
    }

    private static void removeItalic(ItemMeta meta) {
        try {
            java.lang.reflect.Method getDisplay = ItemMeta.class.getMethod("getDisplayNameComponent");
            java.lang.reflect.Method setDisplay = ItemMeta.class.getMethod("setDisplayNameComponent", BaseComponent[].class);
            Object display = getDisplay.invoke(meta);
            if (display instanceof BaseComponent[]) {
                for (BaseComponent component : (BaseComponent[]) display) component.setItalic(false);
                setDisplay.invoke(meta, display);
            }
            java.lang.reflect.Method getLore = ItemMeta.class.getMethod("getLoreComponents");
            java.lang.reflect.Method setLore = ItemMeta.class.getMethod("setLoreComponents", java.util.List.class);
            Object lore = getLore.invoke(meta);
            if (lore instanceof java.util.List) {
                for (Object line : (java.util.List<?>) lore) {
                    if (line instanceof BaseComponent[]) {
                        for (BaseComponent component : (BaseComponent[]) line) component.setItalic(false);
                    }
                }
                setLore.invoke(meta, lore);
            }
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (meta.hasDisplayName() && !meta.getDisplayName().startsWith("§r")) {
            meta.setDisplayName("§r" + meta.getDisplayName());
        }
        if (meta.hasLore()) {
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : meta.getLore()) {
                lore.add(line.startsWith("§r") ? line : "§r" + line);
            }
            meta.setLore(lore);
        }
    }

    public static String translate(String text) {
        if (text == null) return null;
        String result = replaceHex(text);
        result = replaceHexSequence(result);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /** Turns &#rrggbb into the §x§r§r§g§g§b§b sequence used by Minecraft 1.16+. */
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

    /** Also accepts the &x&r&r&g&g&b&b form. */
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

    public static String replace(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private static void applyCustomModelData(ItemMeta meta, String raw, Integer data, Map<String, String> placeholders) {
        Integer value = data;
        if (raw != null) {
            try {
                value = Integer.parseInt(replace(raw, placeholders).trim());
            } catch (NumberFormatException ignored) {
                value = null;
            }
        }
        if (value == null) return;
        try {
            Method method = ItemMeta.class.getMethod("setCustomModelData", Integer.class);
            method.invoke(meta, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // CustomModelData is not available before 1.14.
        }
    }

    private static void applyItemModel(ItemMeta meta, String value) {
        if (value == null || value.isEmpty()) return;
        NamespacedKey key = parseKey(value);
        if (key == null) return;
        try {
            Method method = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            method.invoke(meta, key);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // ItemModel is only available on Paper 1.21.4+.
        }
    }

    private static NamespacedKey parseKey(String value) {
        String input = value.trim();
        int colon = input.indexOf(':');
        String namespace = colon >= 0 ? input.substring(0, colon) : "minecraft";
        String key = colon >= 0 ? input.substring(colon + 1) : input;
        if (!namespace.matches("[a-z0-9_.-]+") || !key.matches("[a-z0-9_./-]+")) {
            return null;
        }
        try {
            return new NamespacedKey(namespace, key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
