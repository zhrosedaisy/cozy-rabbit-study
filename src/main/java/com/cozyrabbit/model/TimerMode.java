package com.cozyrabbit.model;

public enum TimerMode {
    UNLIMITED("Unlimited"),
    POMODORO("Pomodoro");

    private final String label;

    TimerMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
