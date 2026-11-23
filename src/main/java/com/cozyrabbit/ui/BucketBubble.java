package com.cozyrabbit.ui;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.service.CatchCalculator.Catch;
import com.cozyrabbit.service.ReportService;
import com.cozyrabbit.service.TodayState.BucketView;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A speech bubble that pops up above a bucket on the pier and shows the fish inside it.
 * It lives in a transparent layer over the scene, so clicks elsewhere still reach the scene.
 */
public final class BucketBubble extends Pane {

    public interface Actions {
        void studyThis(Subject s);

        void editBucket(Subject s);
    }

    private static final double BUBBLE_WIDTH = 290;
    private static final int MAX_TILES = 12;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Actions actions;
    private final Group group = new Group();
    private final VBox box = new VBox(8);
    private final Polygon tail = new Polygon();
    private long subjectId = -1;
    private String signature = "";

    public BucketBubble(Actions actions) {
        this.actions = actions;
        setPickOnBounds(false);
        box.getStyleClass().add("speech-bubble");
        box.setPrefWidth(BUBBLE_WIDTH);
        box.setMaxWidth(BUBBLE_WIDTH);
        tail.getPoints().setAll(0.0, 0.0, 18.0, 0.0, 6.0, 14.0);
        tail.setFill(Color.web("#FFF9EE"));
        tail.setStroke(Color.web("#D9C1A0"));
        tail.setStrokeWidth(1);
        group.getChildren().addAll(tail, box);
        group.setVisible(false);
        getChildren().add(group);
    }

    public boolean isOpen() {
        return group.isVisible();
    }

    public long subjectId() {
        return isOpen() ? subjectId : -1;
    }

    public void hide() {
        group.setVisible(false);
        subjectId = -1;
        signature = "";
    }

    /** Opens the bubble for a bucket. {@code anchorX/Y} is the top centre of the bucket in layer coordinates. */
    public void show(BucketView b, boolean active, double anchorX, double anchorY) {
        subjectId = b.subject().id();
        signature = "";
        refresh(b, active);
        group.setVisible(true);
        place(anchorX, anchorY);
        group.setScaleX(0.6);
        group.setScaleY(0.6);
        group.setOpacity(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), group);
        scale.setToX(1);
        scale.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(140), group);
        fade.setToValue(1);
        scale.play();
        fade.play();
    }

    /** Rebuilds the content only when the bucket actually changed (a new fish, a rename). */
    public void refresh(BucketView b, boolean active) {
        String sig = b.subject() + "|" + b.fish().size() + "|" + (b.seconds() / 60) + "|" + active;
        if (sig.equals(signature)) {
            return;
        }
        signature = sig;
        box.getChildren().clear();

        Region swatch = new Region();
        swatch.getStyleClass().add("bucket-swatch");
        swatch.setStyle("-fx-background-color: " + b.subject().colorHex() + ";");
        Label title = new Label(b.subject().name());
        title.getStyleClass().add("bubble-title");
        title.setMaxWidth(190);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("×");
        close.getStyleClass().add("bubble-close");
        close.setOnAction(e -> hide());
        HBox head = new HBox(8, swatch, title, spacer, close);
        head.setAlignment(Pos.CENTER_LEFT);

        int n = b.fish().size();
        Label sub = new Label(ReportService.formatDuration(b.seconds()) + " studied today · " + n + " fish");
        sub.getStyleClass().add("muted");
        box.getChildren().addAll(head, sub);

        List<Catch> catches = b.catches();
        if (catches.isEmpty()) {
            Label empty = new Label("Still empty. Study this subject and the first fish will bite soon.");
            empty.setWrapText(true);
            empty.getStyleClass().add("bubble-note");
            box.getChildren().add(empty);
        } else {
            Map<FishType, Integer> counts = new EnumMap<>(FishType.class);
            for (Catch c : catches) {
                counts.merge(c.type(), 1, Integer::sum);
            }
            FlowPane chips = new FlowPane(6, 6);
            counts.forEach((type, count) -> {
                Label chip = new Label("×" + count, PixelIcons.fish(type, 2));
                chip.getStyleClass().add(type.rare() ? "fish-chip-rare" : "fish-chip");
                Tooltip.install(chip, new Tooltip(type.label()));
                chips.getChildren().add(chip);
            });

            FlowPane tiles = new FlowPane(5, 5);
            int from = Math.max(0, catches.size() - MAX_TILES);
            for (int i = catches.size() - 1; i >= from; i--) {
                Catch c = catches.get(i);
                Label time = new Label(c.at().format(HM));
                time.getStyleClass().add("bubble-time");
                VBox tile = new VBox(1, PixelIcons.fish(c.type(), 3), time);
                tile.setAlignment(Pos.CENTER);
                tile.getStyleClass().add(c.type().rare() ? "fish-tile-rare" : "fish-tile");
                Tooltip.install(tile, new Tooltip(c.type().label() + " caught at " + c.at().format(HM)));
                tiles.getChildren().add(tile);
            }
            box.getChildren().addAll(chips, tiles);
            if (from > 0) {
                Label more = new Label("+ " + from + " earlier fish");
                more.getStyleClass().add("muted");
                box.getChildren().add(more);
            }
        }

        Subject subject = b.subject();
        HBox buttons = new HBox(8);
        if (!active) {
            Button study = new Button("Study this");
            study.getStyleClass().add("accent");
            study.setOnAction(e -> {
                hide();
                actions.studyThis(subject);
            });
            buttons.getChildren().add(study);
        }
        Button edit = new Button("Edit bucket");
        edit.setOnAction(e -> {
            hide();
            actions.editBucket(subject);
        });
        buttons.getChildren().add(edit);
        box.getChildren().add(buttons);
    }

    /** Keeps the bubble above the bucket and inside the layer. */
    public void place(double anchorX, double anchorY) {
        if (!isOpen()) {
            return;
        }
        box.applyCss();
        box.layout();
        double w = BUBBLE_WIDTH;
        double h = box.prefHeight(w);
        double x = Math.max(8, Math.min(getWidth() - w - 8, anchorX - w * 0.35));
        double y = anchorY - h - 14;
        boolean below = y < 8;
        if (below) {
            y = Math.min(getHeight() - h - 8, anchorY + 46);
        }
        box.resizeRelocate(x, y, w, h);
        double tx = Math.max(x + 14, Math.min(x + w - 30, anchorX - 6));
        if (below) {
            tail.getPoints().setAll(0.0, 14.0, 18.0, 14.0, 6.0, 0.0);
            tail.relocate(tx, y - 13);
        } else {
            tail.getPoints().setAll(0.0, 0.0, 18.0, 0.0, 6.0, 14.0);
            tail.relocate(tx, y + h - 1);
        }
    }
}
