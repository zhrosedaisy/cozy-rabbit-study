package com.cozyrabbit.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** One continuous stretch of focused study on a single subject. */
public record StudySession(long id, long subjectId, LocalDateTime start, LocalDateTime end, TimerMode mode) {

    public long seconds() {
        return Math.max(0, Duration.between(start, end).getSeconds());
    }
}
