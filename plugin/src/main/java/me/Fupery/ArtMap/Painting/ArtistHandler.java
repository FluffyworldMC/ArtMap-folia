package me.Fupery.ArtMap.Painting;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import me.Fupery.ArtMap.api.Exception.ArtMapException;
import me.Fupery.ArtMap.api.Exception.DuplicateArtworkException;
import me.Fupery.ArtMap.api.Exception.PermissionException;
import me.Fupery.ArtMap.Easel.Canvas;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Easel.EaselEffect;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.IO.TitleFilter;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.ArtistPacket.PacketInteract.InteractType;
import me.Fupery.ArtMap.IO.Protocol.In.Packet.PacketType;
import me.Fupery.ArtMap.Painting.Brush.BrushAction;
import me.Fupery.ArtMap.Painting.Artkit.ArtKitMenu;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitItems;
import me.Fupery.ArtMap.Recipe.ArtMaterial;
import me.Fupery.ArtMap.Utils.ItemUtils;
import me.Fupery.ArtMap.api.Painting.IArtistHandler;

public class ArtistHandler implements IArtistHandler {

	private final ConcurrentHashMap<UUID, ArtSession> artists;

	public ArtistHandler() {
		artists = new ConcurrentHashMap<>();
	}

	public boolean handlePacket(Player sender, ArtistPacket packet) {
		if (packet == null || sender == null) {
			return true;
		}
		if (artists.containsKey(sender.getUniqueId())) {
			ArtSession session = artists.get(sender.getUniqueId());
			PacketType type = packet.getType();

			if (type == PacketType.LOOK) {
				ArtistPacket.PacketLook packetLook = (ArtistPacket.PacketLook) packet;
				session.updatePosition(packetLook.getYaw(), packetLook.getPitch());
				return true;
				// Handle Save brush
			} else if ((type == PacketType.INTERACT || type == PacketType.ARM_ANIMATION) && ArtkitItems.isBrush(
					sender.getInventory().getItemInMainHand())) {
				return this.handleArtkitPacket(sender, session, packet, type);
			} else if (type == PacketType.ARM_ANIMATION) {
				// Through MC 1.21.11 the client sent USE_ENTITY ATTACK for a canvas left-click; from 26.1 it only
				// swings (26.3: punches, which arrives here as the same packet), so treat the swing as a click (#373).
				// Trailing swings are absorbed by the brush cooldown.
				session.paint(sender.getInventory().getItemInMainHand(), BrushAction.LEFT_CLICK);
				session.sendMap(sender);
				return true;
			} else if (type == PacketType.INTERACT) {
				InteractType click = ((ArtistPacket.PacketInteract) packet).getInteraction();
				session.paint(sender.getInventory().getItemInMainHand(),
						(click == InteractType.ATTACK) ? BrushAction.LEFT_CLICK : BrushAction.RIGHT_CLICK);
				session.sendMap(sender);
				return false;
			}
		} else {
			try {
				removePlayer(sender);
			} catch (SQLException | IOException e) {
				ArtMap.instance().getLogger().log(Level.SEVERE, "Error exiting art session!", e);
			}
		}
		return true;
	}

    private boolean handleArtkitPacket(Player sender, ArtSession session, ArtistPacket packet, PacketType type) {
        if (type == PacketType.ARM_ANIMATION) {
            ArtMap.instance().getScheduler().SYNC.run(sender, () -> {
                session.paint(sender.getInventory().getItemInMainHand(), BrushAction.LEFT_CLICK);
                session.sendMap(sender);
            });
            return true;
        }
        InteractType click = ((ArtistPacket.PacketInteract) packet).getInteraction();
        if (click == InteractType.ATTACK) {
            ArtMap.instance().getScheduler().SYNC.run(sender, () -> {
                session.paint(sender.getInventory().getItemInMainHand(), BrushAction.LEFT_CLICK);
                session.sendMap(sender);
            });
            return false;
        }
        ArtMap.instance().getScheduler().SYNC.run(sender, () -> {
            session.cancelStroke();
            ArtMap.instance().getMenuHandler().openMenu(sender, new ArtKitMenu(sender, 0));
        });
        return false;
    }

	public void openSaveDialog(Player sender, ArtSession session) {
		if (ArtMap.instance().getConfiguration().DISABLE_PAINTBRUSH) {
			Lang.PAINTBRUSH_DISABLED.send(sender);
			return;
		}
		if (sender.isInsideVehicle() && containsPlayer(sender)) {
			ArtMap.instance().getScheduler().SYNC.run(sender, () -> showSaveDialog(sender, session));
		}
	}

	private void showSaveDialog(Player player, ArtSession session) {
		if (!player.isOnline()) {
			return;
		}
		Component title = text(Lang.SAVE_DIALOG_TITLE.get(), "Save Artwork");
		Component body = text(Lang.SAVE_DIALOG_BODY.get(), "Enter a title between 3 and 16 characters.");
		Component label = text(Lang.SAVE_DIALOG_LABEL.get(), "Artwork title");
		Dialog dialog = Dialog.create(builder -> builder.empty()
				.base(DialogBase.builder(title)
						.body(List.of(DialogBody.plainMessage(body)))
						.inputs(List.of(DialogInput.text("title", label)
								.maxLength(16)
								.width(300)
								.build()))
						.build())
				.type(DialogType.confirmation(
						ActionButton.create(
								text(Lang.SAVE_DIALOG_SAVE.get(), "Save"),
								text(Lang.SAVE_DIALOG_SAVE_HOVER.get(), "Save this artwork"),
								150,
								DialogAction.customClick((view, audience) -> {
									if (!(audience instanceof Player clicker)) {
										return;
									}
									String entered = view.getText("title");
									clicker.closeDialog();
									ArtMap.instance().getScheduler().SYNC.run(clicker,
											() -> saveArtwork(clicker, session, entered));
								}, ClickCallback.Options.builder().uses(1).build())),
						ActionButton.create(
								text(Lang.SAVE_DIALOG_CANCEL.get(), "Cancel"),
								text(Lang.SAVE_DIALOG_CANCEL_HOVER.get(), "Cancel saving"),
								150,
								null))));
		player.showDialog(dialog);
	}

	private void saveArtwork(Player player, ArtSession session, String enteredTitle) {
		if (!player.isOnline() || !containsPlayer(player) || getCurrentSession(player) != session) {
			return;
		}
		String title = enteredTitle == null ? null : enteredTitle.trim();
		TitleFilter filter = new TitleFilter(Lang.Filter.ILLEGAL_EXPRESSIONS.get());
		if (title == null || !filter.check(title)) {
			player.sendMessage(Lang.BAD_TITLE.get());
			return;
		}
		Easel easel = session.getEasel();
		try {
			Canvas canvas = Canvas.getCanvas(easel.getItem())
					.orElseThrow(() -> new ArtMapException("Failed to retrieve canvas!"));
			MapArt art = ArtMap.instance().getArtDatabase().saveArtwork(canvas, title, player);
			removePlayer(player);
			easel.setItem(new ItemStack(Material.AIR));
			ItemUtils.giveItem(player, art.getMapItem());
			player.sendMessage(String.format(Lang.PREFIX + Lang.SAVE_SUCCESS.get(), title));
			easel.playEffect(EaselEffect.SAVE_ARTWORK);
		} catch (DuplicateArtworkException | PermissionException e) {
			player.sendMessage(e.getMessage());
		} catch (SQLException | IOException | NoSuchFieldException | IllegalAccessException | ArtMapException e) {
			player.sendMessage(String.format(Lang.PREFIX + Lang.SAVE_FAILURE.get(), title));
			ArtMap.instance().getLogger().log(Level.SEVERE, "Error saving artwork!", e);
		}
	}

	private static Component text(String value, String fallback) {
		return LegacyComponentSerializer.legacySection().deserialize(value != null ? value : fallback);
	}

	public void addPlayer(final Player player, Easel easel, Map map, int yawOffset)
			throws ReflectiveOperationException, SQLException, IOException {
		ArtSession session = new ArtSession(player, easel, map, yawOffset);
		if (session.start(player)) {
			ArtMap.instance().getProtocolManager().getPacketReceiver().injectPlayer(player);
			artists.put(player.getUniqueId(), session);
			session.setActive(true);
		}
	}

	public Easel getEasel(Player player) {
		if (artists.containsKey(player.getUniqueId())) {
			return artists.get(player.getUniqueId()).getEasel();
		}
		return null;
	}

	public boolean containsPlayer(Player player) {
		if(player==null) {
			return false;
		}
		return (artists.containsKey(player.getUniqueId()));
	}

	public boolean containsPlayer(UUID player) {
		if(player==null) {
			return false;
		}
		return artists.containsKey(player);
	}

	public void removePlayer(final Player player) throws SQLException, IOException {
		if (player == null)
			return;// just for safety :)
		// Remove the map entry first: bailing out on !isActive() stranded players in `artists` and cancelled
		// all their USE_ENTITY packets, so item frames silently stopped working (#376).
		ArtSession session = artists.remove(player.getUniqueId());
		if (session == null)
			return;
		try {
			if (session.isActive())
				session.end(player);
		} finally {
			ArtMap.instance().getProtocolManager().getPacketReceiver().uninjectPlayer(player);
		}
	}

	public ArtSession getCurrentSession(Player player) {
		return artists.get(player.getUniqueId());
	}

	public ArtSession getCurrentSession(UUID player) {
		return artists.get(player);
	}

	private void clearPlayers() {
		for (UUID uuid : artists.keySet()) {
			try {
				removePlayer(Bukkit.getPlayer(uuid));
			} catch (SQLException | IOException e) {
				ArtMap.instance().getLogger().log(Level.SEVERE,"Error clearing players art session!",e);
			}
		}
	}

	public Set<UUID> getArtists() {
		return artists.keySet();
	}

	public void stop() {
		clearPlayers();
		ArtMap.instance().getProtocolManager().getPacketReceiver().close();
	}
}