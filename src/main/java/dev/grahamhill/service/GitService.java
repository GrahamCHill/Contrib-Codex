package dev.grahamhill.service;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class GitService {

    public List<ContributorStats> getContributorStats(File repoPath, Map<String, String> aliases, Set<String> ignoredExtensions) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            Map<String, StatsBuilder> statsMap = new HashMap<>();

            Iterable<RevCommit> commits = git.log().all().call();
            for (RevCommit commit : commits) {
                String email = commit.getAuthorIdent().getEmailAddress();
                String name = commit.getAuthorIdent().getName();
                
                String targetName = aliases.getOrDefault(email, name);
                
                StatsBuilder builder = statsMap.computeIfAbsent(targetName, k -> new StatsBuilder(targetName, email));
                builder.commitCount++;

                int linesAddedBefore = builder.linesAdded;
                int linesDeletedBefore = builder.linesDeleted;

                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                analyzeDiff(repository, parent, commit, builder, ignoredExtensions, null);
                
                int linesAdded = builder.linesAdded - linesAddedBefore;
                int linesDeleted = builder.linesDeleted - linesDeletedBefore;
                
                // Track files changed for AI heuristic (rough estimate based on language breakdown change)
                // Actually, analyzeDiff doesn't give us files changed easily here without more state.
                // Let's use a simple version.
                double aiProb = calculateAIProbability(commit, linesAdded, linesDeleted, 0); // filesChanged set to 0 for now as proxy
                builder.totalAiProbability += aiProb;
            }

            return statsMap.values().stream()
                    .map(StatsBuilder::build)
                    .sorted(Comparator.comparingInt(ContributorStats::commitCount).reversed())
                    .limit(10)
                    .toList();
        }
    }

    private void analyzeDiff(Repository repository, RevCommit parent, RevCommit commit, StatsBuilder builder, Set<String> ignoredExtensions, Map<String, Integer> commitLanguages) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> diffs;
            if (parent != null) {
                diffs = df.scan(parent.getTree(), commit.getTree());
            } else {
                // For initial commit, compare against empty tree
                diffs = df.scan(null, commit.getTree());
            }

            for (DiffEntry entry : diffs) {
                String path = entry.getNewPath();
                if (path == null || path.equals(DiffEntry.DEV_NULL)) {
                    path = entry.getOldPath();
                }

                if (path != null && !path.equals(DiffEntry.DEV_NULL)) {
                    String ext = "";
                    int lastDot = path.lastIndexOf('.');
                    if (lastDot > 0) {
                        ext = path.substring(lastDot + 1).toLowerCase();
                    }

                    if (!ext.isEmpty()) {
                        boolean ignored = false;
                        for (String ignoredExt : ignoredExtensions) {
                            if (path.toLowerCase().endsWith(ignoredExt.toLowerCase())) {
                                ignored = true;
                                break;
                            }
                        }
                        if (!ignored) {
                            builder.languageBreakdown.merge(ext, 1, Integer::sum);
                            if (commitLanguages != null) {
                                commitLanguages.merge(ext, 1, Integer::sum);
                            }
                        } else {
                            continue;
                        }
                    }
                }

                // Metric for new, edited, other (deleted)
                switch (entry.getChangeType()) {
                    case ADD -> builder.filesAdded++;
                    case MODIFY -> builder.filesEdited++;
                    case DELETE -> builder.filesDeleted++;
                    default -> {} // RENAME, COPY etc as other/edited for now
                }

                for (Edit edit : df.toFileHeader(entry).toEditList()) {
                    int added = edit.getEndB() - edit.getBeginB();
                    int deleted = edit.getEndA() - edit.getBeginA();
                    builder.linesAdded += added;
                    builder.linesDeleted += deleted;
                }
            }
        }
    }

    private double calculateAIProbability(RevCommit commit, int linesAdded, int linesDeleted, int filesChanged) {
        // Heuristic for AI generation probability
        // 1. Code bloat: massive additions in a single commit
        // 2. High addition-to-deletion ratio
        // 3. Large number of files changed
        
        double score = 0.0;
        
        // Bloat: > 500 lines is suspicious, > 2000 is very suspicious
        if (linesAdded > 2000) score += 0.5;
        else if (linesAdded > 500) score += 0.2;
        
        // Ratio: AI often writes lots of new code rather than refactoring
        if (linesAdded > 100 && linesDeleted < (linesAdded * 0.05)) score += 0.3;
        
        // Files: AI often touches many files if it's a boilerplate generation
        if (filesChanged > 20) score += 0.2;
        
        // Commit message length: very short or very generic messages sometimes come from automation
        String msg = commit.getShortMessage().toLowerCase();
        if (msg.contains("update") || msg.contains("fix") || msg.length() < 10) {
             // slight increase, but very weak signal
        }

        return Math.min(1.0, score);
    }

    public List<CommitInfo> getLastCommits(File repoPath, int limit) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            var logCommand = git.log();
            if (limit > 0) {
                logCommand.setMaxCount(limit);
            }
            Iterable<RevCommit> commits = logCommand.call();
            List<CommitInfo> result = new ArrayList<>();
            for (RevCommit commit : commits) {
                Map<String, Integer> languages = new HashMap<>();
                int linesAdded = 0;
                int linesDeleted = 0;
                int fAdded = 0;
                int fEdited = 0;
                int fDeleted = 0;
                
                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    List<DiffEntry> diffs = parent != null ? df.scan(parent.getTree(), commit.getTree()) : df.scan(null, commit.getTree());
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath();
                        if (path != null) {
                            int lastDot = path.lastIndexOf('.');
                            if (lastDot > 0) {
                                languages.merge(path.substring(lastDot + 1).toLowerCase(), 1, Integer::sum);
                            }
                        }

                        switch (entry.getChangeType()) {
                            case ADD -> fAdded++;
                            case MODIFY -> fEdited++;
                            case DELETE -> fDeleted++;
                            default -> {}
                        }

                        for (Edit edit : df.toFileHeader(entry).toEditList()) {
                            linesAdded += edit.getEndB() - edit.getBeginB();
                            linesDeleted += edit.getEndA() - edit.getBeginA();
                        }
                    }
                }

                double aiProb = calculateAIProbability(commit, linesAdded, linesDeleted, languages.size());

                result.add(new CommitInfo(
                        commit.getName().substring(0, 7),
                        commit.getAuthorIdent().getName(),
                        commit.getShortMessage(),
                        LocalDateTime.ofInstant(commit.getAuthorIdent().getWhenAsInstant(), ZoneId.systemDefault()),
                        languages,
                        aiProb,
                        fAdded,
                        fEdited,
                        fDeleted
                ));
            }
            return result;
        }
    }

    public CommitInfo getInitialCommit(File repoPath) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            Iterable<RevCommit> commits = git.log().all().call();
            RevCommit initial = null;
            for (RevCommit commit : commits) {
                initial = commit;
            }
            if (initial != null) {
                Map<String, Integer> languages = new HashMap<>();
                int linesAdded = 0;
                int linesDeleted = 0;
                int fAdded = 0;
                int fEdited = 0;
                int fDeleted = 0;
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    List<DiffEntry> diffs = df.scan(null, initial.getTree());
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath();
                        if (path != null) {
                            int lastDot = path.lastIndexOf('.');
                            if (lastDot > 0) {
                                languages.merge(path.substring(lastDot + 1).toLowerCase(), 1, Integer::sum);
                            }
                        }

                        switch (entry.getChangeType()) {
                            case ADD -> fAdded++;
                            case MODIFY -> fEdited++;
                            case DELETE -> fDeleted++;
                            default -> {}
                        }

                        for (Edit edit : df.toFileHeader(entry).toEditList()) {
                            linesAdded += edit.getEndB() - edit.getBeginB();
                            linesDeleted += edit.getEndA() - edit.getBeginA();
                        }
                    }
                }
                double aiProb = calculateAIProbability(initial, linesAdded, linesDeleted, languages.size());

                return new CommitInfo(
                        initial.getName().substring(0, 7),
                        initial.getAuthorIdent().getName(),
                        initial.getShortMessage(),
                        LocalDateTime.ofInstant(initial.getAuthorIdent().getWhenAsInstant(), ZoneId.systemDefault()),
                        languages,
                        aiProb,
                        fAdded,
                        fEdited,
                        fDeleted
                );
            }
            return null;
        }
    }

    private static class StatsBuilder {
        String name;
        String email;
        int commitCount;
        int linesAdded;
        int linesDeleted;
        Map<String, Integer> languageBreakdown = new HashMap<>();
        double totalAiProbability;
        int filesAdded;
        int filesEdited;
        int filesDeleted;

        StatsBuilder(String name, String email) {
            this.name = name;
            this.email = email;
        }

        ContributorStats build() {
            return new ContributorStats(name, email, commitCount, linesAdded, linesDeleted, languageBreakdown, totalAiProbability / commitCount, filesAdded, filesEdited, filesDeleted);
        }
    }
}
