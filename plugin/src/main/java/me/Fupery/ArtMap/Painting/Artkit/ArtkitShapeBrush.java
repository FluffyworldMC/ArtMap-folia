package me.Fupery.ArtMap.Painting.Artkit;

import me.Fupery.ArtMap.Painting.CanvasRenderer;

/** Draws the geometric ArtKit tools directly into the canvas pixel buffer. */
public final class ArtkitShapeBrush {
    private final CanvasRenderer canvas;

    public ArtkitShapeBrush(CanvasRenderer canvas) {
        this.canvas = canvas;
    }

    public void point(int x, int y, byte colour) {
        pixel(x, y, colour);
    }

    public void line(int x1, int y1, int x2, int y2, byte colour) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            pixel(x1, y1, colour);
            if (x1 == x2 && y1 == y2) return;
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x1 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y1 += sy;
            }
        }
    }

    public void rectangle(int x1, int y1, int x2, int y2, byte colour, boolean filled) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        if (filled) {
            for (int y = top; y <= bottom; y++) {
                line(left, y, right, y, colour);
            }
            return;
        }
        line(left, top, right, top, colour);
        line(left, bottom, right, bottom, colour);
        line(left, top, left, bottom, colour);
        line(right, top, right, bottom, colour);
    }

    public void circle(int centerX, int centerY, int edgeX, int edgeY, byte colour, boolean filled) {
        int radius = (int) Math.round(Math.sqrt(
                (double) (edgeX - centerX) * (edgeX - centerX)
                        + (double) (edgeY - centerY) * (edgeY - centerY)));
        if (filled) {
            for (int y = -radius; y <= radius; y++) {
                int half = (int) Math.floor(Math.sqrt((double) radius * radius - (double) y * y));
                line(centerX - half, centerY + y, centerX + half, centerY + y, colour);
            }
            return;
        }
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            plotCircle(centerX, centerY, x, y, colour);
            y++;
            if (error < 0) {
                error += 2 * y + 1;
            } else {
                x--;
                error += 2 * (y - x) + 1;
            }
        }
    }

    private void plotCircle(int cx, int cy, int x, int y, byte colour) {
        pixel(cx + x, cy + y, colour);
        pixel(cx + y, cy + x, colour);
        pixel(cx - y, cy + x, colour);
        pixel(cx - x, cy + y, colour);
        pixel(cx - x, cy - y, colour);
        pixel(cx - y, cy - x, colour);
        pixel(cx + y, cy - x, colour);
        pixel(cx + x, cy - y, colour);
    }

    private void pixel(int x, int y, byte colour) {
        int axis = canvas.getAxisLength();
        if (x < 0 || y < 0 || x >= axis || y >= axis) return;
        canvas.addPixel(x, y, colour);
    }
}
