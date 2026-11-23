package com.cozyrabbit.render;

/** Small helpers for packed ARGB ints. */
public final class Colors {
    private Colors() {
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int a(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static int withAlpha(int c, double alpha) {
        return ((int) Math.round(clamp01(alpha) * 255) << 24) | (c & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, double t) {
        t = clamp01(t);
        return argb(
                (int) Math.round(a(c1) + (a(c2) - a(c1)) * t),
                (int) Math.round(r(c1) + (r(c2) - r(c1)) * t),
                (int) Math.round(g(c1) + (g(c2) - g(c1)) * t),
                (int) Math.round(b(c1) + (b(c2) - b(c1)) * t));
    }

    public static int shade(int c, double factor) {
        return argb(a(c), (int) (r(c) * factor), (int) (g(c) * factor), (int) (b(c) * factor));
    }

    public static int lighten(int c, double t) {
        return mix(c, 0xFFFFFFFF, t);
    }

    public static int darken(int c, double t) {
        return mix(c, 0xFF000000, t);
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static int parseHex(String hex, int fallback) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static String toHex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }
}
