package dev.grahamhill.model;

import java.util.Map;

public record CompanyMetric(
    String repoName,
    int totalContributors,
    int totalCommits,
    int totalLinesAdded,
    int totalLinesDeleted,
    double averageMeaningfulScore,
    String primaryLanguage,
    Map<String, Integer> languageBreakdown
) {}
