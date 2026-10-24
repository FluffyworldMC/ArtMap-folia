package me.Fupery.ArtMap.Painting.Artkit;

public enum ArtkitTool {
    DYE, FREEHAND, LINE, RECTANGLE, RECTANGLE_FILLED, CIRCLE, CIRCLE_FILLED,
    FILL, FLIP, SHADE_DARK, SHADE_LIGHT, DROPPER;

    public boolean isDrawingTool() {
        switch (this) {
            case DYE:
            case FREEHAND:
            case LINE:
            case RECTANGLE:
            case RECTANGLE_FILLED:
            case CIRCLE:
            case CIRCLE_FILLED:
                return true;
            default:
                return false;
        }
    }

    public String getDisplayName() {
        switch (this) {
            case FREEHAND:
                return "Freehand";
            case LINE:
                return "Line";
            case RECTANGLE:
                return "Rectangle";
            case RECTANGLE_FILLED:
                return "Filled Rectangle";
            case CIRCLE:
                return "Circle";
            case CIRCLE_FILLED:
                return "Filled Circle";
            case FILL:
                return "Fill";
            case FLIP:
                return "Flip";
            case SHADE_DARK:
                return "Shade Darker";
            case SHADE_LIGHT:
                return "Shade Lighter";
            case DROPPER:
                return "Eyedropper";
            case DYE:
            default:
                return "Point";
        }
    }

    public static ArtkitTool parse(String raw, ArtkitTool fallback) {
        if (raw == null) return fallback;
        try {
            return valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
