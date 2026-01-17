package dev.grahamhill.model;

import java.util.List;
import java.util.Map;

public record MeaningfulChangeAnalysis(
    String commitRange,
    int totalInsertions,
    int totalDeletions,
    int whitespaceChurn, // insertions + deletions that are just whitespace
    List<FileChange> topChangedFiles,
    Map<String, CategoryMetrics> categoryBreakdown,
    List<String> warnings,
    String summary,
    double meaningfulChangeScore
) {
    public record CategoryMetrics(int fileCount, int insertions, int deletions) {}
}
