package dev.grahamhill.model;
import java.util.Map;

public record ContributorStats(
    String name,
    String email,
    int commitCount,
    int linesAdded,
    int linesDeleted,
    Map<String, Integer> languageBreakdown,
    double averageAiProbability
) {
    public int getTotalImpact() {
        return linesAdded + linesDeleted;
    }
}
