package dev.grahamhill.model;

import java.time.LocalDateTime;
import java.util.Map;

public record CommitInfo(
    String id,
    String authorName,
    String message,
    LocalDateTime timestamp,
    Map<String, Integer> languageBreakdown,
    double aiProbability,
    int filesAdded,
    int filesEdited,
    int filesDeleted,
    int linesAdded,
    int linesDeleted,
    boolean isMerge,
    String branch
) {}
