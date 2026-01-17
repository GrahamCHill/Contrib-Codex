package dev.grahamhill.service;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.FileChange;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class GitService {

    public MeaningfulChangeAnalysis performMeaningfulChangeAnalysis(File repoPath, int limit) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            var logCommand = git.log();
            if (limit > 0) {
                logCommand.setMaxCount(limit);
            }
            List<RevCommit> commits = new ArrayList<>();
            git.log().all().call().forEach(commits::add);
            
            // If limit is set, we only care about the latest N commits
            if (limit > 0 && commits.size() > limit) {
                commits = commits.subList(0, limit);
            }

            if (commits.isEmpty()) return null;

            RevCommit newest = commits.get(0);
            RevCommit oldest = commits.get(commits.size() - 1);
            String range = oldest.getName().substring(0, 7) + ".." + newest.getName().substring(0, 7);
            if (commits.size() == 1) range = newest.getName().substring(0, 7);

            List<FileChange> allFileChanges = new ArrayList<>();
            int totalIns = 0;
            int totalDel = 0;
            int totalWsIns = 0;
            int totalWsDel = 0;

            // We want to analyze the aggregate change over the range
            // Compare oldest's parent to newest
            RevCommit base = oldest.getParentCount() > 0 ? oldest.getParent(0) : null;
            ObjectId baseTree = base != null ? base.getTree() : null;
            ObjectId newestTree = newest.getTree();

            Map<String, MeaningfulChangeAnalysis.CategoryMetrics> categoryMap = new HashMap<>();
            initializeCategories(categoryMap);

            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                 DiffFormatter dfWs = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                df.setDetectRenames(true);
                dfWs.setRepository(repository);
                dfWs.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);

                List<DiffEntry> diffs = df.scan(baseTree, newestTree);
                for (DiffEntry entry : diffs) {
                    String path = entry.getNewPath().equals(DiffEntry.DEV_NULL) ? entry.getOldPath() : entry.getNewPath();
                    String category = categorizePath(path);
                    
                    int ins = 0;
                    int del = 0;
                    for (Edit edit : df.toFileHeader(entry).toEditList()) {
                        ins += edit.getEndB() - edit.getBeginB();
                        del += edit.getEndA() - edit.getBeginA();
                    }

                    int insWs = 0;
                    int delWs = 0;
                    for (Edit edit : dfWs.toFileHeader(entry).toEditList()) {
                        insWs += edit.getEndB() - edit.getBeginB();
                        delWs += edit.getEndA() - edit.getBeginA();
                    }

                    totalIns += ins;
                    totalDel += del;
                    // Churn is the difference between normal diff and whitespace-ignored diff
                    totalWsIns += (ins - insWs);
                    totalWsDel += (del - delWs);

                    allFileChanges.add(new FileChange(path, ins, del, category, entry.getChangeType().name()));
                    
                    MeaningfulChangeAnalysis.CategoryMetrics cm = categoryMap.get(category);
                    categoryMap.put(category, new MeaningfulChangeAnalysis.CategoryMetrics(
                        cm.fileCount() + 1, cm.insertions() + ins, cm.deletions() + del));
                }
            }

            List<FileChange> top20 = allFileChanges.stream()
                .sorted(Comparator.comparingInt(FileChange::getTotalChange).reversed())
                .limit(20)
                .toList();

            List<String> warnings = generateWarnings(categoryMap, totalIns);
            double score = calculateMeaningfulScore(categoryMap, totalIns);
            String summary = generateSummary(range, totalIns, totalDel, categoryMap, warnings);

            return new MeaningfulChangeAnalysis(
                range, totalIns, totalDel, totalWsIns + totalWsDel,
                top20, categoryMap, warnings, summary, score
            );
        }
    }

    private void initializeCategories(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map) {
        String[] cats = {"Source Code", "Tests", "Generated/Artifacts", "Lockfiles", "Sourcemaps/Minified", "Config/Data", "Other"};
        for (String c : cats) map.put(c, new MeaningfulChangeAnalysis.CategoryMetrics(0, 0, 0));
    }

    private String categorizePath(String path) {
        path = path.toLowerCase();
        if (path.contains("test/") || path.contains("tests/") || path.contains("__tests__") || path.endsWith("test.java") || path.endsWith("spec.js")) return "Tests";
        if (path.startsWith("src/") || path.startsWith("app/") || path.startsWith("lib/") || path.contains("backend/") || path.contains("frontend/")) return "Source Code";
        if (path.startsWith("dist/") || path.startsWith("build/") || path.contains(".next/") || path.contains(".nuxt/") || path.contains("coverage/")) return "Generated/Artifacts";
        if (path.endsWith("package-lock.json") || path.endsWith("yarn.lock") || path.endsWith("pnpm-lock.yaml")) return "Lockfiles";
        if (path.endsWith(".map") || path.endsWith(".min.js") || path.endsWith(".min.css")) return "Sourcemaps/Minified";
        if (path.endsWith(".json") || path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".toml") || path.endsWith(".xml")) return "Config/Data";
        return "Other";
    }

    private List<String> generateWarnings(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map, int totalIns) {
        List<String> warnings = new ArrayList<>();
        MeaningfulChangeAnalysis.CategoryMetrics src = map.get("Source Code");
        MeaningfulChangeAnalysis.CategoryMetrics test = map.get("Tests");
        MeaningfulChangeAnalysis.CategoryMetrics gen = map.get("Generated/Artifacts");
        MeaningfulChangeAnalysis.CategoryMetrics lock = map.get("Lockfiles");
        MeaningfulChangeAnalysis.CategoryMetrics mapMin = map.get("Sourcemaps/Minified");

        if (totalIns > 1000 && src.insertions() < (totalIns * 0.1)) {
            warnings.add("Huge LOC change but minimal source code changes detected.");
        }
        if (src.insertions() > 500 && test.insertions() == 0) {
            warnings.add("Significant source code changes without accompanying test changes.");
        }
        int nonMeaningful = gen.insertions() + lock.insertions() + mapMin.insertions();
        if (totalIns > 0 && (double)nonMeaningful / totalIns > 0.7) {
            warnings.add("Majority of changes appear to be generated artifacts, lockfiles, or minified code.");
        }
        return warnings;
    }

    private double calculateMeaningfulScore(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map, int totalIns) {
        if (totalIns == 0) return 100.0;
        MeaningfulChangeAnalysis.CategoryMetrics src = map.get("Source Code");
        MeaningfulChangeAnalysis.CategoryMetrics test = map.get("Tests");
        
        double srcWeight = (double)src.insertions() / totalIns;
        double testWeight = (double)test.insertions() / totalIns;
        
        // Very basic heuristic
        double score = (srcWeight * 70) + (testWeight * 30);
        return Math.min(100.0, score);
    }

    private String generateSummary(String range, int ins, int del, Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Analysis for range %s: %d insertions, %d deletions. ", range, ins, del));
        MeaningfulChangeAnalysis.CategoryMetrics src = map.get("Source Code");
        sb.append(String.format("Source code accounts for %d lines across %d files. ", src.insertions(), src.fileCount()));
        if (!warnings.isEmpty()) {
            sb.append("Notable issues: ").append(String.join("; ", warnings));
        } else {
            sb.append("Changes appear generally meaningful.");
        }
        return sb.toString();
    }

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
        
        // Initial commits are usually NOT AI generated in this context, and often large.
        if (commit.getParentCount() == 0) return 0.0;
        
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
