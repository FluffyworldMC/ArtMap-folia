package me.Fupery.ArtMap.Painting.Artkit;

public class ArtkitColor {
    private final int index;
    private final int base;
    private final int shade;
    private final String hex;
    private final String name;

    ArtkitColor(int index, int base, int shade, String hex, String name) {
        this.index = index;
        this.base = base;
        this.shade = shade;
        this.hex = hex;
        this.name = name;
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
        return name;
    }

    public byte getByte() {
        return (byte) index;
    }

    public String getShadeName() {
        switch (shade) {
            case 0:
                return "Dark";
            case 1:
                return "Dim";
            case 2:
                return "Base";
            default:
                return "Light";
        }
    }
}
