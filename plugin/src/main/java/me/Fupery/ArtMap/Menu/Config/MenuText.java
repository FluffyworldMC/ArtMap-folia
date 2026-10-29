package me.Fupery.ArtMap.Menu.Config;

import me.Fupery.ArtMap.api.Config.Lang;

/** Shared menu labels, editable through lang.yml with English fallbacks. */
public final class MenuText {

    public static final String CLICK = lang(Lang.BUTTON_CLICK.get(), "§aClick to View");
    public static final String DELETE_NAME = lang(Lang.BUTTON_DELETE_NAME.get(), "§cDelete");
    public static final String DELETE_TEXT = lang(Lang.BUTTON_DELETE_TEXT.get(),
            "§cClicking this will delete every copy of the artwork in the world!");
    public static final String RENAME_NAME = lang(Lang.BUTTON_RENAME_NAME.get(), "§aRename");
    public static final String RENAME_TEXT = lang(Lang.BUTTON_RENAME_TEXT.get(), "§aClick to Rename");
    public static final String ACCEPT_NAME = lang(Lang.BUTTON_ACCEPT_NAME.get(), "§aAccept");
    public static final String ACCEPT_TEXT = lang(Lang.BUTTON_ACCEPT_TEXT.get(), "§aClick to Accept");
    public static final String CLOSE_NAME = lang(Lang.BUTTON_CLOSE.get(), "§aClose");

    private MenuText() {
    }

    private static String lang(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
