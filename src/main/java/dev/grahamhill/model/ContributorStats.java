package dev.grahamhill.model;
import java.util.Map;

public record ContributorStats(
    String name,
    String email,
    String gender,
    int commitCount,
    int mergeCount,
    int linesAdded,
    int linesDeleted,
    Map<String, Integer> languageBreakdown,
    double averageAiProbability,
    int filesAdded,
    int filesEdited,
    int filesDeletedCount,
    double meaningfulChangeScore
) {
    public int getTotalImpact() {
        return linesAdded + linesDeleted;
    }
}
