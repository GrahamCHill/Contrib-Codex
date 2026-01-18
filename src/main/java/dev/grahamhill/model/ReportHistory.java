package dev.grahamhill.model;

import java.time.LocalDate;

public record ReportHistory(
    int id,
    String version,
    LocalDate date,
    String author,
    String description,
    String earliestCommit
) {}
