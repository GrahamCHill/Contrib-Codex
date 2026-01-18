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

    public String getProjectStructure(File repoPath, Set<String> ignoredFolders) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT STRUCTURE (with creation commit IDs):\n");
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            Map<String, String> creationCommits = getFileCreationCommits(git);
            listDirectory(repoPath, repoPath, "", sb, ignoredFolders, 0, creationCommits);
        } catch (Exception e) {
            sb.append("Error reading project structure: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private Map<String, String> getFileCreationCommits(Git git) throws Exception {
        Map<String, String> map = new HashMap<>();
        Iterable<RevCommit> commits = git.log().all().call();
        List<RevCommit> commitList = new ArrayList<>();
        commits.forEach(commitList::add);
        Collections.reverse(commitList); // Oldest first

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(git.getRepository());
            for (RevCommit commit : commitList) {
                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                List<DiffEntry> diffs = df.scan(parent == null ? null : parent.getTree(), commit.getTree());
                for (DiffEntry entry : diffs) {
                    if (entry.getChangeType() == DiffEntry.ChangeType.ADD) {
                        String path = entry.getNewPath();
                        map.putIfAbsent(path, commit.getName().substring(0, 7));
                    }
                }
            }
        }
        return map;
    }

    private void listDirectory(File baseDir, File currentDir, String indent, StringBuilder sb, Set<String> ignoredFolders, int depth, Map<String, String> creationCommits) {
        if (depth > 5) return; // Limit depth to avoid too much context
        File[] files = currentDir.listFiles();
        if (files == null) return;
        
        // Sort files to have consistent output
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareTo(b.getName());
        });

        for (File file : files) {
            if (file.getName().startsWith(".") && !file.getName().equals(".gitignore")) continue;
            if (isIgnoredFolder(file.getName(), ignoredFolders)) continue;
            
            String relativePath = baseDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
            String commitId = creationCommits.getOrDefault(relativePath, "Unknown");

            sb.append(indent).append(file.isDirectory() ? "[D] " : "[F] ").append(file.getName());
            if (!file.isDirectory()) {
                sb.append(" (Created in: ").append(commitId).append(")");
            }
            sb.append("\n");

            if (file.isDirectory()) {
                listDirectory(baseDir, file, indent + "  ", sb, ignoredFolders, depth + 1, creationCommits);
            }
        }
    }

    public MeaningfulChangeAnalysis performMeaningfulChangeAnalysis(File repoPath, int limit, Set<String> ignoredFolders) throws Exception {
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
                    
                    if (isIgnoredFolder(path, ignoredFolders)) continue;

                    String category = categorizePath(path, repository);
                    
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

    private boolean isIgnoredFolder(String path, Set<String> ignoredFolders) {
        if (ignoredFolders == null || ignoredFolders.isEmpty()) return false;
        for (String folder : ignoredFolders) {
            if (path.startsWith(folder + "/") || path.equals(folder) || path.contains("/" + folder + "/")) {
                return true;
            }
        }
        return false;
    }

    private void initializeCategories(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map) {
        String[] cats = {"Source Code", "Tests", "Styling", "Generated/Artifacts", "Lockfiles", "Sourcemaps/Minified", "Config/Data", "Documentation", "Other"};
        for (String c : cats) map.put(c, new MeaningfulChangeAnalysis.CategoryMetrics(0, 0, 0));
    }

    private String categorizePath(String path) {
        return categorizePath(path, null);
    }

    private String categorizePath(String path, Repository repository) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.contains("test/") || lowerPath.contains("tests/") || lowerPath.contains("__tests__") || lowerPath.endsWith("test.java") || lowerPath.endsWith("spec.js")) return "Tests";
        if (lowerPath.endsWith(".css") || lowerPath.endsWith(".scss") || lowerPath.endsWith(".sass") || lowerPath.endsWith(".less")) return "Styling";
        
        if (lowerPath.startsWith("src/") || lowerPath.startsWith("app/") || lowerPath.startsWith("lib/") || lowerPath.contains("backend/") || lowerPath.contains("frontend/")) {
            if (lowerPath.endsWith(".vue") || lowerPath.endsWith(".jsx") || lowerPath.endsWith(".tsx")) {
                if (repository != null) {
                    // Try to see if it's mostly styling
                    try {
                        byte[] data = repository.open(repository.resolve("HEAD:" + path)).getBytes();
                        String content = new String(data);
                        if (content.contains("<style") || content.contains("styled-components") || content.contains("className=")) {
                             // This is still a bit weak, but it's "seeing into" the file.
                             // Let's count it as styling if it has a lot of style related tags and is in a frontend dir.
                             if (content.split("<style").length > 1 || content.split("className=").length > 5) {
                                 // Simple heuristic: many classes or a style block
                                 return "Styling";
                             }
                        }
                    } catch (Exception e) {
                        // ignore and fallback
                    }
                }
            }
            return "Source Code";
        }
        if (lowerPath.startsWith("dist/") || lowerPath.startsWith("build/") || lowerPath.contains(".next/") || lowerPath.contains(".nuxt/") || lowerPath.contains("coverage/")) return "Generated/Artifacts";
        if (lowerPath.endsWith("package-lock.json") || lowerPath.endsWith("yarn.lock") || lowerPath.endsWith("pnpm-lock.yaml") || lowerPath.endsWith("requirements.txt") || lowerPath.endsWith("pom.xml")) return "Lockfiles";
        if (lowerPath.endsWith(".map") || lowerPath.endsWith(".min.js") || lowerPath.endsWith(".min.css")) return "Sourcemaps/Minified";
        if (lowerPath.endsWith(".json") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".toml") || lowerPath.endsWith(".xml")) return "Config/Data";
        if (lowerPath.endsWith(".md")) return "Documentation";
        return "Other";
    }

    private List<String> generateWarnings(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map, int totalIns) {
        List<String> warnings = new ArrayList<>();
        MeaningfulChangeAnalysis.CategoryMetrics src = map.get("Source Code");
        MeaningfulChangeAnalysis.CategoryMetrics test = map.get("Tests");
        MeaningfulChangeAnalysis.CategoryMetrics gen = map.get("Generated/Artifacts");
        MeaningfulChangeAnalysis.CategoryMetrics lock = map.get("Lockfiles");
        MeaningfulChangeAnalysis.CategoryMetrics mapMin = map.get("Sourcemaps/Minified");
        MeaningfulChangeAnalysis.CategoryMetrics doc = map.get("Documentation");

        if (totalIns > 1000 && src.insertions() < (totalIns * 0.1)) {
            warnings.add("Huge LOC change but minimal source code changes detected.");
        }
        if (src.insertions() > 500 && test.insertions() == 0) {
            warnings.add("Significant source code changes without accompanying test changes.");
        }
        int nonMeaningful = gen.insertions() + lock.insertions() + mapMin.insertions() + doc.insertions();
        if (totalIns > 0 && (double)nonMeaningful / totalIns > 0.7) {
            warnings.add("Majority of changes appear to be generated artifacts, lockfiles, minified code, or documentation.");
        }
        return warnings;
    }

    private double calculateMeaningfulScore(Map<String, MeaningfulChangeAnalysis.CategoryMetrics> map, int totalIns) {
        if (totalIns == 0) return 100.0;
        MeaningfulChangeAnalysis.CategoryMetrics src = map.get("Source Code");
        MeaningfulChangeAnalysis.CategoryMetrics test = map.get("Tests");
        MeaningfulChangeAnalysis.CategoryMetrics style = map.get("Styling");
        
        double srcWeight = (double)src.insertions() / totalIns;
        double testWeight = (double)test.insertions() / totalIns;
        double styleWeight = style != null ? (double)style.insertions() / totalIns : 0;
        
        // Very basic heuristic: tests are highly valued, styling is slightly less valued than logic but still meaningful
        double score = (srcWeight * 60) + (testWeight * 30) + (styleWeight * 10);
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

    public List<ContributorStats> getContributorStats(File repoPath, Map<String, String> aliases, Map<String, String> genders, Set<String> ignoredExtensions, Set<String> ignoredFolders, String requiredFeatures) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            Map<String, StatsBuilder> statsMap = new HashMap<>();

            Iterable<RevCommit> commits = git.log().all().call();
            Set<String> processedCommits = new HashSet<>();
            for (RevCommit commit : commits) {
                if (!processedCommits.add(commit.getName())) {
                    continue;
                }
                String email = commit.getAuthorIdent().getEmailAddress();
                String name = commit.getAuthorIdent().getName();
                
                String targetName = aliases.getOrDefault(email, name);
                String gender = genders.getOrDefault(email, genders.getOrDefault(targetName, "unknown"));
                
                StatsBuilder builder = statsMap.computeIfAbsent(targetName, k -> new StatsBuilder(targetName, email, gender));
                
                boolean isMerge = commit.getParentCount() > 1;
                if (isMerge) {
                    builder.mergeCount++;
                } else {
                    builder.commitCount++;
                }

                int linesAddedBefore = builder.linesAdded;
                int linesDeletedBefore = builder.linesDeleted;
                int blankAddedBefore = builder.blankLinesAdded;
                int blankDeletedBefore = builder.blankLinesDeleted;

                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                
                // For merge commits, we don't want to attribute all the merged LOC to the person who clicked merge
                // because it skews the "lines added per commit" metric significantly.
                // We still want to see what was touched for context, but LOC should be handled carefully.
                // However, we DO want to see what was actually changed IN the merge itself (like conflict resolution).
                // Diffing a merge against its FIRST parent (the branch it was merged INTO) usually shows all 
                // changes brought in by the other branch. This is what we want to avoid for contributor stats.
                if (!isMerge) {
                    analyzeDiff(repository, parent, commit, builder, ignoredExtensions, ignoredFolders, null);
                } else {
                    // For merge commits, we don't add the LOC to their total to avoid skewing.
                }
                
                int linesAdded = builder.linesAdded - linesAddedBefore;
                int linesDeleted = builder.linesDeleted - linesDeletedBefore;
                int blankAdded = builder.blankLinesAdded - blankAddedBefore;
                int blankDeleted = builder.blankLinesDeleted - blankDeletedBefore;

                if (!isMerge && linesAdded == 0 && linesDeleted == 0) {
                    builder.meaninglessCommits++;
                } else if (!isMerge && (linesAdded > 0 || linesDeleted > 0)) {
                    // If almost everything is blank lines, consider it meaningless
                    if (linesAdded > 0 && blankAdded >= linesAdded * 0.9) {
                        builder.meaninglessCommits++;
                    }
                }
                
                // Risk assessment for tests: Check if any file changed by this contributor was a test
                boolean touchedTests = false;
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    List<DiffEntry> diffs = df.scan(parent != null ? parent.getTree() : null, commit.getTree());
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath();
                        if ("Tests".equals(categorizePath(path))) {
                            touchedTests = true;
                            break;
                        }
                    }
                } catch (IOException e) {
                    // ignore
                }
                if (touchedTests) {
                    builder.touchedTests = true;
                }

                // Track files changed for AI heuristic (rough estimate based on language breakdown change)
                // Actually, analyzeDiff doesn't give us files changed easily here without more state.
                // Let's use a simple version.
                double aiProb = calculateAIProbability(commit, linesAdded, linesDeleted, 0); // filesChanged set to 0 for now as proxy
                builder.totalAiProbability += aiProb;
            }

            return statsMap.values().stream()
                    .map(b -> {
                        double mScore = calculateMeaningfulScoreForContributor(b, requiredFeatures);
                        return b.build(mScore);
                    })
                    .sorted(Comparator.comparingInt(ContributorStats::commitCount).reversed())
                    .toList();
        }
    }

    private double calculateMeaningfulScoreForContributor(StatsBuilder b, String requiredFeatures) {
        if (b.linesAdded == 0 && b.linesDeleted == 0) return 0.0;
        
        // Base score on proportion of source code/tests vs total
        double score = 50.0; // Start at 50
        
        // Bonus for iterative work (more commits for same lines)
        double linesPerCommit = (double) b.linesAdded / (b.commitCount > 0 ? b.commitCount : 1);
        if (linesPerCommit < 250) score += 30;
        else if (linesPerCommit < 500) score += 15;
        else if (linesPerCommit > 2000) score -= 40;
        else if (linesPerCommit > 1500) score -= 30;
        else if (linesPerCommit > 1000) score -= 20;
        else if (linesPerCommit > 750) score -= 10;
        
        // Refactoring Recognition: 
        // If they delete significantly (refactoring), it drops future technical debt.
        if (b.linesDeleted > b.linesAdded * 0.5) {
            score += 15; // Reward refactoring
        }
        
        // Bonus for testing (rough proxy: files edited/added vs commits)
        if (b.filesEdited > b.commitCount) score += 10;
        
        // Alignment with requirements (very basic keyword matching)
        if (requiredFeatures != null && !requiredFeatures.isEmpty()) {
            String[] features = requiredFeatures.toLowerCase().split("\\W+");
            int matches = 0;
            // Placeholder logic: more activity per feature/complexity
            score += Math.min(20, (double)b.commitCount / 2);
        }

        // Meaningless Change Penalties
        // Deduction for blank lines if they are excessive (> 20% of total lines)
        if (b.linesAdded > 100 && b.blankLinesAdded > b.linesAdded * 0.2) {
            score -= Math.min(20, (double)b.blankLinesAdded / b.linesAdded * 50);
        }
        
        // Deduction for meaningless commits (no lines changed or only blank lines)
        if (b.meaninglessCommits > 0) {
            score -= Math.min(30, b.meaninglessCommits * 5);
        }

        return Math.max(0, Math.min(100, score));
    }

    private void analyzeDiff(Repository repository, RevCommit parent, RevCommit commit, StatsBuilder builder, Set<String> ignoredExtensions, Set<String> ignoredFolders, Map<String, Integer> commitLanguages) throws IOException {
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
                    if (isIgnoredFolder(path, ignoredFolders)) continue;
                    
                    // Explicitly ignore lockfiles
                    String lowerPath = path.toLowerCase();
                    if (lowerPath.contains("package-lock.json") || lowerPath.contains("yarn.lock") || lowerPath.contains("pnpm-lock.yaml")) {
                        continue;
                    }

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

                    // Detect blank lines
                    try {
                        org.eclipse.jgit.lib.ObjectLoader loaderA = entry.getOldId().toObjectId().equals(ObjectId.zeroId()) ? null : repository.open(entry.getOldId().toObjectId());
                        org.eclipse.jgit.lib.ObjectLoader loaderB = entry.getNewId().toObjectId().equals(ObjectId.zeroId()) ? null : repository.open(entry.getNewId().toObjectId());
                        
                        org.eclipse.jgit.diff.RawText aText = loaderA == null ? null : new org.eclipse.jgit.diff.RawText(loaderA.getCachedBytes());
                        org.eclipse.jgit.diff.RawText bText = loaderB == null ? null : new org.eclipse.jgit.diff.RawText(loaderB.getCachedBytes());
                        
                        if (added > 0 && bText != null) {
                            for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
                                String line = bText.getString(i);
                                if (line == null || line.trim().isEmpty()) {
                                    builder.blankLinesAdded++;
                                }
                            }
                        }
                        if (deleted > 0 && aText != null) {
                            for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                                String line = aText.getString(i);
                                if (line == null || line.trim().isEmpty()) {
                                    builder.blankLinesDeleted++;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // fallback or ignore if text cannot be loaded
                    }
                }
            }
        }
    }

    private double calculateAIProbability(RevCommit commit, int linesAdded, int linesDeleted, int filesChanged) {
        // Heuristic for AI generation probability and problematic behavior
        // 1. Code bloat: massive additions in a single commit
        // 2. High addition-to-deletion ratio
        // 3. Large number of files changed
        // 4. Lines per commit (as requested: monitor individuals with large lines added with few commits)
        
        // Initial commits (first few) or commits in setup are usually NOT AI generated and lack tests.
        // We lower the score for very early commits.
        boolean isEarly = commit.getParentCount() == 0;
        boolean isMerge = commit.getParentCount() > 1;
        
        double score = 0.0;
        
        // Bloat: 
        if (linesAdded > 1500) {
            score += 1.5; // Scale up to emphasize extreme risk
        } else if (linesAdded > 1000) {
            score += 1.0;
        } else if (linesAdded > 750) {
            score += 0.6;
        } else if (linesAdded > 500) {
            score += 0.3;
        } else if (linesAdded > 250) {
            score += 0.15;
        } else {
            score += 0.05;
        }
        
        // Refactoring vs Bloat: 
        // If there are many deletions, it's more likely a refactor than bloat.
        // We discount the "bloat" score if linesDeleted is significant.
        if (linesDeleted > linesAdded * 0.5) {
            score *= 0.7; // Refactoring discount
        }
        
        // Ratio: AI often writes lots of new code rather than refactoring
        if (linesAdded > 100 && linesDeleted < (linesAdded * 0.05)) {
            score += 0.1;
        }
        
        // Files: AI often touches many files if it's a boilerplate generation
        if (filesChanged > 20) {
            score += 0.1;
        }

        if (isEarly) {
            score *= 0.5; // Significant discount for initial commit
        }

        if (isMerge) {
            score *= 0.2; // Merge commits are rarely "AI generated bloat" in this sense
        }

        return Math.min(1.0, score);
    }

    public List<CommitInfo> getLastCommits(File repoPath, int limit, Map<String, String> aliases) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            var logCommand = git.log().all(); // Use .all() to include branch commits
            if (limit > 0) {
                logCommand.setMaxCount(limit);
            }
            Iterable<RevCommit> commits = logCommand.call();
            List<CommitInfo> result = new ArrayList<>();

            // Pre-calculate branch mappings
            Map<ObjectId, String> commitToBranch = new HashMap<>();
            List<org.eclipse.jgit.lib.Ref> branches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL).call();
            
            // Step 1: Identify "Trunk" commits for each main branch.
            // A commit is on the trunk if it's reachable from a main branch via FIRST parents only.
            // These should be attributed to the main branch.
            Set<ObjectId> trunkCommits = new HashSet<>();
            
            for (org.eclipse.jgit.lib.Ref branch : branches) {
                String fullBranchName = branch.getName();
                
                // Skip literal HEAD pointers to avoid attributing commits to a "branch" named HEAD
                if (fullBranchName.equals("HEAD") || fullBranchName.equals("refs/remotes/origin/HEAD")) {
                    continue;
                }

                String branchName = repository.shortenRemoteBranchName(fullBranchName);
                if (branchName != null && (branchName.equals(fullBranchName) || branchName.startsWith("refs/"))) {
                    if (fullBranchName.startsWith("refs/heads/")) branchName = fullBranchName.substring(11);
                    else if (fullBranchName.startsWith("refs/remotes/")) branchName = fullBranchName.substring(13);
                }
                if (branchName == null) branchName = fullBranchName;
                
                if (branchName.startsWith("refs/heads/")) branchName = branchName.substring(11);
                if (branchName.startsWith("refs/remotes/")) branchName = branchName.substring(13);
                
                boolean isMainBranch = branchName.equalsIgnoreCase("main") || branchName.equalsIgnoreCase("master") || branchName.equalsIgnoreCase("develop") || 
                                     branchName.equalsIgnoreCase("origin/main") || branchName.equalsIgnoreCase("origin/master") || branchName.equalsIgnoreCase("origin/develop");
                
                if (isMainBranch) {
                    try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
                        RevCommit current = walk.parseCommit(branch.getObjectId());
                        while (current != null) {
                            trunkCommits.add(current.getId());
                            commitToBranch.put(current.getId(), branchName);
                            walk.parseHeaders(current);
                            if (current.getParentCount() > 0) {
                                current = walk.parseCommit(current.getParent(0).getId());
                            } else {
                                current = null;
                            }
                        }
                    } catch (Exception e) {}
                }
            }

            // Step 2: Assign ALL commits to branches, using putIfAbsent.
            // Feature branches should be processed FIRST so they claim their unique commits.
            // Feature branches are those NOT in trunkCommits (conceptually).
            // Sort branches so feature branches are first.
            branches.sort((b1, b2) -> {
                String n1 = b1.getName();
                String n2 = b2.getName();
                
                boolean isMain1 = n1.contains("main") || n1.contains("master") || n1.contains("develop");
                boolean isMain2 = n2.contains("main") || n2.contains("master") || n2.contains("develop");
                
                if (isMain1 && !isMain2) return 1;
                if (!isMain1 && isMain2) return -1;
                return n1.compareTo(n2);
            });

            for (org.eclipse.jgit.lib.Ref branch : branches) {
                String fullBranchName = branch.getName();
                
                // Skip literal HEAD pointers
                if (fullBranchName.equals("HEAD") || fullBranchName.equals("refs/remotes/origin/HEAD")) {
                    continue;
                }

                String branchName = repository.shortenRemoteBranchName(fullBranchName);
                if (branchName != null && (branchName.equals(fullBranchName) || branchName.startsWith("refs/"))) {
                    if (fullBranchName.startsWith("refs/heads/")) branchName = fullBranchName.substring(11);
                    else if (fullBranchName.startsWith("refs/remotes/")) branchName = fullBranchName.substring(13);
                }
                if (branchName == null) branchName = fullBranchName;
                
                if (branchName.startsWith("refs/heads/")) branchName = branchName.substring(11);
                if (branchName.startsWith("refs/remotes/")) branchName = branchName.substring(13);

                Iterable<RevCommit> bCommits = git.log().add(branch.getObjectId()).call();
                for (RevCommit bCommit : bCommits) {
                    if (bCommit != null && bCommit.getId() != null) {
                        commitToBranch.putIfAbsent(bCommit.getId(), branchName);
                    }
                }
            }

            for (RevCommit commit : commits) {
                Map<String, Integer> languages = new HashMap<>();
                int linesAdded = 0;
                int linesDeleted = 0;
                int fAdded = 0;
                int fEdited = 0;
                int fDeleted = 0;
                
                String authorEmail = commit.getAuthorIdent().getEmailAddress();
                String authorName = commit.getAuthorIdent().getName();
                String targetName = aliases != null ? aliases.getOrDefault(authorEmail, authorName) : authorName;

                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    
                    List<DiffEntry> diffs;
                    if (commit.getParentCount() > 1) {
                        // For merge commits, we only want to show what changed in the merge itself (e.g. conflicts)
                        // This is achieved by comparing against all parents (combined diff) 
                        // but a simple way to show just the merge-specific changes is harder in JGit.
                        // Standard practice for 'what's in the merge' is comparing against FIRST parent,
                        // which shows everything from the merged branch.
                        // If we want to include branch commits as regular commits, 
                        // then we should probably let the merge commit only show conflict resolutions if possible.
                        // For now, comparing to parent(0) is what we had, which shows all branch changes.
                        diffs = df.scan(parent.getTree(), commit.getTree());
                    } else {
                        diffs = parent != null ? df.scan(parent.getTree(), commit.getTree()) : df.scan(null, commit.getTree());
                    }
                    
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
                        targetName,
                        commit.getShortMessage(),
                        LocalDateTime.ofInstant(commit.getAuthorIdent().getWhenAsInstant(), ZoneId.systemDefault()),
                        languages,
                        aiProb,
                        fAdded,
                        fEdited,
                        fDeleted,
                        linesAdded,
                        linesDeleted,
                        commit.getParentCount() > 1,
                        commitToBranch.getOrDefault(commit.getId(), "unknown")
                ));
            }
            return result;
        }
    }

    public Map<String, List<FileChange>> getTopFilesPerContributor(File repoPath, int limitPerContributor, Map<String, String> aliases) throws Exception {
        try (Git git = Git.open(repoPath)) {
            Repository repository = git.getRepository();
            Iterable<RevCommit> commits = git.log().all().call();
            Map<String, Map<String, FileChange>> contributorFileChanges = new HashMap<>();
            Set<String> processedCommits = new HashSet<>();

            for (RevCommit commit : commits) {
                if (!processedCommits.add(commit.getName())) {
                    continue;
                }
                if (commit.getParentCount() > 1) {
                    continue; // Skip merge commits for file attribution
                }

                String authorEmail = commit.getAuthorIdent().getEmailAddress();
                String authorName = commit.getAuthorIdent().getName();
                String targetName = aliases != null ? aliases.getOrDefault(authorEmail, authorName) : authorName;

                Map<String, FileChange> fileMap = contributorFileChanges.computeIfAbsent(targetName, k -> new HashMap<>());

                RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    List<DiffEntry> diffs = parent != null ? df.scan(parent.getTree(), commit.getTree()) : df.scan(null, commit.getTree());

                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath();
                        int ins = 0;
                        int del = 0;
                        for (Edit edit : df.toFileHeader(entry).toEditList()) {
                            ins += edit.getEndB() - edit.getBeginB();
                            del += edit.getEndA() - edit.getBeginA();
                        }

                        FileChange existing = fileMap.get(path);
                        if (existing == null) {
                            fileMap.put(path, new FileChange(path, ins, del, categorizePath(path), entry.getChangeType().name()));
                        } else {
                            fileMap.put(path, new FileChange(path, existing.insertions() + ins, existing.deletions() + del, existing.category(), existing.changeType()));
                        }
                    }
                }
            }

            Map<String, List<FileChange>> result = new HashMap<>();
            contributorFileChanges.forEach((contributor, fileMap) -> {
                List<FileChange> topFiles = fileMap.values().stream()
                        .sorted(Comparator.comparingInt(FileChange::getTotalChange).reversed())
                        .limit(limitPerContributor)
                        .toList();
                result.put(contributor, topFiles);
            });
            return result;
        }
    }

    public CommitInfo getInitialCommit(File repoPath, Map<String, String> aliases) throws Exception {
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

                String authorEmail = initial.getAuthorIdent().getEmailAddress();
                String authorName = initial.getAuthorIdent().getName();
                String targetName = aliases != null ? aliases.getOrDefault(authorEmail, authorName) : authorName;

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
                        targetName,
                        initial.getShortMessage(),
                        LocalDateTime.ofInstant(initial.getAuthorIdent().getWhenAsInstant(), ZoneId.systemDefault()),
                        languages,
                        aiProb,
                        fAdded,
                        fEdited,
                        fDeleted,
                        linesAdded,
                        linesDeleted,
                        initial.getParentCount() > 1,
                        "main"
                );
            }
            return null;
        }
    }

    private static class StatsBuilder {
        String name;
        String email;
        String gender;
        int commitCount;
        int mergeCount;
        int linesAdded;
        int linesDeleted;
        int blankLinesAdded;
        int blankLinesDeleted;
        int meaninglessCommits;
        Map<String, Integer> languageBreakdown = new HashMap<>();
        double totalAiProbability;
        int filesAdded;
        int filesEdited;
        int filesDeleted;
        boolean touchedTests;

        StatsBuilder(String name, String email, String gender) {
            this.name = name;
            this.email = email;
            this.gender = gender;
        }

        ContributorStats build(double meaningfulChangeScore) {
            return new ContributorStats(name, email, gender, commitCount, mergeCount, linesAdded, linesDeleted, languageBreakdown, totalAiProbability / (commitCount + mergeCount > 0 ? commitCount + mergeCount : 1), filesAdded, filesEdited, filesDeleted, meaningfulChangeScore, touchedTests);
        }
    }
}
