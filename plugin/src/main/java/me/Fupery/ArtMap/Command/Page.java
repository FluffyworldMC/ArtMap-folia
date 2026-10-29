package me.Fupery.ArtMap.Command;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.Fupery.ArtMap.Menu.API.TextPagination;
import me.Fupery.ArtMap.api.Config.Lang;

/** Page command handles pagination requests from other commands. */
public class Page extends AsyncCommand {

    private static Map<UUID,TextPagination> pageMap = new HashMap<>();

    Page() {
        //artmap page <playerid> <page>
        super(null, "/art page <playerid> <page>", false);
    }

    public static void startPagination(Player player, TextPagination pages) {
        pageMap.put(player.getUniqueId(), pages);
        pages.sendPlayerPage(player, 1);
    }

    @Override
    public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
        // args[0] is export
        if (args.length < 2) {
            msg.message = Lang.PAGE_SYNTAX_ERROR.get();
            return;
        }

        UUID playerId = null;
        int pageNum = 1;
        try {
            playerId = UUID.fromString(args[1]);
            pageNum = Integer.parseInt(args[2]);
        } catch(Exception e) {
            msg.message = Lang.PAGE_FORMAT_ERROR.get();
        }

        Player player = Bukkit.getPlayer(playerId);
        if(player == null) {
            msg.message = Lang.PAGE_PLAYER_NOT_FOUND.get();
            return;
        }

        TextPagination pages = pageMap.get(playerId);
        if(pages == null) {
            msg.message = Lang.PAGE_EXPIRED.get();
            return;
        }
        pages.sendPlayerPage(player, pageNum);
    }

}
