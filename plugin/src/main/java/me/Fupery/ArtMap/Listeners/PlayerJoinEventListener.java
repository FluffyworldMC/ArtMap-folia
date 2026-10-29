package me.Fupery.ArtMap.Listeners;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

class PlayerJoinEventListener implements RegisteredListener {
    @EventHandler
    public void onPlayerLogin(PlayerJoinEvent event) {
        // update the playes skin in the cache
        ArtMap.instance().getScheduler().ASYNC.run(() -> 
            ArtMap.instance().getHeadsCache().updateCache(event.getPlayer().getUniqueId())
        );
        if(ArtMap.instance().isDBUpgradeNeeded() && event.getPlayer().hasPermission("artmap.admin")) {
            event.getPlayer().sendMessage(Lang.DATABASE_CONVERSION_NOTICE.get());
        }   
    }

    @Override
    public void unregister() {
        PlayerJoinEvent.getHandlerList().unregister(this);
    }
}