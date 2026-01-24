package dev.grahamhill.service;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import javafx.collections.FXCollections;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import java.util.*;
import java.util.stream.Collectors;

public class ChartManager {

    private void updateActivityLineChart(LineChart<String, Number> activityLineChart, List<CommitInfo> chronological) {
        activityLineChart.setAnimated(false);
        if (chronological != null && !chronological.isEmpty()) {
            XYChart.Series<String, Number> activitySeries = new XYChart.Series<>();
            activitySeries.setName("Lines Added");
            for (CommitInfo ci : chronological) {
                activitySeries.getData().add(new XYChart.Data<>(ci.id().substring(0, 7), ci.linesAdded()));
            }
            activityLineChart.setData(FXCollections.observableArrayList(activitySeries));
        } else {
            activityLineChart.setData(FXCollections.observableArrayList());
        }
    }

    private void updateCalendarActivityChart(LineChart<String, Number> calendarActivityChart, List<CommitInfo> commits) {
        calendarActivityChart.setAnimated(false);
        if (commits != null && !commits.isEmpty()) {
            XYChart.Series<String, Number> calSeries = new XYChart.Series<>();
            calSeries.setName("Daily Impact");
            TreeMap<java.time.LocalDate, Integer> dailyImpact = new TreeMap<>();
            for (CommitInfo ci : commits) {
                if (ci.isMerge()) continue;
                java.time.LocalDate date = ci.timestamp().toLocalDate();
                dailyImpact.merge(date, ci.linesAdded(), Integer::sum);
            }
            if (!dailyImpact.isEmpty()) {
                java.time.LocalDate firstDate = dailyImpact.firstKey();
                java.time.LocalDate lastDate = dailyImpact.lastKey();
                java.time.LocalDate current = firstDate;
                while (!current.isAfter(lastDate)) {
                    dailyImpact.putIfAbsent(current, 0);
                    current = current.plusDays(1);
                }
            }
            dailyImpact.forEach((date, impact) -> calSeries.getData().add(new XYChart.Data<>(date.toString(), impact)));
            calendarActivityChart.setData(FXCollections.observableArrayList(calSeries));
        } else {
            calendarActivityChart.setData(FXCollections.observableArrayList());
        }
    }

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "svg", "ico", "git", "exe", "dll", "so", "dylib", "bin", "zip", "tar", "gz", "7z", "rar",
            "map", "min.js", "min.css", "lock", "lockfiles", "generated", "artifacts"
    );

    private static final Set<String> DOCUMENTATION_EXTENSIONS = Set.of(
            "md", "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
    );

    private Map<String, Integer> processLanguageBreakdown(Map<String, Integer> raw) {
        Map<String, Integer> processed = new HashMap<>();
        if (raw == null) return processed;
        raw.forEach((ext, count) -> {
            String lower = ext.toLowerCase();
            if (IGNORED_EXTENSIONS.contains(lower)) {
                return;
            }
            if (DOCUMENTATION_EXTENSIONS.contains(lower)) {
                processed.merge("Documentation", count, Integer::sum);
            } else {
                processed.merge(ext, count, Integer::sum);
            }
        });
        return processed;
    }

    public void updateCharts(PieChart commitPieChart, PieChart languagePieChart, PieChart contribLanguagePieChart,
                             LineChart<String, Number> commitsPerDayLineChart,
                             StackedBarChart<String, Number> impactBarChart, 
                             LineChart<String, Number> activityLineChart, LineChart<String, Number> calendarActivityChart, 
                             LineChart<String, Number> contributorActivityChart,
                             List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        
        // Limited to Top 5 for visuals
        List<ContributorStats> top5 = stats.stream().limit(5).collect(Collectors.toList());

        languagePieChart.setAnimated(false);
        Map<String, Integer> overallLangs = new HashMap<>();
        for (ContributorStats s : stats) {
            Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
            processed.forEach((lang, count) -> overallLangs.merge(lang, count, Integer::sum));
        }
        int totalLangFiles = (int) overallLangs.values().stream().mapToLong(Integer::intValue).sum();
        if (totalLangFiles == 0) {
            languagePieChart.setData(FXCollections.observableArrayList());
        } else {
            List<PieChart.Data> langPieData = overallLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(e -> {
                        double percentage = (totalLangFiles > 0) ? (double)e.getValue() / totalLangFiles * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
            
            // To prevent NPE in JavaFX PieChart layout:
            // 1. Disable animations
            languagePieChart.setAnimated(false);
            // 2. Set new data as a fresh list
            languagePieChart.setData(FXCollections.observableArrayList(langPieData));
            // 3. Force layout on the thread (this method should be called on FX thread)
            languagePieChart.layout();
            
            languagePieChart.setLabelsVisible(true);
        }

        // Languages by Contributor
        contribLanguagePieChart.setAnimated(false);
        Map<String, Integer> contribLangs = new HashMap<>();
        for (ContributorStats s : stats) {
            Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
            if (!processed.isEmpty()) {
                // Find primary language for this contributor
                String primaryLang = processed.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("Unknown");
                contribLangs.merge(primaryLang, 1, Integer::sum);
            } else {
                contribLangs.merge("Unknown", 1, Integer::sum);
            }
        }
        int totalLangContributions = (int) contribLangs.values().stream().mapToLong(Integer::intValue).sum();
        if (totalLangContributions == 0) {
            contribLanguagePieChart.setData(FXCollections.observableArrayList());
        } else {
            List<PieChart.Data> contribLangPieData = contribLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .map(e -> {
                        double percentage = (totalLangContributions > 0) ? (double)e.getValue() / totalLangContributions * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
            
            contribLanguagePieChart.setAnimated(false);
            contribLanguagePieChart.setData(FXCollections.observableArrayList(contribLangPieData));
            contribLanguagePieChart.layout();
            
            contribLanguagePieChart.setLabelsVisible(true);
        }

        // Commits per Day (Overall)
        commitsPerDayLineChart.setAnimated(false);
        if (recentCommits != null && !recentCommits.isEmpty()) {
            TreeMap<java.time.LocalDate, Integer> dailyCommits = new TreeMap<>();
            for (CommitInfo ci : recentCommits) {
                dailyCommits.merge(ci.timestamp().toLocalDate(), 1, Integer::sum);
            }
            
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Commits");
            
            // Fill gaps if any
            java.time.LocalDate firstDate = dailyCommits.firstKey();
            java.time.LocalDate lastDate = dailyCommits.lastKey();
            java.time.LocalDate current = firstDate;
            while (!current.isAfter(lastDate)) {
                series.getData().add(new XYChart.Data<>(current.toString(), dailyCommits.getOrDefault(current, 0)));
                current = current.plusDays(1);
            }
            commitsPerDayLineChart.setData(FXCollections.observableArrayList(series));
            // Apply line thickness after adding to chart so the node is created
            if (series.getNode() != null) {
                series.getNode().setStyle("-fx-stroke-width: 4px;");
            } else {
                // If node is null (e.g. animation or not yet rendered), try to set it when it becomes available
                series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        newNode.setStyle("-fx-stroke-width: 4px;");
                    }
                });
            }
        } else {
            commitsPerDayLineChart.setData(FXCollections.observableArrayList());
        }

        commitPieChart.setAnimated(false);
        commitPieChart.setMinWidth(1080);
        commitPieChart.setMaxWidth(1080);
        commitPieChart.setPrefWidth(1080);
        commitPieChart.setMinHeight(1080);
        commitPieChart.setMaxHeight(1080);
        commitPieChart.setPrefHeight(1080);
        commitPieChart.setLabelsVisible(true);
        commitPieChart.setLabelLineLength(25);
        commitPieChart.setStartAngle(0);
        commitPieChart.setClockwise(true);
        commitPieChart.requestLayout();
        commitPieChart.setLabelsVisible(true);
        
        int totalCommits = stats.stream().mapToInt(ContributorStats::commitCount).sum();
        List<PieChart.Data> pieData = stats.stream()
                .limit(10)
                .map(s -> {
                    double percentage = (totalCommits > 0) ? (double)s.commitCount() / totalCommits * 100 : 0;
                    String displayName = sanitizeName(s.name());
                    return new PieChart.Data(String.format("%s (%.1f%%)", displayName, percentage), (double) s.commitCount());
                })
                .toList();
        
        commitPieChart.setAnimated(false);
        commitPieChart.setData(FXCollections.observableArrayList(pieData));
        commitPieChart.layout();
        
        commitPieChart.setLegendVisible(true);
        commitPieChart.setLegendSide(javafx.geometry.Side.BOTTOM);

        // Stacked Bar Chart for Impact
        impactBarChart.setAnimated(false);
        XYChart.Series<String, Number> addedSeries = new XYChart.Series<>();
        addedSeries.setName("Added");
        XYChart.Series<String, Number> deletedSeries = new XYChart.Series<>();
        deletedSeries.setName("Deleted");

        for (ContributorStats s : top5) {
            String displayName = sanitizeName(s.name());
            XYChart.Data<String, Number> addedData = new XYChart.Data<>(displayName, Math.max(0, s.linesAdded()));
            XYChart.Data<String, Number> deletedData = new XYChart.Data<>(displayName, Math.max(0, s.linesDeleted()));
            addedSeries.getData().add(addedData);
            deletedSeries.getData().add(deletedData);
        }
        impactBarChart.setData(FXCollections.observableArrayList(addedSeries, deletedSeries));

        // Activity Line Chart
        if (recentCommits != null && !recentCommits.isEmpty()) {
            List<CommitInfo> chronological = recentCommits.stream()
                    .filter(ci -> !ci.isMerge())
                    .collect(Collectors.toList());
            Collections.reverse(chronological);
            updateActivityLineChart(activityLineChart, chronological);
            updateCalendarActivityChart(calendarActivityChart, chronological);
        } else {
            activityLineChart.setData(FXCollections.observableArrayList());
            calendarActivityChart.setData(FXCollections.observableArrayList());
        }

        updateContributorActivityChart(contributorActivityChart, stats, recentCommits);
        updateCpdPerContributorChart(commitsPerDayLineChart, stats, recentCommits);
    }

    public void updateContributorActivityChart(LineChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.setAnimated(false);

        if (recentCommits == null || recentCommits.isEmpty()) {
            chart.setData(FXCollections.observableArrayList());
            return;
        }

        List<XYChart.Series<String, Number>> allSeries = new ArrayList<>();
        // Group lines added by date and author
        Map<String, TreeMap<java.time.LocalDate, Integer>> contributorDailyLines = new HashMap<>();
        TreeMap<java.time.LocalDate, Integer> totalDailyLines = new TreeMap<>();
        
        // Find date range
        java.time.LocalDate firstDate = null;
        java.time.LocalDate lastDate = null;

        for (CommitInfo ci : recentCommits) {
            if (ci.isMerge()) continue;
            java.time.LocalDate date = ci.timestamp().toLocalDate();
            if (firstDate == null || date.isBefore(firstDate)) firstDate = date;
            if (lastDate == null || date.isAfter(lastDate)) lastDate = date;

            String authorName = sanitizeName(ci.authorName());
            contributorDailyLines.computeIfAbsent(authorName, k -> new TreeMap<>())
                                 .merge(date, ci.linesAdded(), Integer::sum);
            totalDailyLines.merge(date, ci.linesAdded(), Integer::sum);
        }

        if (firstDate == null) {
            chart.setData(FXCollections.observableArrayList());
            return;
        }

        // Total Series
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total");
        
        java.time.LocalDate current = firstDate;
        while (!current.isAfter(lastDate)) {
            int lines = totalDailyLines.getOrDefault(current, 0);
            totalSeries.getData().add(new XYChart.Data<>(current.toString(), lines));
            current = current.plusDays(1);
        }
        allSeries.add(totalSeries);

        // Fill gaps with zeros and create series for top contributors
        if (stats != null) {
            List<String> topContributors = stats.stream()
                    .limit(5)
                    .map(s -> sanitizeName(s.name()))
                    .collect(Collectors.toList());

            for (String author : topContributors) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(author);
                
                TreeMap<java.time.LocalDate, Integer> dailyLines = contributorDailyLines.getOrDefault(author, new TreeMap<>());
                
                current = firstDate;
                while (!current.isAfter(lastDate)) {
                    int lines = dailyLines.getOrDefault(current, 0);
                    series.getData().add(new XYChart.Data<>(current.toString(), lines));
                    current = current.plusDays(1);
                }
                allSeries.add(series);
            }
        }
        chart.setData(FXCollections.observableArrayList(allSeries));
    }

    public void updateCpdPerContributorChart(LineChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.setAnimated(false);

        if (recentCommits == null || recentCommits.isEmpty()) {
            chart.setData(FXCollections.observableArrayList());
            return;
        }

        List<XYChart.Series<String, Number>> allSeries = new ArrayList<>();
        // Group commits by date and author
        Map<String, TreeMap<java.time.LocalDate, Integer>> contributorDailyCommits = new HashMap<>();
        TreeMap<java.time.LocalDate, Integer> totalDailyCommits = new TreeMap<>();
        
        // Find date range
        java.time.LocalDate firstDate = null;
        java.time.LocalDate lastDate = null;

        for (CommitInfo ci : recentCommits) {
            java.time.LocalDate date = ci.timestamp().toLocalDate();
            if (firstDate == null || date.isBefore(firstDate)) firstDate = date;
            if (lastDate == null || date.isAfter(lastDate)) lastDate = date;

            String authorName = sanitizeName(ci.authorName());
            contributorDailyCommits.computeIfAbsent(authorName, k -> new TreeMap<>())
                                   .merge(date, 1, Integer::sum);
            totalDailyCommits.merge(date, 1, Integer::sum);
        }

        if (firstDate == null) {
            chart.setData(FXCollections.observableArrayList());
            return;
        }

        // Total Series
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total");
        
        java.time.LocalDate current = firstDate;
        while (!current.isAfter(lastDate)) {
            int count = totalDailyCommits.getOrDefault(current, 0);
            totalSeries.getData().add(new XYChart.Data<>(current.toString(), count));
            current = current.plusDays(1);
        }
        allSeries.add(totalSeries);

        // Fill gaps with zeros and create series for top contributors
        if (stats != null) {
            List<String> topContributors = stats.stream()
                    .limit(5)
                    .map(s -> sanitizeName(s.name()))
                    .collect(Collectors.toList());

            for (String author : topContributors) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(author);
                
                TreeMap<java.time.LocalDate, Integer> dailyCommits = contributorDailyCommits.getOrDefault(author, new TreeMap<>());
                
                current = firstDate;
                while (!current.isAfter(lastDate)) {
                    int count = dailyCommits.getOrDefault(current, 0);
                    series.getData().add(new XYChart.Data<>(current.toString(), count));
                    current = current.plusDays(1);
                }
                allSeries.add(series);
            }
        }
        chart.setData(FXCollections.observableArrayList(allSeries));
    }

    public void updateCompanyCharts(PieChart commitPieChart, PieChart languagePieChart, StackedBarChart<String, Number> impactBarChart, PieChart devPieChart, 
                                   PieChart projectLangPieChart, PieChart contribLangPieChart,
                                   LineChart<String, Number> activityLineChart, LineChart<String, Number> calendarActivityChart, 
                                   LineChart<String, Number> contributorActivityChart, LineChart<String, Number> commitsPerDayLineChart,
                                   List<dev.grahamhill.model.CompanyMetric> metrics, List<ContributorStats> allContributors, List<CommitInfo> allCommits) {
        // Commits by Repo
        commitPieChart.setAnimated(false);
        int totalCommits = (int) metrics.stream().mapToLong(dev.grahamhill.model.CompanyMetric::totalCommits).sum();
        List<PieChart.Data> commitData = metrics.stream()
                .map(m -> {
                    double percentage = (totalCommits > 0) ? (double) m.totalCommits() / totalCommits * 100 : 0;
                    String repoName = new java.io.File(m.repoName()).getName();
                    return new PieChart.Data(String.format("%s (%.1f%%)", repoName, percentage), (double) m.totalCommits());
                })
                .collect(Collectors.toList());
        
        commitPieChart.setAnimated(false);
        commitPieChart.setData(FXCollections.observableArrayList(commitData));
        commitPieChart.layout();
        
        commitPieChart.setLabelsVisible(true);

        // Language Breakdown (Company)
        languagePieChart.setAnimated(false);
        Map<String, Integer> companyOverallLangs = new HashMap<>();
        metrics.forEach(m -> {
            if (m.languageBreakdown() != null) {
                m.languageBreakdown().forEach((k, v) -> companyOverallLangs.merge(k, v, Integer::sum));
            }
        });
        int totalCompanyLangFiles = (int) companyOverallLangs.values().stream().mapToLong(Integer::intValue).sum();
        if (totalCompanyLangFiles == 0) {
            languagePieChart.setData(FXCollections.observableArrayList());
        } else {
            List<PieChart.Data> companyLangData = companyOverallLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(e -> {
                        double percentage = (totalCompanyLangFiles > 0) ? (double) e.getValue() / totalCompanyLangFiles * 100 : 0;
                        PieChart.Data data = new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                        return data;
                    })
                    .collect(Collectors.toList());
            
            languagePieChart.setAnimated(false);
            languagePieChart.setData(FXCollections.observableArrayList(companyLangData));
            languagePieChart.layout();
            
            languagePieChart.setLabelsVisible(true);
        }

        // Impact Bar Chart
        impactBarChart.setAnimated(false);
        XYChart.Series<String, Number> addedSeries = new XYChart.Series<>();
        addedSeries.setName("Added");
        XYChart.Series<String, Number> deletedSeries = new XYChart.Series<>();
        deletedSeries.setName("Deleted");

        for (dev.grahamhill.model.CompanyMetric m : metrics) {
            String repoName = new java.io.File(m.repoName()).getName();
            addedSeries.getData().add(new XYChart.Data<>(repoName, Math.max(0, m.totalLinesAdded())));
            deletedSeries.getData().add(new XYChart.Data<>(repoName, Math.max(0, m.totalLinesDeleted())));
        }
        impactBarChart.setData(FXCollections.observableArrayList(addedSeries, deletedSeries));

        // Code by Developer (Company)
        if (devPieChart != null && allContributors != null) {
            devPieChart.setAnimated(false);
            Map<String, Integer> devCommits = new HashMap<>();
            for (ContributorStats s : allContributors) {
                devCommits.merge(s.name(), s.commitCount(), Integer::sum);
            }
            int totalDevCommits = (int) devCommits.values().stream().mapToLong(Integer::intValue).sum();
            List<PieChart.Data> devData = devCommits.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(15)
                    .map(e -> {
                        double percentage = (totalDevCommits > 0) ? (double) e.getValue() / totalDevCommits * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", sanitizeName(e.getKey()), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
            
            devPieChart.setAnimated(false);
            devPieChart.setData(FXCollections.observableArrayList(devData));
            devPieChart.layout();
            
            devPieChart.setLabelsVisible(true);
        }

        // Language of Projects
        if (projectLangPieChart != null) {
            projectLangPieChart.setAnimated(false);
            Map<String, Integer> projLangs = new HashMap<>();
            metrics.forEach(m -> {
                String lang = m.primaryLanguage();
                if (lang == null || lang.equals("N/A") || lang.equals("Unknown")) {
                    // Try to pick from breakdown if primary is missing
                    if (m.languageBreakdown() != null && !m.languageBreakdown().isEmpty()) {
                        lang = m.languageBreakdown().entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse("Unknown");
                    } else {
                        lang = "Unknown";
                    }
                }
                projLangs.merge(lang, 1, Integer::sum);
            });
            int totalProjs = metrics.size();
            List<PieChart.Data> projLangData = projLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .map(e -> {
                        double percentage = (totalProjs > 0) ? (double) e.getValue() / totalProjs * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
            
            projectLangPieChart.setAnimated(false);
            projectLangPieChart.setData(FXCollections.observableArrayList(projLangData));
            projectLangPieChart.layout();
            
            projectLangPieChart.setLabelsVisible(true);
        }

        // Languages by Contributor (Company)
        if (contribLangPieChart != null && allContributors != null) {
            contribLangPieChart.setAnimated(false);
            Map<String, Integer> cLangs = new HashMap<>();
        
            // Re-aggregate contributors by name first
            Map<String, Map<String, Integer>> aggregatedContribLangs = new HashMap<>();
            for (ContributorStats s : allContributors) {
                Map<String, Integer> langs = aggregatedContribLangs.computeIfAbsent(s.name(), k -> new HashMap<>());
                if (s.languageBreakdown() != null) {
                    Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
                    processed.forEach((lang, count) -> langs.merge(lang, count, Integer::sum));
                }
            }

            for (Map<String, Integer> langs : aggregatedContribLangs.values()) {
                if (!langs.isEmpty()) {
                    // Find primary language for this contributor
                    String primaryLang = langs.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("Unknown");
                    cLangs.merge(primaryLang, 1, Integer::sum);
                } else {
                    cLangs.merge("Unknown", 1, Integer::sum);
                }
            }

            int totalC = (int) cLangs.values().stream().mapToLong(Integer::intValue).sum();
            if (totalC == 0) {
                contribLangPieChart.setData(FXCollections.observableArrayList());
            } else {
                List<PieChart.Data> cLangData = cLangs.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .map(e -> {
                            double percentage = (totalC > 0) ? (double) e.getValue() / totalC * 100 : 0;
                            return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                        })
                        .collect(Collectors.toList());
                
                contribLangPieChart.setAnimated(false);
                contribLangPieChart.setData(FXCollections.observableArrayList(cLangData));
                contribLangPieChart.layout();
                
                contribLangPieChart.setLabelsVisible(true);
            }
        }

        // Activity charts for company
        if (allCommits != null) {
            // Limited view of recent company commits
            List<CommitInfo> recent = allCommits.stream()
                    .sorted(Comparator.comparing(CommitInfo::timestamp).reversed())
                    .limit(100)
                    .collect(Collectors.toList());
            Collections.reverse(recent);

            updateActivityLineChart(activityLineChart, recent);
            updateCalendarActivityChart(calendarActivityChart, allCommits);
            updateContributorActivityChart(contributorActivityChart, null, allCommits);
            updateCpdPerContributorChart(commitsPerDayLineChart, null, allCommits);
        }
    }

    private String sanitizeName(String name) {
        if (name == null) return "Unknown";
        if (name.contains("<") && name.contains(">")) {
            return name.substring(0, name.indexOf("<")).trim();
        }
        return name;
    }

    private void addTooltips(Chart chart) {
        if (chart instanceof XYChart<?, ?> xyChart) {
            for (Object sObj : xyChart.getData()) {
                XYChart.Series<String, Number> s = (XYChart.Series<String, Number>) sObj;
                for (XYChart.Data<String, Number> d : s.getData()) {
                    if (d.getNode() != null) {
                        Tooltip.install(d.getNode(), new Tooltip(s.getName() + ": " + String.format("%.1f", d.getYValue().doubleValue())));
                    }
                }
            }
        }
    }
}
