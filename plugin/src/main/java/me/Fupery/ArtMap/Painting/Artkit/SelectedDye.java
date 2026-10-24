package me.Fupery.ArtMap.Painting.Artkit;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.Painting.Brush;
import me.Fupery.ArtMap.Painting.CachedPixel;
import me.Fupery.ArtMap.Painting.CanvasRenderer;

public class SelectedDye extends Brush {
    private final ArrayList<CachedPixel> dirtyPixels = new ArrayList<>();
    private byte colour;

    public SelectedDye(CanvasRenderer renderer, Player player) {
        super(renderer, player);
    }

    public void setColour(byte colour) {
        this.colour = colour;
    }

    @Override
    public List<CachedPixel> paint(BrushAction action, ItemStack brush, long strokeTime) {
        if (action == BrushAction.LEFT_CLICK) {
            clean();
            applyCurrent();
        } else {
            if (strokeTime > 250) clean();
            applyCurrent();
        }
        return dirtyPixels;
    }

    private void applyCurrent() {
        byte[] pixel = getCurrentPixel();
        if (pixel == null) return;
        if (dirtyPixels.size() > 0) {
            CachedPixel last = dirtyPixels.get(dirtyPixels.size() - 1);
            flow(last.x, last.y, pixel[0], pixel[1]);
        } else {
            add(pixel[0], pixel[1]);
        }
    }

    private void flow(int x, int y, int x2, int y2) {
        int w = x2 - x;
        int h = y2 - y;
        int dx1 = Integer.compare(w, 0);
        int dy1 = Integer.compare(h, 0);
        int dx2 = dx1;
        int dy2 = 0;
        int longest = Math.abs(w);
        int shortest = Math.abs(h);
        if (longest < shortest) {
            longest = Math.abs(h);
            shortest = Math.abs(w);
            dx2 = 0;
            dy2 = dy1;
        }
        int numerator = longest >> 1;
        for (int i = 0; i <= longest; i++) {
            add(x, y);
            numerator += shortest;
            if (numerator >= longest) {
                numerator -= longest;
                x += dx1;
                y += dy1;
            } else {
                x += dx2;
                y += dy2;
            }
        }
    }

    private void add(int x, int y) {
        if (x < 0 || y < 0 || x >= getAxisLength() || y >= getAxisLength()) return;
        addPixel(x, y, colour);
        dirtyPixels.add(new CachedPixel(x, y, colour));
    }

    @Override
    public boolean checkMaterial(ItemStack brush) {
        return true;
    }

    @Override
    public void clean() {
        dirtyPixels.clear();
    }
}
