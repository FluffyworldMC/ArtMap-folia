package me.Fupery.ArtMap.Menu.HelpMenu;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.SkullMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.ArtistStat;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.api.Exception.HeadFetchException;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.API.ListMenu;
import me.Fupery.ArtMap.Menu.API.MenuType;
import me.Fupery.ArtMap.Menu.API.SoundCompat;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Config.MenuDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItemDefinition;
import me.Fupery.ArtMap.Menu.Config.MenuItems;
import me.Fupery.ArtMap.Menu.Config.MenuListDefinition;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;

public class ArtistMenu extends ListMenu implements ChildMenu {

	private static final String MENU_ID = "artists";
	private final Player viewer;
	private static boolean haveWarnedUser = false;

	public ArtistMenu(Player viewer) {
		super(definition().getTitle(Lang.MenuTitle.MENU_ARTIST.get(), null, header()),
				definition().resolveType(new MenuType(InventoryType.CHEST)),
				ArtMap.instance().getMenuHandler().MENU.HELP, 0, true);
		this.viewer = viewer;
	}

	private static MenuDefinition definition() {
		return ArtMap.instance().getMenuConfig().get(MENU_ID);
	}

	private static String header() {
		return ArtMap.instance().getMenuConfig().getHeader();
	}

	@Override
	protected String getMenuId() {
		return MENU_ID;
	}

	@Override
	public CacheableMenu getParent(Player viewer) {
		return ArtMap.instance().getMenuHandler().MENU.HELP.get(viewer);
	}

	@Override
	protected Future<List<Button>> getListItems() {
		FutureTask<List<Button>> task = new FutureTask<> (()->{
			List<UUID> artists;
			try {
				artists = ArtMap.instance().getArtDatabase().listArtists(this.viewer.getUniqueId());
			} catch (SQLException e) {
				ArtMap.instance().getLogger().log(Level.SEVERE, "Database error!", e);
				return new ArrayList<>();
			}
			if (artists == null) artists = new ArrayList<>();
			Map<UUID, ArtistStat> stats = Collections.emptyMap();
			try {
				stats = ArtMap.instance().getArtDatabase().listArtistStats();
				if (stats == null) stats = Collections.emptyMap();
			} catch (SQLException e) {
				ArtMap.instance().getLogger().log(Level.WARNING, "Could not load artist sort data.", e);
			}

			// One head per UUID, with the viewer always present.
			LinkedHashSet<UUID> uniqueArtists = new LinkedHashSet<>();
			uniqueArtists.add(viewer.getUniqueId());
			uniqueArtists.addAll(artists);

			int notCached = artists.size() - ArtMap.instance().getHeadsCache().getCacheSize();
			if (notCached > 1 && !haveWarnedUser) {
				this.viewer.sendMessage(MessageFormat.format(
						"ArtMap: {0} artist(s) currently not cached you might see some incorrect heads until they are all loaded.", notCached));
				haveWarnedUser = true;
			}

			List<ArtworkListButton> buttons = new ArrayList<>();
			for (UUID artist : uniqueArtists) {
				try {
					buttons.add(new ArtworkListButton(artist, true));
				} catch (HeadFetchException e) {
					//try again with the fetch set to false as it will fail over and over
					try {
						buttons.add(new ArtworkListButton(artist, false));
					} catch (HeadFetchException e1) {
						// this one won't fail
					}
				}
			}

			MenuListDefinition list = definition().getList();
			boolean pinViewer = list == null || list.isPinViewer();
			ArtworkListButton viewerButton = null;
			if (pinViewer) {
				for (int i = 0; i < buttons.size(); i++) {
					if (buttons.get(i).artist.equals(viewer.getUniqueId())) {
						viewerButton = buttons.remove(i);
						break;
					}
				}
			}
			buttons.sort(comparator(getSortMode(), stats));

			List<Button> result = new ArrayList<>(buttons.size() + 1);
			if (viewerButton != null) result.add(viewerButton);
			result.addAll(buttons);
			return result;
		});
		ArtMap.instance().getScheduler().ASYNC.run(task);
		return task;
	}

	private Comparator<ArtworkListButton> comparator(String mode, Map<UUID, ArtistStat> stats) {
		Comparator<ArtworkListButton> byName = Comparator.comparing(ArtworkListButton::sortName,
				String.CASE_INSENSITIVE_ORDER);
		if (mode == null) return byName;
		switch (mode) {
			case "name-desc":
				return byName.reversed();
			case "artworks":
				return Comparator.comparingInt((ArtworkListButton button) -> artworks(stats, button.artist))
						.reversed().thenComparing(byName);
			case "artworks-desc":
				return Comparator.comparingInt((ArtworkListButton button) -> artworks(stats, button.artist))
						.thenComparing(byName);
			case "newest":
				return Comparator.comparingLong((ArtworkListButton button) -> newestDay(stats, button.artist))
						.reversed().thenComparing(byName);
			case "oldest":
				return Comparator.comparingLong((ArtworkListButton button) -> oldestDay(stats, button.artist))
						.thenComparing(byName);
			case "name":
			default:
				return byName;
		}
	}

	private static int artworks(Map<UUID, ArtistStat> stats, UUID artist) {
		ArtistStat stat = stats.get(artist);
		return stat != null ? stat.getArtworks() : 0;
	}

	private static long newestDay(Map<UUID, ArtistStat> stats, UUID artist) {
		ArtistStat stat = stats.get(artist);
		return stat != null ? stat.getNewestDay() : Long.MIN_VALUE;
	}

	private static long oldestDay(Map<UUID, ArtistStat> stats, UUID artist) {
		ArtistStat stat = stats.get(artist);
		return stat != null ? stat.getOldestDay() : Long.MAX_VALUE;
	}

	public Player getViewer() {
		return Bukkit.getPlayer(this.viewer.getUniqueId());
	}

	private ArtistMenu getMenu() {
		return this;
	}

	public class ArtworkListButton extends Button {

		final UUID artist;
		final String artistName;
		//TODO: Fix this when we rearrange the database.  Get the artist name from the database and not try and pull it off the meta
		public ArtworkListButton(UUID artist, boolean loadHeads) throws HeadFetchException {
			super(org.bukkit.Material.PLAYER_HEAD);
			this.artist = artist;

			SkullMeta meta = (SkullMeta) getItemMeta();
			String name = artist.toString();
			meta.setDisplayName(name);
			if(loadHeads) {
				meta = (SkullMeta) ArtMap.instance().getHeadsCache().getHead(artist).getItemMeta();
				String fetched = meta.getDisplayName();
				if (fetched != null && !fetched.isEmpty()) name = fetched;
			}
			this.artistName = name;
			meta.setLore(Collections.singletonList(HelpMenu.CLICK));
			setItemMeta(meta.clone());

			MenuListDefinition list = definition().getList();
			MenuItemDefinition item = list != null ? list.getOverride(artist.toString()) : null;
			if (item == null && list != null) item = list.getItem();
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("artist", this.artistName);
			MenuItems.apply(this, item, placeholders);
		}

		String sortName() {
			String name = ChatColor.stripColor(artistName);
			return name == null || name.isEmpty() ? artist.toString() : name;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {
			SoundCompat.UI_BUTTON_CLICK.play(player);
			ArtMap.instance().getMenuHandler().openMenu(player,
			        new ArtistArtworksMenu(getMenu(), this.artist, this.artistName, player.hasPermission("artmap.admin"), 0));
		}
	}
}
