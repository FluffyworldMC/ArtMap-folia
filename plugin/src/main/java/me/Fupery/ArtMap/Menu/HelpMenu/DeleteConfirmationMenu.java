package me.Fupery.ArtMap.Menu.HelpMenu;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.ListMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuButtons;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Recipe.ArtItem;

public class DeleteConfirmationMenu extends ListMenu implements ChildMenu {
	private static final String MENU_ID = "delete";
	private ArtistArtworksMenu parent;
	private boolean adminViewing;
	private MapArt artwork;

	public DeleteConfirmationMenu(ArtistArtworksMenu parent, MapArt artwork, boolean adminViewing) {
		super(definition().getTitle(Lang.MenuTitle.MENU_DELETE.get(), null, ArtMap.instance().getMenuConfig().getHeader()),
				definition().resolveType(new MenuType(InventoryType.CHEST)), 0, true);
		this.parent = parent;
		this.adminViewing = adminViewing;
		this.artwork = artwork;
	}

	private static MenuDefinition definition() {
		return ArtMap.instance().getMenuConfig().get(MENU_ID);
	}

	@Override
	protected String getMenuId() {
		return MENU_ID;
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
	public Future<Button[]> getButtons() {
		MenuDefinition definition = definition();
		int size = Math.max(9, definition.resolveType(new MenuType(InventoryType.CHEST)).getDefaultSize());
		Button[] buttons = new Button[size];
		MenuItemDefinition item = definition.getItem("accept");
		int slot = item != null && item.getSlot() >= 0 ? item.getSlot() : 1;
		if (slot < size) {
			buttons[slot] = new AcceptButton(this.parent, this.artwork, adminViewing, item);
		}
		MenuButtons.placeNavigation(definition, viewer, new HashMap<>(), buttons);
		return CompletableFuture.completedFuture(buttons);
	}

	@Override
	protected Future<List<Button>> getListItems() {
		return CompletableFuture.completedFuture(new ArrayList<>());
	}

	private static class AcceptButton extends Button {

		private final MapArt artwork;
		private final ArtistArtworksMenu artworkMenu;

		private AcceptButton(ArtistArtworksMenu menu, MapArt artwork, boolean adminButton, MenuItemDefinition item) {
			super(Material.REDSTONE);
			ItemMeta meta = new ItemStack(Material.REDSTONE).getItemMeta();
			meta.setDisplayName(HelpMenu.ACCEPT_NAME);
			List<String> lore = new ArrayList<>();
			lore.add(HelpMenu.ACCEPT_TEXT);
			meta.setLore(lore);
			setItemMeta(meta);
			MenuItems.apply(this, item, null);
			this.artwork = artwork;
			this.artworkMenu = menu;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {

			if (clickType == ClickType.LEFT) {
				ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);

				ArtMap.instance().getScheduler().SYNC.run(player, () -> {
					if (this.artwork.getArtist().equals(player.getUniqueId()) || player.hasPermission("artmap.admin")) {
						try {
							ArtMap.instance().getArtDatabase().deleteArtwork(this.artwork);
							player.sendMessage(String.format(Lang.DELETED.get(), this.artwork.getTitle()));
						} catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
							ArtMap.instance().getLogger().log(Level.SEVERE, "Error deleting artwork!!", e);
							player.sendMessage("Error deleting Artwork check logs.");
							return; 
						}
					} else {
						player.sendMessage(Lang.NO_PERM.get());
						return;
					}
					ArtMap.instance().getMenuHandler().openMenu(player, this.artworkMenu);
				});
			}
		}
	}
}
