package com.cozyrabbit.storage;

import com.cozyrabbit.model.TimerMode;
import com.cozyrabbit.model.Weather;

import java.util.Map;

/** Typed access to user preferences, persisted in the settings table. */
public final class AppSettings {

    private final Database db;
    private final Map<String, String> values;

    public AppSettings(Database db) {
        this.db = db;
        this.values = db.loadSettings();
    }

    private String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void put(String key, Object value) {
        String v = String.valueOf(value);
        values.put(key, v);
        db.saveSetting(key, v);
    }

    public Weather weather() {
        try {
            return Weather.valueOf(get("weather", Weather.SUNNY.name()));
        } catch (IllegalArgumentException e) {
            return Weather.SUNNY;
        }
    }

    public void setWeather(Weather w) {
        put("weather", w.name());
    }

    public TimerMode timerMode() {
        try {
            return TimerMode.valueOf(get("timerMode", TimerMode.UNLIMITED.name()));
        } catch (IllegalArgumentException e) {
            return TimerMode.UNLIMITED;
        }
    }

    public void setTimerMode(TimerMode m) {
        put("timerMode", m.name());
    }

    public boolean rainSound() {
        return Boolean.parseBoolean(get("rainSound", "true"));
    }

    public void setRainSound(boolean on) {
        put("rainSound", on);
    }

    public int rainVolume() {
        return getInt("rainVolume", 60);
    }

    public void setRainVolume(int v) {
        put("rainVolume", v);
    }

    public boolean soundEffects() {
        return Boolean.parseBoolean(get("sfx", "true"));
    }

    public void setSoundEffects(boolean on) {
        put("sfx", on);
    }

    public int focusMinutes() {
        return getInt("focusMin", 25);
    }

    public void setFocusMinutes(int v) {
        put("focusMin", v);
    }

    public int shortBreakMinutes() {
        return getInt("shortBreakMin", 5);
    }

    public void setShortBreakMinutes(int v) {
        put("shortBreakMin", v);
    }

    public int longBreakMinutes() {
        return getInt("longBreakMin", 15);
    }

    public void setLongBreakMinutes(int v) {
        put("longBreakMin", v);
    }

    public int longBreakEvery() {
        return getInt("longEvery", 4);
    }

    public void setLongBreakEvery(int v) {
        put("longEvery", v);
    }

    public int dailyGoalMinutes() {
        return getInt("dailyGoalMin", 240);
    }

    public void setDailyGoalMinutes(int v) {
        put("dailyGoalMin", v);
    }

    /** How often a fish bites, in minutes (at least 10). One fish arrives at a random moment in each window. */
    public int catchMinutes() {
        return Math.max(10, getInt("catchMin", 10));
    }

    public void setCatchMinutes(int v) {
        put("catchMin", Math.max(10, v));
    }

    /** The small always-on-top timer at the top of the screen. */
    public boolean miniIsland() {
        return Boolean.parseBoolean(get("miniIsland", "true"));
    }

    public void setMiniIsland(boolean on) {
        put("miniIsland", on);
    }

    /** A random id that tells your rabbit apart from your friends' in a study room. */
    public String playerId() {
        String id = values.get("playerId");
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            put("playerId", id);
        }
        return id;
    }

    public String playerName() {
        return get("playerName", "");
    }

    public void setPlayerName(String name) {
        put("playerName", name.trim());
    }

    public String hatColor() {
        return get("hatColor", "#7D8A55");
    }

    public void setHatColor(String hex) {
        put("hatColor", hex);
    }

    /** -1 means "follow the real clock"; 0..23 forces an hour (handy to preview night). */
    public int forcedHour() {
        return getInt("forcedHour", -1);
    }

    public void setForcedHour(int h) {
        put("forcedHour", h);
    }
}
