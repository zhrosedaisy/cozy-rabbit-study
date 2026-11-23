package com.cozyrabbit.render;

import java.util.Map;

/** A sprite defined as rows of characters; '.' is transparent. */
public final class Sprite {

    public final int w;
    public final int h;
    public final int[] px;

    public Sprite(String[] rows, Map<Character, Integer> palette) {
        this.h = rows.length;
        int width = 0;
        for (String r : rows) {
            if (width != 0 && r.length() != width) {
                throw new IllegalArgumentException("Sprite rows must have equal length: '" + r + "'");
            }
            width = Math.max(width, r.length());
        }
        this.w = width;
        this.px = new int[w * h];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < r.length(); x++) {
                char ch = r.charAt(x);
                if (ch != '.' && ch != ' ') {
                    Integer c = palette.get(ch);
                    if (c == null) {
                        throw new IllegalArgumentException("No color for '" + ch + "' in sprite row " + y);
                    }
                    px[y * w + x] = c;
                }
            }
        }
    }
}
