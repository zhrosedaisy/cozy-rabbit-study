package com.cozyrabbit.storage;

import com.cozyrabbit.model.StudySession;
import com.cozyrabbit.model.Subject;
import com.cozyrabbit.model.TimerMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite storage. The file lives in ~/.cozyrabbit/cozyrabbit.db so it survives rebuilds.
 */
public final class Database implements AutoCloseable {

    private final Connection conn;

    public Database(Path file) {
        try {
            Files.createDirectories(file.getParent());
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS subjects (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            color TEXT NOT NULL,
                            created_at TEXT NOT NULL)""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            subject_id INTEGER NOT NULL REFERENCES subjects(id),
                            start_ts TEXT NOT NULL,
                            end_ts TEXT NOT NULL,
                            mode TEXT NOT NULL)""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_start ON sessions(start_ts)");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS day_buckets (
                            day TEXT NOT NULL,
                            subject_id INTEGER NOT NULL REFERENCES subjects(id),
                            position INTEGER NOT NULL,
                            PRIMARY KEY (day, subject_id))""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS settings (
                            key TEXT PRIMARY KEY,
                            value TEXT NOT NULL)""");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not open database " + file, e);
        }
    }

    public static Path defaultLocation() {
        Path home = Path.of(System.getProperty("user.home"));
        Path file = home.resolve(".cozyrabbit").resolve("cozyrabbit.db");
        migrateFromCatVersion(home.resolve(".cozycat").resolve("cozycat.db"), file);
        return file;
    }

    /**
     * Early versions starred a cat and kept their data in ~/.cozycat. Copy it over once so no study
     * history is lost; the old folder stays untouched as a backup.
     */
    private static void migrateFromCatVersion(Path oldFile, Path newFile) {
        if (Files.exists(newFile) || !Files.exists(oldFile)) {
            return;
        }
        try {
            Files.createDirectories(newFile.getParent());
            for (String suffix : new String[]{"", "-wal", "-shm"}) {
                Path from = Path.of(oldFile + suffix);
                if (Files.exists(from)) {
                    Files.copy(from, Path.of(newFile + suffix));
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Could not copy old data from " + oldFile + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- subjects

    public List<Subject> allSubjects() {
        List<Subject> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, color FROM subjects ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) {
                out.add(new Subject(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public Map<Long, Subject> subjectsById() {
        Map<Long, Subject> map = new LinkedHashMap<>();
        for (Subject s : allSubjects()) {
            map.put(s.id(), s);
        }
        return map;
    }

    public Optional<Subject> findSubjectByName(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, color FROM subjects WHERE lower(name) = lower(?)")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Subject(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return Optional.empty();
    }

    public Subject createSubject(String name, String color) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO subjects(name, color, created_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, color);
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Subject(keys.getLong(1), name.trim(), color);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void updateSubject(Subject s) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE subjects SET name = ?, color = ? WHERE id = ?")) {
            ps.setString(1, s.name().trim());
            ps.setString(2, s.colorHex());
            ps.setLong(3, s.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Total seconds ever studied for a subject. */
    public long totalSecondsFor(long subjectId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM((julianday(end_ts) - julianday(start_ts)) * 86400), 0) "
                        + "FROM sessions WHERE subject_id = ?")) {
            ps.setLong(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.round(rs.getDouble(1)) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Removes a subject together with all its study sessions and buckets. Cannot be undone. */
    public void deleteSubject(long subjectId) {
        try {
            conn.setAutoCommit(false);
            for (String sql : new String[]{
                    "DELETE FROM sessions WHERE subject_id = ?",
                    "DELETE FROM day_buckets WHERE subject_id = ?",
                    "DELETE FROM subjects WHERE id = ?"}) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, subjectId);
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // nothing more we can do
            }
            throw new IllegalStateException(e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // keep going
            }
        }
    }

    // ---------------------------------------------------------------- buckets of a day

    public List<Long> bucketsOf(LocalDate day) {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT subject_id FROM day_buckets WHERE day = ? ORDER BY position")) {
            ps.setString(1, day.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void addBucket(LocalDate day, long subjectId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR IGNORE INTO day_buckets(day, subject_id, position)
                VALUES (?, ?, (SELECT COALESCE(MAX(position), -1) + 1 FROM day_buckets WHERE day = ?))""")) {
            ps.setString(1, day.toString());
            ps.setLong(2, subjectId);
            ps.setString(3, day.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void removeBucket(LocalDate day, long subjectId) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM day_buckets WHERE day = ? AND subject_id = ?")) {
            ps.setString(1, day.toString());
            ps.setLong(2, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- sessions

    public long insertSession(long subjectId, LocalDateTime start, TimerMode mode) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sessions(subject_id, start_ts, end_ts, mode) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, subjectId);
            ps.setString(2, start.toString());
            ps.setString(3, start.toString());
            ps.setString(4, mode.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void updateSessionEnd(long sessionId, LocalDateTime end) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE sessions SET end_ts = ? WHERE id = ?")) {
            ps.setString(1, end.toString());
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteSession(long sessionId) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE id = ?")) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sessions that start inside [from, to). Sessions are split at midnight, so each belongs to one day. */
    public List<StudySession> sessionsBetween(LocalDate from, LocalDate toExclusive) {
        List<StudySession> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, subject_id, start_ts, end_ts, mode FROM sessions
                WHERE start_ts >= ? AND start_ts < ? ORDER BY start_ts""")) {
            ps.setString(1, from.atStartOfDay().toString());
            ps.setString(2, toExclusive.atStartOfDay().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StudySession(rs.getLong(1), rs.getLong(2),
                            LocalDateTime.parse(rs.getString(3)), LocalDateTime.parse(rs.getString(4)),
                            TimerMode.valueOf(rs.getString(5))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    // ---------------------------------------------------------------- settings

    public Map<String, String> loadSettings() {
        Map<String, String> map = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT key, value FROM settings")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return map;
    }

    public void saveSetting(String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // closing on exit
        }
    }
}
