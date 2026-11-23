package com.cozyrabbit.render;

/**
 * A tiny software frame buffer. The whole scene is drawn here at low resolution
 * and then scaled up without smoothing, which gives the crisp pixel-art look.
 */
public final class PixelCanvas {

    public final int w;
    public final int h;
    public final int[] px;
    /** Marks "foreground" pixels so the day/night grading can tint only them. */
    public final byte[] mask;
    private boolean marking;

    public PixelCanvas(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
        this.mask = new byte[w * h];
    }

    public void clearMask() {
        java.util.Arrays.fill(mask, (byte) 0);
    }

    public void setMarking(boolean on) {
        marking = on;
    }

    public void clear(int c) {
        java.util.Arrays.fill(px, c);
        java.util.Arrays.fill(mask, (byte) 0);
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return 0;
        }
        return px[y * w + x];
    }

    /** Draws a pixel, alpha-blending when the color is translucent. */
    public void set(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        int alpha = (c >>> 24) & 0xFF;
        if (alpha == 0) {
            return;
        }
        int i = y * w + x;
        if (alpha == 255) {
            px[i] = c;
        } else {
            int d = px[i];
            int inv = 255 - alpha;
            int r = (((c >> 16) & 0xFF) * alpha + ((d >> 16) & 0xFF) * inv) / 255;
            int g = (((c >> 8) & 0xFF) * alpha + ((d >> 8) & 0xFF) * inv) / 255;
            int b = ((c & 0xFF) * alpha + (d & 0xFF) * inv) / 255;
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        if (marking) {
            mask[i] = 1;
        }
    }

    /** Additive light, used for glows. */
    public void add(int x, int y, int c, double strength) {
        if (x < 0 || y < 0 || x >= w || y >= h || strength <= 0) {
            return;
        }
        int i = y * w + x;
        int d = px[i];
        int r = Math.min(255, ((d >> 16) & 0xFF) + (int) (((c >> 16) & 0xFF) * strength));
        int g = Math.min(255, ((d >> 8) & 0xFF) + (int) (((c >> 8) & 0xFF) * strength));
        int b = Math.min(255, (d & 0xFF) + (int) ((c & 0xFF) * strength));
        px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public void fillRect(int x, int y, int rw, int rh, int c) {
        for (int yy = y; yy < y + rh; yy++) {
            for (int xx = x; xx < x + rw; xx++) {
                set(xx, yy, c);
            }
        }
    }

    public void hline(int x0, int x1, int y, int c) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            set(x, y, c);
        }
    }

    public void vline(int x, int y0, int y1, int c) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            set(x, y, c);
        }
    }

    public void fillCircle(double cx, double cy, double r, int c) {
        int x0 = (int) Math.floor(cx - r);
        int x1 = (int) Math.ceil(cx + r);
        int y0 = (int) Math.floor(cy - r);
        int y1 = (int) Math.ceil(cy + r);
        double r2 = r * r;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                if (dx * dx + dy * dy <= r2) {
                    set(x, y, c);
                }
            }
        }
    }

    public void glow(double cx, double cy, double radius, int c, double strength) {
        int x0 = (int) Math.floor(cx - radius);
        int x1 = (int) Math.ceil(cx + radius);
        int y0 = (int) Math.floor(cy - radius);
        int y1 = (int) Math.ceil(cy + radius);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius;
                if (d < 1) {
                    double f = (1 - d) * (1 - d);
                    add(x, y, c, f * strength);
                }
            }
        }
    }

    public void line(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            set(x0, y0, c);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void sprite(Sprite s, int x, int y, boolean flip) {
        for (int row = 0; row < s.h; row++) {
            for (int col = 0; col < s.w; col++) {
                int c = s.px[row * s.w + col];
                if (c != 0) {
                    set(flip ? x + s.w - 1 - col : x + col, y + row, c);
                }
            }
        }
    }

    /** Draws each sprite pixel as a 2x2 block. */
    public void sprite2x(Sprite s, int x, int y, boolean flip) {
        for (int row = 0; row < s.h; row++) {
            for (int col = 0; col < s.w; col++) {
                int c = s.px[row * s.w + col];
                if (c != 0) {
                    int dx = flip ? s.w - 1 - col : col;
                    fillRect(x + dx * 2, y + row * 2, 2, 2, c);
                }
            }
        }
    }
}
