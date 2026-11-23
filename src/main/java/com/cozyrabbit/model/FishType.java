package com.cozyrabbit.model;

/**
 * What the rabbit pulls out of the water. One catch happens in every catch window of focus.
 * Long uninterrupted sessions earn rarer fish.
 */
public enum FishType {
    BLUE("Blue fish", 0xFF5B8DB8, 0xFFB9D6EA, false),
    TEAL("Teal fish", 0xFF3E9C8F, 0xFFA9E0D2, false),
    ORANGE("Orange fish", 0xFFE07B3C, 0xFFF7C99B, false),
    /** First catch after 60 uninterrupted minutes. */
    GOLDEN("Golden fish", 0xFFF2C14E, 0xFFFFF1A8, false),
    /** First catch after 120 uninterrupted minutes. */
    KOI("Legendary koi", 0xFFF4F1EA, 0xFFE8603C, true);

    public static final int SECONDS_PER_CATCH = 600;
    public static final int GOLDEN_AFTER_SECONDS = 3600;
    public static final int KOI_AFTER_SECONDS = 7200;

    private final String label;
    private final int body;
    private final int belly;
    private final boolean big;

    FishType(String label, int body, int belly, boolean big) {
        this.label = label;
        this.body = body;
        this.belly = belly;
        this.big = big;
    }

    public String label() {
        return label;
    }

    public int body() {
        return body;
    }

    public int belly() {
        return belly;
    }

    public boolean big() {
        return big;
    }

    public boolean rare() {
        return this == GOLDEN || this == KOI;
    }

    public static FishType common(int index) {
        // Deterministic mix so the bucket always looks the same for the same day.
        int h = (index * 7 + 3) % 5;
        return h < 2 ? BLUE : (h < 4 ? TEAL : ORANGE);
    }
}
