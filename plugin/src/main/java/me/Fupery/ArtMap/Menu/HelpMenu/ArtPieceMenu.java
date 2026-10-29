package me.Fupery.ArtMap.Menu.HelpMenu;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.TitleFilter;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.ListMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.API.SoundCompat;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuButtons;
import me.Fupery.ArtMap.Menu.Config.MenuText;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.Preview.ArtPreview;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Utils.ItemUtils;

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
		return ArtItem.isPreviewItem(item);
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
		MenuButtons.placeNavigation(definition, viewer, placeholders, buttons);
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
			setItemMeta(meta);
			MenuItems.applyInPlace(this, definition().getItem("preview"), placeholders);
			if (player.hasPermission("artmap.admin")) {
				addLore(Lang.ADMIN_RECIPE.get());
			} else if (artwork.getArtistPlayer().equals(player)) {
				addLore(Lang.RECIPE_PLAYER_MAP_COPY.get());
			}
			this.artwork = artwork;
			this.artworkMenu = menu;
		}

		private void addLore(String line) {
			if (line == null) return;
			ItemMeta meta = getItemMeta();
			List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
			lore.add(line);
			meta.setLore(lore);
			setItemMeta(meta);
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
			meta.setDisplayName(MenuText.DELETE_NAME);
			List<String> lore = new ArrayList<>();
			lore.add(MenuText.DELETE_TEXT);
			meta.setLore(lore);
			setItemMeta(meta);
			MenuItems.applyInPlace(this, item, null);
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
			ItemMeta meta = new ItemStack(Material.WRITABLE_BOOK).getItemMeta();
			meta.setDisplayName(MenuText.RENAME_NAME);
			List<String> lore = new ArrayList<>();
			lore.add(MenuText.RENAME_TEXT);
			meta.setLore(lore);
			setItemMeta(meta);
			MenuItems.applyInPlace(this, item, null);
			this.artwork = artwork;
			this.artworkMenu = menu;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {
			if (clickType != ClickType.LEFT) {
				return;
			}
			ArtMap.instance().getMenuHandler().closeMenu(player, MenuCloseReason.DONE);

			if (!this.artwork.getArtist().equals(player.getUniqueId()) && !player.hasPermission("artmap.admin")) {
				Lang.NO_PERM.send(player);
				return;
			}
			ArtMap.instance().getScheduler().SYNC.run(player, () -> showDialog(player));
		}

		private void showDialog(Player player) {
			if (!player.isOnline()) {
				return;
			}
			Component title = text(Lang.RENAME_DIALOG_TITLE.get(), "Rename Artwork");
			Component body = text(Lang.RENAME_DIALOG_BODY.get(),
					"Enter a new title between 3 and 16 characters.");
			Component label = text(Lang.RENAME_DIALOG_LABEL.get(), "Artwork title");
			Dialog dialog = Dialog.create(builder -> builder.empty()
					.base(DialogBase.builder(title)
							.body(List.of(DialogBody.plainMessage(body)))
							.inputs(List.of(DialogInput.text("title", label)
									.initial(this.artwork.getTitle())
									.maxLength(16)
									.width(300)
									.build()))
							.build())
					.type(DialogType.confirmation(
							ActionButton.create(
									text(Lang.RENAME_DIALOG_SAVE.get(), "Rename"),
									text(Lang.RENAME_DIALOG_SAVE_HOVER.get(), "Rename this artwork"),
									150,
									DialogAction.customClick((view, audience) -> {
										if (!(audience instanceof Player clicker)) {
											return;
										}
										String entered = view.getText("title");
										clicker.closeDialog();
										ArtMap.instance().getScheduler().SYNC.run(clicker,
												() -> rename(clicker, entered));
									}, ClickCallback.Options.builder().uses(1).build())),
							ActionButton.create(
									text(Lang.RENAME_DIALOG_CANCEL.get(), "Cancel"),
									text(Lang.RENAME_DIALOG_CANCEL_HOVER.get(), "Cancel renaming"),
									150,
									null))));
			player.showDialog(dialog);
		}

		private void rename(Player player, String enteredTitle) {
			if (!player.isOnline()) {
				return;
			}
			String title = enteredTitle == null ? null : enteredTitle.trim();
			TitleFilter filter = new TitleFilter(Lang.Filter.ILLEGAL_EXPRESSIONS.get());
			if (title == null || !filter.check(title)) {
				player.sendMessage(Lang.BAD_TITLE.get());
				return;
			}
			try {
				ArtMap.instance().getArtDatabase().renameArtwork(this.artwork, title);
				player.sendMessage(String.format(Lang.RENAMED.get(), title));
				ArtMap.instance().getMenuHandler().openMenu(player, this.artworkMenu.getParent(player));
			} catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
				ArtMap.instance().getLogger().log(Level.SEVERE, "Rename Artwork Failure!", e);
				player.sendMessage(Lang.ARTWORK_RENAME_ERROR.get());
			}
		}

		private static Component text(String value, String fallback) {
			return LegacyComponentSerializer.legacySection().deserialize(value != null ? value : fallback);
		}
	}
}
