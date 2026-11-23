package com.cozyrabbit.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * A small always-on-top "island" at the top centre of the screen, like the Dynamic Island on an iPhone.
 * Compact it shows the running time; click it to open controls (pause, fish, stop, open the app).
 * It can be dragged anywhere.
 */
public final class MiniIsland {

    public interface Actions {
        void toggleStart();

        void stopTimer();

        void showActiveFish();

        void openApp();
    }

    /** What the island shows, refreshed a few times per second by the app. */
    public record State(String phase, String time, String subject, String subjectColor, String fishText,
                        double fishProgress, int fishToday, boolean idle, boolean running, boolean canStart) {
    }

    private static final double MARGIN = 14; // room for the soft shadow

    private final Actions actions;
    private final Stage owner = new Stage(StageStyle.UTILITY);
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final StackPane root = new StackPane();
    private final VBox pill = new VBox();

    // compact
    private final Label compactTime = new Label();
    private final Region compactDot = new Region();
    private final Button compactButton = new Button();
    private final HBox compact;

    // expanded
    private final Label phase = new Label();
    private final Label bigTime = new Label();
    private final Region subjectDot = new Region();
    private final Label subject = new Label();
    private final Label fishText = new Label();
    private final ProgressBar fishBar = new ProgressBar(0);
    private final Label fishCount = new Label();
    private final Button startButton = new Button();
    private final Button stopButton = new Button();
    private final VBox expanded;

    private boolean open;
    private boolean placed;
    private double pressX;
    private double pressY;
    private boolean dragged;
    private String lastSignature = "";

    public MiniIsland(Actions actions) {
        this.actions = actions;

        // An invisible utility owner keeps the island out of the Dock / taskbar.
        owner.setOpacity(0);
        owner.setWidth(1);
        owner.setHeight(1);
        owner.setX(-10_000);
        owner.setY(-10_000);
        stage.initOwner(owner);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setTitle("Cozy Rabbit Study");

        // ---- compact pill: fish · time · pause
        compactTime.getStyleClass().add("island-time");
        compactDot.getStyleClass().add("island-dot");
        compactButton.getStyleClass().add("island-icon-button");
        compactButton.setOnAction(e -> actions.toggleStart());
        compact = new HBox(9, PixelIcons.fish(), compactTime, compactDot, compactButton);
        compact.setAlignment(Pos.CENTER_LEFT);
        compact.setPadding(new Insets(6, 8, 6, 14));

        // ---- expanded panel
        Label title = new Label("Cozy Rabbit", PixelIcons.fish());
        title.getStyleClass().add("island-title");
        phase.getStyleClass().add("island-phase");
        Region s1 = new Region();
        HBox.setHgrow(s1, Priority.ALWAYS);
        HBox head = new HBox(8, title, s1, phase);
        head.setAlignment(Pos.CENTER_LEFT);

        bigTime.getStyleClass().add("island-big-time");
        subjectDot.getStyleClass().add("island-dot");
        subject.getStyleClass().add("island-subject");
        HBox subjectRow = new HBox(7, subjectDot, subject);
        subjectRow.setAlignment(Pos.CENTER);
        VBox timeCard = new VBox(2, bigTime, subjectRow);
        timeCard.setAlignment(Pos.CENTER);
        timeCard.getStyleClass().add("island-card");

        fishBar.getStyleClass().add("island-bar");
        fishBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fishBar, Priority.ALWAYS);
        fishText.getStyleClass().add("island-muted");
        fishCount.getStyleClass().add("island-muted");
        HBox fishRow = new HBox(8, fishBar, fishText);
        fishRow.setAlignment(Pos.CENTER_LEFT);

        startButton.setOnAction(e -> actions.toggleStart());
        stopButton.setGraphic(icon("M0 0h10v10H0z", 10));
        stopButton.setText("Stop");
        stopButton.setOnAction(e -> actions.stopTimer());
        Button fish = new Button("Fish", PixelIcons.fish());
        fish.setOnAction(e -> actions.showActiveFish());
        Button openApp = new Button("Open", icon("M1 9L9 1M3.5 1H9v5.5", 10, true));
        openApp.setOnAction(e -> actions.openApp());
        HBox buttons = new HBox(6, startButton, fish, stopButton, openApp);
        for (Node n : buttons.getChildren()) {
            Button b = (Button) n;
            b.getStyleClass().add("island-button");
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        Tooltip.install(fish, new Tooltip("Show the fish in the current bucket"));

        expanded = new VBox(10, head, timeCard, fishRow, fishCount, buttons);
        expanded.setPadding(new Insets(14, 16, 14, 16));
        expanded.setPrefWidth(330);

        pill.getStyleClass().add("island");
        pill.getChildren().setAll(compact);
        root.getChildren().add(pill);
        root.setPadding(new Insets(MARGIN));
        root.setStyle("-fx-background-color: transparent;");
        StackPane.setAlignment(pill, Pos.TOP_CENTER);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(MiniIsland.class.getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        stage.setScene(scene);

        // click toggles, drag moves
        pill.setOnMousePressed(e -> {
            pressX = e.getScreenX() - stage.getX();
            pressY = e.getScreenY() - stage.getY();
            dragged = false;
        });
        pill.setOnMouseDragged(e -> {
            double nx = e.getScreenX() - pressX;
            double ny = e.getScreenY() - pressY;
            if (Math.abs(nx - stage.getX()) + Math.abs(ny - stage.getY()) > 2) {
                dragged = true;
            }
            stage.setX(nx);
            stage.setY(ny);
        });
        pill.setOnMouseClicked(e -> {
            if (!dragged && !(e.getTarget() instanceof Button) && !isInsideButton(e.getPickResult().getIntersectedNode())) {
                setOpen(!open);
            }
        });
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                setOpen(false);
            }
        });
    }

    private static boolean isInsideButton(Node n) {
        while (n != null) {
            if (n instanceof Button) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    private static SVGPath icon(String path, double size) {
        return icon(path, size, false);
    }

    private static SVGPath icon(String path, double size, boolean stroke) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        if (stroke) {
            p.setFill(Color.TRANSPARENT);
            p.setStroke(Color.web("#FFF1DA"));
            p.setStrokeWidth(1.6);
        } else {
            p.setFill(Color.web("#FFF1DA"));
        }
        p.getStyleClass().add("island-svg");
        return p;
    }

    private static Node playPause(boolean running) {
        return running ? icon("M0 0h3.5v11H0zM6.5 0H10v11H6.5z", 11) : icon("M0 0L10 5.5L0 11z", 11);
    }

    public void setVisible(boolean visible) {
        if (visible && !stage.isShowing()) {
            if (!owner.isShowing()) {
                owner.show();
            }
            stage.show();
            if (!placed) {
                centerTop();
                placed = true;
            }
        } else if (!visible && stage.isShowing()) {
            stage.hide();
        }
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    /** Top centre of the main screen, just below the menu bar (and the camera notch). */
    private void centerTop() {
        stage.sizeToScene();
        Rectangle2D v = Screen.getPrimary().getVisualBounds();
        stage.setX(v.getMinX() + (v.getWidth() - stage.getWidth()) / 2);
        stage.setY(v.getMinY() + 4 - MARGIN);
    }

    private void setOpen(boolean value) {
        if (open == value) {
            return;
        }
        open = value;
        double centerX = stage.getX() + stage.getWidth() / 2;
        pill.getChildren().setAll(open ? expanded : compact);
        pill.getStyleClass().removeAll("island-open");
        if (open) {
            pill.getStyleClass().add("island-open");
        }
        stage.sizeToScene();
        stage.setX(centerX - stage.getWidth() / 2);
        FadeTransition fade = new FadeTransition(Duration.millis(160), pill.getChildren().get(0));
        fade.setFromValue(0.2);
        fade.setToValue(1);
        fade.play();
    }

    public void update(State s) {
        String sig = s.toString();
        if (sig.equals(lastSignature)) {
            return;
        }
        lastSignature = sig;
        String dotColor = s.subjectColor() == null || s.subjectColor().isEmpty() ? "#8C7460" : s.subjectColor();

        compactTime.setText(s.time());
        compactDot.setStyle("-fx-background-color: " + dotColor + ";");
        compactButton.setGraphic(playPause(s.running()));
        compactButton.setDisable(!s.canStart());
        compactButton.setTooltip(new Tooltip(s.running() ? "Pause" : (s.idle() ? "Start" : "Resume")));

        phase.setText(s.phase());
        phase.getStyleClass().removeAll("island-phase-run", "island-phase-break", "island-phase-idle");
        phase.getStyleClass().add(s.running() ? "island-phase-run" : (s.idle() ? "island-phase-idle" : "island-phase-break"));
        bigTime.setText(s.time());
        subjectDot.setStyle("-fx-background-color: " + dotColor + ";");
        subject.setText(s.subject());
        fishBar.setProgress(s.fishProgress());
        fishText.setText(s.fishText());
        fishCount.setText(s.fishToday() == 1 ? "1 fish caught today" : s.fishToday() + " fish caught today");
        startButton.setGraphic(playPause(s.running()));
        startButton.setText(s.running() ? "Pause" : (s.idle() ? "Start" : "Resume"));
        startButton.setDisable(!s.canStart());
        stopButton.setDisable(s.idle());
        if (!open) {
            double centerX = stage.getX() + stage.getWidth() / 2;
            stage.sizeToScene();
            if (placed) {
                stage.setX(centerX - stage.getWidth() / 2);
            }
        }
    }

    /** Developer helper for screenshots. */
    public Scene debugScene(boolean opened) {
        setOpen(opened);
        return stage.getScene();
    }

    /** Lets the app add the same button click sounds as in the main window. */
    public void addSoundFilter(javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        stage.getScene().addEventFilter(javafx.event.ActionEvent.ACTION, handler);
    }

    public void close() {
        stage.hide();
        owner.hide();
    }
}
