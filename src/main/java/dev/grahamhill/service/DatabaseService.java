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
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    public void saveMetrics(List<ContributorStats> stats) throws SQLException {
        String sql = "INSERT INTO contributor_metrics (name, email, commits, lines_added, lines_deleted) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ContributorStats stat : stats) {
                pstmt.setString(1, stat.name());
                pstmt.setString(2, stat.email());
                pstmt.setInt(3, stat.commitCount());
                pstmt.setInt(4, stat.linesAdded());
                pstmt.setInt(5, stat.linesDeleted());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<ContributorStats> getLatestMetrics() throws SQLException {
        List<ContributorStats> stats = new ArrayList<>();
        // This is a simplified version, just getting the last set of entries
        String sql = "SELECT name, email, commits, lines_added, lines_deleted FROM contributor_metrics ORDER BY timestamp DESC LIMIT 10";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new ContributorStats(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getInt("commits"),
                        rs.getInt("lines_added"),
                        rs.getInt("lines_deleted")
                ));
            }
        }
        return stats;
    }
}
