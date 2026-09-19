package me.Fupery.ArtMap.Preview;

import me.Fupery.ArtMap.ArtMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.UUID;

public class PreviewManager {
    private HashMap<UUID, Preview> activePreviews;

    public PreviewManager() {
        this.activePreviews = new HashMap<>();
    }

    public void checkAllowed(Player player, Event event) {
        if (!isPreviewing(player.getUniqueId())) return;
        if (!getPreview(player).isEventAllowed(player.getUniqueId(), event)) endPreview(player.getUniqueId());
    }

    public boolean isPreviewing(UUID player) {
        return activePreviews.containsKey(player);
    }

    public boolean isPreviewing(Player player) {
        return activePreviews.containsKey(player.getUniqueId());
    }

    private Preview getPreview(Player player) {
        return activePreviews.get(player.getUniqueId());
    }

    public boolean startPreview(Player player, Preview preview) {
        if (isPreviewing(player.getUniqueId())) endPreview(player.getUniqueId());
        if (!preview.start(player)) return false;
        activePreviews.put(player.getUniqueId(), preview);
        return true;
    }

    public void endAllPreviews() {
        // Iterate a copy: endPreview() removes from the map, and the scheduler can run the task on the same tick.
        for (UUID uuid : new java.util.ArrayList<>(activePreviews.keySet())) {
            endPreview(uuid);
        }
    }

    void endPreview(UUID uuid) {
        ArtMap.instance().getScheduler().SYNC.run(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                // Scheduled 300 ticks out; if the player logged off, Bukkit.getPlayer is null. Drop the stale
                // entry rather than dereferencing it, or their next clicks stay swallowed as "previewing".
                activePreviews.remove(uuid);
                return;
            }
            endPreview(player);
        });
    }

    public boolean endPreview(Player player) {
        if (player == null || !isPreviewing(player.getUniqueId())) {
            return false;
        }
        Preview preview = getPreview(player);
        activePreviews.remove(player.getUniqueId());
        return preview.end(player);
    }
}
