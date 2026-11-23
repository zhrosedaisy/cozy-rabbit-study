package com.cozyrabbit.service;

import com.cozyrabbit.model.PomodoroPhase;
import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.model.TimerMode;
import com.cozyrabbit.storage.AppSettings;
import com.cozyrabbit.storage.Database;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The study clock. Focused time is written to the database as "segments" (sessions).
 * A segment ends on pause, stop, subject switch, pomodoro phase change and at midnight.
 * While a segment is open its end time is saved every 15 seconds, so a crash loses almost nothing.
 */
public final class StudyTimer {

    public enum State { IDLE, RUNNING, PAUSED }

    public interface Listener {
        void onSegmentClosed();

        void onPhaseChanged(PomodoroPhase from, PomodoroPhase to);

        void onDayChanged();

        void onAutoPaused();
    }

    private static final long MAX_TICK_GAP_MS = 120_000;
    private static final long HEARTBEAT_SECONDS = 15;
    private static final long MIN_SESSION_SECONDS = 20;

    private final Database db;
    private final AppSettings settings;
    private final Listener listener;

    private TimerMode mode;
    private State state = State.IDLE;
    private PomodoroPhase phase = PomodoroPhase.FOCUS;
    private int completedFocus;
    private long phaseElapsedMs;
    private long runFocusMs;

    private Subject subject;
    private long segmentId = -1;
    private LocalDateTime segmentStart;
    private LocalDateTime lastTick;
    private LocalDateTime lastHeartbeat;

    public StudyTimer(Database db, AppSettings settings, Listener listener) {
        this.db = db;
        this.settings = settings;
        this.listener = listener;
        this.mode = settings.timerMode();
    }

    // ------------------------------------------------------------------ queries

    public State state() {
        return state;
    }

    public TimerMode mode() {
        return mode;
    }

    public PomodoroPhase phase() {
        return phase;
    }

    public Subject subject() {
        return subject;
    }

    public boolean isRecording() {
        return state == State.RUNNING && (mode == TimerMode.UNLIMITED || phase == PomodoroPhase.FOCUS);
    }

    public boolean isOnBreak() {
        return state != State.IDLE && mode == TimerMode.POMODORO && phase.isBreak();
    }

    /** Focused milliseconds since the last Start (across pauses). */
    public long runFocusMs() {
        return runFocusMs;
    }

    public long phaseLengthMs() {
        int minutes = switch (phase) {
            case FOCUS -> settings.focusMinutes();
            case SHORT_BREAK -> settings.shortBreakMinutes();
            case LONG_BREAK -> settings.longBreakMinutes();
        };
        return minutes * 60_000L;
    }

    public long phaseRemainingMs() {
        return Math.max(0, phaseLengthMs() - phaseElapsedMs);
    }

    public double phaseProgress() {
        long len = phaseLengthMs();
        return len <= 0 ? 0 : Math.min(1.0, phaseElapsedMs / (double) len);
    }

    /** 1-based number of the current pomodoro inside its cycle. */
    public int pomodoroRound() {
        int every = Math.max(1, settings.longBreakEvery());
        if (phase.isBreak()) {
            int r = completedFocus % every;
            return r == 0 ? every : r;
        }
        return completedFocus % every + 1;
    }

    /** The open segment as a session ending "now", or null when nothing is being recorded. */
    public StudySession liveSession(LocalDateTime now) {
        if (segmentId < 0 || subject == null) {
            return null;
        }
        return new StudySession(segmentId, subject.id(), segmentStart, now, mode);
    }

    // ------------------------------------------------------------------ commands

    public void setMode(TimerMode newMode) {
        if (state != State.IDLE) {
            return;
        }
        mode = newMode;
        settings.setTimerMode(newMode);
        phase = PomodoroPhase.FOCUS;
        phaseElapsedMs = 0;
        completedFocus = 0;
    }

    public void setSubject(Subject s, LocalDateTime now) {
        if (s == null) {
            return;
        }
        boolean wasRecording = segmentId >= 0;
        if (wasRecording) {
            closeSegment(now);
        }
        subject = s;
        if (wasRecording) {
            openSegment(now);
        }
    }

    /** Forget the selected subject (it was deleted). Only while the timer is idle. */
    public void clearSubject() {
        if (state == State.IDLE) {
            subject = null;
        }
    }

    /** The subject was renamed or recolored. */
    public void refreshSubject(Subject s) {
        if (subject != null && subject.id() == s.id()) {
            subject = s;
        }
    }

    public void start(LocalDateTime now) {
        if (subject == null) {
            return;
        }
        if (state == State.PAUSED) {
            resume(now);
            return;
        }
        if (state == State.RUNNING) {
            return;
        }
        state = State.RUNNING;
        phase = PomodoroPhase.FOCUS;
        phaseElapsedMs = 0;
        completedFocus = 0;
        runFocusMs = 0;
        lastTick = now;
        openSegment(now);
    }

    public void pause(LocalDateTime now) {
        if (state != State.RUNNING) {
            return;
        }
        tick(now);
        if (segmentId >= 0) {
            closeSegment(now);
        }
        state = State.PAUSED;
    }

    public void resume(LocalDateTime now) {
        if (state != State.PAUSED) {
            return;
        }
        state = State.RUNNING;
        lastTick = now;
        if (isRecording()) {
            openSegment(now);
        }
    }

    public void stop(LocalDateTime now) {
        if (state == State.IDLE) {
            return;
        }
        if (state == State.RUNNING) {
            tick(now);
        }
        if (segmentId >= 0) {
            closeSegment(now);
        }
        state = State.IDLE;
        phase = PomodoroPhase.FOCUS;
        phaseElapsedMs = 0;
        completedFocus = 0;
        runFocusMs = 0;
    }

    /** Pomodoro only: jump to the next phase. */
    public void skip(LocalDateTime now) {
        if (mode != TimerMode.POMODORO || state == State.IDLE) {
            return;
        }
        advancePhase(now);
    }

    public void tick(LocalDateTime now) {
        if (state != State.RUNNING) {
            lastTick = now;
            return;
        }
        long deltaMs = Math.max(0, Duration.between(lastTick, now).toMillis());

        if (deltaMs > MAX_TICK_GAP_MS) {
            // The computer slept or the app froze: don't count that gap as study time.
            if (segmentId >= 0) {
                closeSegment(lastTick);
            }
            state = State.PAUSED;
            lastTick = now;
            listener.onAutoPaused();
            return;
        }

        if (segmentId >= 0 && !now.toLocalDate().equals(segmentStart.toLocalDate())) {
            LocalDateTime midnight = now.toLocalDate().atStartOfDay();
            closeSegment(midnight);
            listener.onDayChanged();
            openSegment(midnight);
        }

        if (isRecording()) {
            runFocusMs += deltaMs;
        }

        if (mode == TimerMode.POMODORO) {
            phaseElapsedMs += deltaMs;
            if (phaseElapsedMs >= phaseLengthMs()) {
                advancePhase(now);
            }
        }

        if (segmentId >= 0 && Duration.between(lastHeartbeat, now).getSeconds() >= HEARTBEAT_SECONDS) {
            db.updateSessionEnd(segmentId, now);
            lastHeartbeat = now;
        }
        lastTick = now;
    }

    // ------------------------------------------------------------------ internals

    private void advancePhase(LocalDateTime now) {
        PomodoroPhase from = phase;
        if (phase == PomodoroPhase.FOCUS) {
            if (segmentId >= 0) {
                closeSegment(now);
            }
            completedFocus++;
            int every = Math.max(1, settings.longBreakEvery());
            phase = completedFocus % every == 0 ? PomodoroPhase.LONG_BREAK : PomodoroPhase.SHORT_BREAK;
        } else {
            phase = PomodoroPhase.FOCUS;
            if (state == State.RUNNING) {
                openSegment(now);
            }
        }
        phaseElapsedMs = 0;
        listener.onPhaseChanged(from, phase);
    }

    private void openSegment(LocalDateTime now) {
        segmentStart = now;
        lastHeartbeat = now;
        segmentId = db.insertSession(subject.id(), now, mode);
    }

    private void closeSegment(LocalDateTime end) {
        long id = segmentId;
        segmentId = -1;
        if (Duration.between(segmentStart, end).getSeconds() < MIN_SESSION_SECONDS) {
            db.deleteSession(id);
        } else {
            db.updateSessionEnd(id, end);
        }
        listener.onSegmentClosed();
    }
}
