package me.Fupery.ArtMap.Preview;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Utils.Scheduler;
import org.bukkit.entity.Player;

import java.util.UUID;

abstract class TimedPreview implements Preview {
    private Scheduler.TaskHandler timeout;

    @Override
    public boolean start(Player player) {
        timeout = ArtMap.instance().getScheduler().getTaskHandler(player, new Timeout(player.getUniqueId()));
        timeout.runLater(false, 300);
        return true;
    }

    @Override
    public boolean end(Player player) {
        if (timeout != null) {
            timeout.cancel();
            timeout = null;
        }
        return true;
    }

    private static class Timeout implements Runnable {

        private final UUID player;

        public Timeout(UUID player) {
            this.player = player;
        }

        @Override
        public void run() {
            ArtMap.instance().getPreviewManager().endPreview(player);
        }
    }
}
