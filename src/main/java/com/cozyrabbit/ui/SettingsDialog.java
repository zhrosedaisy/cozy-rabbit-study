package com.cozyrabbit.ui;

import com.cozyrabbit.storage.AppSettings;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/** Pomodoro lengths, daily goal, sound effects and time-of-day preview. */
public final class SettingsDialog {

    private static final String[] TIME_OPTIONS = {"Follow my clock", "Morning", "Day", "Sunset", "Night"};
    private static final int[] TIME_HOURS = {-1, 7, 13, 19, 23};

    private SettingsDialog() {
    }

    public static boolean show(Window owner, AppSettings s) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Settings");
        d.getDialogPane().getStylesheets().add(
                SettingsDialog.class.getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        d.getDialogPane().getStyleClass().add("cozy-dialog");

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        g.setPadding(new Insets(8, 4, 4, 4));

        Spinner<Integer> focus = spinner(5, 120, s.focusMinutes(), 5);
        Spinner<Integer> shortBreak = spinner(1, 30, s.shortBreakMinutes(), 1);
        Spinner<Integer> longBreak = spinner(5, 60, s.longBreakMinutes(), 5);
        Spinner<Integer> every = spinner(2, 8, s.longBreakEvery(), 1);
        Spinner<Integer> catchEvery = spinner(10, 60, s.catchMinutes(), 5);
        Spinner<Double> goal = new Spinner<>(0.5, 16, s.dailyGoalMinutes() / 60.0, 0.5);
        goal.setEditable(true);
        goal.setPrefWidth(110);
        CheckBox sfx = new CheckBox("Sound effects (casting, splashes, catches, buttons, pomodoro bell)");
        sfx.setSelected(s.soundEffects());
        CheckBox island = new CheckBox("Mini island: a small timer at the top of the screen, always on top");
        island.setSelected(s.miniIsland());
        ComboBox<String> timeOfDay = new ComboBox<>();
        timeOfDay.getItems().addAll(TIME_OPTIONS);
        int idx = 0;
        for (int i = 0; i < TIME_HOURS.length; i++) {
            if (TIME_HOURS[i] == s.forcedHour()) {
                idx = i;
            }
        }
        timeOfDay.getSelectionModel().select(idx);

        Label title = new Label("Pomodoro");
        title.getStyleClass().add("dialog-title");
        g.add(title, 0, 0, 2, 1);
        g.addRow(1, new Label("Focus (minutes)"), focus);
        g.addRow(2, new Label("Short break (minutes)"), shortBreak);
        g.addRow(3, new Label("Long break (minutes)"), longBreak);
        g.addRow(4, new Label("Long break after every"), every);
        Label fishing = new Label("Fishing");
        fishing.getStyleClass().add("dialog-title");
        g.add(fishing, 0, 5, 2, 1);
        g.addRow(6, new Label("One fish every (minutes)"), catchEvery);
        Label catchNote = new Label("The fish bites at a random moment inside each window.");
        catchNote.getStyleClass().add("muted");
        g.add(catchNote, 0, 7, 2, 1);
        Label other = new Label("Goals and atmosphere");
        other.getStyleClass().add("dialog-title");
        g.add(other, 0, 8, 2, 1);
        g.addRow(9, new Label("Daily goal (hours)"), goal);
        g.addRow(10, new Label("Sky"), timeOfDay);
        g.add(sfx, 0, 11, 2, 1);
        g.add(island, 0, 12, 2, 1);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        d.getDialogPane().setContent(g);

        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != save) {
            return false;
        }
        s.setFocusMinutes(focus.getValue());
        s.setShortBreakMinutes(shortBreak.getValue());
        s.setLongBreakMinutes(longBreak.getValue());
        s.setLongBreakEvery(every.getValue());
        s.setCatchMinutes(catchEvery.getValue());
        s.setDailyGoalMinutes((int) Math.round(goal.getValue() * 60));
        s.setSoundEffects(sfx.isSelected());
        s.setMiniIsland(island.isSelected());
        s.setForcedHour(TIME_HOURS[Math.max(0, timeOfDay.getSelectionModel().getSelectedIndex())]);
        return true;
    }

    private static Spinner<Integer> spinner(int min, int max, int value, int step) {
        Spinner<Integer> sp = new Spinner<>(min, max, Math.max(min, Math.min(max, value)), step);
        sp.setEditable(true);
        sp.setPrefWidth(110);
        return sp;
    }
}
