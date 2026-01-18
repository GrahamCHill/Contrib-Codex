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
                             BarChart<String, Number> contributorActivityChart, BarChart<String, Number> commitsPerDayChart,
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

    public void updateContributorActivityChart(BarChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.getData().clear();
        chart.setAnimated(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Avg Lines Per Commit");

        List<ContributorStats> sorted = stats.stream()
                .sorted(Comparator.comparingDouble((ContributorStats s) -> 
                    s.commitCount() == 0 ? 0 : (double) s.linesAdded() / s.commitCount()).reversed())
                .limit(10)
                .toList();

        for (ContributorStats s : sorted) {
            double avg = s.commitCount() == 0 ? 0 : (double) s.linesAdded() / s.commitCount();
            series.getData().add(new XYChart.Data<>(sanitizeName(s.name()), avg));
        }

        chart.getData().add(series);
        addTooltips(chart);
    }

    public void updateCpdPerContributorChart(BarChart<String, Number> chart, List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        chart.getData().clear();
        chart.setAnimated(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("AI Probability %");

        List<ContributorStats> sorted = stats.stream()
                .sorted(Comparator.comparingDouble(ContributorStats::averageAiProbability).reversed())
                .limit(10)
                .toList();

        for (ContributorStats s : sorted) {
            series.getData().add(new XYChart.Data<>(sanitizeName(s.name()), s.averageAiProbability() * 100));
        }

        chart.getData().add(series);
        addTooltips(chart);
    }

    private String sanitizeName(String name) {
        if (name == null) return "Unknown";
        if (name.contains("<") && name.contains(">")) {
            return name.substring(0, name.indexOf("<")).trim();
        }
        return name;
    }

    private void addTooltips(BarChart<String, Number> chart) {
        for (XYChart.Series<String, Number> s : chart.getData()) {
            for (XYChart.Data<String, Number> d : s.getData()) {
                if (d.getNode() != null) {
                    Tooltip.install(d.getNode(), new Tooltip(s.getName() + ": " + String.format("%.1f", d.getYValue().doubleValue())));
                }
            }
        }
    }
}
