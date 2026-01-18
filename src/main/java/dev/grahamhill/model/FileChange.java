package dev.grahamhill.model;

public record FileChange(
    String path,
    int insertions,
    int deletions,
    String category,
    String changeType, // ADD, MODIFY, DELETE, RENAME, COPY
    String diff
) {
    public int getTotalChange() {
        return insertions + deletions;
    }
}
