package com.cozyrabbit.service;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.storage.Database;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The buckets standing on the pier today and the sessions recorded for them.
 * Every day starts with an empty pier.
 */
public final class TodayState {

    public static final int MAX_BUCKETS = 6;

    /**
     * @param secondsToNextCatch hidden exact time until the next bite
     * @param secondsToWindowEnd the latest the next fish can arrive
     */
    public record BucketView(Subject subject, List<FishType> fish, List<CatchCalculator.Catch> catches,
                             long seconds, long secondsToNextCatch, long secondsToWindowEnd, int intervalSeconds) {
    }

    private final Database db;
    private final java.util.function.IntSupplier intervalSeconds;
    private LocalDate day;
    private Map<Long, Subject> subjects = new HashMap<>();
    private List<Long> bucketIds = new ArrayList<>();
    private final Map<Long, List<StudySession>> sessions = new HashMap<>();

    public TodayState(Database db, java.util.function.IntSupplier intervalSeconds) {
        this.db = db;
        this.intervalSeconds = intervalSeconds;
        reload(LocalDate.now());
    }

    public void reload(LocalDate newDay) {
        this.day = newDay;
        subjects = db.subjectsById();
        sessions.clear();
        for (StudySession s : db.sessionsBetween(day, day.plusDays(1))) {
            sessions.computeIfAbsent(s.subjectId(), k -> new ArrayList<>()).add(s);
            // A subject studied today always has a bucket, even if the row is missing.
            db.addBucket(day, s.subjectId());
        }
        bucketIds = db.bucketsOf(day);
    }

    public void reload() {
        reload(day);
    }

    public LocalDate day() {
        return day;
    }

    public boolean isFull() {
        return bucketIds.size() >= MAX_BUCKETS;
    }

    public boolean hasBucket(long subjectId) {
        return bucketIds.contains(subjectId);
    }

    public void addBucket(Subject s) {
        db.addBucket(day, s.id());
        reload();
    }

    /** Only buckets that are still empty can be taken off the pier. */
    public boolean removeBucket(long subjectId) {
        if (sessions.containsKey(subjectId)) {
            return false;
        }
        db.removeBucket(day, subjectId);
        reload();
        return true;
    }

    public boolean hasSessions(long subjectId) {
        return sessions.containsKey(subjectId);
    }

    public List<Subject> subjectsNotOnPier() {
        List<Subject> out = new ArrayList<>();
        for (Subject s : subjects.values()) {
            if (!bucketIds.contains(s.id())) {
                out.add(s);
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** @param live the session currently being recorded, or null */
    public List<BucketView> buckets(StudySession live) {
        List<BucketView> out = new ArrayList<>();
        for (long id : bucketIds) {
            Subject subject = subjects.get(id);
            if (subject == null) {
                continue;
            }
            List<StudySession> list = new ArrayList<>(sessions.getOrDefault(id, List.of()));
            if (live != null && live.subjectId() == id) {
                list.add(live);
            }
            CatchCalculator.Result r = CatchCalculator.compute(list, intervalSeconds.getAsInt(),
                    CatchCalculator.seed(id, day));
            out.add(new BucketView(subject, r.fish(), r.catches(), r.totalSeconds(), r.secondsToNextCatch(),
                    r.secondsToWindowEnd(), r.intervalSeconds()));
        }
        return out;
    }
}
