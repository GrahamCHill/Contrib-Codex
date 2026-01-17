package dev.grahamhill.service;

import dev.grahamhill.model.ContributorStats;

import java.sql.*;
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
                    commits INTEGER,
                    lines_added INTEGER,
                    lines_deleted INTEGER,
                    language_breakdown TEXT,
                    ai_probability REAL,
                    files_added INTEGER DEFAULT 0,
                    files_edited INTEGER DEFAULT 0,
                    files_deleted_count INTEGER DEFAULT 0,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    public void saveMetrics(List<ContributorStats> stats) throws SQLException {
        String sql = "INSERT INTO contributor_metrics (name, email, commits, lines_added, lines_deleted, language_breakdown, ai_probability, files_added, files_edited, files_deleted_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ContributorStats stat : stats) {
                pstmt.setString(1, stat.name());
                pstmt.setString(2, stat.email());
                pstmt.setInt(3, stat.commitCount());
                pstmt.setInt(4, stat.linesAdded());
                pstmt.setInt(5, stat.linesDeleted());
                pstmt.setString(6, stat.languageBreakdown().toString());
                pstmt.setDouble(7, stat.averageAiProbability());
                pstmt.setInt(8, stat.filesAdded());
                pstmt.setInt(9, stat.filesEdited());
                pstmt.setInt(10, stat.filesDeletedCount());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<ContributorStats> getLatestMetrics() throws SQLException {
        List<ContributorStats> stats = new ArrayList<>();
        // This is a simplified version, just getting the last set of entries
        String sql = "SELECT name, email, commits, lines_added, lines_deleted, language_breakdown, ai_probability, files_added, files_edited, files_deleted_count FROM contributor_metrics ORDER BY timestamp DESC LIMIT 10";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new ContributorStats(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getInt("commits"),
                        rs.getInt("lines_added"),
                        rs.getInt("lines_deleted"),
                        parseLanguageBreakdown(rs.getString("language_breakdown")),
                        rs.getDouble("ai_probability"),
                        rs.getInt("files_added"),
                        rs.getInt("files_edited"),
                        rs.getInt("files_deleted_count")
                ));
            }
        }
        return stats;
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
