package me.Fupery.ArtMap.Menu.Config;

public enum MenuAction {
    NONE,
    OPEN,
    BACK,
    CLOSE,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    RECIPE,
    COLOR,
    TOOL,
    SAVE;

    public static MenuAction parse(String raw) {
        if (raw == null) return NONE;
        switch (raw.toLowerCase()) {
            case "open":
                return OPEN;
            case "back":
                return BACK;
            case "close":
                return CLOSE;
            case "previous-page":
            case "previous_page":
            case "prev":
                return PREVIOUS_PAGE;
            case "next-page":
            case "next_page":
            case "next":
                return NEXT_PAGE;
            case "recipe":
                return RECIPE;
            case "color":
                return COLOR;
            case "tool":
                return TOOL;
            case "save":
                return SAVE;
            default:
                return NONE;
        }
    }
}
