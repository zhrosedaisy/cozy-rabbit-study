package com.cozyrabbit;

import com.cozyrabbit.audio.SoundEngine;
import com.cozyrabbit.model.PomodoroPhase;
import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.model.TimerMode;
import com.cozyrabbit.model.Weather;
import com.cozyrabbit.render.SceneRenderer;
import com.cozyrabbit.render.SceneRenderer.RabbitMode;
import com.cozyrabbit.service.ReportService;
import com.cozyrabbit.service.StudyTimer;
import com.cozyrabbit.service.TodayState;
import com.cozyrabbit.service.TodayState.BucketView;
import com.cozyrabbit.storage.AppSettings;
import com.cozyrabbit.storage.Database;
import com.cozyrabbit.ui.BucketDialogs;
import com.cozyrabbit.ui.BucketBubble;
import com.cozyrabbit.ui.JournalPanel;
import com.cozyrabbit.ui.ReportsWindow;
import com.cozyrabbit.ui.RoomDialogs;
import com.cozyrabbit.ui.SettingsDialog;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Cozy Rabbit Study: a study timer where a fishing rabbit fills buckets with fish. */
public class CozyRabbitApp extends Application implements StudyTimer.Listener, JournalPanel.Actions {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH);

    private Database db;
    private AppSettings settings;
    private TodayState today;
    private StudyTimer timer;
    private ReportService reports;
    private final SoundEngine sound = new SoundEngine();
    private final SceneRenderer renderer = new SceneRenderer();

    private Stage stage;
    private Canvas canvas;
    private WritableImage frameImage;
    private JournalPanel journal;
    private ReportsWindow reportsWindow;
    private BucketBubble bubble;
    private com.cozyrabbit.ui.MiniIsland island;
    private double islandTimer;
    private com.cozyrabbit.net.Room room;
    private String roomStatus = "";
    private double publishTimer;
    private long weekSeconds;

    private List<BucketView> buckets = List.of();
    private int hoverIndex = -1;
    private long lastFrameNanos;
    private double statsTimer;
    private int streak;

    public static void main(String[] args) {
        // Dialog buttons (Cancel, OK) follow the default locale; keep the whole app in English.
        Locale.setDefault(Locale.ENGLISH);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        String dbPath = System.getProperty("cozyrabbit.db");
        db = new Database(dbPath != null ? Path.of(dbPath) : Database.defaultLocation());
        settings = new AppSettings(db);
        today = new TodayState(db, () -> settings.catchMinutes() * 60);
        reports = new ReportService(db, () -> settings.catchMinutes() * 60);
        timer = new StudyTimer(db, settings, this);

        sound.setEffectsEnabled(settings.soundEffects());
        sound.setRainVolume(settings.rainVolume() / 100.0);
        renderer.setSoundCues(new SceneRenderer.SoundCues() {
            @Override
            public void cast() {
                sound.cast();
            }

            @Override
            public void splash() {
                sound.splash();
            }

            @Override
            public void reelIn() {
                sound.reelIn();
            }

            @Override
            public void landed(com.cozyrabbit.model.FishType type) {
                sound.caught(type.rare());
                if (room != null && !room.closed()) {
                    room.announceCatch(settings.playerId(), settings.playerName(), type.name());
                }
            }
        });

        journal = new JournalPanel(this, timer.mode());
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setCenter(buildSceneArea());
        root.setRight(journal);

        Scene scene = new Scene(root, 1400, 840);
        scene.getStylesheets().add(getClass().getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        // every button and toggle in the window gives a small sound
        scene.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (e.getTarget() instanceof javafx.scene.control.ToggleButton) {
                sound.toggle();
            } else if (e.getTarget() instanceof javafx.scene.control.ButtonBase) {
                sound.click();
            }
        });
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                bubble.hide();
            }
        });
        stage.setScene(scene);
        stage.setTitle("Cozy Rabbit Study");
        stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/com/cozyrabbit/icon.png")));
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        List<BucketView> initial = today.buckets(null);
        if (!initial.isEmpty()) {
            timer.setSubject(initial.get(0).subject(), LocalDateTime.now());
        }
        applyWeather();
        refreshStats();

        island = new com.cozyrabbit.ui.MiniIsland(new com.cozyrabbit.ui.MiniIsland.Actions() {
            @Override
            public void toggleStart() {
                CozyRabbitApp.this.toggleStart();
            }

            @Override
            public void stopTimer() {
                CozyRabbitApp.this.stopTimer();
            }

            @Override
            public void showActiveFish() {
                openApp();
                if (timer.subject() != null) {
                    Subject s = timer.subject();
                    Platform.runLater(() -> showFish(s));
                }
            }

            @Override
            public void openApp() {
                CozyRabbitApp.this.openApp();
            }
        });
        island.addSoundFilter(e -> {
            if (e.getTarget() instanceof javafx.scene.control.ButtonBase) {
                sound.click();
            }
        });
        island.setVisible(settings.miniIsland());
        if (Boolean.getBoolean("cozyrabbit.autostart")) {
            toggleStart(); // developer helper for screenshots
        }

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                frame(now);
            }
        }.start();

        if (Boolean.getBoolean("cozyrabbit.autohost")) {
            // developer helper for testing rooms without clicking through the dialog
            try {
                room = new com.cozyrabbit.net.RoomHost(roomListener);
            } catch (java.io.IOException e) {
                System.err.println("autohost failed: " + e.getMessage());
            }
        }

        String snapshotDir = System.getProperty("cozyrabbit.snapshot");
        if (snapshotDir != null && Boolean.getBoolean("cozyrabbit.islandshots")) {
            javafx.animation.PauseTransition p1 = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            p1.setOnFinished(e -> {
                toggleStart();
                javafx.animation.PauseTransition p2 = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
                p2.setOnFinished(e2 -> {
                    DebugSnapshots.save(island.debugScene(false), new java.io.File(snapshotDir, "island-compact.png"));
                    island.debugScene(true);
                    javafx.animation.PauseTransition p3 = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(0.8));
                    p3.setOnFinished(e3 -> {
                        DebugSnapshots.save(island.debugScene(true), new java.io.File(snapshotDir, "island-open.png"));
                        stopTimer();
                        Platform.exit();
                    });
                    p3.play();
                });
                p2.play();
            });
            p1.play();
        } else if (snapshotDir != null) {
            DebugSnapshots.run(snapshotDir, stage, this::openReports, () -> reportsWindow, () -> {
                buckets.stream().max(java.util.Comparator.comparingInt(b -> b.fish().size()))
                        .ifPresent(b -> showFish(b.subject()));
            });
        }
    }

    // ================================================================== scene area

    private Pane buildSceneArea() {
        canvas = new Canvas(1000, 800);
        frameImage = new WritableImage(SceneRenderer.W, SceneRenderer.H);
        StackPane holder = new StackPane(canvas);
        holder.getStyleClass().add("scene-holder");
        holder.setMinSize(0, 0);
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());

        canvas.setOnMouseMoved(e -> {
            hoverIndex = bucketUnderMouse(e.getX(), e.getY());
            canvas.setCursor(hoverIndex >= 0 ? Cursor.HAND : Cursor.DEFAULT);
        });
        canvas.setOnMouseExited(e -> hoverIndex = -1);
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            int i = bucketUnderMouse(e.getX(), e.getY());
            if (i >= 0 && i < buckets.size()) {
                Subject s = buckets.get(i).subject();
                if (bubble.subjectId() == s.id()) {
                    bubble.hide();
                } else {
                    showFish(s);
                }
            } else {
                bubble.hide();
            }
        });

        canvas.setOnContextMenuRequested(e -> {
            int i = bucketUnderMouse(e.getX(), e.getY());
            if (i < 0 || i >= buckets.size()) {
                return;
            }
            Subject s = buckets.get(i).subject();
            javafx.scene.control.MenuItem study = new javafx.scene.control.MenuItem("Study this");
            study.setOnAction(ev -> selectBucket(s));
            javafx.scene.control.MenuItem see = new javafx.scene.control.MenuItem("See fish");
            see.setOnAction(ev -> showFish(s));
            javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Edit bucket…");
            edit.setOnAction(ev -> editBucket(s));
            javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Delete subject…");
            delete.getStyleClass().add("danger-item");
            delete.setOnAction(ev -> deleteSubject(s));
            new javafx.scene.control.ContextMenu(study, see, edit,
                    new javafx.scene.control.SeparatorMenuItem(), delete).show(canvas, e.getScreenX(), e.getScreenY());
        });

        bubble = new BucketBubble(new BucketBubble.Actions() {
            @Override
            public void studyThis(Subject s) {
                selectBucket(s);
            }

            @Override
            public void editBucket(Subject s) {
                Platform.runLater(() -> CozyRabbitApp.this.editBucket(s));
            }
        });
        bubble.setMinSize(0, 0);
        holder.getChildren().add(bubble);
        return holder;
    }

    /**
     * The scene fills the width of its area and is anchored to the bottom (the pier).
     * A taller area shows more sky; a wider one trims the top of the sky.
     * @return {scale, offsetX, offsetY}
     */
    private double[] viewport() {
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        // Show at least the lower 205 rows (pier, lake, hills); trim the sides if needed,
        // keeping the pier and the bobber in view.
        double scale = Math.max(cw / SceneRenderer.W, ch / 205.0);
        double ox = cw / 2 - 172 * scale;
        ox = Math.max(cw - SceneRenderer.W * scale, Math.min(0, ox));
        double oy = Math.floor(ch - SceneRenderer.H * scale);
        return new double[]{scale, Math.floor(ox), oy};
    }

    private int bucketUnderMouse(double x, double y) {
        double[] v = viewport();
        return renderer.bucketAt((x - v[1]) / v[0], (y - v[2]) / v[0]);
    }

    // ================================================================== frame loop

    private void frame(long nanos) {
        double dt = lastFrameNanos == 0 ? 0.016 : (nanos - lastFrameNanos) / 1e9;
        lastFrameNanos = nanos;
        LocalDateTime now = LocalDateTime.now();

        timer.tick(now);
        if (!today.day().equals(now.toLocalDate())) {
            today.reload(now.toLocalDate());
            refreshStats();
        }

        StudySession live = timer.liveSession(now);
        buckets = today.buckets(live);
        int active = activeIndex();
        if (active < 0 && !buckets.isEmpty() && timer.state() == StudyTimer.State.IDLE) {
            timer.setSubject(buckets.get(0).subject(), now);
            active = 0;
        }

        RabbitMode rabbit;
        if (timer.state() == StudyTimer.State.IDLE) {
            rabbit = RabbitMode.DOZING;
        } else if (timer.isOnBreak() || timer.state() == StudyTimer.State.PAUSED) {
            // Breaks and pauses: the rod is put away and the rabbit has a coffee.
            rabbit = RabbitMode.COFFEE;
        } else {
            rabbit = RabbitMode.FISHING;
        }
        long toNext = active >= 0 ? buckets.get(active).secondsToNextCatch() : 600;
        long toWindowEnd = active >= 0 ? buckets.get(active).secondsToWindowEnd() : 600;
        SceneRenderer.Frame f = new SceneRenderer.Frame(hourOfDay(now), settings.weather(), rabbit, buckets,
                active, hoverIndex, timer.isRecording() ? toNext : 999);

        renderer.update(dt, f);
        int[] px = renderer.render(f);
        frameImage.getPixelWriter().setPixels(0, 0, SceneRenderer.W, SceneRenderer.H,
                PixelFormat.getIntArgbInstance(), px, 0, SceneRenderer.W);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double[] view = viewport();
        int top = renderer.topColor();
        gc.setFill(Color.rgb((top >> 16) & 0xFF, (top >> 8) & 0xFF, top & 0xFF));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setImageSmoothing(false);
        gc.drawImage(frameImage, view[1], view[2], SceneRenderer.W * view[0], SceneRenderer.H * view[0]);
        renderer.drawOverlay(gc, view[0], view[1], view[2], f, timer.state() == StudyTimer.State.PAUSED);

        statsTimer -= dt;
        if (statsTimer <= 0) {
            statsTimer = 30;
            refreshStats();
        }
        JournalPanel.Snapshot snap = snapshot(now, active, toWindowEnd);
        journal.update(snap);
        islandTimer -= dt;
        if (islandTimer <= 0) {
            islandTimer = 0.25;
            updateIsland(snap, rabbit);
        }
        updateBubble();
        updateRoom(dt, rabbit);
    }

    private JournalPanel.Snapshot snapshot(LocalDateTime now, int active, long toWindowEnd) {
        StudyTimer.State st = timer.state();
        String subject = timer.subject() == null ? "no bucket yet" : timer.subject().name();
        boolean pomodoro = timer.mode() == TimerMode.POMODORO;
        String phase;
        String time;
        if (st == StudyTimer.State.IDLE) {
            phase = buckets.isEmpty() ? "ADD A BUCKET TO BEGIN" : "READY · " + subject.toUpperCase();
            time = pomodoro ? clock(settings.focusMinutes() * 60_000L) : "00:00:00";
        } else if (pomodoro) {
            PomodoroPhase p = timer.phase();
            String head = p == PomodoroPhase.FOCUS
                    ? "FOCUS " + timer.pomodoroRound() + "/" + settings.longBreakEvery() + " · " + subject.toUpperCase()
                    : p.label().toUpperCase() + " · COFFEE TIME";
            phase = (st == StudyTimer.State.PAUSED ? "PAUSED · " : "") + head;
            time = clock(timer.phaseRemainingMs());
        } else {
            phase = (st == StudyTimer.State.PAUSED ? "PAUSED · " : "UNLIMITED · ") + subject.toUpperCase();
            time = clockLong(timer.runFocusMs());
        }

        double fishProgress = 0;
        String fishText = "";
        if (active >= 0) {
            if (timer.isOnBreak()) {
                fishProgress = timer.phaseProgress();
                fishText = "break ends in " + clock(timer.phaseRemainingMs());
            } else {
                int interval = settings.catchMinutes() * 60;
                fishProgress = Math.max(0, 1 - toWindowEnd / (double) interval);
                fishText = "next fish within " + clock(toWindowEnd * 1000);
            }
        }

        long total = 0;
        for (BucketView b : buckets) {
            total += b.seconds();
        }
        String startText = st == StudyTimer.State.RUNNING ? "Pause" : (st == StudyTimer.State.PAUSED ? "Resume" : "Start");
        return new JournalPanel.Snapshot(now.format(DATE), streak, phase, time, fishProgress, fishText, startText,
                timer.subject() == null || buckets.isEmpty(), st == StudyTimer.State.IDLE, pomodoro,
                st != StudyTimer.State.IDLE, buckets, timer.subject() == null ? -1 : timer.subject().id(), total,
                settings.dailyGoalMinutes() * 60L);
    }

    private double hourOfDay(LocalDateTime now) {
        int forced = settings.forcedHour();
        if (forced >= 0) {
            return forced + 0.5;
        }
        return now.getHour() + now.getMinute() / 60.0 + now.getSecond() / 3600.0;
    }

    private int activeIndex() {
        Subject s = timer.subject();
        if (s == null) {
            return -1;
        }
        for (int i = 0; i < buckets.size(); i++) {
            if (buckets.get(i).subject().id() == s.id()) {
                return i;
            }
        }
        return -1;
    }

    private static String clock(long ms) {
        long s = (ms + 999) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String clockLong(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void refreshStats() {
        StudySession live = timer.liveSession(LocalDateTime.now());
        streak = reports.streak(LocalDate.now(), live);
        weekSeconds = reports.week(LocalDate.now(), live).totalSeconds();
    }

    // ================================================================== mini island

    private void openApp() {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    private void updateIsland(JournalPanel.Snapshot s, RabbitMode rabbit) {
        island.setVisible(settings.miniIsland());
        if (!island.isShowing()) {
            return;
        }
        StudyTimer.State st = timer.state();
        String phase;
        if (st == StudyTimer.State.IDLE) {
            phase = "Ready";
        } else if (st == StudyTimer.State.PAUSED) {
            phase = "Paused";
        } else if (timer.isOnBreak()) {
            phase = "Coffee break";
        } else {
            phase = "Studying";
        }
        Subject subj = timer.subject();
        int fish = 0;
        for (BucketView b : buckets) {
            fish += b.fish().size();
        }
        island.update(new com.cozyrabbit.ui.MiniIsland.State(phase, s.time(),
                subj == null ? "Add a bucket to begin" : subj.name(), subj == null ? "" : subj.colorHex(),
                s.fishText(), s.fishProgress(), fish, st == StudyTimer.State.IDLE, st == StudyTimer.State.RUNNING,
                !s.startDisabled()));
    }

    // ================================================================== study room

    private final com.cozyrabbit.net.Room.Listener roomListener = new com.cozyrabbit.net.Room.Listener() {
        @Override
        public void onCatch(String memberId, String memberName, String fishType) {
            Platform.runLater(() -> {
                com.cozyrabbit.model.FishType type;
                try {
                    type = com.cozyrabbit.model.FishType.valueOf(fishType);
                } catch (IllegalArgumentException e) {
                    type = com.cozyrabbit.model.FishType.BLUE;
                }
                renderer.toast(memberName + " caught a " + type.label().toLowerCase() + "!", type.rare());
                renderer.friendCatch(memberId, type);
                sound.pop();
            });
        }

        @Override
        public void onStatus(String message) {
            Platform.runLater(() -> {
                roomStatus = message.startsWith("Back in") ? "" : message;
                if (room != null && room.closed()) {
                    String text = message;
                    room = null;
                    roomStatus = "";
                    info("You left the study room", text);
                }
            });
        }
    };

    private void updateRoom(double dt, RabbitMode rabbit) {
        if (room == null) {
            journal.updateRoom(null);
            renderer.setFriends(List.of());
            return;
        }
        List<SceneRenderer.FriendView> friends = new java.util.ArrayList<>();
        for (com.cozyrabbit.net.MemberState m : room.members()) {
            if (m.id().equals(settings.playerId())) {
                continue;
            }
            RabbitMode mode = switch (m.status()) {
                case FISHING -> RabbitMode.FISHING;
                case COFFEE -> RabbitMode.COFFEE;
                case DOZING -> RabbitMode.DOZING;
            };
            friends.add(new SceneRenderer.FriendView(m.id(), m.name(),
                    com.cozyrabbit.render.Colors.parseHex(m.hatColor(), com.cozyrabbit.render.Colors.rgb(0x7D8A55)), mode));
        }
        renderer.setFriends(friends);
        publishTimer -= dt;
        if (publishTimer <= 0) {
            publishTimer = 1;
            room.publish(myState(rabbit));
        }
        journal.updateRoom(new JournalPanel.RoomView(room.code(), room.address(), room.isHost(), room.connected(),
                roomStatus, room.members(), settings.playerId()));
    }

    private com.cozyrabbit.net.MemberState myState(RabbitMode rabbit) {
        long total = 0;
        int fish = 0;
        int rare = 0;
        for (BucketView b : buckets) {
            total += b.seconds();
            fish += b.fish().size();
            for (com.cozyrabbit.model.FishType f : b.fish()) {
                if (f.rare()) {
                    rare++;
                }
            }
        }
        com.cozyrabbit.net.MemberState.Status status = switch (rabbit) {
            case FISHING -> com.cozyrabbit.net.MemberState.Status.FISHING;
            case COFFEE -> com.cozyrabbit.net.MemberState.Status.COFFEE;
            default -> com.cozyrabbit.net.MemberState.Status.DOZING;
        };
        Subject s = timer.subject();
        boolean showSubject = s != null && timer.state() != StudyTimer.State.IDLE;
        return new com.cozyrabbit.net.MemberState(settings.playerId(), settings.playerName(), settings.hatColor(),
                status, showSubject ? s.name() : "", showSubject ? s.colorHex() : "", total, fish, rare,
                Math.max(weekSeconds, total), streak);
    }

    @Override
    public void hostRoom() {
        if (room != null || !RoomDialogs.showHost(stage, settings)) {
            return;
        }
        try {
            room = new com.cozyrabbit.net.RoomHost(roomListener);
            roomStatus = "";
            publishTimer = 0;
            refreshStats();
        } catch (java.io.IOException e) {
            info("Couldn't open a room", "Your computer didn't allow a study room to open: " + e.getMessage());
        }
    }

    @Override
    public void joinRoom() {
        if (room != null) {
            return;
        }
        Optional<String> code = RoomDialogs.showJoin(stage, settings);
        if (code.isEmpty()) {
            return;
        }
        String typed = code.get().trim();
        if (typed.startsWith("127.") || typed.equalsIgnoreCase("localhost")) {
            info("That address is this computer", typed + " always means the computer you are typing on. "
                    + "Type the address or the 4-digit code shown in the host's Study room section.");
            return;
        }
        roomStatus = "Looking for room " + code.get() + "…";
        journal.updateRoom(new JournalPanel.RoomView(code.get(), "", false, false, roomStatus, List.of(),
                settings.playerId()));
        Thread t = new Thread(() -> {
            try {
                com.cozyrabbit.net.RoomClient c = com.cozyrabbit.net.RoomClient.join(code.get(), roomListener);
                Platform.runLater(() -> {
                    room = c;
                    roomStatus = "";
                    publishTimer = 0;
                    refreshStats();
                });
            } catch (java.io.IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    roomStatus = "";
                    info("Couldn't join the room", e.getMessage() == null ? "Something went wrong." : e.getMessage());
                });
            }
        }, "room-join");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void leaveRoom() {
        if (room != null) {
            com.cozyrabbit.net.Room r = room;
            room = null;
            roomStatus = "";
            r.close();
        }
    }

    // ================================================================== journal actions

    @Override
    public void toggleStart() {
        LocalDateTime now = LocalDateTime.now();
        switch (timer.state()) {
            case RUNNING -> timer.pause(now);
            case PAUSED, IDLE -> {
                Subject s = timer.subject();
                if (s != null && !today.hasBucket(s.id())) {
                    today.addBucket(s);
                }
                timer.start(now);
            }
        }
    }

    @Override
    public void stopTimer() {
        timer.stop(LocalDateTime.now());
    }

    @Override
    public void skip() {
        timer.skip(LocalDateTime.now());
    }

    @Override
    public void setMode(TimerMode mode) {
        timer.setMode(mode);
    }

    @Override
    public void selectBucket(Subject s) {
        timer.setSubject(s, LocalDateTime.now());
    }

    @Override
    public void addBucket() {
        if (today.isFull()) {
            info("The pier is full", "There is room for " + TodayState.MAX_BUCKETS
                    + " buckets a day. Study one of today's subjects, or remove an empty bucket.");
            return;
        }
        Optional<Subject> s = BucketDialogs.showAdd(stage, today, db);
        s.ifPresent(subject -> {
            today.addBucket(subject);
            timer.setSubject(subject, LocalDateTime.now());
        });
    }

    /** Opens the speech bubble over the bucket of this subject. */
    @Override
    public void showFish(Subject s) {
        for (int i = 0; i < buckets.size(); i++) {
            if (buckets.get(i).subject().id() == s.id()) {
                double[] anchor = bucketAnchor(i);
                if (anchor == null) {
                    return;
                }
                bubble.show(buckets.get(i), isActive(s), anchor[0], anchor[1]);
                sound.pop();
                return;
            }
        }
    }

    private boolean isActive(Subject s) {
        return timer.subject() != null && timer.subject().id() == s.id();
    }

    /** Top centre of a bucket in window coordinates of the scene area. */
    private double[] bucketAnchor(int i) {
        int[] r = renderer.bucketRect(i);
        if (r == null || r[2] == 0) {
            return null;
        }
        double[] v = viewport();
        return new double[]{v[1] + (r[0] + r[2] / 2.0) * v[0], v[2] + r[1] * v[0]};
    }

    private void updateBubble() {
        long id = bubble.subjectId();
        if (id < 0) {
            return;
        }
        for (int i = 0; i < buckets.size(); i++) {
            BucketView b = buckets.get(i);
            if (b.subject().id() == id) {
                bubble.refresh(b, isActive(b.subject()));
                double[] anchor = bucketAnchor(i);
                if (anchor != null) {
                    bubble.place(anchor[0], anchor[1]);
                }
                return;
            }
        }
        bubble.hide();
    }

    @Override
    public void editBucket(Subject s) {
        boolean canRemove = !today.hasSessions(s.id()) && !(timer.subject() != null
                && timer.subject().id() == s.id() && timer.state() != StudyTimer.State.IDLE);
        BucketDialogs.showEdit(stage, s, canRemove).ifPresent(result -> {
            if (result instanceof BucketDialogs.Saved saved) {
                db.updateSubject(saved.subject());
                timer.refreshSubject(saved.subject());
                today.reload();
            } else if (result instanceof BucketDialogs.Removed) {
                today.removeBucket(s.id());
                List<BucketView> left = today.buckets(null);
                if (timer.state() == StudyTimer.State.IDLE && !left.isEmpty()) {
                    timer.setSubject(left.get(0).subject(), LocalDateTime.now());
                }
            }
            if (reportsWindow != null) {
                reportsWindow.refreshIfShowing();
            }
        });
    }

    @Override
    public void deleteSubject(Subject s) {
        deleteSubject(s, stage);
    }

    /**
     * Deletes a subject for good: its bucket, every study session and so every fish, also from the reports.
     * Asks first, because it cannot be undone.
     */
    private void deleteSubject(Subject s, javafx.stage.Window owner) {
        long total = db.totalSecondsFor(s.id());
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(owner);
        a.setTitle("Delete subject");
        a.setHeaderText("Delete \"" + s.name() + "\"?");
        a.setContentText("This removes its bucket, all of its study time (" + ReportService.formatDuration(total)
                + " in total) and every fish caught for it, from today and from your reports. This can't be undone.");
        javafx.scene.control.ButtonType delete = new javafx.scene.control.ButtonType("Delete",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType cancel = new javafx.scene.control.ButtonType("Cancel",
                javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(cancel, delete);
        a.getDialogPane().getStylesheets().add(getClass().getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        a.getDialogPane().lookupButton(delete).getStyleClass().add("danger");
        ((javafx.scene.control.Button) a.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        ((javafx.scene.control.Button) a.getDialogPane().lookupButton(delete)).setDefaultButton(false);
        if (a.showAndWait().orElse(cancel) != delete) {
            return;
        }

        boolean wasSelected = timer.subject() != null && timer.subject().id() == s.id();
        if (wasSelected && timer.state() != StudyTimer.State.IDLE) {
            timer.stop(LocalDateTime.now());
        }
        if (bubble.subjectId() == s.id()) {
            bubble.hide();
        }
        db.deleteSubject(s.id());
        today.reload();
        if (wasSelected) {
            timer.clearSubject();
            List<BucketView> left = today.buckets(null);
            if (!left.isEmpty()) {
                timer.setSubject(left.get(0).subject(), LocalDateTime.now());
            }
        }
        refreshStats();
        if (reportsWindow != null) {
            reportsWindow.refreshIfShowing();
        }
    }

    @Override
    public void openReports() {
        if (reportsWindow == null) {
            reportsWindow = new ReportsWindow(stage, db, reports, settings,
                    () -> timer.liveSession(LocalDateTime.now()), this::deleteSubject);
        }
        reportsWindow.show();
    }

    @Override
    public void openSettings() {
        if (SettingsDialog.show(stage, settings)) {
            sound.setEffectsEnabled(settings.soundEffects());
        }
    }

    @Override
    public void setWeather(Weather w) {
        settings.setWeather(w);
        applyWeather();
    }

    @Override
    public void setRainSound(boolean on) {
        settings.setRainSound(on);
        applyWeather();
    }

    @Override
    public void setRainVolume(int volume) {
        sound.setRainVolume(volume / 100.0);
        settings.setRainVolume(volume);
    }

    private void applyWeather() {
        Weather w = settings.weather();
        journal.setWeather(w, settings.rainSound(), settings.rainVolume());
        sound.setRain(w == Weather.RAINY && settings.rainSound());
    }

    private void info(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(stage);
        a.setHeaderText(header);
        a.setContentText(text);
        a.getDialogPane().getStylesheets().add(getClass().getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        a.showAndWait();
    }

    private void shutdown() {
        if (island != null) {
            island.close();
        }
        leaveRoom();
        timer.stop(LocalDateTime.now());
        sound.shutdown();
        db.close();
    }

    // ================================================================== timer events

    @Override
    public void onSegmentClosed() {
        today.reload();
        if (reportsWindow != null) {
            reportsWindow.refreshIfShowing();
        }
    }

    @Override
    public void onPhaseChanged(PomodoroPhase from, PomodoroPhase to) {
        sound.chime();
    }

    @Override
    public void onDayChanged() {
        today.reload(LocalDate.now());
        if (timer.subject() != null) {
            today.addBucket(timer.subject());
        }
        statsTimer = 0;
    }

    @Override
    public void onAutoPaused() {
        statsTimer = 0;
    }
}
