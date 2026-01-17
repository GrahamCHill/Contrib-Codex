package dev.grahamhill.model;

import java.time.LocalDateTime;
import java.util.Map;

public record CommitInfo(
    String id,
    String authorName,
    String message,
    LocalDateTime timestamp,
    Map<String, Integer> languageBreakdown,
    double aiProbability
) {}
