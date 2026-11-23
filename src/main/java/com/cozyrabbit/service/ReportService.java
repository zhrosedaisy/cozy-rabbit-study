package com.cozyrabbit.service;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.storage.Database;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numbers for the daily and weekly report pages. */
public final class ReportService {

    /** A day counts towards the streak once it has at least one fish (10 minutes). */
    public static final long STREAK_MIN_SECONDS = FishType.SECONDS_PER_CATCH;

    public record DaySummary(LocalDate day, Map<Long, Long> secondsBySubject, long totalSeconds,
                             List<StudySession> sessions, Map<FishType, Integer> fish, int fishCount,
                             Map<Long, Integer> fishBySubject) {
    }

    public record WeekSummary(LocalDate monday, List<DaySummary> days, Map<Long, Long> secondsBySubject,
                              long totalSeconds, int activeDays, int fishCount, Map<FishType, Integer> fish,
                              long[][] heatmap, Map<Long, Integer> fishBySubject) {

        public long averagePerDay() {
            return totalSeconds / 7;
        }

        public long averagePerActiveDay() {
            return activeDays == 0 ? 0 : totalSeconds / activeDays;
        }

        public DaySummary bestDay() {
            DaySummary best = null;
            for (DaySummary d : days) {
                if (d.totalSeconds() > 0 && (best == null || d.totalSeconds() > best.totalSeconds())) {
                    best = d;
                }
            }
            return best;
        }
    }

    private final Database db;
    private final java.util.function.IntSupplier intervalSeconds;

    public ReportService(Database db, java.util.function.IntSupplier intervalSeconds) {
        this.db = db;
        this.intervalSeconds = intervalSeconds;
    }

    public static LocalDate mondayOf(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private List<StudySession> load(LocalDate from, LocalDate toExclusive, StudySession live) {
        List<StudySession> list = new ArrayList<>(db.sessionsBetween(from, toExclusive));
        if (live != null) {
            list.removeIf(s -> s.id() == live.id());
            LocalDate d = live.start().toLocalDate();
            if (!d.isBefore(from) && d.isBefore(toExclusive)) {
                list.add(live);
            }
        }
        return list;
    }

    public DaySummary day(LocalDate day, StudySession live) {
        return summarize(day, load(day, day.plusDays(1), live));
    }

    private DaySummary summarize(LocalDate day, List<StudySession> sessions) {
        Map<Long, Long> bySubject = new LinkedHashMap<>();
        Map<Long, List<StudySession>> grouped = new LinkedHashMap<>();
        long total = 0;
        for (StudySession s : sessions) {
            bySubject.merge(s.subjectId(), s.seconds(), Long::sum);
            grouped.computeIfAbsent(s.subjectId(), k -> new ArrayList<>()).add(s);
            total += s.seconds();
        }
        Map<FishType, Integer> fish = new LinkedHashMap<>();
        Map<Long, Integer> fishBySubject = new LinkedHashMap<>();
        int count = 0;
        int interval = intervalSeconds.getAsInt();
        for (Map.Entry<Long, List<StudySession>> g : grouped.entrySet()) {
            List<FishType> caught = CatchCalculator.compute(g.getValue(), interval,
                    CatchCalculator.seed(g.getKey(), day)).fish();
            fishBySubject.put(g.getKey(), caught.size());
            for (FishType f : caught) {
                fish.merge(f, 1, Integer::sum);
                count++;
            }
        }
        return new DaySummary(day, bySubject, total, sessions, fish, count, fishBySubject);
    }

    public WeekSummary week(LocalDate anyDayOfWeek, StudySession live) {
        LocalDate monday = mondayOf(anyDayOfWeek);
        List<StudySession> all = load(monday, monday.plusDays(7), live);
        Map<LocalDate, List<StudySession>> byDay = new HashMap<>();
        for (StudySession s : all) {
            byDay.computeIfAbsent(s.start().toLocalDate(), k -> new ArrayList<>()).add(s);
        }

        List<DaySummary> days = new ArrayList<>();
        Map<Long, Long> bySubject = new LinkedHashMap<>();
        Map<FishType, Integer> fish = new LinkedHashMap<>();
        Map<Long, Integer> fishBySubject = new LinkedHashMap<>();
        long total = 0;
        int active = 0;
        int fishCount = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            DaySummary ds = summarize(d, byDay.getOrDefault(d, List.of()));
            days.add(ds);
            total += ds.totalSeconds();
            fishCount += ds.fishCount();
            if (ds.totalSeconds() > 0) {
                active++;
            }
            ds.secondsBySubject().forEach((k, v) -> bySubject.merge(k, v, Long::sum));
            ds.fish().forEach((k, v) -> fish.merge(k, v, Integer::sum));
            ds.fishBySubject().forEach((k, v) -> fishBySubject.merge(k, v, Integer::sum));
        }
        return new WeekSummary(monday, days, bySubject, total, active, fishCount, fish, heatmap(monday, all),
                fishBySubject);
    }

    /** Seconds studied per [day of week][hour of day]. */
    private static long[][] heatmap(LocalDate monday, List<StudySession> sessions) {
        long[][] grid = new long[7][24];
        for (StudySession s : sessions) {
            LocalDateTime cursor = s.start();
            LocalDateTime end = s.end();
            while (cursor.isBefore(end)) {
                LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                LocalDateTime sliceEnd = end.isBefore(nextHour) ? end : nextHour;
                int day = (int) ChronoUnit.DAYS.between(monday, cursor.toLocalDate());
                if (day >= 0 && day < 7) {
                    grid[day][cursor.getHour()] += ChronoUnit.SECONDS.between(cursor, sliceEnd);
                }
                cursor = sliceEnd;
            }
        }
        return grid;
    }

    /** Days in a row (ending today, or yesterday if today has nothing yet) with at least one fish. */
    public int streak(LocalDate today, StudySession live) {
        LocalDate from = today.minusDays(400);
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (StudySession s : load(from, today.plusDays(1), live)) {
            perDay.merge(s.start().toLocalDate(), s.seconds(), Long::sum);
        }
        LocalDate cursor = today;
        if (perDay.getOrDefault(cursor, 0L) < STREAK_MIN_SECONDS) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (perDay.getOrDefault(cursor, 0L) >= STREAK_MIN_SECONDS) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h == 0) {
            return m + "m";
        }
        return h + "h " + (m < 10 ? "0" : "") + m + "m";
    }
}
