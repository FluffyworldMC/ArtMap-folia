package me.Fupery.ArtMap.Listeners;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Exception.ArtMapException;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Utils.ItemUtils;
import me.Fupery.ArtMap.api.Utils.VersionHandler.BukkitVersion;

class PlayerCraftListener implements RegisteredListener {

    @EventHandler
    public void onPlayerCraftEvent(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();

        // Tuinity fork sends nulls sometimes so handle it.
        if (result == null) {
            return;
        }
        // Disallow players from copying ArtMap maps in the crafting table
        if (result.getType() == Material.FILLED_MAP) {
            Optional<MapArt> artwork = lookupArtwork(result, event.getWhoClicked());
            if (!artwork.isPresent()) {
                // A regular map (or a database failure, already reported): leave it to vanilla.
                return;
            }
            MapArt art = artwork.get();
            if (event.getWhoClicked().getUniqueId().equals(art.getArtistPlayer().getUniqueId())) {
                Player player = (Player) event.getWhoClicked();
                ItemStack artworkItem = art.getMapItem();
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    onShiftClick(artworkItem, player, event);
                } else {
                    result.setItemMeta(artworkItem.getItemMeta());
                }
            } else {
                Lang.NO_CRAFT_PERM.send(event.getWhoClicked());
                event.setResult(Event.Result.DENY);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Tuinity fork sometimes throws a null even down the pipe
        if (event == null) {
            return;
        }
        // one check this if 1.14+
        if (ArtMap.instance().getBukkitVersion().getVersion().isLessThan(BukkitVersion.v1_14)) {
            return;
        }
        // exit if not a cartogaphy inventory
        if (!(event.getInventory() instanceof CartographyInventory)) {
            return;
        }
        // if the result is empty there is nothing to do
        ItemStack result = event.getCurrentItem();
        if (result == null) {
            return;
        }
        // Disallow players from copying ArtMap maps in the cartography table
        if (result.getType() == Material.FILLED_MAP) {
            Optional<MapArt> artwork = lookupArtwork(result, event.getWhoClicked());
            if (!artwork.isPresent()) {
                // A regular map (or a database failure, already reported): leave it to vanilla.
                return;
            }
            MapArt art = artwork.get();
            if (event.getWhoClicked().getUniqueId().equals(art.getArtistPlayer().getUniqueId())) {
                ItemStack artworkItem = art.getMapItem();
                result.setItemMeta(artworkItem.getItemMeta());
            } else {
                Lang.NO_CRAFT_PERM.send(event.getWhoClicked());
                event.setResult(Event.Result.DENY);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Resolve the ArtMap artwork behind a filled map.
     * <p>
     * Both crafting listeners see every filled map a player clicks, and most of
     * those are ordinary vanilla maps that ArtMap has no record of (#377). A map
     * that is not an artwork is not an error, so it comes back empty without any
     * logging and the caller lets vanilla handle the click. Only a database
     * failure is reported, and it is also returned as empty so the click still
     * falls through to vanilla rather than being swallowed.
     */
    private Optional<MapArt> lookupArtwork(ItemStack map, HumanEntity who) {
        try {
            Optional<Integer> mapId = ItemUtils.getMapID(map);
            if (!mapId.isPresent()) {
                return Optional.empty();
            }
            return ArtMap.instance().getArtDatabase().getArtwork(mapId.get());
        } catch (ArtMapException e) {
            // Not a map with map meta: nothing for ArtMap to do.
            return Optional.empty();
        } catch (SQLException e) {
            ArtMap.instance().getLogger().log(Level.SEVERE, "Database error!", e);
            who.sendMessage("Error Retrieving Artwork check logs.");
            return Optional.empty();
        }
    }


    private void onShiftClick(ItemStack artworkItem, Player player, CraftItemEvent event) {
        event.setCancelled(true);

        int i = 0;
        ItemStack[] items = event.getInventory().getMatrix();
        for (ItemStack item : items) {

            if (item != null) {
                i += item.getAmount();
            }
        }
        event.getInventory().setMatrix(new ItemStack[items.length]);
        artworkItem.setAmount(i);
        ItemUtils.giveItem(player, artworkItem);
    }

	@Override
    public void unregister() {
        CraftItemEvent.getHandlerList().unregister(this);
    }
}
