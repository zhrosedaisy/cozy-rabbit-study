package com.cozyrabbit.model;

public enum Weather {
    SUNNY("Sunny"),
    RAINY("Rainy"),
    SNOWY("Snowy");

    private final String label;

    Weather(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
