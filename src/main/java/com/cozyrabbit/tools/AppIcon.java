package com.cozyrabbit.tools;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.render.Colors;
import com.cozyrabbit.render.PixelCanvas;
import com.cozyrabbit.render.Sprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the app icon in pixel art (64x64, scaled up without smoothing).
 * Run {@code main} with an output folder and a design letter (A-E) to write PNGs in all macOS icon sizes.
 */
public final class AppIcon {

    private static final int S = 64;
    private static final int INSET = 6;      // macOS icon grid: the body leaves a transparent margin
    private static final int RADIUS = 12;

    private AppIcon() {
    }

    public static PixelCanvas draw(char design) {
        PixelCanvas pc = new PixelCanvas(S, S);
        pc.clear(0);
        switch (Character.toUpperCase(design)) {
            case 'A' -> portrait(pc);
            case 'B' -> sunsetPier(pc);
            case 'C' -> bucket(pc);
            case 'D' -> nightLantern(pc);
            default -> bobber(pc);
        }
        maskRounded(pc);
        return pc;
    }

    // ------------------------------------------------------------------ shared helpers

    private static boolean inBody(int x, int y) {
        int x0 = INSET;
        int y0 = INSET;
        int x1 = S - INSET - 1;
        int y1 = S - INSET - 1;
        if (x < x0 || y < y0 || x > x1 || y > y1) {
            return false;
        }
        int cx = Math.max(x0 + RADIUS, Math.min(x1 - RADIUS, x));
        int cy = Math.max(y0 + RADIUS, Math.min(y1 - RADIUS, y));
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy <= RADIUS * RADIUS + RADIUS * 0.6;
    }

    private static void background(PixelCanvas pc, int top, int bottom) {
        for (int y = 0; y < S; y++) {
            int c = Colors.mix(top, bottom, (y - INSET) / (double) (S - 2 * INSET));
            for (int x = 0; x < S; x++) {
                pc.px[y * S + x] = c;
            }
        }
    }

    /** Cuts the rounded-square shape, adds a soft rim light and a dark edge. */
    private static void maskRounded(PixelCanvas pc) {
        int[] copy = pc.px.clone();
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                if (!inBody(x, y)) {
                    pc.px[y * S + x] = 0;
                    continue;
                }
                boolean edge = !inBody(x - 1, y) || !inBody(x + 1, y) || !inBody(x, y - 1) || !inBody(x, y + 1);
                int c = copy[y * S + x];
                if (edge) {
                    c = Colors.shade(c, 0.62);
                } else if (!inBody(x, y - 2)) {
                    c = Colors.lighten(c, 0.22);
                }
                pc.px[y * S + x] = c;
            }
        }
    }

    private static void rabbitHead2x(PixelCanvas pc, int x, int y, boolean closed) {
        pc.sprite2x(Sprites.RABBIT_EAR, x - 6, y + 6, false);
        pc.sprite2x(Sprites.RABBIT_EAR, x + 32, y + 6, true);
        pc.sprite2x(closed ? Sprites.RABBIT_HEAD_CLOSED : Sprites.RABBIT_HEAD, x, y, false);
        pc.sprite2x(Sprites.HAT, x - 4, y - 6, false);
    }

    private static void star(PixelCanvas pc, int x, int y, int c) {
        pc.set(x, y, c);
        pc.set(x - 1, y, Colors.withAlpha(c, 0.5));
        pc.set(x + 1, y, Colors.withAlpha(c, 0.5));
        pc.set(x, y - 1, Colors.withAlpha(c, 0.5));
        pc.set(x, y + 1, Colors.withAlpha(c, 0.5));
    }

    private static void water(PixelCanvas pc, int fromY, int top, int bottom, int sparkle) {
        for (int y = fromY; y < S; y++) {
            int c = Colors.mix(top, bottom, (y - fromY) / (double) (S - fromY));
            for (int x = 0; x < S; x++) {
                pc.px[y * S + x] = c;
                if (((x * 7 + y * 13) % 23) == 0 && y > fromY + 1) {
                    pc.hline(x, x + 2, y, sparkle);
                }
            }
        }
    }

    // ------------------------------------------------------------------ A: portrait

    private static void portrait(PixelCanvas pc) {
        background(pc, Colors.rgb(0xF9D9B5), Colors.rgb(0xE9A77E));
        // a soft halo behind the head
        pc.fillCircle(32, 33, 22, Colors.withAlpha(0xFFFFF3E0, 0.55));
        // little fish badge in the corner
        rabbitHead2x(pc, 14, 18, false);
        pc.sprite(Sprites.FISH.get(FishType.GOLDEN), 40, 47, false);
    }

    // ------------------------------------------------------------------ B: sunset pier

    private static void sunsetPier(PixelCanvas pc) {
        background(pc, Colors.rgb(0x5B5FA6), Colors.rgb(0xF39A63));
        pc.glow(42, 30, 16, Colors.rgb(0xFFC27A), 0.5);
        pc.fillCircle(42, 30, 6, Colors.rgb(0xFFE3A8));
        for (int x = 0; x < S; x++) {
            int h = 34 - (int) (Math.sin(x * 0.12) * 2 + Math.sin(x * 0.31) * 1.5);
            pc.vline(x, h, 37, Colors.rgb(0x6A5478));
        }
        water(pc, 38, Colors.rgb(0xD58A6E), Colors.rgb(0x3E4E7E), Colors.rgb(0xF6C39A));
        // pier going away diagonally
        for (int y = 40; y < S; y++) {
            double t = (y - 40) / 24.0;
            int l = (int) (30 - t * 34);
            int r = (int) (42 - t * 6);
            pc.hline(Math.max(0, l), r, y, (y % 3 == 0) ? Colors.rgb(0x8A5634) : Colors.rgb(0xB98552));
            pc.set(r + 1, y, Colors.rgb(0x5A3825));
        }
        // rabbit sitting on the pier
        int bx = 22;
        int by = 34;
        pc.sprite(Sprites.RABBIT_BODY, bx, by + 10, false);
        pc.sprite(Sprites.RABBIT_HEAD, bx, by, false);
        pc.sprite(Sprites.RABBIT_EAR, bx - 3, by + 3, false);
        pc.sprite(Sprites.RABBIT_EAR, bx + 16, by + 3, true);
        pc.sprite(Sprites.HAT, bx - 2, by - 3, false);
        // rod and line
        pc.line(bx + 15, by + 16, bx + 34, by - 4, Colors.rgb(0x4A2E1F));
        for (int i = 0; i < 18; i++) {
            pc.set(bx + 34 + i / 6, by - 3 + i, Colors.withAlpha(0xFFFFF4E8, 0.8));
        }
        pc.hline(bx + 36, bx + 38, by + 15, Colors.rgb(0xE04A3A));
        pc.hline(bx + 36, bx + 38, by + 16, Colors.rgb(0xF4EDE1));
    }

    // ------------------------------------------------------------------ C: bucket full of fish

    private static void bucket(PixelCanvas pc) {
        background(pc, Colors.rgb(0x7CC3C0), Colors.rgb(0x3E8E96));
        for (int i = 0; i < 6; i++) {
            pc.fillCircle(14 + i * 8, 52 + (i % 2), 5, Colors.withAlpha(0xFFFFFFFF, 0.12));
        }
        int color = Colors.rgb(0xE07A5F);
        int x0 = 17;
        int y0 = 30;
        int w = 30;
        int h = 22;
        // handle
        for (int a = 0; a <= 180; a += 3) {
            double r = Math.toRadians(a);
            pc.set((int) Math.round(32 + Math.cos(r) * 14), (int) Math.round(y0 + 1 - Math.sin(r) * 12), Colors.rgb(0x8A8A94));
        }
        // fish heap
        FishType[] heap = {FishType.BLUE, FishType.TEAL, FishType.ORANGE, FishType.TEAL, FishType.BLUE};
        for (int i = 0; i < heap.length; i++) {
            pc.sprite(Sprites.FISH.get(heap[i]), x0 + 1 + (i % 3) * 9, y0 - 4 - (i / 3) * 3, i % 2 == 0);
        }
        // golden fish jumping out
        pc.sprite2x(Sprites.FISH.get(FishType.GOLDEN), 30, 9, true);
        star(pc, 26, 12, Colors.rgb(0xFFF3B0));
        star(pc, 54, 18, Colors.rgb(0xFFF3B0));
        // bucket body
        pc.hline(x0 + 1, x0 + w - 2, y0, Colors.shade(color, 0.3));
        for (int r = 1; r < h; r++) {
            int in = (r * 3) / (h - 1);
            for (int x = x0 + in; x <= x0 + w - 1 - in; x++) {
                int c;
                if (x == x0 + in || x == x0 + w - 1 - in || r == h - 1) {
                    c = Colors.shade(color, 0.42);
                } else if (r <= 2) {
                    c = Colors.lighten(color, 0.3);
                } else if (r == 7 || r == 15) {
                    c = Colors.mix(Colors.shade(color, 0.72), Colors.rgb(0x8A8A94), 0.35);
                } else if (x <= x0 + in + 4) {
                    c = Colors.lighten(color, 0.25);
                } else if (x >= x0 + w - 1 - in - 6) {
                    c = Colors.shade(color, 0.75);
                } else {
                    c = color;
                }
                pc.set(x, y0 + r, c);
            }
        }
        // the khaki hat hanging on the bucket
        pc.sprite(Sprites.HAT, x0 + 14, y0 + 6, false);
    }

    // ------------------------------------------------------------------ D: night lantern

    private static void nightLantern(PixelCanvas pc) {
        background(pc, Colors.rgb(0x141B3D), Colors.rgb(0x33407A));
        int[][] stars = {{12, 12}, {50, 10}, {44, 20}, {18, 24}, {56, 30}, {9, 34}};
        for (int[] s : stars) {
            star(pc, s[0], s[1], Colors.rgb(0xF4F0DC));
        }
        pc.fillCircle(48, 15, 5, Colors.rgb(0xF3EED8));
        pc.fillCircle(50, 13, 4.3, Colors.rgb(0x18204A));
        water(pc, 50, Colors.rgb(0x26305E), Colors.rgb(0x141B3D), Colors.rgb(0x4A5A9A));
        // lantern
        pc.vline(51, 32, 52, Colors.rgb(0x3E2A1E));
        pc.fillRect(49, 26, 5, 7, Colors.rgb(0x2E2A2A));
        pc.fillRect(50, 27, 3, 5, Colors.rgb(0xFFD27A));
        pc.glow(51.5, 29.5, 18, Colors.rgb(0xFFC56A), 0.45);
        // sleepy rabbit holding a mug
        rabbitHead2x(pc, 9, 20, true);
        pc.sprite(Sprites.MUG, 21, 47, false);
        for (int i = 0; i < 4; i++) {
            pc.set(23 + (i % 2), 44 - i * 2, Colors.withAlpha(0xFFF4F1EA, 0.6));
        }
    }

    // ------------------------------------------------------------------ E: bobber logo

    private static void bobber(PixelCanvas pc) {
        background(pc, Colors.rgb(0xF6E7CF), Colors.rgb(0xEAD2AE));
        // round lake
        pc.fillCircle(32, 36, 21, Colors.rgb(0x2F6E8E));
        pc.fillCircle(32, 36, 21, Colors.withAlpha(0xFF5FA4C4, 0.0));
        for (int y = 16; y < 57; y++) {
            for (int x = 11; x < 53; x++) {
                double d = Math.hypot(x + 0.5 - 32, y + 0.5 - 36);
                if (d < 21 && y < 36) {
                    pc.set(x, y, Colors.rgb(0x8CC8DD));
                }
            }
        }
        pc.hline(12, 51, 36, Colors.rgb(0xD8F0F6));
        // ripples
        for (int a = 0; a < 360; a += 8) {
            double r = Math.toRadians(a);
            pc.set((int) Math.round(32 + Math.cos(r) * 12), (int) Math.round(37 + Math.sin(r) * 3), Colors.rgb(0xD8F0F6));
            pc.set((int) Math.round(32 + Math.cos(r) * 17), (int) Math.round(37 + Math.sin(r) * 4.5),
                    Colors.withAlpha(0xFFD8F0F6, 0.6));
        }
        // big bobber
        pc.vline(32, 18, 24, Colors.rgb(0x3A2A22));
        pc.fillCircle(32, 30, 6, Colors.rgb(0xE04A3A));
        for (int y = 30; y <= 37; y++) {
            for (int x = 26; x <= 38; x++) {
                if (Math.hypot(x + 0.5 - 32, y + 0.5 - 30) <= 6) {
                    pc.set(x, y, Colors.rgb(0xF4EDE1));
                }
            }
        }
        pc.hline(26, 38, 30, Colors.rgb(0x7A2A20));
        pc.set(29, 27, Colors.rgb(0xF59A8A));
        // fish shadow under water
        pc.sprite2x(Sprites.FISH.get(FishType.TEAL), 30, 42, false);
        // the hat floating like a little boat on the left
        pc.sprite(Sprites.HAT, 9, 9, false);
    }

    // ------------------------------------------------------------------ export

    public static BufferedImage image(char design, int size) {
        PixelCanvas pc = draw(design);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, pc.px[(y * S / size) * S + (x * S / size)]);
            }
        }
        return img;
    }

    /** Writes an .iconset folder (all sizes macOS needs) for one design. */
    public static void writeIconset(char design, File dir) throws IOException {
        dir.mkdirs();
        int[] sizes = {16, 32, 128, 256, 512};
        for (int s : sizes) {
            ImageIO.write(image(design, s), "png", new File(dir, "icon_" + s + "x" + s + ".png"));
            ImageIO.write(image(design, s * 2), "png", new File(dir, "icon_" + s + "x" + s + "@2x.png"));
        }
    }

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "icons");
        String designs = args.length > 1 ? args[1] : "ABCDE";
        out.mkdirs();
        for (char d : designs.toCharArray()) {
            ImageIO.write(image(d, 512), "png", new File(out, "icon-" + d + ".png"));
        }
    }
}
