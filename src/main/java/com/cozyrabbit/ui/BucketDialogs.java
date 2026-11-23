package com.cozyrabbit.ui;

import com.cozyrabbit.model.Subject;
import com.cozyrabbit.service.TodayState;
import com.cozyrabbit.storage.Database;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/** Dialogs to put a new bucket on the pier and to rename / recolor one. */
public final class BucketDialogs {

    public static final String[] PALETTE = {
            "#E07A5F", "#E9C46A", "#81B29A", "#5B9BD5", "#9B72CF",
            "#F4A6C0", "#6FB7B7", "#C98B5A", "#5C6BC0", "#B5C96A",
    };

    private BucketDialogs() {
    }

    /** Result of the edit dialog. */
    public sealed interface EditResult permits Saved, Removed {
    }

    public record Saved(Subject subject) implements EditResult {
    }

    public record Removed() implements EditResult {
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /** A row of color swatches plus a custom color picker; returns a getter for the chosen color. */
    private static final class ColorChooser {
        final VBox node = new VBox(8);
        final ColorPicker picker = new ColorPicker();
        final ToggleGroup group = new ToggleGroup();

        ColorChooser(String initial) {
            FlowPane swatches = new FlowPane(6, 6);
            boolean matched = false;
            for (String c : PALETTE) {
                ToggleButton b = new ToggleButton();
                b.getStyleClass().add("swatch");
                b.setStyle("-fx-background-color: " + c + ";");
                b.setUserData(c);
                b.setToggleGroup(group);
                if (c.equalsIgnoreCase(initial)) {
                    b.setSelected(true);
                    matched = true;
                }
                swatches.getChildren().add(b);
            }
            picker.setValue(Color.web(initial));
            picker.getStyleClass().add("button");
            if (!matched) {
                group.selectToggle(null);
            }
            group.selectedToggleProperty().addListener((o, a, b) -> {
                if (b != null) {
                    picker.setValue(Color.web((String) b.getUserData()));
                }
            });
            picker.setOnAction(e -> {
                String chosen = hex(picker.getValue());
                group.getToggles().stream()
                        .filter(t -> !chosen.equalsIgnoreCase((String) t.getUserData()))
                        .forEach(t -> t.setSelected(false));
            });
            HBox custom = new HBox(8, new Label("Custom:"), picker);
            custom.setAlignment(Pos.CENTER_LEFT);
            node.getChildren().addAll(swatches, custom);
        }

        String value() {
            return hex(picker.getValue());
        }
    }

    private static Dialog<ButtonType> baseDialog(Window owner, String title) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(title);
        d.getDialogPane().getStylesheets().add(
                BucketDialogs.class.getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        d.getDialogPane().getStyleClass().add("cozy-dialog");
        return d;
    }

    public static Optional<Subject> showAdd(Window owner, TodayState today, Database db) {
        Dialog<ButtonType> d = baseDialog(owner, "Add a bucket");
        VBox box = new VBox(12);
        box.setPadding(new Insets(8, 4, 4, 4));
        box.setPrefWidth(400);

        Label title = new Label("Which subject are you studying?");
        title.getStyleClass().add("dialog-title");
        box.getChildren().add(title);

        final Subject[] picked = {null};
        List<Subject> previous = today.subjectsNotOnPier();
        if (!previous.isEmpty()) {
            box.getChildren().add(new Label("Studied before — click to add:"));
            FlowPane chips = new FlowPane(6, 6);
            for (Subject s : previous) {
                Button chip = new Button(s.name());
                chip.getStyleClass().add("chip");
                chip.setStyle("-fx-border-color: " + s.colorHex() + "; -fx-border-width: 0 0 0 6;");
                chip.setOnAction(e -> {
                    picked[0] = s;
                    d.setResult(ButtonType.OK);
                    d.close();
                });
                chips.getChildren().add(chip);
            }
            box.getChildren().addAll(chips, new Label("…or create a new one:"));
        }

        TextField name = new TextField();
        name.setPromptText("e.g. Math, Physics, English");
        String suggested = PALETTE[(int) (Math.random() * PALETTE.length)];
        ColorChooser colors = new ColorChooser(suggested);
        box.getChildren().addAll(new Label("Bucket name"), name, new Label("Bucket color"), colors.node);

        ButtonType add = new ButtonType("Add bucket", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(add).disableProperty().bind(name.textProperty().isEmpty());
        d.getDialogPane().setContent(box);
        javafx.application.Platform.runLater(name::requestFocus);

        Optional<ButtonType> r = d.showAndWait();
        if (picked[0] != null) {
            return Optional.of(picked[0]);
        }
        if (r.isEmpty() || r.get() != add || name.getText().isBlank()) {
            return Optional.empty();
        }
        String n = name.getText().trim();
        Optional<Subject> existing = db.findSubjectByName(n);
        return Optional.of(existing.orElseGet(() -> db.createSubject(n, colors.value())));
    }

    public static Optional<EditResult> showEdit(Window owner, Subject s, boolean canRemove) {
        Dialog<ButtonType> d = baseDialog(owner, "Edit bucket");
        VBox box = new VBox(12);
        box.setPadding(new Insets(8, 4, 4, 4));
        box.setPrefWidth(400);
        Label title = new Label("Edit \"" + s.name() + "\"");
        title.getStyleClass().add("dialog-title");
        TextField name = new TextField(s.name());
        ColorChooser colors = new ColorChooser(s.colorHex());
        Label note = new Label("Renaming changes this subject in your reports too.");
        note.getStyleClass().add("muted");
        box.getChildren().addAll(title, new Label("Bucket name"), name, new Label("Bucket color"), colors.node, note);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType remove = new ButtonType("Remove from today", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (canRemove) {
            d.getDialogPane().getButtonTypes().add(remove);
        }
        d.getDialogPane().lookupButton(save).disableProperty().bind(name.textProperty().isEmpty());
        d.getDialogPane().setContent(box);

        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty()) {
            return Optional.empty();
        }
        if (r.get() == remove) {
            return Optional.of(new Removed());
        }
        if (r.get() == save) {
            return Optional.of(new Saved(new Subject(s.id(), name.getText().trim(), colors.value())));
        }
        return Optional.empty();
    }
}
