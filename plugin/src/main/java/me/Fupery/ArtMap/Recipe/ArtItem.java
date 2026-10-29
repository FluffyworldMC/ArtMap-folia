package me.Fupery.ArtMap.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Utils.ItemUtils;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Config.LangColors;

public class ArtItem {

    /**
     * Default preview lore used before lang.yml is loaded. Prefer {@link #previewKey()} so a
     * server owner's PREVIEW_TAG is honoured.
     */
    public static final String PREVIEW_KEY = ChatColor.AQUA.toString() + ChatColor.ITALIC + "Preview Artwork";

    private ArtItem() {
        //hide constructor
    }

    /** The configured tag for an artwork item type, translated to section signs. */
    public static String tag(Lang key) {
        return translate(key != null ? key.get() : null);
    }

    public static String previewKey() {
        return tag(Lang.PREVIEW_TAG);
    }

    /** Keeps old/corrupt rows from printing the raw artist placeholder on artwork items. */
    private static String safeArtistName(String name) {
        return name == null || name.isEmpty() || "null".equalsIgnoreCase(name)
                ? translate(Lang.ARTWORK_UNKNOWN_ARTIST.get()) : name;
    }

    public static boolean isArtwork(ItemStack itemStack) {
        return isTagged(itemStack, tag(Lang.ARTWORK_TAG))
                || isTagged(itemStack, tag(Lang.UNFINISHED_TAG))
                || isTagged(itemStack, tag(Lang.COPY_TAG));
    }

    public static boolean isOriginalArtwork(ItemStack itemStack) {
        return isTagged(itemStack, tag(Lang.ARTWORK_TAG));
    }

    public static boolean isUnfinishedArtwork(ItemStack itemStack) {
        return isTagged(itemStack, tag(Lang.UNFINISHED_TAG));
    }

    public static boolean isCopyArtwork(ItemStack itemStack) {
        return isTagged(itemStack, tag(Lang.COPY_TAG));
    }

    public static boolean isPreviewItem(ItemStack itemStack) {
        return isTagged(itemStack, previewKey());
    }

    /**
     * Checks every lore line rather than only the first one, so server owners can put extra
     * information (scaling, levels, ...) above the artwork tag in their lore templates.
     */
    private static boolean isTagged(ItemStack itemStack, String tag) {
        if (tag == null || tag.isEmpty()) return false;
        if (itemStack == null || itemStack.getType() != Material.FILLED_MAP || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> lore = itemMeta.getLore();
        if (lore == null) return false;
        for (String line : lore) {
            if (line != null && line.contains(tag)) return true;
        }
        return false;
    }

    public static String translate(String text) {
        return LangColors.translate(text);
    }

    private static Map<String, String> placeholders(int id, String title, String artistName, String date) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", title != null ? title : "");
        placeholders.put("artist", safeArtistName(artistName));
        placeholders.put("date", date != null ? date : "");
        placeholders.put("id", String.valueOf(id));
        return placeholders;
    }

    private static String apply(String template, Map<String, String> placeholders) {
        String result = template != null ? template : "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return translate(result);
    }

    private static List<String> applyLore(Lang.Array template, Map<String, String> placeholders) {
        List<String> lines = new ArrayList<>();
        for (String line : template.get()) {
            if (line == null) continue;
            lines.add(apply(line, placeholders));
        }
        return lines;
    }

    private static ItemStack filledMap(int id) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        meta.setMapView(ArtMap.getMap(id));
        stack.setItemMeta(meta);
        return stack;
    }

    public static class ArtworkItem extends CustomItem {
        /**
         * Creates an original Artwork item.
         * @param id - The Map ID of the artwork.
         * @param title - The title of the artwork.
         * @param artistName - The name of the artist.
         * @param date - The date the item was created.
         */
        public ArtworkItem(int id, String title, String artistName, String date) {
            super(filledMap(id), tag(Lang.ARTWORK_TAG));
            Map<String, String> placeholders = placeholders(id, title, artistName, date);
            placeholders.put("tag", tag(Lang.ARTWORK_TAG));
            name(apply(Lang.ARTWORK_NAME.get(), placeholders));
            lore(applyLore(Lang.Array.ARTWORK_LORE, placeholders));
        }
    }

    public static class InProgressArtworkItem extends CustomItem {
        /**
         * Creates an in progress artwork item.
         * @param id - The map id of the inprogress artwork.
         */
        public InProgressArtworkItem(int id, String artistName) {
            super(filledMap(id), tag(Lang.UNFINISHED_TAG));
            Map<String, String> placeholders = placeholders(id, null, artistName, null);
            placeholders.put("tag", tag(Lang.UNFINISHED_TAG));
            name(apply(Lang.UNFINISHED_NAME.get(), placeholders));
            lore(applyLore(Lang.Array.UNFINISHED_LORE, placeholders));
        }
    }

    public static class CopyArtworkItem extends CustomItem {
        /**
         * Creates a copy artwork item.
         * @param id - The map id of the copy.
         */
        public CopyArtworkItem(int id, String title, String artistName, String date) {
            super(filledMap(id), tag(Lang.COPY_TAG));
            Map<String, String> placeholders = placeholders(id, title, artistName, date);
            placeholders.put("tag", tag(Lang.COPY_TAG));
            name(apply(Lang.COPY_NAME.get(), placeholders));
            lore(applyLore(Lang.Array.COPY_LORE, placeholders));
        }
    }

    public static class ConfiguredItem extends CustomItem {
        private final ItemStack display;

        public ConfiguredItem(ItemStack display, String key) {
            super(display, key);
            this.display = display;
        }

        @Override
        public boolean checkItem(ItemStack itemStack) {
            return itemStack != null && itemStack.getType() == getMaterial()
                    && ItemUtils.isArtkitItem(itemStack);
        }

        @Override
        public ItemStack toItemStack() {
            return display.clone();
        }
    }
}
