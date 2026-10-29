package me.Fupery.ArtMap.Menu.Config;

import org.bukkit.ChatColor;

import me.Fupery.ArtMap.api.Config.Lang;

/** Shared menu labels, editable through lang.yml with English fallbacks. */
public final class MenuText {

    public static final String CLICK = ChatColor.GREEN + lang(Lang.BUTTON_CLICK.get(), "Click to View");
    public static final String DELETE_NAME = ChatColor.RED + lang(Lang.BUTTON_DELETE_NAME.get(), "Delete");
    public static final String DELETE_TEXT = ChatColor.RED
            + lang(Lang.BUTTON_DELETE_TEXT.get(), "Clicking this will delete every copy of the artwork in the world!");
    public static final String RENAME_NAME = ChatColor.GREEN + lang(Lang.BUTTON_RENAME_NAME.get(), "Rename");
    public static final String RENAME_TEXT = ChatColor.GREEN + lang(Lang.BUTTON_RENAME_TEXT.get(), "Click to Rename");
    public static final String ACCEPT_NAME = ChatColor.GREEN + lang(Lang.BUTTON_ACCEPT_NAME.get(), "Accept");
    public static final String ACCEPT_TEXT = ChatColor.GREEN + lang(Lang.BUTTON_ACCEPT_TEXT.get(), "Click to Accept");
    public static final String CLOSE_NAME = ChatColor.GREEN + lang(Lang.BUTTON_CLOSE.get(), "Close");

    private MenuText() {
    }

    private static String lang(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
