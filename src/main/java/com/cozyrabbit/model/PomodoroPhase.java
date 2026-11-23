package com.cozyrabbit.model;

public enum PomodoroPhase {
    FOCUS("Focus"),
    SHORT_BREAK("Short break"),
    LONG_BREAK("Long break");

    private final String label;

    PomodoroPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isBreak() {
        return this != FOCUS;
    }
}
