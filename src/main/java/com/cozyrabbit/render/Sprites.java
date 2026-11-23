package com.cozyrabbit.render;

import com.cozyrabbit.model.FishType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Hand-made pixel art. Rows are built from pieces so every row has the same width. */
public final class Sprites {

    private Sprites() {
    }

    // White lop-eared rabbit palette
    public static final int OUTLINE = Colors.rgb(0x6B5E73);
    public static final int FUR = Colors.rgb(0xFFFFFF);
    public static final int FUR_SHADE = Colors.rgb(0xE6E0EE);
    public static final int FUR_DARK = Colors.rgb(0xCFC6DC);
    public static final int EAR_PINK = Colors.rgb(0xF6C3CB);
    public static final int CHEEK = Colors.rgb(0xF9C2CA);
    public static final int NOSE = Colors.rgb(0xE88A96);
    public static final int EYE = Colors.rgb(0x2B1B22);

    private static Map<Character, Integer> rabbitPalette() {
        Map<Character, Integer> p = new HashMap<>();
        p.put('o', OUTLINE);
        p.put('w', FUR);
        p.put('s', FUR_SHADE);
        p.put('d', FUR_DARK);
        p.put('P', EAR_PINK);
        p.put('c', CHEEK);
        p.put('n', NOSE);
        p.put('e', EYE);
        p.put('h', Colors.rgb(0xFFFFFF));
        p.put('m', Colors.rgb(0xB98A96));
        return p;
    }

    private static String[] headRows(String eyesTop, String eyesBottom) {
        return new String[]{
                "......" + "oooooo" + "......",
                "...." + "oo" + "wwwwww" + "oo" + "....",
                "..." + "o" + "wwwwwwwwww" + "o" + "...",
                ".." + "o" + "wwwwwwwwwwww" + "o" + "..",
                "." + "o" + "wwwwwwwwwwwwww" + "o" + ".",
                "." + "o" + "wwwwwwwwwwwwww" + "o" + ".",
                eyesTop,
                eyesBottom,
                "o" + "ww" + "cc" + "www" + "nn" + "www" + "cc" + "ww" + "o",
                "o" + "wwwwwww" + "mm" + "wwwwwww" + "o",
                ".o" + "swwwwwwwwwwwws" + "o.",
                "..o" + "sswwwwwwwwss" + "o..",
                "...oo" + "ssssssss" + "oo...",
                "....." + "oooooooo" + ".....",
        };
    }

    public static final Sprite RABBIT_HEAD = new Sprite(headRows(
            "o" + "wwww" + "he" + "wwww" + "he" + "wwww" + "o",
            "o" + "wwww" + "ee" + "wwww" + "ee" + "wwww" + "o"), rabbitPalette());

    public static final Sprite RABBIT_HEAD_CLOSED = new Sprite(headRows(
            "o" + "wwwwwwwwwwwwwwww" + "o",
            "o" + "wwww" + "oo" + "wwww" + "oo" + "wwww" + "o"), rabbitPalette());

    /** A floppy ear, hanging beside the cheek; pink side faces the viewer. */
    public static final Sprite RABBIT_EAR = new Sprite(new String[]{
            ".oo..",
            "owwo.",
            "owwwo",
            "owPwo",
            "owPwo",
            "owPwo",
            "owPPo",
            "owPPo",
            "owPPo",
            "owPPo",
            "owPwo",
            "owwwo",
            "oswwo",
            ".osso",
            "..oo.",
    }, rabbitPalette());

    public static final Sprite RABBIT_BODY = new Sprite(new String[]{
            "...oo" + "wwwwwwww" + "oo...",
            "..o" + "wwwwwwwwwwww" + "o..",
            ".o" + "wwwwwwwwwwwwww" + "o.",
            ".o" + "wwwwwwwwwwwwws" + "o.",
            "o" + "wwwwwwwwwwwwwwss" + "o",
            "o" + "wwwwwwwwwwwwwwss" + "o",
            "o" + "swwwwwwwwwwwwwss" + "o",
            "o" + "sswwwwwwwwwwwsss" + "o",
            "o" + "sswwwwwwwwwwwsss" + "o",
            "o" + "sssss" + "wwwwww" + "sssss" + "o",
            "." + "o" + "wwwwoooooowwww" + "o" + ".",
            ".." + "ooooo" + "...." + "ooooo" + "..",
    }, rabbitPalette());

    /** Khaki green fisherman's bucket hat with a little lure pinned on the band. */
    public static final Sprite HAT;

    static {
        Map<Character, Integer> p = new HashMap<>();
        p.put('k', Colors.rgb(0x34391F));
        p.put('g', Colors.rgb(0x7D8A55));
        p.put('G', Colors.rgb(0x9DAA70));
        p.put('D', Colors.rgb(0x5E6A3C));
        p.put('b', Colors.rgb(0x4B5530));
        p.put('r', Colors.rgb(0xD9473A));
        p.put('f', Colors.rgb(0xF2C14E));
        HAT = new Sprite(new String[]{
                "......" + "kkkkkkkkkk" + "......",
                "....." + "k" + "GGgggggggg" + "k" + ".....",
                "...." + "k" + "GGgggggggggD" + "k" + "....",
                "...." + "k" + "bbbbbbbrfbbb" + "k" + "....",
                "...." + "k" + "gggggggggggD" + "k" + "....",
                ".." + "kk" + "GggggggggggggD" + "kk" + "..",
                "k" + "GGggggggggggggggggDD" + "k",
                "." + "kkkkkkkkkkkkkkkkkkkk" + ".",
        }, p);
    }

    public static final Sprite MUG;

    static {
        Map<Character, Integer> p = new HashMap<>();
        p.put('k', Colors.rgb(0x5A3A2A));
        p.put('m', Colors.rgb(0xD9734E));
        p.put('n', Colors.rgb(0xF0A07C));
        p.put('f', Colors.rgb(0x6B3E22));
        MUG = new Sprite(new String[]{
                "kffffk..",
                "knmmmkk.",
                "knmmmk.k",
                "knmmmkk.",
                "knmmmk..",
                ".kkkk...",
        }, p);
    }

    private static final String[] FISH_ROWS = {
            "..kkkkk.kk",
            ".kbebbbkbk",
            "kbbbbbbbk.",
            ".kblllbkbk",
            "..kkkkk.kk",
    };

    public static final Map<FishType, Sprite> FISH = new EnumMap<>(FishType.class);

    static {
        for (FishType f : FishType.values()) {
            Map<Character, Integer> p = new HashMap<>();
            p.put('k', Colors.shade(f.body(), 0.45));
            p.put('b', f.body());
            p.put('l', f.belly());
            p.put('e', Colors.rgb(0x1E1E28));
            FISH.put(f, new Sprite(FISH_ROWS, p));
        }
    }
}
