package me.Fupery.ArtMap.Listeners;

import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.MapInitializeEvent;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.IO.Database.Map;

public class MapInitializeListener implements Runnable, RegisteredListener {

    private static final Queue<Integer> mapQueue = new LinkedList<>();
    private MyScheduledTask runnable = null;
    private boolean scheduled = false;

    private synchronized void createRunner() {
        if (!scheduled) {
            scheduled = true;
            ArtMap.instance().getLogger().info("Created Artmap map initialize queue task.");
            runnable = ArtMap.instance().getScheduler().ASYNC.runTimer(this, 0, 20);
        }
    }

    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) {
        createRunner();
        Integer mapId = event.getMap().getId();
        if(!mapQueue.contains(mapId)) { //check that we are not inserting duplicates
            mapQueue.add(mapId);
        }
    }

    @Override
    public void unregister() {
        MapInitializeEvent.getHandlerList().unregister(this);
        scheduled = false;
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
    }

    @Override
    public void run() {
        int stop = 0;
        while (!mapQueue.isEmpty()) {
            Integer mapId = mapQueue.poll();
            if(mapId == null) {
                return; //this shouldn't happen but lets be safe
            }
            try {
                stop = 0;
                if (!ArtMap.instance().getArtDatabase().containsArtwork(mapId))
                    continue; // a vanilla map must not hold up the artworks queued behind it
                stop = 1;
                Map map = new Map(mapId);
                stop = 2;
                ArtMap.instance().getArtDatabase().restoreMap(map, true, false);
            } catch (Exception e) {
                ArtMap.instance().getLogger().log(Level.SEVERE, "Error with map restore! Stop="+stop, e);
            }
        }
    }
}
