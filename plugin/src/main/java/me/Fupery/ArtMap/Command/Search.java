package me.Fupery.ArtMap.Command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.API.TextPagination;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Search extends AsyncCommand {

    Search() {
        super(null, "/art search [--player <playername> | --mine] <searchterm>", false);
    }

    @Override
    public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
        // args[0] is search
        if (args.length < 2) {
            msg.message = Lang.SEARCH_USAGE.get();
            return;
        }

        String searchTerm = "";
        String playerName = null;
        UUID playerId = null;
        boolean mine = false;
        Player pSender = (Player) sender;

        if("--mine".equals(args[1].trim())) {
            mine = true;
            if(args.length>2) {
                searchTerm = args[2].trim();
            }
            playerId = pSender.getUniqueId();
        } else if("--player".equals(args[1].trim())) {
            // `/art search --player` with no name still clears the length<2 guard
            // above, so the player name has to be checked for separately.
            if(args.length < 3) {
                msg.message = Lang.SEARCH_USAGE.get();
                return;
            }
            playerName = args[2].trim();
            Optional<UUID> optPlayerId = ArtMap.instance().getHeadsCache().getPlayerUUID(playerName);
            if(!optPlayerId.isPresent()) {
                msg.message = String.format(Lang.SEARCH_PLAYER_NOT_FOUND.get(), playerName);
                return;
            } else {
                playerId = optPlayerId.get();
            }
            if(args.length>3) {
                searchTerm = args[3].trim();
            }
        } else {
            searchTerm = args[1];
        }

        
        List<MapArt> art = new ArrayList<>();
        try {
            art = ArtMap.instance().getArtDatabase().searchArtworks(searchTerm,playerId);
        } catch (SQLException e) {
            Lang.GENERIC_ERROR.send(sender);
            ArtMap.instance().getLogger().log(Level.SEVERE,"SQL Exception on search.",e);
        }

        List<TextComponent> text = new ArrayList<>();

        if(!mine && playerName == null) {
            List<String> artists = ArtMap.instance().getArtDatabase().searchArtists(searchTerm);
            text.add(component(String.format(Lang.SEARCH_ARTISTS_HEADER.get(), artists.size())));
            //clickable links for each artist
            for(String artist : artists) {
                text.add(this.clickableArtist(artist));
            }
        }
        text.add(component(String.format(Lang.SEARCH_ARTWORKS_HEADER.get(), art.size())));
        //clickable links for each artwork
        for(MapArt artwork : art) {
            text.add(this.clickableArtwork(pSender,artwork));
        }

        TextPagination pages = new TextPagination(text.toArray(new TextComponent[text.size()]), Lang.SEARCH_TITLE.get(), pSender.getUniqueId());
        Page.startPagination(pSender, pages);
    }

    private TextComponent clickableArtist(String artist) {
        TextComponent text = component(Lang.SEARCH_ARTIST_BUTTON.get().replace("{artist}", artist));
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/artmap search --player " + artist);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{ new TextComponent(Lang.SEARCH_ARTIST_HOVER.get()) });
        applyEvents(text, click, hover);
        return text;
    }

    private TextComponent clickableArtwork(Player user, MapArt art) {
        TextComponent text = component(Lang.SEARCH_ARTWORK_TITLE.get().replace("{title}", art.getTitle()));
        // The name comes from the heads cache and is null for an artist it never cached.
        String artistName = art.getArtistName() != null ? art.getArtistName() : "?";
        text.addExtra(component(Lang.SEARCH_BY.get() + artistName));
        int textLen = art.getTitle().length() + artistName.length();
        StringBuilder sb = new StringBuilder();
        for(int i=textLen; i<(50-textLen); i++) {
            sb.append(Lang.SEARCH_FILLER.get());
        }
        text.addExtra(component(sb.toString()));
        TextComponent previewButton = component(" [" + Lang.SEARCH_PREVIEW_BUTTON.get() + "] ");
        applyEvents(previewButton,
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/art preview \"" + art.getTitle() + "\""),
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{
                        new TextComponent(Lang.SEARCH_PREVIEW_HOVER.get()) }));
        text.addExtra(previewButton);
        if(user.hasPermission("artmap.admin")) {
            //"/art give <player> <easel|canvas|paintbrush|artwork:<title>> [amount]"
            TextComponent giveButton = component(" [" + Lang.SEARCH_GIVE_BUTTON.get() + "] ");
            applyEvents(giveButton,
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/art give " + user.getName() + " \"artwork:" + art.getTitle() + "\""),
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{
                            new TextComponent(Lang.SEARCH_GIVE_HOVER.get()) }));
            text.addExtra(giveButton);
        }
        return text;
    }

    /** Builds a component from a lang value, so the colour codes live in lang.yml. */
    private static TextComponent component(String legacy) {
        return new TextComponent(TextComponent.fromLegacyText(legacy));
    }

    /** TextComponent.fromLegacyText returns several parts; click/hover have to be set on each. */
    private static void applyEvents(TextComponent component, ClickEvent click, HoverEvent hover) {
        component.setClickEvent(click);
        component.setHoverEvent(hover);
        if (component.getExtra() != null) {
            for (BaseComponent extra : component.getExtra()) {
                extra.setClickEvent(click);
                extra.setHoverEvent(hover);
            }
        }
    }

}
