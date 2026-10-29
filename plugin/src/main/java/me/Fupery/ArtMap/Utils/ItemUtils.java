package me.Fupery.ArtMap.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Exception.ArtMapException;

public class ItemUtils {

    protected ItemUtils() {
        //this is a static class so hide the constructor
    }

    public static void giveItem(Player player, ItemStack item) {
        ItemStack leftOver = player.getInventory().addItem(item).get(0);
        if (leftOver != null && leftOver.getAmount() > 0)
            player.getWorld().dropItemNaturally(player.getLocation(), leftOver);
    }

    /** Hidden marker for every item that belongs to the painting kit. */
    public static ItemStack markArtkit(ItemStack item) {
        return mark(item, "artkit_item");
    }

    /** Hidden marker for the kit's next-page button. */
    public static ItemStack markArtkitNext(ItemStack item) {
        return mark(item, "artkit_next");
    }

    /** Hidden marker for the kit's back-page button. */
    public static ItemStack markArtkitBack(ItemStack item) {
        return mark(item, "artkit_back");
    }

    public static boolean isArtkitItem(ItemStack item) {
        return hasMarker(item, "artkit_item") || isArtkitPage(item);
    }

    public static boolean isArtkitNext(ItemStack item) {
        return hasMarker(item, "artkit_next");
    }

    public static boolean isArtkitBack(ItemStack item) {
        return hasMarker(item, "artkit_back");
    }

    private static boolean isArtkitPage(ItemStack item) {
        return isArtkitNext(item) || isArtkitBack(item);
    }

    private static ItemStack mark(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        org.bukkit.persistence.PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data == null) return item;
        data.set(namespaced(key), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean hasMarker(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        org.bukkit.persistence.PersistentDataContainer data = meta.getPersistentDataContainer();
        return data != null && data.has(namespaced(key), PersistentDataType.BYTE);
    }

    private static NamespacedKey namespaced(String key) {
        return new NamespacedKey(ArtMap.instance(), key);
    }

    public static boolean hasKey(ItemStack itemStack, String key) {
        if (itemStack != null && itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            return itemMeta.hasLore() && itemMeta.getLore().contains(key);
        }
        return false;
    }

    public static ItemStack addKey(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if(meta == null) {
            return null;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else if (lore.contains(key)) {
            return item;
        } 
        lore.add(key);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

	/**
     * Handle 1.13 maps having their ID as part of the meta data.
     * 
     * @param item The Map Item to get the id of.
     * @return The ID of the map or Empty if the mapview isnt present.
     * @throws ArtMapException 
     */
    public static Optional<Integer> getMapID(ItemStack item) throws ArtMapException {
		if (item.getType() != Material.FILLED_MAP) {
			throw new ArtMapException("Tried to get the map id of an item that is not a map.");
		}

        MapMeta meta = (MapMeta) item.getItemMeta();
        if(null == meta) {
            throw new ArtMapException("Tried to get the map id but the map doesn't have a map meta?");
        }
        if(meta.hasMapView() && meta.getMapView() != null) {
            return Optional.of(meta.getMapView().getId());
        } else {
            return Optional.empty();
        }
	}
}
