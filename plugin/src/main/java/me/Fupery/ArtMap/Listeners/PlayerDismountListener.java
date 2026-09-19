package me.Fupery.ArtMap.Listeners;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Painting.ArtSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.spigotmc.event.entity.EntityDismountEvent;

public class PlayerDismountListener implements RegisteredListener {

    @EventHandler
    public void onPlayerDismount(EntityDismountEvent event) {

        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (ArtMap.instance().getArtistHandler().containsPlayer(player)) {
            ArtSession session = ArtMap.instance().getArtistHandler().getCurrentSession(player);
            try {
                //[#292] don't dismount players that painted within the last second.
                // Only honour this while the seat still exists: if the easel is being
                // removed, cancelling the dismount cannot put the player back on a
                // dead entity, and returning here would leave them in the artists map
                // forever with their entity interactions silently cancelled (#376).
                if(session.lastPaintActionWithin(1, TimeUnit.SECONDS)
                        && event.getDismounted() != null && event.getDismounted().isValid()) {
                    event.setCancelled(true);
                    return;
                }
                ArtMap.instance().getArtistHandler().removePlayer(player);
            } catch (SQLException | IOException e) {
                ArtMap.instance().getLogger().log(Level.SEVERE, "Database error!", e);
                player.sendMessage("Error Saving Artwork check logs.");
            }
        }
    }

    @Override
    public void unregister() {
        EntityDismountEvent.getHandlerList().unregister(this);
    }
}
