package me.Fupery.ArtMap.Menu.HelpMenu;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Preview.ArtPreview;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Utils.ItemUtils;
import net.wesjd.anvilgui.AnvilGUI;

public class ArtPieceMenu extends ListMenu implements ChildMenu {
	private static final String MENU_ID = "artwork";
	private ArtistArtworksMenu parent;
	private Player viewer;
	private MapArt artwork;

	public ArtPieceMenu(ArtistArtworksMenu parent, MapArt artwork, Player viewer) {
		super(title(artwork.getTitle()), definition().resolveType(new MenuType(InventoryType.CHEST)), 0, true);
		this.parent = parent;
		this.artwork = artwork;
		this.viewer = viewer;
	}

	private static MenuDefinition definition() {
		return ArtMap.instance().getMenuConfig().get(MENU_ID);
	}

	private static String title(String artworkTitle) {
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("title", artworkTitle);
		return definition().getTitle("Artwork " + artworkTitle,
				placeholders, ArtMap.instance().getMenuConfig().getHeader());
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
	public void onMenuCloseEvent(Player viewer, MenuCloseReason reason) {
		if (reason == MenuCloseReason.SPECIAL)
			return;
		ItemStack offHand = viewer.getInventory().getItemInOffHand();
		if (isPreviewItem(offHand))
			viewer.getInventory().setItemInOffHand(new ItemStack(Material.AIR));	
	}

	@Override
	public Future<Button[]> getButtons() {
		MenuDefinition definition = definition();
		int size = Math.max(9, definition.resolveType(new MenuType(InventoryType.CHEST)).getDefaultSize());
		Button[] buttons = new Button[size];
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("title", artwork.getTitle());
		placeholders.put("artist", artwork.getArtistName() != null ? artwork.getArtistName() : "");
		placeholders.put("date", artwork.getDate());

		MenuItemDefinition preview = definition.getItem("preview");
		int previewSlot = preview != null && preview.getSlot() >= 0 ? preview.getSlot() : 0;
		buttons[previewSlot] = new PreviewButton(this, this.artwork, viewer, placeholders);

		if (viewer.hasPermission("artmap.admin") || artwork.getArtist().equals(viewer.getUniqueId())) {
			MenuItemDefinition delete = definition.getItem("delete");
			int deleteSlot = delete != null && delete.getSlot() >= 0 ? delete.getSlot() : 1;
			if (deleteSlot < size) buttons[deleteSlot] = new DeleteButton(this.parent, this.artwork, delete);

			MenuItemDefinition rename = definition.getItem("rename");
			int renameSlot = rename != null && rename.getSlot() >= 0 ? rename.getSlot() : 2;
			if (renameSlot < size) buttons[renameSlot] = new RenameButton(this.parent, this.artwork, rename);
		}
		return CompletableFuture.completedFuture(buttons);
	}

	@Override
	protected Future<List<Button>> getListItems() {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	private static class PreviewButton extends Button {

		private final MapArt artwork;
		private final ArtPieceMenu artworkMenu;

		private PreviewButton(ArtPieceMenu menu, MapArt artwork, Player player, Map<String, String> placeholders) {
			super(Material.FILLED_MAP);
			MapMeta meta = (MapMeta) artwork.getMapItem().getItemMeta();
			meta.setMapView(ArtMap.getMap(artwork.getMapId()));
			List<String> lore = meta.getLore();
			lore.add(HelpMenu.CLICK);
			if (player.hasPermission("artmap.admin")) {
				lore.add(lore.size(), ChatColor.GOLD + Lang.ADMIN_RECIPE.get());
			} else if (artwork.getArtistPlayer().equals(player)) {
				lore.add(ChatColor.GOLD + Lang.RECIPE_PLAYER_MAP_COPY.get());
			}
			meta.setLore(lore);
			setItemMeta(meta);
			MenuItems.apply(this, definition().getItem("preview"), placeholders);
			this.artwork = artwork;
			this.artworkMenu = menu;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {

			if (clickType == ClickType.LEFT) {
				ItemStack offHand = player.getInventory().getItemInOffHand();
				if (offHand.getType() == Material.AIR || isPreviewItem(offHand)) {
					ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.SPECIAL);
					ArtMap.instance().getPreviewManager().startPreview(player, new ArtPreview(artwork));
					ArtMap.instance().getMenuHandler().openMenu(player, this.artworkMenu);
				} else {
					Lang.EMPTY_HAND_PREVIEW.send(player);
				}
			} else if (clickType == ClickType.RIGHT) {
				if (player.hasPermission("artmap.admin")) {
					SoundCompat.BLOCK_CLOTH_FALL.play(player);
					ArtMap.instance().getScheduler().SYNC.run(player, () -> ItemUtils.giveItem(player, artwork.getMapItem()));
				} else if (artwork.getArtistPlayer().equals(player)) {
					if (player.getInventory().contains(Material.MAP)) {
						// remove a map from the player
						HashMap<Integer, ? extends ItemStack> maps = player.getInventory().all(Material.MAP);
						ItemStack map = maps.entrySet().iterator().next().getValue();
						map.setAmount(map.getAmount() - 1);
						ArtMap.instance().getScheduler().SYNC.run(player, () -> ItemUtils.giveItem(player, artwork.getMapItem()));
					} else {
						player.sendMessage(Lang.RECIPE_PLAYER_MAP_COPY_MISSING.get());
					}
				} else {
					Lang.NO_PERM.send(player);
				}
			}
		}
	}

	private static class DeleteButton extends Button {

		private final MapArt artwork;
		private final ArtistArtworksMenu parent;

		private DeleteButton(ArtistArtworksMenu parent, MapArt artwork, MenuItemDefinition item) {
			super(Material.REDSTONE);
			ItemMeta meta = new ItemStack(Material.REDSTONE).getItemMeta();
			meta.setDisplayName(HelpMenu.DELETE_NAME);
			List<String> lore = new ArrayList<>();
			lore.add(HelpMenu.DELETE_TEXT);
			meta.setLore(lore);
			setItemMeta(meta);
			MenuItems.apply(this, item, null);
			this.artwork = artwork;
			this.parent = parent;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {

			if (clickType == ClickType.LEFT) {
				ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);

				ArtMap.instance().getScheduler().SYNC.run(player, () -> {
					ArtMap.instance().getMenuHandler().openMenu(player,
							new DeleteConfirmationMenu(this.parent, this.artwork, false));
				});
			}
		}
	}

	private static class RenameButton extends Button {

		private final MapArt artwork;
		private final ArtistArtworksMenu artworkMenu;

		private RenameButton(ArtistArtworksMenu menu, MapArt artwork, MenuItemDefinition item) {
			super(Material.WRITABLE_BOOK);
			ItemMeta meta = new ItemStack(Material.REDSTONE).getItemMeta();
			meta.setDisplayName(HelpMenu.RENAME_NAME);
			List<String> lore = new ArrayList<>();
			lore.add(HelpMenu.RENAME_TEXT);
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

				if (this.artwork.getArtist().equals(player.getUniqueId()) || player.hasPermission("artmap.admin")) {
					AnvilGUI.Builder gui = new AnvilGUI.Builder();
					gui.plugin(ArtMap.instance()).text(Lang.TITLE_QUESTION.get()).onClick((slot, snapshot) -> {
						//ignore anything that isnt the output slot
						if(slot != AnvilGUI.Slot.OUTPUT) {
							return Collections.emptyList();
						}
						ArtMap.instance().getScheduler().SYNC.run(player, () -> {
							try {
								ArtMap.instance().getArtDatabase().renameArtwork(this.artwork, snapshot.getText());
								player.sendMessage(String.format(Lang.RENAMED.get(), this.artwork.getTitle()));
								ArtMap.instance().getMenuHandler().openMenu(player, this.artworkMenu.getParent(player));
							} catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
								ArtMap.instance().getLogger().log(Level.SEVERE, "Rename Artwork Failure!", e);
								player.sendMessage("Error Renaming Artwork check logs.");
            					return; 
							}
						});
						return Collections.singletonList(AnvilGUI.ResponseAction.close());
					});
					gui.open(player);
				} else {
					player.sendMessage(Lang.NO_PERM.get() + " " + this.artwork.getArtist().equals(player.getUniqueId())
							+ " " + player.hasPermission("artmap.admin"));
				}
			}
		}
	}
}
