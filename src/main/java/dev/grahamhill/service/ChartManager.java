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
        activityLineChart.setData(FXCollections.observableArrayList());
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
        calendarActivityChart.setData(FXCollections.observableArrayList());
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
            "map", "min.js", "min.css", "lock", "lockfiles", "generated", "artifacts", "gitkeep"
    );

    private static final Set<String> DOCUMENTATION_EXTENSIONS = Set.of(
            "md", "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
    );

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            "dev", "ym", "conf", "sh", "example", "yaml", "yml", "json", "xml", "ini", "properties", "toml"
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
            } else if (CONFIG_EXTENSIONS.contains(lower)) {
                processed.merge("Config", count, Integer::sum);
            } else {
                processed.merge(ext, count, Integer::sum);
            }
        });
        return processed;
    }

    private void safePieUpdate(PieChart chart, List<PieChart.Data> data) {
        if (chart == null) return;
        chart.setAnimated(false);
        if (data == null || data.isEmpty()) {
            chart.setData(FXCollections.observableArrayList());
            return;
        }
        chart.setData(FXCollections.observableArrayList(data));
        chart.setLabelsVisible(true);
        // Force labels to be visible for each data point
        for (PieChart.Data d : chart.getData()) {
            if (d.getNode() != null) {
                d.getNode().setVisible(true);
            }
        }
    }

    public void updateCharts(PieChart commitPieChart, PieChart languagePieChart, StackedBarChart<String, Number> contribLanguageBarChart,
                             LineChart<String, Number> commitsPerDayLineChart,
                             StackedBarChart<String, Number> impactBarChart, 
                             LineChart<String, Number> activityLineChart, LineChart<String, Number> calendarActivityChart, 
                             LineChart<String, Number> contributorActivityChart,
                             PieChart devPieChart, PieChart projectLangPieChart,
                             List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        
        // Limited to Top 5 for visuals
        List<ContributorStats> top5 = stats.stream().limit(5).collect(Collectors.toList());

        // Language Breakdown (Repo Mode)
        Map<String, Integer> overallLangs = new HashMap<>();
        for (ContributorStats s : stats) {
            Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
            processed.forEach((lang, count) -> overallLangs.merge(lang, count, Integer::sum));
        }
        int totalLangFiles = (int) overallLangs.values().stream().mapToLong(Integer::intValue).sum();
        List<PieChart.Data> langPieData = new ArrayList<>();
        if (totalLangFiles > 0) {
            langPieData = overallLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(e -> {
                        double percentage = (double)e.getValue() / totalLangFiles * 100;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
        }
        safePieUpdate(languagePieChart, langPieData);

        // Languages by Contributor (Stacked Bar Chart)
        contribLanguageBarChart.setAnimated(false);
        contribLanguageBarChart.setData(FXCollections.observableArrayList());
        if (stats != null && !stats.isEmpty()) {
            Map<String, XYChart.Series<String, Number>> seriesMap = new HashMap<>();
            
            // Limit to top 10 contributors for better visualization
            List<ContributorStats> topStats = stats.stream().limit(10).collect(Collectors.toList());
            
            for (ContributorStats s : topStats) {
                String displayName = sanitizeName(s.name());
                Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
                processed.forEach((lang, count) -> {
                    XYChart.Series<String, Number> series = seriesMap.computeIfAbsent(lang, k -> {
                        XYChart.Series<String, Number> ser = new XYChart.Series<>();
                        ser.setName(k);
                        return ser;
                    });
                    series.getData().add(new XYChart.Data<>(displayName, count));
                });
            }
            contribLanguageBarChart.setData(FXCollections.observableArrayList(new ArrayList<>(seriesMap.values())));
        }

        // Commits per Day (Overall)
        commitsPerDayLineChart.setAnimated(false);
        commitsPerDayLineChart.setData(FXCollections.observableArrayList());
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
        
        safePieUpdate(commitPieChart, pieData);
        
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
        impactBarChart.setData(FXCollections.observableArrayList());
        impactBarChart.setData(FXCollections.observableArrayList(addedSeries, deletedSeries));
        impactBarChart.layout();

        // Activity Line Chart
        if (recentCommits != null && !recentCommits.isEmpty()) {
            List<CommitInfo> chronological = recentCommits.stream()
                    .filter(ci -> !ci.isMerge())
                    .collect(Collectors.toList());
            Collections.reverse(chronological);
            updateActivityLineChart(activityLineChart, chronological);
            updateCalendarActivityChart(calendarActivityChart, chronological);
        } else {
            activityLineChart.setAnimated(false);
            activityLineChart.setData(FXCollections.observableArrayList());
            calendarActivityChart.setAnimated(false);
            calendarActivityChart.setData(FXCollections.observableArrayList());
        }

        updateContributorActivityChart(contributorActivityChart, stats, recentCommits);
        updateCpdPerContributorChart(commitsPerDayLineChart, stats, recentCommits);

        // Code by Developer (Repo Mode)
        if (devPieChart != null) {
            int totalCommitsCount = stats.stream().mapToInt(ContributorStats::commitCount).sum();
            List<PieChart.Data> devData = stats.stream()
                    .limit(10)
                    .map(s -> {
                        double percentage = (totalCommitsCount > 0) ? (double) s.commitCount() / totalCommitsCount * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", sanitizeName(s.name()), percentage), (double) s.commitCount());
                    })
                    .collect(Collectors.toList());
            safePieUpdate(devPieChart, devData);
        }

        // Language of Projects (Repo Mode - shows same as Language Breakdown but on this specific chart)
        if (projectLangPieChart != null) {
            int totalFiles = (int) overallLangs.values().stream().mapToLong(Integer::intValue).sum();
            List<PieChart.Data> projLangData = overallLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(e -> {
                        double percentage = (totalFiles > 0) ? (double) e.getValue() / totalFiles * 100 : 0;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
            safePieUpdate(projectLangPieChart, projLangData);
        }
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
        chart.setAnimated(false);
        chart.setData(FXCollections.observableArrayList());
        chart.setData(FXCollections.observableArrayList(allSeries));
        chart.layout();
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
        chart.setAnimated(false);
        chart.setData(FXCollections.observableArrayList());
        chart.setData(FXCollections.observableArrayList(allSeries));
        chart.layout();
    }

    public void updateCompanyCharts(PieChart commitPieChart, PieChart languagePieChart, StackedBarChart<String, Number> impactBarChart, PieChart devPieChart, 
                                   PieChart projectLangPieChart, StackedBarChart<String, Number> contribLangBarChart,
                                   LineChart<String, Number> activityLineChart, LineChart<String, Number> calendarActivityChart, 
                                   LineChart<String, Number> contributorActivityChart, LineChart<String, Number> commitsPerDayLineChart,
                                   javafx.scene.control.ListView<String> companyBreakdownList,
                                   List<dev.grahamhill.model.CompanyMetric> metrics, List<ContributorStats> allContributors, List<CommitInfo> allCommits) {
        
        // Populate scrollable breakdown list
        if (companyBreakdownList != null && metrics != null) {
            List<String> breakdownStrings = new ArrayList<>();
            int totalCompanyCommits = (int) metrics.stream().mapToLong(dev.grahamhill.model.CompanyMetric::totalCommits).sum();
            int totalCompanyAdded = (int) metrics.stream().mapToLong(dev.grahamhill.model.CompanyMetric::totalLinesAdded).sum();
            int totalCompanyDeleted = (int) metrics.stream().mapToLong(dev.grahamhill.model.CompanyMetric::totalLinesDeleted).sum();

            breakdownStrings.add(String.format("COMPANY TOTALS: %d repos, %d commits, +%d / -%d lines", 
                metrics.size(), totalCompanyCommits, totalCompanyAdded, totalCompanyDeleted));
            breakdownStrings.add("------------------------------------------------------------");

            metrics.stream()
                .sorted(Comparator.comparingLong(dev.grahamhill.model.CompanyMetric::totalCommits).reversed())
                .forEach(m -> {
                    String repoName = new java.io.File(m.repoName()).getName();
                    double commitPct = (totalCompanyCommits > 0) ? (double) m.totalCommits() / totalCompanyCommits * 100 : 0;
                    breakdownStrings.add(String.format("Repo: %s", repoName));
                    breakdownStrings.add(String.format("  Commits: %d (%.1f%%)", m.totalCommits(), commitPct));
                    breakdownStrings.add(String.format("  Impact: +%d / -%d lines", m.totalLinesAdded(), m.totalLinesDeleted()));
                    breakdownStrings.add(String.format("  Primary Language: %s", m.primaryLanguage()));
                    breakdownStrings.add("");
                });
            companyBreakdownList.setItems(FXCollections.observableArrayList(breakdownStrings));
        }

        // Commits by Repo
        int totalCommits = (int) metrics.stream().mapToLong(dev.grahamhill.model.CompanyMetric::totalCommits).sum();
        List<PieChart.Data> commitData = metrics.stream()
                .map(m -> {
                    double percentage = (totalCommits > 0) ? (double) m.totalCommits() / totalCommits * 100 : 0;
                    String repoName = new java.io.File(m.repoName()).getName();
                    return new PieChart.Data(String.format("%s (%.1f%%)", repoName, percentage), (double) m.totalCommits());
                })
                .collect(Collectors.toList());
        
        safePieUpdate(commitPieChart, commitData);

        // Language Breakdown (Company)
        Map<String, Integer> companyOverallLangs = new HashMap<>();
        metrics.forEach(m -> {
            if (m.languageBreakdown() != null) {
                Map<String, Integer> processed = processLanguageBreakdown(m.languageBreakdown());
                processed.forEach((k, v) -> companyOverallLangs.merge(k, v, Integer::sum));
            }
        });
        int totalCompanyLangFiles = (int) companyOverallLangs.values().stream().mapToLong(Integer::intValue).sum();
        List<PieChart.Data> companyLangData = new ArrayList<>();
        if (totalCompanyLangFiles > 0) {
            companyLangData = companyOverallLangs.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(e -> {
                        double percentage = (double) e.getValue() / totalCompanyLangFiles * 100;
                        return new PieChart.Data(String.format("%s (%.1f%%)", e.getKey(), percentage), (double) e.getValue());
                    })
                    .collect(Collectors.toList());
        }
        safePieUpdate(languagePieChart, companyLangData);

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
        impactBarChart.setData(FXCollections.observableArrayList());
        impactBarChart.setData(FXCollections.observableArrayList(addedSeries, deletedSeries));
        impactBarChart.layout();

        // Code by Developer (Company)
        if (devPieChart != null && allContributors != null) {
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
            
            safePieUpdate(devPieChart, devData);
        }

        // Language of Projects
        if (projectLangPieChart != null) {
            Map<String, Integer> projLangs = new HashMap<>();
            metrics.forEach(m -> {
                String lang = m.primaryLanguage();
                if (lang == null || lang.equals("N/A") || lang.equals("Unknown")) {
                    // Try to pick from breakdown if primary is missing
                    if (m.languageBreakdown() != null && !m.languageBreakdown().isEmpty()) {
                        Map<String, Integer> processed = processLanguageBreakdown(m.languageBreakdown());
                        lang = processed.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse("Unknown");
                    } else {
                        lang = "Unknown";
                    }
                } else {
                    // Normalize primary language too if it's one of the config/docs
                    Map<String, Integer> dummy = new HashMap<>();
                    dummy.put(lang, 1);
                    Map<String, Integer> processed = processLanguageBreakdown(dummy);
                    if (!processed.isEmpty()) {
                        lang = processed.keySet().iterator().next();
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
            
            safePieUpdate(projectLangPieChart, projLangData);
        }

        // Languages by Contributor (Company - Stacked Bar Chart)
        if (contribLangBarChart != null && allContributors != null) {
            contribLangBarChart.setAnimated(false);
            contribLangBarChart.setData(FXCollections.observableArrayList());
            
            Map<String, XYChart.Series<String, Number>> seriesMap = new HashMap<>();
            
            // Re-aggregate contributors by name first
            Map<String, Map<String, Integer>> aggregatedContribLangs = new HashMap<>();
            for (ContributorStats s : allContributors) {
                Map<String, Integer> langs = aggregatedContribLangs.computeIfAbsent(s.name(), k -> new HashMap<>());
                if (s.languageBreakdown() != null) {
                    Map<String, Integer> processed = processLanguageBreakdown(s.languageBreakdown());
                    processed.forEach((lang, count) -> langs.merge(lang, count, Integer::sum));
                }
            }

            // Limit to top 15 contributors for company view
            List<Map.Entry<String, Map<String, Integer>>> topContributors = aggregatedContribLangs.entrySet().stream()
                    .sorted((e1, e2) -> {
                        int count1 = e1.getValue().values().stream().mapToInt(Integer::intValue).sum();
                        int count2 = e2.getValue().values().stream().mapToInt(Integer::intValue).sum();
                        return Integer.compare(count2, count1);
                    })
                    .limit(15)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Map<String, Integer>> entry : topContributors) {
                String displayName = sanitizeName(entry.getKey());
                entry.getValue().forEach((lang, count) -> {
                    XYChart.Series<String, Number> series = seriesMap.computeIfAbsent(lang, k -> {
                        XYChart.Series<String, Number> ser = new XYChart.Series<>();
                        ser.setName(k);
                        return ser;
                    });
                    series.getData().add(new XYChart.Data<>(displayName, count));
                });
            }
            contribLangBarChart.setData(FXCollections.observableArrayList(new ArrayList<>(seriesMap.values())));
        }

        commitsPerDayLineChart.setAnimated(false);
        commitsPerDayLineChart.setData(FXCollections.observableArrayList());
        if (allCommits != null) {
            java.time.LocalDateTime oneMonthAgo = java.time.LocalDateTime.now().minusMonths(1);
            List<CommitInfo> recent = allCommits.stream()
                    .filter(ci -> ci.timestamp().isAfter(oneMonthAgo))
                    .sorted(Comparator.comparing(CommitInfo::timestamp))
                    .collect(Collectors.toList());

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
