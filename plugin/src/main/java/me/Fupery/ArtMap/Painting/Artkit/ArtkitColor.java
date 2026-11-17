package me.Fupery.ArtMap.Painting.Artkit;

import me.Fupery.ArtMap.ArtMap;

public class ArtkitColor {
    private final int index;
    private final int base;
    private final int shade;
    private final String hex;

    ArtkitColor(int index, int base, int shade, String hex) {
        this.index = index;
        this.base = base;
        this.shade = shade;
        this.hex = hex;
    }

    public int getIndex() {
        return index;
    }

    public int getBase() {
        return base;
    }

    public int getShade() {
        return shade;
    }

    public String getHex() {
        return hex;
    }

    public String getName() {
        return ArtMap.instance().getMenuConfig().get("artkit").getColorName(base);
    }

    public byte getByte() {
        return (byte) index;
    }

    public String getShadeName() {
        return ArtMap.instance().getMenuConfig().get("artkit").getShadeName(shade);
    }
}
