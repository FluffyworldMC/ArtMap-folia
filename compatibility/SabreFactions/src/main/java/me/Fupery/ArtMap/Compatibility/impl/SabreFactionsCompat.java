package me.Fupery.ArtMap.Compatibility.impl;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import me.Fupery.ArtMap.api.Compatability.RegionHandler;
import me.Fupery.ArtMap.api.Easel.ClickType;

/** Support for Sabre Factions, apparently a fork of an old MassiveCraft Factions. It does not appear to
 *  have player permissions, or the wiki is too sparse to show them. */
public class SabreFactionsCompat implements RegionHandler {
    private boolean loaded = false;

    public SabreFactionsCompat() {
        Factions.getInstance();
        loaded = true;
    }

    @Override
    public boolean checkBuildAllowed(Player player, Location location) {
        FLocation loc = new FLocation(location);
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        Faction hostFaction = Board.getInstance().getFactionAt(loc);
        return hostFaction == null || hostFaction.getFPlayers().contains(fPlayer);
    }

    @Override
    public boolean checkInteractAllowed(Player player, Entity entity, ClickType click) {
        FLocation loc = new FLocation(entity.getLocation());
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        Faction hostFaction = Board.getInstance().getFactionAt(loc);
        return hostFaction == null || hostFaction.getFPlayers().contains(fPlayer);
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
