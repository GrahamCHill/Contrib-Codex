package dev.grahamhill.service;

import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.ReportHistory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private static final String DB_URL = "jdbc:sqlite:metrics.db";

    public DatabaseService() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contributor_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT,
                    email TEXT,
                    gender TEXT,
                    commits INTEGER,
                    merges INTEGER DEFAULT 0,
                    lines_added INTEGER,
                    lines_deleted INTEGER,
                    language_breakdown TEXT,
                    ai_probability REAL,
                    files_added INTEGER DEFAULT 0,
                    files_edited INTEGER DEFAULT 0,
                    files_deleted_count INTEGER DEFAULT 0,
                    meaningful_change_score REAL DEFAULT 0.0,
                    touched_tests INTEGER DEFAULT 0,
                    generated_files_pushed INTEGER DEFAULT 0,
                    documentation_lines_added INTEGER DEFAULT 0,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS global_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS report_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    version TEXT,
                    date TEXT,
                    author TEXT,
                    description TEXT,
                    earliest_commit TEXT
                )
                """);
        }
    }

    public void saveGlobalSetting(String key, String value) throws SQLException {
        String sql = "INSERT OR REPLACE INTO global_settings (key, value) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        }
    }

    public String getGlobalSetting(String key) throws SQLException {
        String sql = "SELECT value FROM global_settings WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        }
        return null;
    }

    public void saveMetrics(List<ContributorStats> stats) throws SQLException {
        String sql = "INSERT INTO contributor_metrics (name, email, gender, commits, merges, lines_added, lines_deleted, language_breakdown, ai_probability, files_added, files_edited, files_deleted_count, meaningful_change_score, touched_tests, generated_files_pushed, documentation_lines_added) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ContributorStats stat : stats) {
                pstmt.setString(1, stat.name());
                pstmt.setString(2, stat.email());
                pstmt.setString(3, stat.gender());
                pstmt.setInt(4, stat.commitCount());
                pstmt.setInt(5, stat.mergeCount());
                pstmt.setInt(6, stat.linesAdded());
                pstmt.setInt(7, stat.linesDeleted());
                pstmt.setString(8, stat.languageBreakdown().toString());
                pstmt.setDouble(9, stat.averageAiProbability());
                pstmt.setInt(10, stat.filesAdded());
                pstmt.setInt(11, stat.filesEdited());
                pstmt.setInt(12, stat.filesDeletedCount());
                pstmt.setDouble(13, stat.meaningfulChangeScore());
                pstmt.setInt(14, stat.touchedTests() ? 1 : 0);
                pstmt.setInt(15, stat.generatedFilesPushed());
                pstmt.setInt(16, stat.documentationLinesAdded());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<ContributorStats> getLatestMetrics() throws SQLException {
        List<ContributorStats> stats = new ArrayList<>();
        // This is a simplified version, just getting the last set of entries
        String sql = "SELECT name, email, gender, commits, merges, lines_added, lines_deleted, language_breakdown, ai_probability, files_added, files_edited, files_deleted_count, meaningful_change_score, touched_tests, generated_files_pushed, documentation_lines_added FROM contributor_metrics ORDER BY timestamp DESC LIMIT 10";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new ContributorStats(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("gender"),
                        rs.getInt("commits"),
                        rs.getInt("merges"),
                        rs.getInt("lines_added"),
                        rs.getInt("lines_deleted"),
                        parseLanguageBreakdown(rs.getString("language_breakdown")),
                        rs.getDouble("ai_probability"),
                        rs.getInt("files_added"),
                        rs.getInt("files_edited"),
                        rs.getInt("files_deleted_count"),
                        rs.getDouble("meaningful_change_score"),
                        rs.getInt("touched_tests") == 1,
                        rs.getInt("generated_files_pushed"),
                        rs.getInt("documentation_lines_added")
                ));
            }
        }
        return stats;
    }

    public List<ReportHistory> getLatestReportHistory(int limit) throws SQLException {
        List<ReportHistory> history = new ArrayList<>();
        String sql = "SELECT id, version, date, author, description, earliest_commit FROM report_history ORDER BY id DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new ReportHistory(
                            rs.getInt("id"),
                            rs.getString("version"),
                            LocalDate.parse(rs.getString("date")),
                            rs.getString("author"),
                            rs.getString("description"),
                            rs.getString("earliest_commit")
                    ));
                }
            }
        }
        return history;
    }

    public ReportHistory getLatestReportForCommit(String earliestCommit) throws SQLException {
        String sql = "SELECT id, version, date, author, description, earliest_commit FROM report_history WHERE earliest_commit = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, earliestCommit);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ReportHistory(
                            rs.getInt("id"),
                            rs.getString("version"),
                            LocalDate.parse(rs.getString("date")),
                            rs.getString("author"),
                            rs.getString("description"),
                            rs.getString("earliest_commit")
                    );
                }
            }
        }
        return null;
    }

    public void saveReportHistory(ReportHistory history) throws SQLException {
        String sql = "INSERT INTO report_history (version, date, author, description, earliest_commit) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, history.version());
            pstmt.setString(2, history.date().toString());
            pstmt.setString(3, history.author());
            pstmt.setString(4, history.description());
            pstmt.setString(5, history.earliestCommit());
            pstmt.executeUpdate();
        }
    }

    private java.util.Map<String, Integer> parseLanguageBreakdown(String str) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        if (str == null || str.isEmpty() || str.equals("{}")) return map;
        str = str.substring(1, str.length() - 1); // remove { and }
        String[] parts = str.split(", ");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                map.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
        return map;
    }
}
