package dev.grahamhill.service;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.FileChange;
import dev.grahamhill.model.MeaningfulChangeAnalysis;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class LlmService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    public String callLlmApi(String apiUrl, String apiKey, String model, String system, String user) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));
        body.put("messages", messages);
        body.put("temperature", 0.1);

        String jsonBody = serializeMapToJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMinutes(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode() + " - " + response.body());
        }

        return parseLlmResponse(response.body());
    }

    private String serializeMapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        map.forEach((k, v) -> {
            json.append("\"").append(k).append("\":");
            if (v instanceof String) {
                json.append("\"").append(escapeJson((String) v)).append("\",");
            } else if (v instanceof List) {
                json.append("[");
                List<Map<String, String>> list = (List<Map<String, String>>) v;
                for (Map<String, String> m : list) {
                    json.append("{");
                    m.forEach((mk, mv) -> {
                        json.append("\"").append(mk).append("\":\"").append(escapeJson(mv)).append("\",");
                    });
                    if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
                    json.append("},");
                }
                if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
                json.append("],");
            } else {
                json.append(v).append(",");
            }
        });
        if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseLlmResponse(String responseBody) {
        // Simple manual parsing to avoid heavy dependencies
        if (responseBody.contains("\"content\":\"")) {
            int start = responseBody.indexOf("\"content\":\"") + 11;
            int end = responseBody.lastIndexOf("\"");
            // Need to find the end of the content string correctly handling escaped quotes
            // This is a simplified version
            String content = responseBody.substring(start, responseBody.length());
            // Find the ending quote of the content field
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (escaped) {
                    switch (c) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '\\': sb.append('\\'); break;
                        case '\"': sb.append('\"'); break;
                        case 'u':
                            if (i + 4 < content.length()) {
                                String hex = content.substring(i + 1, i + 5);
                                try {
                                    sb.append((char) Integer.parseInt(hex, 16));
                                    i += 4;
                                } catch (NumberFormatException e) {
                                    sb.append('u');
                                }
                            } else {
                                sb.append('u');
                            }
                            break;
                        default: sb.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '\"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return responseBody;
    }

    public String buildMetricsText(
            File repoDir,
            List<ContributorStats> stats,
            MeaningfulChangeAnalysis meaningfulAnalysis,
            List<CommitInfo> allCommits,
            Map<String, List<FileChange>> contributorFiles,
            String projectStructure,
            String requiredFeatures,
            Map<String, String> emailOverrides) {
        return buildMetricsText(repoDir, stats, meaningfulAnalysis, allCommits, contributorFiles, projectStructure, requiredFeatures, emailOverrides, false, 0);
    }

    public String buildMetricsText(
            File repoDir,
            List<ContributorStats> stats,
            MeaningfulChangeAnalysis meaningfulAnalysis,
            List<CommitInfo> allCommits,
            Map<String, List<FileChange>> contributorFiles,
            String projectStructure,
            String requiredFeatures,
            Map<String, String> emailOverrides,
            boolean includeDiffs) {
        return buildMetricsText(repoDir, stats, meaningfulAnalysis, allCommits, contributorFiles, projectStructure, requiredFeatures, emailOverrides, includeDiffs, 0);
    }

    public String buildMetricsText(
            File repoDir,
            List<ContributorStats> stats,
            MeaningfulChangeAnalysis meaningfulAnalysis,
            List<CommitInfo> allCommits,
            Map<String, List<FileChange>> contributorFiles,
            String projectStructure,
            String requiredFeatures,
            Map<String, String> emailOverrides,
            boolean includeDiffs,
            int commitLimit) {
        
        StringBuilder metricsText = new StringBuilder("METRICS:\n");
        metricsText.append(projectStructure).append("\n");

        for (ContributorStats s : stats) {
            boolean hasTests = s.languageBreakdown().containsKey("test") || 
                              s.languageBreakdown().keySet().stream().anyMatch(l -> l.toLowerCase().contains("test"));
            String name = s.name();
            if (name.contains("<") && name.contains(">")) {
                name = name.substring(0, name.indexOf("<")).trim();
            }
            String email = emailOverrides.getOrDefault(s.name(), s.email());
            metricsText.append(String.format("- %s (%s, %s):\n", name, email, s.gender()));
            metricsText.append(String.format("  Stats: %d commits, %d merges, +%d/-%d lines, %d new/%d edited/%d deleted files\n",
                s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), 
                s.filesAdded(), s.filesEdited(), s.filesDeletedCount()));
            metricsText.append(String.format("  Risk Profile: AI Probability %.1f%%, Meaningful Score %.1f/100, Generated Files Pushed: %d, Documentation Lines Added: %d%s\n",
                s.averageAiProbability() * 100, s.meaningfulChangeScore(), s.generatedFilesPushed(), s.documentationLinesAdded(), hasTests ? " [INCLUDES TESTS]" : ""));
            double linesPerCommit = (double) s.linesAdded() / (s.commitCount() > 0 ? s.commitCount() : 1);
            metricsText.append(String.format("  Average Lines Added per Commit: %.1f\n", linesPerCommit));
            metricsText.append("  Language breakdown: ").append(s.languageBreakdown()).append("\n");
        }

        metricsText.append("\nREPOSITORY SUMMARY METRICS:\n");
        if (meaningfulAnalysis != null) {
            metricsText.append(String.format("Total Range: %s\n", meaningfulAnalysis.commitRange()));
            metricsText.append(String.format("Total Insertions: %d, Total Deletions: %d, Whitespace Churn: %d\n",
                meaningfulAnalysis.totalInsertions(), meaningfulAnalysis.totalDeletions(), 
                meaningfulAnalysis.whitespaceChurn()));
            
            metricsText.append("Category Breakdown:\n");
            meaningfulAnalysis.categoryBreakdown().forEach((cat, m) -> {
                if (m.fileCount() > 0) {
                    metricsText.append(String.format("  * %s: %d files, +%d/-%d lines\n", cat, m.fileCount(), m.insertions(), m.deletions()));
                }
            });
            
            if (!meaningfulAnalysis.warnings().isEmpty()) {
                metricsText.append("Structural Observations: ").append(String.join("; ", meaningfulAnalysis.warnings())).append("\n");
            }

            metricsText.append("Top 50 Impactful Files:\n");
            meaningfulAnalysis.topChangedFiles().stream().limit(50).forEach(f -> {
                metricsText.append(String.format("  * %s (+%d/-%d) [%s] Type: %s, Creator: %s\n", f.path(), f.insertions(), f.deletions(), f.category(), f.changeType(), f.creator()));
            });
        }

        if (allCommits != null) {
            int actualLimit = commitLimit > 0 ? Math.min(allCommits.size(), commitLimit) : allCommits.size();
            metricsText.append(String.format("\nCOMMIT HISTORY (LATEST %d COMMITS, INCLUDING MERGED BRANCHES, LATEST FIRST):\n", actualLimit));
            for (int i = 0; i < actualLimit; i++) {
                CommitInfo ci = allCommits.get(i);
                String mergeMarker = ci.isMerge() ? " [MERGE]" : "";
                metricsText.append(String.format("[%s]%s %s <%s> [%s]: %s (%s) +%d/-%d l, %d n/%d e/%d d f, AI: %.0f%%\n",
                    ci.id(), mergeMarker, ci.authorName(), ci.branch(), ci.timestamp().toString(), ci.message(), formatLanguages(ci.languageBreakdown()),
                    ci.linesAdded(), ci.linesDeleted(), ci.filesAdded(), ci.filesEdited(), ci.filesDeleted(),
                    ci.aiProbability() * 100));
            }
        }

        if (contributorFiles != null) {
            metricsText.append("\nCONTRIBUTOR TOP FILES (Impactful Files per Contributor):\n");
            contributorFiles.forEach((contributor, files) -> {
                metricsText.append(String.format("Contributor: %s\n", contributor));
                files.forEach(f -> {
                    metricsText.append(String.format("  * %s (+%d/-%d) [%s] Type: %s, Creator: %s\n", f.path(), f.insertions(), f.deletions(), f.category(), f.changeType(), f.creator()));
                    if (includeDiffs && f.diff() != null && !f.diff().isEmpty()) {
                        String diffContent = f.diff();
                        // Additional safety truncation for individual file diffs in the metrics text
                        if (diffContent.length() > 2000) {
                            diffContent = diffContent.substring(0, 2000) + "... [diff truncated in metrics]";
                        }
                        metricsText.append("    DIFF:\n").append(diffContent.indent(6)).append("\n");
                    }
                });
            });
        }

        metricsText.append("\nRISK RULES: CALCULATE 'Lines Added/Commit' = (Total Lines Added / Total Commits).\n");
        metricsText.append("Scale: 1500+ VERY HIGH, 1000-1500 HIGH, 750-1000 MED-HIGH, 500-750 MED, 250-500 LOW-MED, <250 LOW.\n");
        metricsText.append("Higher = Higher Risk. Secondary risk factors: High churn, low test coverage, high AI probability.\n");
        metricsText.append("Don't subtract deletions.\n");

        if (requiredFeatures != null && !requiredFeatures.isEmpty()) {
            metricsText.append("\nFeatures:\n").append(requiredFeatures).append("\n");
        }

        return metricsText.toString();
    }

    private String formatLanguages(Map<String, Integer> languages) {
        if (languages == null || languages.isEmpty()) return "N/A";
        return languages.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public String demoteMarkdownHeaders(String content) {
        if (content == null) return "";
        // Replace headers like # Header with ## Header
        // But only if they are not already demoted
        return content.lines()
                .map(line -> {
                    if (line.startsWith("#")) {
                        return "#" + line;
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));
    }

    public String formatSectionTitle(String title) {
        if (title == null || title.isEmpty()) return "";
        String[] parts = title.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.matches("\\d+")) continue; // Skip numbers like "01"
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
