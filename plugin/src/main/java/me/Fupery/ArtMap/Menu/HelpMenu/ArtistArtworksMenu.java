package me.Fupery.ArtMap.Menu.HelpMenu;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.ListMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.API.SoundCompat;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuListDefinition;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Utils.ItemUtils;

public class ArtistArtworksMenu extends ListMenu implements ChildMenu {
    private static final String MENU_ID = "artworks";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final UUID artist;
    private final String artistName;
    private ArtistMenu parent;
    private boolean adminViewing;

    public ArtistArtworksMenu(ArtistMenu parent, UUID artist, String artistName, boolean adminViewing, int page) {
        super(title(artistName), definition().resolveType(new MenuType(InventoryType.CHEST)),
                viewer -> parent, page, true);
        this.parent = parent;
        this.adminViewing = adminViewing;
        this.artist = artist;
        this.artistName = artistName;
    }

    private static MenuDefinition definition() {
        return ArtMap.instance().getMenuConfig().get(MENU_ID);
    }

    private static String title(String artistName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("artist", artistName != null ? artistName : "");
        return definition().getTitle(processTitle(artistName), placeholders,
                ArtMap.instance().getMenuConfig().getHeader());
    }

    @Override
    protected String getMenuId() {
        return MENU_ID;
    }

    private static String processTitle(String artistName) {
        String name = artistName;
        String title = ChatColor.DARK_BLUE + "%s's Art";
        String processedName = String.format(title, name);
        if (processedName.length() <= 32)
            return processedName;
        return (name.length() <= 30) ? ChatColor.DARK_BLUE + name : ChatColor.DARK_BLUE + name.substring(0, 29);
    }

    public static boolean isPreviewItem(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()
                && item.getItemMeta().hasLore() && item.getItemMeta().getLore().get(0).equals(ArtItem.PREVIEW_KEY);
    }

    @Override
    public CacheableMenu getParent(Player viewer) {
        return parent;
    }

    @Override
    public void onMenuCloseEvent(Player viewer, MenuCloseReason reason) {
        if (reason == MenuCloseReason.SPECIAL)
            return;
        ItemStack offHand = viewer.getInventory().getItemInOffHand();
        if (isPreviewItem(offHand))
            viewer.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }

    @Override
    protected Future<List<Button>> getListItems() {
        FutureTask<List<Button>> task = new FutureTask<> (()-> {
            List<MapArt> artworks;
            try {
                List<MapArt> loaded = ArtMap.instance().getArtDatabase().listMapArt(this.artist);
                artworks = loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
            } catch (SQLException e) {
                ArtMap.instance().getLogger().log(Level.SEVERE, "Database error!", e);
                return new ArrayList<>();
            }
            artworks.sort(comparator(getSortMode()));

            List<Button> buttons = new ArrayList<>();
            for (MapArt art : artworks) {
                if (art == null) continue;
                String artistName = art.getArtistName();
                if(artistName == null || artistName.isEmpty() || "null".equalsIgnoreCase(artistName)) {
                   art = art.setAristName(this.artistName);
                }
                buttons.add(new PreviewButton(this, art, adminViewing));
            }
            return buttons;
        });
        ArtMap.instance().getScheduler().SYNC.run(task);
        return task;
    }

    private static Comparator<MapArt> comparator(String mode) {
        Comparator<MapArt> byTitle = Comparator.comparing(
                (MapArt art) -> art.getTitle() != null ? art.getTitle() : "", String.CASE_INSENSITIVE_ORDER);
        if (mode == null) return byTitle;
        switch (mode) {
            case "title-desc":
                return byTitle.reversed();
            case "newest":
                return Comparator.comparingLong((MapArt art) -> day(art.getDate()))
                        .reversed().thenComparing(byTitle);
            case "oldest":
                return Comparator.comparingLong((MapArt art) -> day(art.getDate()))
                        .thenComparing(byTitle);
            case "title":
            default:
                return byTitle;
        }
    }

    private static long day(String date) {
        if (date == null || date.isEmpty()) return 0L;
        try {
            return LocalDate.parse(date, DATE_FORMAT).toEpochDay();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private class PreviewButton extends Button {

        private final MapArt artwork;
        private final ArtistArtworksMenu artworkMenu;

        private PreviewButton(ArtistArtworksMenu menu, MapArt artwork, boolean adminButton) {
			super(Material.FILLED_MAP);
			MapMeta meta = (MapMeta) artwork.getMapItem().getItemMeta();
			meta.setMapView(ArtMap.getMap(artwork.getMapId()));
			meta.setLocationName(artwork.getTitle());
            setItemMeta(meta);

            MenuListDefinition list = definition().getList();
            MenuItemDefinition item = list != null ? list.getItem() : null;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("title", artwork.getTitle());
            placeholders.put("artist", artwork.getArtistName() != null ? artwork.getArtistName() : "");
            placeholders.put("date", artwork.getDate());
            MenuItems.applyInPlace(this, item, placeholders);
            if (adminButton) addLore(ChatColor.GOLD, Lang.ADMIN_RECIPE.get());

            this.artwork = artwork;
            this.artworkMenu = menu;
        }

        private void addLore(ChatColor colour, String line) {
            if (line == null) return;
            org.bukkit.inventory.meta.ItemMeta meta = getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(colour + line);
            meta.setLore(lore);
            setItemMeta(meta);
        }

        @Override
        public void onClick(Player player, ClickType clickType) {

            if (clickType == ClickType.LEFT) {
				ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.SWITCH);
				ArtMap.instance().getMenuHandler().openMenu(player, new ArtPieceMenu(this.artworkMenu, this.artwork, player));
            } else if (clickType == ClickType.RIGHT) {
                if (player.hasPermission("artmap.admin")) {
                    SoundCompat.BLOCK_CLOTH_FALL.play(player);
                    ArtMap.instance().getScheduler().SYNC.run(player, () -> ItemUtils.giveItem(player, artwork.getMapItem()));
                } else if (adminViewing) {
                    Lang.NO_PERM.send(player);
                }
            }
        }
    }
}
