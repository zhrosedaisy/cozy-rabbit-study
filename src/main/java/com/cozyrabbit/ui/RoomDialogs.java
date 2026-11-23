package com.cozyrabbit.ui;

import com.cozyrabbit.storage.AppSettings;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Asks for a nickname and hat color, and for the room code when joining. */
public final class RoomDialogs {

    public static final String[] HAT_COLORS = {
            "#7D8A55", "#C9853F", "#B5473A", "#5B7FA6", "#8A6BB0", "#D97A9A", "#4E8C80", "#6B5A4A",
    };

    private RoomDialogs() {
    }

    private static Dialog<ButtonType> base(Window owner, String title) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(title);
        d.getDialogPane().getStylesheets().add(RoomDialogs.class.getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        d.getDialogPane().getStyleClass().add("cozy-dialog");
        return d;
    }

    /** Nickname and hat color fields, filled from the settings. */
    private static final class Identity {
        final TextField name = new TextField();
        final ToggleGroup hats = new ToggleGroup();
        final VBox node;

        Identity(AppSettings s) {
            name.setText(s.playerName());
            name.setPromptText("Alex");
            FlowPane swatches = new FlowPane(6, 6);
            for (String c : HAT_COLORS) {
                ToggleButton b = new ToggleButton();
                b.getStyleClass().add("swatch");
                b.setStyle("-fx-background-color: " + c + ";");
                b.setUserData(c);
                b.setToggleGroup(hats);
                if (c.equalsIgnoreCase(s.hatColor())) {
                    b.setSelected(true);
                }
                swatches.getChildren().add(b);
            }
            if (hats.getSelectedToggle() == null) {
                hats.getToggles().get(0).setSelected(true);
            }
            hats.selectedToggleProperty().addListener((o, a, b) -> {
                if (b == null) {
                    a.setSelected(true);
                }
            });
            node = new VBox(8, new Label("Your name in the room"), name, new Label("Your hat color"), swatches);
        }

        void save(AppSettings s) {
            s.setPlayerName(name.getText());
            s.setHatColor((String) hats.getSelectedToggle().getUserData());
        }
    }

    public static boolean showHost(Window owner, AppSettings s) {
        Dialog<ButtonType> d = base(owner, "Host a study room");
        Label title = new Label("Host a study room");
        title.getStyleClass().add("dialog-title");
        Label note = new Label("Friends on the same Wi-Fi join with the code (or address) you'll see in your journal. "
                + "School and café Wi-Fi often blocks this; a phone hotspot works. "
                + "Only your name, hat color, current subject and today's study time and fish are shared.");
        note.setWrapText(true);
        note.getStyleClass().add("muted");
        Identity id = new Identity(s);
        VBox box = new VBox(12, title, note, id.node);
        box.setPadding(new Insets(6, 2, 2, 2));
        box.setPrefWidth(400);
        ButtonType host = new ButtonType("Open room", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(host, new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(host).disableProperty().bind(id.name.textProperty().isEmpty());
        d.getDialogPane().setContent(box);
        javafx.application.Platform.runLater(id.name::requestFocus);
        Optional<ButtonType> r = d.showAndWait();
        if (r.isPresent() && r.get() == host) {
            id.save(s);
            return true;
        }
        return false;
    }

    /** @return the room code or host address the user typed */
    public static Optional<String> showJoin(Window owner, AppSettings s) {
        Dialog<ButtonType> d = base(owner, "Join a study room");
        Label title = new Label("Join a study room");
        title.getStyleClass().add("dialog-title");
        TextField code = new TextField();
        code.setPromptText("4-digit code, or the host's address like 192.168.1.20");
        Label note = new Label("Ask the host for the code or address shown in their Study room section. "
                + "You need to be on the same network, and some school Wi-Fi networks don't allow that.");
        note.setWrapText(true);
        note.getStyleClass().add("muted");
        Identity id = new Identity(s);
        VBox box = new VBox(12, title, new Label("Room code"), code, note, id.node);
        box.setPadding(new Insets(6, 2, 2, 2));
        box.setPrefWidth(400);
        ButtonType join = new ButtonType("Join", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(join, new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(join).disableProperty()
                .bind(id.name.textProperty().isEmpty().or(code.textProperty().isEmpty()));
        d.getDialogPane().setContent(box);
        javafx.application.Platform.runLater(code::requestFocus);
        Optional<ButtonType> r = d.showAndWait();
        if (r.isPresent() && r.get() == join) {
            id.save(s);
            return Optional.of(code.getText().trim());
        }
        return Optional.empty();
    }
}
