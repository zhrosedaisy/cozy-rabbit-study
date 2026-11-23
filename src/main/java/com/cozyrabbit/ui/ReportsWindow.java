package com.cozyrabbit.ui;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.service.ReportService;
import com.cozyrabbit.service.ReportService.DaySummary;
import com.cozyrabbit.service.ReportService.WeekSummary;
import com.cozyrabbit.storage.AppSettings;
import com.cozyrabbit.storage.Database;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Daily and weekly study reports. */
public final class ReportsWindow {

    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Stage stage = new Stage();
    private final Database db;
    private final ReportService reports;
    private final AppSettings settings;
    private final Supplier<StudySession> live;
    private final BiConsumer<Subject, javafx.stage.Window> onDelete;

    private LocalDate day = LocalDate.now();
    private LocalDate weekOf = LocalDate.now();
    private final VBox dailyContent = new VBox(16);
    private final VBox weeklyContent = new VBox(16);
    private final TabPane tabs = new TabPane();

    public ReportsWindow(Stage owner, Database db, ReportService reports, AppSettings settings,
                         Supplier<StudySession> live, BiConsumer<Subject, javafx.stage.Window> onDelete) {
        this.onDelete = onDelete;
        this.db = db;
        this.reports = reports;
        this.settings = settings;
        this.live = live;
        stage.initOwner(owner);
        stage.setTitle("Cozy Rabbit Study — Reports");

        dailyContent.setPadding(new Insets(20));
        weeklyContent.setPadding(new Insets(20));
        Tab daily = new Tab("Daily", scroll(dailyContent));
        Tab weekly = new Tab("Weekly", scroll(weeklyContent));
        tabs.getTabs().addAll(daily, weekly);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("report-tabs");

        Scene scene = new Scene(tabs, 980, 760);
        scene.getStylesheets().add(getClass().getResource("/com/cozyrabbit/cozy.css").toExternalForm());
        stage.setScene(scene);
    }

    private static ScrollPane scroll(Node n) {
        ScrollPane sp = new ScrollPane(n);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("report-scroll");
        return sp;
    }

    public void show() {
        day = LocalDate.now();
        weekOf = LocalDate.now();
        refresh();
        stage.show();
        stage.toFront();
    }

    public void refreshIfShowing() {
        if (stage.isShowing()) {
            refresh();
        }
    }

    public void refresh() {
        Map<Long, Subject> subjects = db.subjectsById();
        buildDaily(subjects);
        buildWeekly(subjects);
    }

    // ================================================================== daily

    private void buildDaily(Map<Long, Subject> subjects) {
        DaySummary d = reports.day(day, live.get());
        dailyContent.getChildren().clear();

        dailyContent.getChildren().add(navigator(day.format(DAY_TITLE),
                () -> { day = day.minusDays(1); refresh(); },
                () -> { day = day.plusDays(1); refresh(); },
                () -> { day = LocalDate.now(); refresh(); },
                !day.isBefore(LocalDate.now())));

        long goal = settings.dailyGoalMinutes() * 60L;
        int pct = goal == 0 ? 0 : (int) Math.round(100.0 * d.totalSeconds() / goal);
        FlowPane cards = new FlowPane(12, 12,
                card("Studied", ReportService.formatDuration(d.totalSeconds()), "total focus time"),
                card("Daily goal", pct + "%", "of " + ReportService.formatDuration(goal)),
                card("Fish caught", Integer.toString(d.fishCount()), fishDetail(d.fish())),
                card("Sessions", Integer.toString(d.sessions().size()), longestSession(d.sessions())));
        dailyContent.getChildren().add(cards);

        if (d.totalSeconds() == 0) {
            dailyContent.getChildren().add(empty("No study time on this day yet. The rabbit is waiting by the water!"));
            return;
        }

        dailyContent.getChildren().add(section("Subjects"));
        HBox split = new HBox(20, pie(d.secondsBySubject(), subjects), subjectTable(d.secondsBySubject(),
                d.totalSeconds(), subjects, 1, d.fishBySubject()));
        HBox.setHgrow(split.getChildren().get(1), Priority.ALWAYS);
        dailyContent.getChildren().add(split);

        dailyContent.getChildren().add(section("Timeline"));
        dailyContent.getChildren().add(timeline(d.sessions(), subjects));
        VBox list = new VBox(4);
        List<StudySession> sorted = new ArrayList<>(d.sessions());
        sorted.sort(Comparator.comparing(StudySession::start));
        for (StudySession s : sorted) {
            Subject sub = subjects.get(s.subjectId());
            HBox row = new HBox(10, dot(sub), new Label(s.start().format(HM) + " – " + s.end().format(HM)),
                    bold(sub == null ? "?" : sub.name()), spacer(),
                    new Label(ReportService.formatDuration(s.seconds())), muted(s.mode().label()));
            row.getStyleClass().add("session-row");
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        dailyContent.getChildren().add(list);
    }

    private Node timeline(List<StudySession> sessions, Map<Long, Subject> subjects) {
        Canvas c = new Canvas(900, 58);
        Region holder = canvasHolder(c);
        Runnable draw = () -> {
            GraphicsContext g = c.getGraphicsContext2D();
            double w = c.getWidth();
            g.clearRect(0, 0, w, c.getHeight());
            g.setFill(Color.web("#EADBC2"));
            g.fillRoundRect(0, 8, w, 24, 10, 10);
            for (StudySession s : sessions) {
                Subject sub = subjects.get(s.subjectId());
                double start = s.start().getHour() + s.start().getMinute() / 60.0 + s.start().getSecond() / 3600.0;
                double end = start + s.seconds() / 3600.0;
                double x0 = start / 24 * w;
                double x1 = Math.max(x0 + 2, end / 24 * w);
                g.setFill(Color.web(sub == null ? "#999999" : sub.colorHex()));
                g.fillRoundRect(x0, 8, x1 - x0, 24, 6, 6);
            }
            g.setFill(Color.web("#7A6250"));
            g.setFont(Font.font("Avenir", 11));
            for (int h = 0; h <= 24; h += 3) {
                double x = h / 24.0 * w;
                g.fillRect(Math.min(w - 1, x), 34, 1, 5);
                g.fillText(String.format("%02d", h % 24), Math.min(w - 16, Math.max(0, x - 7)), 52);
            }
        };
        c.widthProperty().addListener((o, a, b) -> draw.run());
        draw.run();
        return holder;
    }

    // ================================================================== weekly

    private void buildWeekly(Map<Long, Subject> subjects) {
        WeekSummary w = reports.week(weekOf, live.get());
        weeklyContent.getChildren().clear();
        LocalDate monday = w.monday();
        String title = monday.format(SHORT) + " – " + monday.plusDays(6).format(SHORT) + " " + monday.plusDays(6).getYear();
        weeklyContent.getChildren().add(navigator(title,
                () -> { weekOf = weekOf.minusWeeks(1); refresh(); },
                () -> { weekOf = weekOf.plusWeeks(1); refresh(); },
                () -> { weekOf = LocalDate.now(); refresh(); },
                !ReportService.mondayOf(weekOf).isBefore(ReportService.mondayOf(LocalDate.now()))));

        DaySummary best = w.bestDay();
        int streak = reports.streak(LocalDate.now(), live.get());
        FlowPane cards = new FlowPane(12, 12,
                card("This week", ReportService.formatDuration(w.totalSeconds()), "total focus time"),
                card("Daily average", ReportService.formatDuration(w.averagePerDay()), "over all 7 days"),
                card("Avg. on study days", ReportService.formatDuration(w.averagePerActiveDay()),
                        w.activeDays() + " of 7 days studied"),
                card("Best day", best == null ? "—" : best.day().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        best == null ? "" : ReportService.formatDuration(best.totalSeconds())),
                card("Fish caught", Integer.toString(w.fishCount()), fishDetail(w.fish())),
                card("Streak", streak + (streak == 1 ? " day" : " days"), "days in a row with 10+ min"));
        weeklyContent.getChildren().add(cards);

        if (w.totalSeconds() == 0) {
            weeklyContent.getChildren().add(empty("Nothing recorded this week yet."));
            return;
        }

        weeklyContent.getChildren().add(section("Hours per day"));
        weeklyContent.getChildren().add(weekChart(w, subjects));

        weeklyContent.getChildren().add(section("Subjects this week"));
        weeklyContent.getChildren().add(subjectTable(w.secondsBySubject(), w.totalSeconds(), subjects, 7, w.fishBySubject()));

        weeklyContent.getChildren().add(section("When you study"));
        weeklyContent.getChildren().add(heatmap(w.heatmap()));
    }

    private Node weekChart(WeekSummary w, Map<Long, Subject> subjects) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("hours");
        y.setForceZeroInRange(true);
        StackedBarChart<String, Number> chart = new StackedBarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(28);
        chart.setPrefHeight(300);
        List<String> dayNames = new ArrayList<>();
        for (DaySummary d : w.days()) {
            dayNames.add(dayLabel(d.day()));
        }
        x.getCategories().setAll(dayNames);

        for (Map.Entry<Long, Long> e : sortedEntries(w.secondsBySubject())) {
            Subject sub = subjects.get(e.getKey());
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(sub == null ? "?" : sub.name());
            for (DaySummary d : w.days()) {
                long secs = d.secondsBySubject().getOrDefault(e.getKey(), 0L);
                series.getData().add(new XYChart.Data<>(dayLabel(d.day()), secs / 3600.0));
            }
            chart.getData().add(series);
            String color = sub == null ? "#999999" : sub.colorHex();
            for (XYChart.Data<String, Number> data : series.getData()) {
                styleNode(data, "-fx-bar-fill: " + color + ";",
                        series.getName() + ": " + ReportService.formatDuration(Math.round(data.getYValue().doubleValue() * 3600)));
            }
        }
        VBox box = new VBox(8, chart, legend(w.secondsBySubject(), subjects));
        box.getStyleClass().add("chart-card");
        return box;
    }

    private static void styleNode(XYChart.Data<String, Number> data, String style, String tip) {
        if (data.getNode() != null) {
            data.getNode().setStyle(style);
            Tooltip.install(data.getNode(), new Tooltip(tip));
        }
        data.nodeProperty().addListener((o, a, n) -> {
            if (n != null) {
                n.setStyle(style);
                Tooltip.install(n, new Tooltip(tip));
            }
        });
    }

    private static String dayLabel(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth();
    }

    private Node heatmap(long[][] grid) {
        Canvas c = new Canvas(900, 7 * 26 + 30);
        Region holder = canvasHolder(c);
        long max = 1;
        for (long[] row : grid) {
            for (long v : row) {
                max = Math.max(max, v);
            }
        }
        final long maxV = max;
        Runnable draw = () -> {
            GraphicsContext g = c.getGraphicsContext2D();
            double w = c.getWidth();
            g.clearRect(0, 0, w, c.getHeight());
            double left = 44;
            double cell = (w - left) / 24;
            g.setFont(Font.font("Avenir", 11));
            for (int d = 0; d < 7; d++) {
                g.setFill(Color.web("#7A6250"));
                g.fillText(DayOfWeek.of(d + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 4, d * 26 + 17);
                for (int h = 0; h < 24; h++) {
                    double t = grid[d][h] / (double) maxV;
                    Color col = grid[d][h] == 0 ? Color.web("#EFE3CF")
                            : Color.web("#F6D7A7").interpolate(Color.web("#C0582E"), Math.sqrt(t));
                    g.setFill(col);
                    g.fillRoundRect(left + h * cell + 1.5, d * 26 + 2, cell - 3, 22, 6, 6);
                }
            }
            g.setFill(Color.web("#7A6250"));
            for (int h = 0; h < 24; h += 3) {
                g.fillText(String.format("%02d", h), left + h * cell + 2, 7 * 26 + 16);
            }
        };
        c.widthProperty().addListener((o, a, b) -> draw.run());
        draw.run();
        return holder;
    }

    // ================================================================== shared pieces

    /**
     * Wraps a canvas so it follows the card's width without feeding its own width back
     * into the layout (which would make the page grow forever).
     */
    private static Region canvasHolder(Canvas c) {
        javafx.scene.layout.Pane holder = new javafx.scene.layout.Pane(c);
        holder.getStyleClass().add("chart-card");
        c.setManaged(false);
        c.relocate(12, 12);
        holder.setMinWidth(0);
        holder.setPrefWidth(10);
        holder.setMinHeight(c.getHeight() + 24);
        holder.setPrefHeight(c.getHeight() + 24);
        c.widthProperty().bind(holder.widthProperty().subtract(24));
        return holder;
    }

    private Node pie(Map<Long, Long> bySubject, Map<Long, Subject> subjects) {
        PieChart pie = new PieChart();
        pie.setLegendVisible(false);
        pie.setLabelsVisible(false);
        pie.setAnimated(false);
        pie.setPrefSize(260, 260);
        pie.setMinSize(260, 260);
        for (Map.Entry<Long, Long> e : sortedEntries(bySubject)) {
            Subject sub = subjects.get(e.getKey());
            PieChart.Data data = new PieChart.Data(sub == null ? "?" : sub.name(), e.getValue());
            pie.getData().add(data);
            String color = sub == null ? "#999999" : sub.colorHex();
            String tip = data.getName() + ": " + ReportService.formatDuration(e.getValue());
            if (data.getNode() != null) {
                data.getNode().setStyle("-fx-pie-color: " + color + ";");
                Tooltip.install(data.getNode(), new Tooltip(tip));
            }
            data.nodeProperty().addListener((o, a, n) -> {
                if (n != null) {
                    n.setStyle("-fx-pie-color: " + color + ";");
                    Tooltip.install(n, new Tooltip(tip));
                }
            });
        }
        StackPane card = new StackPane(pie);
        card.getStyleClass().add("chart-card");
        return card;
    }

    private Node subjectTable(Map<Long, Long> bySubject, long total, Map<Long, Subject> subjects, int days,
                              Map<Long, Integer> fishBySubject) {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        g.getStyleClass().add("chart-card");
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        grow.setMinWidth(120);
        g.getColumnConstraints().addAll(new ColumnConstraints(), new ColumnConstraints(), grow);

        int row = 0;
        g.add(muted("Subject"), 0, row, 2, 1);
        g.add(muted("Share"), 2, row);
        g.add(muted("Time"), 3, row);
        if (days > 1) {
            g.add(muted("Avg / day"), 4, row);
        }
        g.add(muted("Fish"), days > 1 ? 5 : 4, row);
        row++;
        for (Map.Entry<Long, Long> e : sortedEntries(bySubject)) {
            Subject sub = subjects.get(e.getKey());
            double share = total == 0 ? 0 : e.getValue() / (double) total;
            Region bar = new Region();
            bar.getStyleClass().add("share-bar");
            bar.setStyle("-fx-background-color: " + (sub == null ? "#999" : sub.colorHex()) + ";");
            bar.setMinHeight(12);
            bar.setMaxHeight(12);
            Region track = new Region();
            track.getStyleClass().add("share-track");
            StackPane barBox = new StackPane(track, bar);
            barBox.setAlignment(Pos.CENTER_LEFT);
            bar.maxWidthProperty().bind(barBox.widthProperty().multiply(share));
            HBox shareCell = new HBox(8, barBox, new Label(Math.round(share * 100) + "%"));
            HBox.setHgrow(barBox, Priority.ALWAYS);
            shareCell.setAlignment(Pos.CENTER_LEFT);

            g.add(dot(sub), 0, row);
            g.add(bold(sub == null ? "?" : sub.name()), 1, row);
            g.add(shareCell, 2, row);
            g.add(new Label(ReportService.formatDuration(e.getValue())), 3, row);
            if (days > 1) {
                g.add(new Label(ReportService.formatDuration(e.getValue() / days)), 4, row);
            }
            g.add(new Label(Integer.toString(fishBySubject.getOrDefault(e.getKey(), 0))), days > 1 ? 5 : 4, row);
            if (sub != null) {
                Button delete = new Button("Delete");
                delete.getStyleClass().add("danger-link");
                delete.setTooltip(new Tooltip("Delete " + sub.name() + " with all its study time and fish"));
                delete.setOnAction(ev -> onDelete.accept(sub, stage));
                g.add(delete, days > 1 ? 6 : 5, row);
            }
            row++;
        }
        return g;
    }

    private Node legend(Map<Long, Long> bySubject, Map<Long, Subject> subjects) {
        FlowPane f = new FlowPane(16, 6);
        f.setPadding(new Insets(0, 0, 0, 50));
        for (Map.Entry<Long, Long> e : sortedEntries(bySubject)) {
            Subject sub = subjects.get(e.getKey());
            HBox item = new HBox(6, dot(sub), new Label(sub == null ? "?" : sub.name()));
            item.setAlignment(Pos.CENTER_LEFT);
            f.getChildren().add(item);
        }
        return f;
    }

    private static List<Map.Entry<Long, Long>> sortedEntries(Map<Long, Long> m) {
        List<Map.Entry<Long, Long>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list;
    }

    private static String fishDetail(Map<FishType, Integer> fish) {
        int golden = fish.getOrDefault(FishType.GOLDEN, 0);
        int koi = fish.getOrDefault(FishType.KOI, 0);
        if (golden == 0 && koi == 0) {
            return "one per catch interval";
        }
        StringBuilder sb = new StringBuilder();
        if (golden > 0) {
            sb.append(golden).append(" golden");
        }
        if (koi > 0) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(koi).append(" koi");
        }
        return sb.toString();
    }

    private static String longestSession(List<StudySession> sessions) {
        long longest = 0;
        for (StudySession s : sessions) {
            longest = Math.max(longest, s.seconds());
        }
        return sessions.isEmpty() ? "" : "longest " + ReportService.formatDuration(longest);
    }

    private static Node navigator(String title, Runnable prev, Runnable next, Runnable now, boolean atPresent) {
        Button p = new Button("‹");
        p.getStyleClass().add("nav");
        p.setOnAction(e -> prev.run());
        Button n = new Button("›");
        n.getStyleClass().add("nav");
        n.setOnAction(e -> next.run());
        n.setDisable(atPresent);
        Button t = new Button("Today");
        t.setOnAction(e -> now.run());
        t.setDisable(atPresent);
        Label l = new Label(title);
        l.getStyleClass().add("report-title");
        HBox box = new HBox(10, p, l, n, spacer(), t);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node card(String title, String value, String sub) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("card-title");
        Label v = new Label(value);
        v.getStyleClass().add("card-value");
        Label s = new Label(sub);
        s.getStyleClass().add("muted");
        VBox box = new VBox(2, t, v, s);
        box.getStyleClass().add("stat-card");
        box.setPrefWidth(210);
        return box;
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private static Node empty(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-note");
        l.setWrapText(true);
        return l;
    }

    private static Node dot(Subject s) {
        Circle c = new Circle(6, Color.web(s == null ? "#999999" : s.colorHex()));
        c.setStroke(Color.web("#00000033"));
        return c;
    }

    private static Label bold(String s) {
        Label l = new Label(s);
        l.setFont(Font.font("Avenir", FontWeight.BOLD, 13));
        l.getStyleClass().add("strong");
        return l;
    }

    private static Label muted(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("muted");
        return l;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
