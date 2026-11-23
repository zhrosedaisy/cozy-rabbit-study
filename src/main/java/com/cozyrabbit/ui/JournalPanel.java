package com.cozyrabbit.ui;

import com.cozyrabbit.model.Subject;
import com.cozyrabbit.model.TimerMode;
import com.cozyrabbit.model.Weather;
import com.cozyrabbit.service.ReportService;
import com.cozyrabbit.service.TodayState.BucketView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * The study journal on the right side of the window: a ruled notebook page with the timer,
 * today's buckets, the daily goal and the weather controls.
 */
public final class JournalPanel extends HBox {

    public static final double WIDTH = 340;

    /** What the journal can ask the app to do. */
    public interface Actions {
        void toggleStart();

        void stopTimer();

        void skip();

        void setMode(TimerMode mode);

        void selectBucket(Subject s);

        void addBucket();

        void showFish(Subject s);

        void editBucket(Subject s);

        void deleteSubject(Subject s);

        void hostRoom();

        void joinRoom();

        void leaveRoom();

        void openReports();

        void openSettings();

        void setWeather(Weather w);

        void setRainSound(boolean on);

        void setRainVolume(int volume);
    }

    /** Everything the journal shows, computed once per frame by the app. */
    public record Snapshot(String date, int streak, String phase, String time, double fishProgress, String fishText,
                           String startText, boolean startDisabled, boolean stopDisabled, boolean pomodoro,
                           boolean running, List<BucketView> buckets, long activeId, long todaySeconds,
                           long goalSeconds) {
    }

    /** The study room as the journal shows it; null when you're studying alone. */
    public record RoomView(String code, String address, boolean host, boolean connected, String status,
                           List<com.cozyrabbit.net.MemberState> members, String myId) {
    }

    private final Actions actions;
    private final VBox roomBox = new VBox(6);
    private String roomSignature = "-";

    private final Label dateLabel = new Label();
    private final Label streakLabel = new Label();
    private final Label phaseLabel = new Label();
    private final Label timeLabel = new Label();
    private final ProgressBar fishBar = new ProgressBar(0);
    private final Label fishLabel = new Label();
    private final Button startButton = new Button("Start");
    private final Button stopButton = new Button("Stop");
    private final Button skipButton = new Button("Skip");
    private final ToggleButton unlimited;
    private final ToggleButton pomodoro;
    private final VBox bucketList = new VBox(4);
    private final Label goalLabel = new Label();
    private final ProgressBar goalBar = new ProgressBar(0);
    private final ToggleButton sunny;
    private final ToggleButton rainy;
    private final ToggleButton snowy;
    private final CheckBox rainSound = new CheckBox("Rain sound");
    private final Slider rainVolume = new Slider(0, 100, 60);
    private String bucketSignature = "";
    private boolean updatingToggles;

    public JournalPanel(Actions actions, TimerMode mode) {
        this.actions = actions;
        getStyleClass().add("journal");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);

        // ---- header
        dateLabel.getStyleClass().add("journal-title");
        streakLabel.getStyleClass().add("streak-badge");
        Region hs = new Region();
        HBox.setHgrow(hs, Priority.ALWAYS);
        HBox header = new HBox(8, dateLabel, hs, streakLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---- timer
        phaseLabel.getStyleClass().add("journal-phase");
        timeLabel.getStyleClass().add("journal-time");
        fishBar.getStyleClass().add("fish-bar");
        fishBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fishBar, Priority.ALWAYS);
        fishLabel.getStyleClass().add("muted");
        fishLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox fishRow = new HBox(8, PixelIcons.fish(), fishBar, fishLabel);
        fishRow.setAlignment(Pos.CENTER_LEFT);

        startButton.getStyleClass().addAll("accent", "big");
        startButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startButton, Priority.ALWAYS);
        startButton.setOnAction(e -> actions.toggleStart());
        stopButton.getStyleClass().add("big");
        stopButton.setOnAction(e -> actions.stopTimer());
        skipButton.getStyleClass().add("big");
        skipButton.setTooltip(new Tooltip("Jump to the next pomodoro phase"));
        skipButton.setOnAction(e -> actions.skip());
        // All three share the card width so the page never grows wider than the journal
        // (the Skip button appears in pomodoro mode).
        for (Button b : new Button[]{startButton, stopButton, skipButton}) {
            b.setMinWidth(0);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        HBox buttons = new HBox(8, startButton, stopButton, skipButton);

        ToggleGroup modeGroup = new ToggleGroup();
        unlimited = segment("Unlimited", modeGroup, "seg-left");
        pomodoro = segment("Pomodoro", modeGroup, "seg-right");
        unlimited.setMaxWidth(Double.MAX_VALUE);
        pomodoro.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(unlimited, Priority.ALWAYS);
        HBox.setHgrow(pomodoro, Priority.ALWAYS);
        (mode == TimerMode.POMODORO ? pomodoro : unlimited).setSelected(true);
        modeGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
            } else {
                actions.setMode(b == pomodoro ? TimerMode.POMODORO : TimerMode.UNLIMITED);
            }
        });
        HBox modes = new HBox(0, unlimited, pomodoro);

        VBox timerCard = new VBox(6, phaseLabel, timeLabel, fishRow, buttons, modes);
        timerCard.getStyleClass().add("journal-card");

        // ---- buckets
        Label bucketsTitle = new Label("Today's buckets");
        bucketsTitle.getStyleClass().add("journal-heading");
        Button add = new Button("+ Add");
        add.getStyleClass().add("link-button");
        add.setOnAction(e -> actions.addBucket());
        Region bs = new Region();
        HBox.setHgrow(bs, Priority.ALWAYS);
        HBox bucketsHeader = new HBox(bucketsTitle, bs, add);
        bucketsHeader.setAlignment(Pos.CENTER_LEFT);

        // ---- goal
        Label goalTitle = new Label("Daily goal");
        goalTitle.getStyleClass().add("journal-heading");
        goalBar.getStyleClass().add("goal-bar");
        goalBar.setMaxWidth(Double.MAX_VALUE);
        goalLabel.getStyleClass().add("muted");

        // ---- weather
        Label weatherTitle = new Label("Weather");
        weatherTitle.getStyleClass().add("journal-heading");
        ToggleGroup weatherGroup = new ToggleGroup();
        sunny = segment("", weatherGroup, "seg-left");
        sunny.setGraphic(PixelIcons.sun());
        sunny.setTooltip(new Tooltip("Sunny"));
        rainy = segment("", weatherGroup, "seg-mid");
        rainy.setGraphic(PixelIcons.rain());
        rainy.setTooltip(new Tooltip("Rainy"));
        snowy = segment("", weatherGroup, "seg-right");
        snowy.setGraphic(PixelIcons.snow());
        snowy.setTooltip(new Tooltip("Snowy"));
        weatherGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
            } else if (!updatingToggles) {
                actions.setWeather(b == rainy ? Weather.RAINY : (b == snowy ? Weather.SNOWY : Weather.SUNNY));
            }
        });
        HBox weatherRow = new HBox(0, sunny, rainy, snowy);
        rainSound.setOnAction(e -> actions.setRainSound(rainSound.isSelected()));
        rainVolume.setPrefWidth(110);
        rainVolume.valueChangingProperty().addListener((o, a, changing) -> {
            if (!changing) {
                actions.setRainVolume((int) rainVolume.getValue());
            }
        });
        rainVolume.valueProperty().addListener((o, a, b) -> {
            if (!rainVolume.isValueChanging() && !updatingToggles) {
                actions.setRainVolume(b.intValue());
            }
        });
        Region ws = new Region();
        HBox.setHgrow(ws, Priority.ALWAYS);
        HBox soundRow = new HBox(10, rainSound, ws, rainVolume);
        soundRow.setAlignment(Pos.CENTER_LEFT);
        HBox weatherLine = new HBox(10, weatherTitle, new Region(), weatherRow);
        HBox.setHgrow(weatherLine.getChildren().get(1), Priority.ALWAYS);
        weatherLine.setAlignment(Pos.CENTER_LEFT);

        // ---- footer
        Button reports = new Button("Reports");
        reports.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(reports, Priority.ALWAYS);
        reports.setOnAction(e -> actions.openReports());
        Button settings = new Button("Settings");
        settings.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(settings, Priority.ALWAYS);
        settings.setOnAction(e -> actions.openSettings());
        HBox footer = new HBox(8, reports, settings);

        VBox page = new VBox(10,
                header,
                timerCard,
                bucketsHeader, bucketList,
                roomBox,
                goalTitle, goalBar, goalLabel,
                weatherLine, soundRow,
                footer);
        page.getStyleClass().add("journal-page");
        page.setMinWidth(0);
        page.setMaxWidth(WIDTH - 26);
        page.setPadding(new Insets(18, 18, 18, 16));
        VBox.setMargin(footer, new Insets(6, 0, 0, 0));

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("journal-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(spiral(), scroll);
    }

    private static ToggleButton segment(String text, ToggleGroup g, String cls) {
        ToggleButton t = new ToggleButton(text);
        t.setToggleGroup(g);
        t.getStyleClass().addAll("segment", cls);
        return t;
    }

    /** The spiral binding along the left edge of the notebook. */
    private static Pane spiral() {
        Canvas c = new Canvas(26, 100);
        Pane p = new Pane(c);
        p.getStyleClass().add("journal-spine");
        p.setMinWidth(26);
        p.setPrefWidth(26);
        c.setManaged(false);
        c.heightProperty().bind(p.heightProperty());
        c.heightProperty().addListener((o, a, b) -> {
            GraphicsContext g = c.getGraphicsContext2D();
            g.clearRect(0, 0, 26, b.doubleValue());
            for (double y = 18; y < b.doubleValue() - 6; y += 26) {
                g.setFill(Color.web("#3A2A20", 0.55));
                g.fillOval(15, y - 3, 9, 9);
                g.setStroke(Color.web("#9C9AA6"));
                g.setLineWidth(3);
                g.strokeArc(4, y - 5, 18, 12, 200, 220, javafx.scene.shape.ArcType.OPEN);
                g.setStroke(Color.web("#E4E2EA"));
                g.setLineWidth(1.2);
                g.strokeArc(5, y - 4.5, 16, 10, 210, 180, javafx.scene.shape.ArcType.OPEN);
            }
        });
        return p;
    }

    public void setWeather(Weather w, boolean rainOn, int volume) {
        updatingToggles = true;
        sunny.setSelected(w == Weather.SUNNY);
        rainy.setSelected(w == Weather.RAINY);
        snowy.setSelected(w == Weather.SNOWY);
        rainSound.setSelected(rainOn);
        rainSound.setDisable(w != Weather.RAINY);
        rainVolume.setValue(volume);
        rainVolume.setDisable(w != Weather.RAINY || !rainOn);
        updatingToggles = false;
    }

    public void update(Snapshot s) {
        set(dateLabel, s.date());
        set(streakLabel, s.streak() == 1 ? "1 day streak" : s.streak() + " day streak");
        set(phaseLabel, s.phase());
        set(timeLabel, s.time());
        fishBar.setProgress(s.fishProgress());
        set(fishLabel, s.fishText());
        startButton.setText(s.startText());
        startButton.setDisable(s.startDisabled());
        stopButton.setDisable(s.stopDisabled());
        skipButton.setVisible(s.pomodoro());
        skipButton.setManaged(s.pomodoro());
        skipButton.setDisable(!s.running() && s.stopDisabled());
        unlimited.setDisable(!s.stopDisabled());
        pomodoro.setDisable(!s.stopDisabled());

        long goal = s.goalSeconds();
        goalBar.setProgress(goal == 0 ? 0 : Math.min(1, s.todaySeconds() / (double) goal));
        set(goalLabel, (s.todaySeconds() >= goal && goal > 0 ? "Goal reached!  " : "")
                + ReportService.formatDuration(s.todaySeconds()) + " of " + ReportService.formatDuration(goal));

        StringBuilder sig = new StringBuilder().append(s.activeId());
        for (BucketView b : s.buckets()) {
            sig.append('|').append(b.subject()).append(b.seconds() / 60).append(':').append(b.fish().size());
        }
        if (!sig.toString().equals(bucketSignature)) {
            bucketSignature = sig.toString();
            rebuildBuckets(s);
        }
    }

    public void updateRoom(RoomView v) {
        StringBuilder sig = new StringBuilder();
        if (v != null) {
            sig.append(v.code()).append(v.connected()).append(v.status());
            for (com.cozyrabbit.net.MemberState m : v.members()) {
                sig.append('|').append(m.id()).append(m.name()).append(m.hatColor()).append(m.status())
                        .append(m.subject()).append(m.todaySeconds() / 60).append(m.todayFish());
            }
        }
        if (sig.toString().equals(roomSignature)) {
            return;
        }
        roomSignature = sig.toString();
        roomBox.getChildren().clear();

        Label title = new Label("Study room");
        title.getStyleClass().add("journal-heading");
        if (v == null) {
            Label note = new Label("Study side by side with friends on the same Wi-Fi.");
            note.setWrapText(true);
            note.setMinHeight(Region.USE_PREF_SIZE);
            note.getStyleClass().add("muted");
            Button host = new Button("Host a room");
            Button join = new Button("Join a room");
            host.setOnAction(e -> actions.hostRoom());
            join.setOnAction(e -> actions.joinRoom());
            for (Button b : new Button[]{host, join}) {
                b.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(b, Priority.ALWAYS);
            }
            roomBox.getChildren().addAll(title, note, new HBox(8, host, join));
            return;
        }

        Button leave = new Button(v.host() ? "Close" : "Leave");
        leave.getStyleClass().add("link-button");
        leave.setMinWidth(Region.USE_PREF_SIZE);
        leave.setOnAction(e -> actions.leaveRoom());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        title.setMinWidth(Region.USE_PREF_SIZE);
        HBox head = new HBox(8, title, sp, leave);
        head.setAlignment(Pos.CENTER_LEFT);
        Label code = new Label("Code " + v.code());
        code.getStyleClass().add("room-code");
        code.setMinWidth(Region.USE_PREF_SIZE);
        Label where = new Label(v.host() ? (Character.isDigit(v.address().charAt(0)) ? "address " : "") + v.address()
                : (v.connected() ? "connected" : ""));
        where.getStyleClass().add("muted");
        HBox codeRow = new HBox(8, code, where);
        codeRow.setAlignment(Pos.CENTER_LEFT);
        roomBox.getChildren().addAll(head, codeRow);
        if (!v.connected() || (v.status() != null && !v.status().isEmpty())) {
            Label st = new Label(v.status() == null || v.status().isEmpty() ? "Connecting…" : v.status());
            st.setWrapText(true);
            st.setMinHeight(Region.USE_PREF_SIZE);
            st.getStyleClass().add("room-status");
            roomBox.getChildren().add(st);
        }

        List<com.cozyrabbit.net.MemberState> sorted = new java.util.ArrayList<>(v.members());
        sorted.sort((a, b) -> Long.compare(b.todaySeconds(), a.todaySeconds()));
        int rank = 1;
        for (com.cozyrabbit.net.MemberState m : sorted) {
            Label place = new Label(rank + ".");
            place.getStyleClass().add("room-rank");
            place.setMinWidth(18);
            Region hat = new Region();
            hat.getStyleClass().add("bucket-swatch");
            hat.setStyle("-fx-background-color: " + m.hatColor() + "; -fx-background-radius: 7; -fx-border-radius: 7;");
            boolean me = m.id().equals(v.myId());
            Label name = new Label(m.name() + (me ? " (you)" : ""));
            name.getStyleClass().add("bucket-name");
            name.setMaxWidth(110);
            String doing = switch (m.status()) {
                case FISHING -> "fishing" + (m.subject().isEmpty() ? "" : " · " + m.subject());
                case COFFEE -> "coffee break";
                case DOZING -> "resting";
            };
            Label what = new Label(doing);
            what.getStyleClass().add("room-doing");
            what.setMaxWidth(130);
            VBox who = new VBox(0, name, what);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label time = new Label(ReportService.formatDuration(m.todaySeconds()));
            time.getStyleClass().add("muted");
            time.setMinWidth(Region.USE_PREF_SIZE);
            Label fish = new Label(Integer.toString(m.todayFish()), PixelIcons.fish());
            fish.getStyleClass().add(m.rareFish() > 0 ? "fish-chip-rare" : "fish-chip");
            fish.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(8, place, hat, who, spacer, time, fish);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add(me ? "bucket-row-active" : "bucket-row");
            Tooltip.install(row, new Tooltip(m.name() + ": " + ReportService.formatDuration(m.todaySeconds())
                    + " today, " + ReportService.formatDuration(m.weekSeconds()) + " this week, "
                    + m.todayFish() + " fish today" + (m.rareFish() > 0 ? " (" + m.rareFish() + " rare)" : "")
                    + ", " + m.streak() + " day streak"));
            roomBox.getChildren().add(row);
            rank++;
        }
    }

    private static void set(Label l, String text) {
        if (!text.equals(l.getText())) {
            l.setText(text);
        }
    }

    private void rebuildBuckets(Snapshot s) {
        bucketList.getChildren().clear();
        if (s.buckets().isEmpty()) {
            Label empty = new Label("The pier is empty today. Add a bucket for the subject you're studying.");
            empty.setWrapText(true);
            empty.setMinHeight(Region.USE_PREF_SIZE);
            empty.getStyleClass().add("muted");
            bucketList.getChildren().add(empty);
            return;
        }
        for (BucketView b : s.buckets()) {
            Subject subject = b.subject();
            Region swatch = new Region();
            swatch.getStyleClass().add("bucket-swatch");
            swatch.setStyle("-fx-background-color: " + subject.colorHex() + ";");
            Label name = new Label(subject.name());
            name.getStyleClass().add("bucket-name");
            name.setMaxWidth(120);
            Label time = new Label(ReportService.formatDuration(b.seconds()));
            time.getStyleClass().add("muted");
            time.setMinWidth(Region.USE_PREF_SIZE);
            Button fish = new Button(Integer.toString(b.fish().size()), PixelIcons.fish());
            fish.getStyleClass().add("fish-button");
            fish.setMinWidth(Region.USE_PREF_SIZE);
            fish.setTooltip(new Tooltip("See the fish in this bucket"));
            fish.setOnAction(e -> actions.showFish(subject));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(8, swatch, name, spacer, time, fish);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add(subject.id() == s.activeId() ? "bucket-row-active" : "bucket-row");
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    if (e.getClickCount() == 2) {
                        actions.showFish(subject);
                    } else {
                        actions.selectBucket(subject);
                    }
                }
            });
            MenuItem study = new MenuItem("Study this");
            study.setOnAction(e -> actions.selectBucket(subject));
            MenuItem see = new MenuItem("See fish");
            see.setOnAction(e -> actions.showFish(subject));
            MenuItem edit = new MenuItem("Edit bucket…");
            edit.setOnAction(e -> actions.editBucket(subject));
            MenuItem delete = new MenuItem("Delete subject…");
            delete.getStyleClass().add("danger-item");
            delete.setOnAction(e -> actions.deleteSubject(subject));
            ContextMenu menu = new ContextMenu(study, see, edit, new javafx.scene.control.SeparatorMenuItem(), delete);
            row.setOnContextMenuRequested(e -> menu.show(row, e.getScreenX(), e.getScreenY()));
            Tooltip.install(row, new Tooltip("Click to study this subject · right-click for more"));
            bucketList.getChildren().add(row);
        }
    }
}
