package me.Fupery.ArtMap.Painting.Artkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArtkitPalette {
    private static final String[] NAMES = {
            "Transparent", "Grass", "Sand", "Wool", "Fire", "Ice", "Metal", "Plant",
            "Snow", "Clay", "Dirt", "Stone", "Water", "Wood", "Quartz", "Orange",
            "Magenta", "Light Blue", "Yellow", "Light Green", "Pink", "Gray", "Light Gray", "Cyan",
            "Purple", "Blue", "Brown", "Green", "Red", "Black", "Gold", "Diamond",
            "Lapis", "Emerald", "Podzol", "Nether", "Terracotta White", "Terracotta Orange",
            "Terracotta Magenta", "Terracotta Light Blue", "Terracotta Yellow", "Terracotta Lime",
            "Terracotta Pink", "Terracotta Gray", "Terracotta Light Gray", "Terracotta Cyan",
            "Terracotta Purple", "Terracotta Blue", "Terracotta Brown", "Terracotta Green",
            "Terracotta Red", "Terracotta Black", "Crimson Nylium", "Crimson Stem", "Crimson Hyphae",
            "Warped Nylium", "Warped Stem", "Warped Hyphae", "Warped Wart", "Deepslate", "Raw Iron",
            "Glow Lichen", "Color 62", "Color 63"
    };
    private static final String[] HEX = {
            "000000", "7fb238", "f7e9a3", "c7c7c7", "ff0000", "a0a0ff", "a7a7a7", "007c00",
            "ffffff", "a4a8b8", "976d4d", "707070", "4040ff", "8f7748", "fffcf5", "d87f33",
            "b24cd8", "6699d8", "e5e533", "7fcc19", "f27fa5", "4c4c4c", "999999", "4c7f99",
            "7f3fb2", "334cb2", "664c33", "667f33", "993333", "191919", "faee4d", "5cdbd5",
            "4a80ff", "00d93a", "815631", "700200", "d1b1a1", "9f5224", "95576c", "706c8a",
            "ba8524", "677535", "a04d4e", "392923", "876b62", "575c5c", "7a4958", "4c3e5c",
            "4c3223", "4c522a", "8e3c2e", "251610", "bd3031", "943f61", "5c191d", "167e86",
            "3a8e8c", "562c3e", "14b485", "565656", "d8af93", "7fa796", "888888", "999999"
    };
    private static final double[] SHADE_FACTOR = {0.55, 0.75, 1.0, 1.25};
    private static final List<ArtkitColor> COLORS = build();
    private static final ArtkitColor DEFAULT = find(59);

    private ArtkitPalette() {
    }

    private static List<ArtkitColor> build() {
        List<ArtkitColor> colors = new ArrayList<>();
        for (int base = 1; base < NAMES.length; base++) {
            for (int shade = 0; shade < 4; shade++) {
                int index = base * 4 + shade;
                if (index > 255) break;
                String hex = shade(HEX[base], SHADE_FACTOR[shade]);
                colors.add(new ArtkitColor(index, base, shade, hex, NAMES[base]));
            }
        }
        return Collections.unmodifiableList(colors);
    }

    private static String shade(String baseHex, double factor) {
        int rgb = Integer.parseInt(baseHex, 16);
        int r = Math.min(255, (int) Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) Math.round((rgb & 0xFF) * factor));
        return String.format("%02x%02x%02x", r, g, b);
    }

    public static List<ArtkitColor> colors() {
        return COLORS;
    }

    public static ArtkitColor get(int index) {
        return find(index);
    }

    private static ArtkitColor find(int index) {
        for (ArtkitColor color : COLORS) {
            if (color.getIndex() == index) return color;
        }
        return COLORS.isEmpty() ? null : COLORS.get(0);
    }

    public static ArtkitColor defaultColor() {
        return DEFAULT;
    }
}
