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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Heads.HeadsCache;
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
import me.Fupery.ArtMap.Menu.Config.MenuText;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;

public class ArtistMenu extends ListMenu implements ChildMenu {

	private static final String MENU_ID = "artists";
	private final Player viewer;
	private static boolean haveWarnedUser = false;

	// Built once per menu instance; cycling the sort mode only re-orders these rows instead
	// of querying the database and rebuilding every head again.
	private final Object artistLock = new Object();
	private boolean artistsLoaded;
	private boolean statsLoaded;
	private List<ArtworkListButton> cachedArtists = Collections.emptyList();
	private Map<UUID, ArtistStat> cachedStats = Collections.emptyMap();

	public ArtistMenu(Player viewer) {
		super(definition().getTitle(Lang.MenuTitle.MENU_ARTIST.get(), null, header()),
				definition().resolveType(new MenuType(InventoryType.CHEST)), 0, true);
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
		return null;
	}

	@Override
	protected Future<List<Button>> getListItems() {
		FutureTask<List<Button>> task = new FutureTask<> (()->{
			List<ArtworkListButton> buttons;
			Map<UUID, ArtistStat> stats;
			synchronized (artistLock) {
				ensureArtistsLoaded();
				if (sortNeedsStats(getSortMode())) ensureStatsLoaded();
				buttons = new ArrayList<>(cachedArtists);
				stats = cachedStats;
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

	/** Loads the artist rows once. Head textures are read from the cache only, never fetched here. */
	private void ensureArtistsLoaded() {
		if (artistsLoaded) return;
		artistsLoaded = true;

		List<UUID> artists;
		try {
			artists = ArtMap.instance().getArtDatabase().listArtists(this.viewer.getUniqueId());
		} catch (SQLException e) {
			ArtMap.instance().getLogger().log(Level.SEVERE, "Database error!", e);
			return;
		}
		if (artists == null) artists = new ArrayList<>();

		// One head per UUID, with the viewer always present.
		LinkedHashSet<UUID> uniqueArtists = new LinkedHashSet<>();
		uniqueArtists.add(viewer.getUniqueId());
		uniqueArtists.addAll(artists);

		HeadsCache heads = ArtMap.instance().getHeadsCache();
		int notCached = 0;
		for (UUID artist : artists) {
			if (heads != null && !heads.isHeadCached(artist)) notCached++;
		}
		List<ArtworkListButton> buttons = new ArrayList<>(uniqueArtists.size());
		for (UUID artist : uniqueArtists) {
			buttons.add(new ArtworkListButton(artist));
		}

		cachedArtists = buttons;

		if (notCached > 1 && !haveWarnedUser) {
			this.viewer.sendMessage(MessageFormat.format(
					"ArtMap: {0} artist(s) currently not cached you might see some incorrect heads until they are all loaded.", notCached));
			haveWarnedUser = true;
		}
	}

	/** Only the count/date sort modes need the extra table scan. */
	private static boolean sortNeedsStats(String mode) {
		return "artworks".equals(mode) || "artworks-desc".equals(mode)
				|| "newest".equals(mode) || "oldest".equals(mode);
	}

	private void ensureStatsLoaded() {
		if (statsLoaded) return;
		statsLoaded = true;
		try {
			Map<UUID, ArtistStat> stats = ArtMap.instance().getArtDatabase().listArtistStats();
			cachedStats = stats != null ? stats : Collections.emptyMap();
		} catch (SQLException e) {
			ArtMap.instance().getLogger().log(Level.WARNING, "Could not load artist sort data.", e);
		}
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

	private static boolean hasName(String name) {
		return name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name);
	}

	private static String resolveName(UUID artist) {
		if (artist == null) return Lang.ARTWORK_UNKNOWN_ARTIST.get();
		HeadsCache heads = ArtMap.instance().getHeadsCache();
		String name = heads != null ? heads.getPlayerName(artist) : null;
		return hasName(name) ? name : artist.toString();
	}

	public class ArtworkListButton extends Button {

		final UUID artist;
		final String artistName;
		private final String sortName;

		public ArtworkListButton(UUID artist) {
			super(Material.PLAYER_HEAD);
			this.artist = artist;

			String name = resolveName(artist);
			try {
				ItemStack head = ArtMap.instance().getHeadsCache().getCachedHead(artist);
				if (head != null && head.getItemMeta() != null) {
					setItemMeta(head.getItemMeta());
					ItemMeta headMeta = getItemMeta();
					if (headMeta != null && hasName(headMeta.getDisplayName())) {
						name = headMeta.getDisplayName();
					}
				}
			} catch (HeadFetchException | RuntimeException ignored) {
				// A missing texture is not fatal: the row is still usable with its UUID/name.
			}

			SkullMeta meta = (SkullMeta) getItemMeta();
			if (meta == null) {
				meta = (SkullMeta) new ItemStack(Material.PLAYER_HEAD).getItemMeta();
			}
			meta.setDisplayName(name);
			meta.setLore(Collections.singletonList(MenuText.CLICK));
			setItemMeta(meta);

			this.artistName = name;
			this.sortName = sortName(name);

			MenuListDefinition list = definition().getList();
			MenuItemDefinition item = list != null ? list.getOverride(artist.toString()) : null;
			if (item == null && list != null) item = list.getItem();
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("artist", this.artistName);
			MenuItems.applyInPlace(this, item, placeholders);

			ItemMeta applied = getItemMeta();
			if (applied != null && !hasName(applied.getDisplayName())) {
				applied.setDisplayName(name);
				setItemMeta(applied);
			}
		}

		private String sortName(String name) {
			String stripped = ChatColor.stripColor(name);
			return stripped == null || stripped.isEmpty() ? artist.toString() : stripped;
		}

		String sortName() {
			return sortName;
		}

		@Override
		public void onClick(Player player, ClickType clickType) {
			SoundCompat.UI_BUTTON_CLICK.play(player);
			ArtMap.instance().getMenuHandler().openMenu(player,
			        new ArtistArtworksMenu(getMenu(), this.artist, this.artistName, player.hasPermission("artmap.admin"), 0));
		}
	}
}
