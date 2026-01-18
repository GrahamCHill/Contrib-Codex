package dev.grahamhill.service;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import javafx.collections.FXCollections;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import java.util.*;
import java.util.stream.Collectors;

public class ChartManager {

    public void updateCharts(PieChart commitPieChart, StackedBarChart<String, Number> impactBarChart, 
                             LineChart<String, Number> activityLineChart, LineChart<String, Number> calendarActivityChart, 
                             LineChart<String, Number> contributorActivityChart, LineChart<String, Number> commitsPerDayChart,
                             List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        
        // Limited to Top 5 for visuals
        List<ContributorStats> top5 = stats.stream().limit(5).collect(Collectors.toList());

        // Pie Chart with percentages
        commitPieChart.getData().clear();
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
                    PieChart.Data data = new PieChart.Data(String.format("%s (%.1f%%)", displayName, percentage), s.commitCount());
                    return data;
                })
                .toList();
        commitPieChart.setData(FXCollections.observableArrayList(pieData));
        commitPieChart.setLegendVisible(true);
        commitPieChart.setLegendSide(javafx.geometry.Side.BOTTOM);

        // Stacked Bar Chart for Impact
        impactBarChart.getData().clear();
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
        impactBarChart.getData().addAll(addedSeries, deletedSeries);

        // Activity Line Chart
        activityLineChart.getData().clear();
        activityLineChart.setAnimated(false);
        if (recentCommits != null && !recentCommits.isEmpty()) {
            XYChart.Series<String, Number> activitySeries = new XYChart.Series<>();
            activitySeries.setName("Lines Added");
            List<CommitInfo> chronological = recentCommits.stream()
                    .filter(ci -> !ci.isMerge())
                    .collect(Collectors.toList());
            Collections.reverse(chronological);
            
            for (CommitInfo ci : chronological) {
                activitySeries.getData().add(new XYChart.Data<>(ci.id().substring(0, 7), ci.linesAdded()));
            }
            activityLineChart.getData().add(activitySeries);
        }

        // Calendar Activity Line Chart
        calendarActivityChart.getData().clear();
        calendarActivityChart.setAnimated(false);
        if (recentCommits != null && !recentCommits.isEmpty()) {
            XYChart.Series<String, Number> calSeries = new XYChart.Series<>();
            calSeries.setName("Daily Impact");
            TreeMap<java.time.LocalDate, Integer> dailyImpact = new TreeMap<>();
            for (CommitInfo ci : recentCommits) {
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
            calendarActivityChart.getData().add(calSeries);
        }

        updateContributorActivityChart(contributorActivityChart, stats, recentCommits);
        updateCpdPerContributorChart(commitsPerDayChart, stats, recentCommits);
    }

    public void updateContributorActivityChart(LineChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.getData().clear();
        chart.setAnimated(false);

        if (recentCommits == null || recentCommits.isEmpty()) return;

        // Group lines added by date and author
        Map<String, TreeMap<java.time.LocalDate, Integer>> contributorDailyLines = new HashMap<>();
        
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
        }

        if (firstDate == null) return;

        // Fill gaps with zeros and create series for top contributors
        List<String> topContributors = stats.stream()
                .limit(5)
                .map(s -> sanitizeName(s.name()))
                .collect(Collectors.toList());

        for (String author : topContributors) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(author);
            
            TreeMap<java.time.LocalDate, Integer> dailyLines = contributorDailyLines.getOrDefault(author, new TreeMap<>());
            
            java.time.LocalDate current = firstDate;
            while (!current.isAfter(lastDate)) {
                int lines = dailyLines.getOrDefault(current, 0);
                series.getData().add(new XYChart.Data<>(current.toString(), lines));
                current = current.plusDays(1);
            }
            chart.getData().add(series);
        }
    }

    public void updateCpdPerContributorChart(LineChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.getData().clear();
        chart.setAnimated(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Commits Per Day");

        TreeMap<java.time.LocalDate, Integer> dailyCommits = new TreeMap<>();
        if (recentCommits != null) {
            for (CommitInfo ci : recentCommits) {
                java.time.LocalDate date = ci.timestamp().toLocalDate();
                dailyCommits.merge(date, 1, Integer::sum);
            }
        }

        if (!dailyCommits.isEmpty()) {
            java.time.LocalDate firstDate = dailyCommits.firstKey();
            java.time.LocalDate lastDate = dailyCommits.lastKey();
            java.time.LocalDate current = firstDate;
            while (!current.isAfter(lastDate)) {
                dailyCommits.putIfAbsent(current, 0);
                current = current.plusDays(1);
            }
        }

        dailyCommits.forEach((date, count) -> {
            series.getData().add(new XYChart.Data<>(date.toString(), count));
        });

        chart.getData().add(series);
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
