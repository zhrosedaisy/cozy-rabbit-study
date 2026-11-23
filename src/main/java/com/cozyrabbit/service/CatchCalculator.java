package com.cozyrabbit.service;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.StudySession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Turns study time into fish.
 *
 * <p>A subject's study time of the day is split into windows of the chosen length (10 minutes or more).
 * Exactly one fish bites in each window, at a random second inside it. The random seconds are derived
 * from the day and the subject, so the bucket always shows the same fish for the same study time.
 * Leftover minutes carry over between sessions (and pomodoros) of the same day.
 * A session that runs 60 uninterrupted minutes earns a golden fish, 120 minutes a koi.
 */
public final class CatchCalculator {

    public static final int MIN_INTERVAL_SECONDS = 600;

    private CatchCalculator() {
    }

    /** One fish and the moment it was caught. */
    public record Catch(FishType type, LocalDateTime at) {
    }

    /**
     * @param secondsToNextCatch the hidden exact time until the next bite (used for the bobber animation)
     * @param secondsToWindowEnd the latest the next fish can come, shown to the user
     */
    public record Result(List<FishType> fish, List<Catch> catches, long totalSeconds,
                         long secondsToNextCatch, long secondsToWindowEnd, int intervalSeconds) {
    }

    public static long seed(long subjectId, LocalDate day) {
        return subjectId * 1_000_003L + day.toEpochDay() * 7_919L;
    }

    /** Second inside window {@code k} at which the fish bites. */
    private static long biteOffset(long seed, int k, int interval) {
        SplittableRandom r = new SplittableRandom(seed * 31 + k);
        int margin = Math.min(20, interval / 10);
        return margin + r.nextInt(interval - margin);
    }

    public static Result compute(List<StudySession> sessionsOfOneSubjectOneDay, int intervalSeconds, long seed) {
        int interval = Math.max(MIN_INTERVAL_SECONDS, intervalSeconds);
        List<StudySession> sorted = new ArrayList<>(sessionsOfOneSubjectOneDay);
        sorted.sort(Comparator.comparing(StudySession::start));

        List<FishType> fish = new ArrayList<>();
        List<Catch> catches = new ArrayList<>();
        long cumulative = 0;
        int window = 0;
        long target = biteOffset(seed, 0, interval);
        int commonIndex = 0;

        for (StudySession s : sorted) {
            long duration = s.seconds();
            long local = 0;
            boolean goldenGiven = false;
            boolean koiGiven = false;
            while (cumulative + (duration - local) >= target) {
                local += target - cumulative;
                cumulative = target;
                FishType type;
                if (!koiGiven && local >= FishType.KOI_AFTER_SECONDS) {
                    type = FishType.KOI;
                    koiGiven = true;
                } else if (!goldenGiven && local >= FishType.GOLDEN_AFTER_SECONDS) {
                    type = FishType.GOLDEN;
                    goldenGiven = true;
                } else {
                    type = FishType.common(commonIndex++);
                }
                fish.add(type);
                catches.add(new Catch(type, s.start().plusSeconds(local)));
                window++;
                target = (long) window * interval + biteOffset(seed, window, interval);
            }
            cumulative += duration - local;
        }
        long windowEnd = (long) (window + 1) * interval;
        return new Result(fish, catches, cumulative, target - cumulative, windowEnd - cumulative, interval);
    }

    /** Convenience for the classic fixed 10-minute rhythm (used by tests). */
    public static Result compute(List<StudySession> sessions) {
        return compute(sessions, MIN_INTERVAL_SECONDS, 0);
    }
}
