package dev.grahamhill.ui;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.service.DatabaseService;
import dev.grahamhill.service.ExportService;
import dev.grahamhill.service.GitService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MainApp extends Application {

    private final GitService gitService = new GitService();
    private DatabaseService databaseService;
    private final ExportService exportService = new ExportService();

    private TableView<ContributorStats> statsTable;
    private ListView<String> commitList;
    private Label initialCommitLabel;
    private TextField repoPathField;
    private Spinner<Integer> commitLimitSpinner;
    private TextArea aliasesArea;
    private TextField ignoredExtensionsField;

    private PieChart commitPieChart;
    private BarChart<String, Number> impactBarChart;

    private List<ContributorStats> currentStats;

    private TextArea systemPromptArea;
    private TextArea userPromptArea;
    private TextArea llmResponseArea;

    @Override
    public void start(Stage primaryStage) {
        try {
            databaseService = new DatabaseService();
        } catch (Exception e) {
            e.printStackTrace();
        }

        primaryStage.setTitle("Git Contributor Metrics");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top: Repo Selection and Settings
        VBox topBox = new VBox(10);
        HBox repoBox = new HBox(10);
        repoPathField = new TextField();
        repoPathField.setPrefWidth(400);
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseRepo(primaryStage));
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> analyzeRepo());
        repoBox.getChildren().addAll(new Label("Repo Path:"), repoPathField, browseButton, analyzeButton);

        HBox settingsBox = new HBox(10);
        commitLimitSpinner = new Spinner<>(1, 100, 10);
        ignoredExtensionsField = new TextField("json,csv");
        ignoredExtensionsField.setPromptText("e.g. json,csv");
        Button exportButton = new Button("Export to PDF");
        exportButton.setOnAction(e -> exportToPdf(primaryStage));
        settingsBox.getChildren().addAll(
                new Label("Git Tree Commits:"), commitLimitSpinner,
                new Label("Ignore Extensions:"), ignoredExtensionsField,
                exportButton
        );

        VBox aliasBox = new VBox(5);
        aliasesArea = new TextArea();
        aliasesArea.setPromptText("Enter email=Name mappings (one per line)");
        aliasesArea.setPrefHeight(60);
        aliasBox.getChildren().addAll(new Label("User Aliases (email=Combined Name):"), aliasesArea);

        topBox.getChildren().addAll(repoBox, settingsBox, aliasBox);
        root.setTop(topBox);

        // SplitPane for Main Content and LLM Panel
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Center: Stats Table and Charts
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(10, 10, 10, 10));
        
        statsTable = new TableView<>();
        TableColumn<ContributorStats, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<ContributorStats, Integer> commitsCol = new TableColumn<>("Commits");
        commitsCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().commitCount()).asObject());
        TableColumn<ContributorStats, Integer> addedCol = new TableColumn<>("Added");
        addedCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().linesAdded()).asObject());
        TableColumn<ContributorStats, Integer> deletedCol = new TableColumn<>("Deleted");
        deletedCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().linesDeleted()).asObject());
        
        TableColumn<ContributorStats, Integer> fNewCol = new TableColumn<>("New Files");
        fNewCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().filesAdded()).asObject());
        TableColumn<ContributorStats, Integer> fEditCol = new TableColumn<>("Edited Files");
        fEditCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().filesEdited()).asObject());
        TableColumn<ContributorStats, Integer> fDelCol = new TableColumn<>("Deleted Files");
        fDelCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().filesDeletedCount()).asObject());

        TableColumn<ContributorStats, String> languagesCol = new TableColumn<>("Languages");
        languagesCol.setCellValueFactory(data -> new SimpleStringProperty(formatLanguages(data.getValue().languageBreakdown())));
        TableColumn<ContributorStats, String> aiCol = new TableColumn<>("AI Prob");
        aiCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.1f%%", data.getValue().averageAiProbability() * 100)));

        statsTable.getColumns().addAll(nameCol, commitsCol, addedCol, deletedCol, fNewCol, fEditCol, fDelCol, languagesCol, aiCol);
        setupStatsTableContextMenu();

        HBox chartsBox = new HBox(10);
        commitPieChart = new PieChart();
        commitPieChart.setTitle("Commits by Contributor");
        commitPieChart.setMinWidth(300);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        impactBarChart = new BarChart<>(xAxis, yAxis);
        impactBarChart.setTitle("Impact (Lines Added/Deleted)");
        impactBarChart.setMinWidth(300);

        chartsBox.getChildren().addAll(commitPieChart, impactBarChart);
        HBox.setHgrow(commitPieChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(impactBarChart, javafx.scene.layout.Priority.ALWAYS);

        centerBox.getChildren().addAll(new Label("Top 10 Contributors:"), statsTable, chartsBox);
        VBox.setVgrow(statsTable, javafx.scene.layout.Priority.ALWAYS);

        // Right side: Commits
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10, 10, 10, 10));
        commitList = new ListView<>();
        initialCommitLabel = new Label("Initial Commit: N/A");
        rightBox.getChildren().addAll(new Label("Recent Commits:"), commitList, initialCommitLabel);
        VBox.setVgrow(commitList, javafx.scene.layout.Priority.ALWAYS);

        SplitPane horizontalSplit = new SplitPane(centerBox, rightBox);
        horizontalSplit.setDividerPositions(0.7);

        // LLM Panel
        VBox llmPanel = new VBox(10);
        llmPanel.setPadding(new Insets(10));
        systemPromptArea = new TextArea("You are a senior software engineer. Analyze the following git metrics and provide a detailed report on contributor activity, code quality, and any suspicious AI-generated patterns.");
        systemPromptArea.setPrefHeight(60);
        userPromptArea = new TextArea("Please summarize the performance of the team and identify the key contributors.");
        userPromptArea.setPrefHeight(60);
        llmResponseArea = new TextArea();
        llmResponseArea.setEditable(false);
        llmResponseArea.setPromptText("LLM Report will appear here...");
        Button generateLlmReportBtn = new Button("Generate LLM Report");
        generateLlmReportBtn.setOnAction(e -> generateLlmReport());

        llmPanel.getChildren().addAll(
            new Label("System Prompt:"), systemPromptArea,
            new Label("User Prompt:"), userPromptArea,
            generateLlmReportBtn,
            new Label("LLM Response:"), llmResponseArea
        );
        VBox.setVgrow(llmResponseArea, javafx.scene.layout.Priority.ALWAYS);

        mainSplit.getItems().addAll(horizontalSplit, llmPanel);
        mainSplit.setDividerPositions(0.6);

        root.setCenter(mainSplit);

        Scene scene = new Scene(root, 1100, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupStatsTableContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem mergeItem = new MenuItem("Merge with another user...");
        mergeItem.setOnAction(e -> {
            ContributorStats selected = statsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showMergeDialog(selected);
            }
        });
        contextMenu.getItems().add(mergeItem);
        statsTable.setContextMenu(contextMenu);
    }

    private void showMergeDialog(ContributorStats selected) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Merge User");
        dialog.setHeaderText("Merge " + selected.email() + " into a combined name");
        dialog.setContentText("Enter the name to merge into:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String currentAliases = aliasesArea.getText();
            String newAlias = selected.email() + "=" + name;
            if (currentAliases.isEmpty()) {
                aliasesArea.setText(newAlias);
            } else {
                aliasesArea.setText(currentAliases + "\n" + newAlias);
            }
            analyzeRepo(); // Re-analyze with new alias
        });
    }

    private void generateLlmReport() {
        if (currentStats == null || currentStats.isEmpty()) {
            showAlert("Error", "No metrics to analyze. Run analysis first.");
            return;
        }

        StringBuilder metricsText = new StringBuilder("Git Metrics Data:\n");
        for (ContributorStats s : currentStats) {
            metricsText.append(String.format("- %s (%s): %d commits, +%d/-%d lines, %d new/%d edited/%d deleted files, AI Prob: %.1f%%\n",
                s.name(), s.email(), s.commitCount(), s.linesAdded(), s.linesDeleted(), s.filesAdded(), s.filesEdited(), s.filesDeletedCount(), s.averageAiProbability() * 100));
        }

        llmResponseArea.setText("Generating report (placeholder)...\n\n" +
            "In a real application, this would send the following to an LLM:\n\n" +
            "SYSTEM: " + systemPromptArea.getText() + "\n\n" +
            "USER: " + userPromptArea.getText() + "\n\n" +
            "DATA: " + metricsText.toString());
        
        // You could use an HTTP client here to call OpenAI/Anthropic/etc.
    }

    private void browseRepo(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            repoPathField.setText(selected.getAbsolutePath());
        }
    }

    private void analyzeRepo() {
        String path = repoPathField.getText();
        File repoDir = new File(path);
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            showAlert("Error", "Invalid git repository path.");
            return;
        }

        Map<String, String> aliases = new HashMap<>();
        String[] lines = aliasesArea.getText().split("\n");
        for (String line : lines) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                aliases.put(parts[0].trim(), parts[1].trim());
            }
        }

        Set<String> ignoredExtensions = Arrays.stream(ignoredExtensionsField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(".") ? s : "." + s)
                .collect(Collectors.toSet());

        new Thread(() -> {
            try {
                currentStats = gitService.getContributorStats(repoDir, aliases, ignoredExtensions);
                List<CommitInfo> recentCommits = gitService.getLastCommits(repoDir, commitLimitSpinner.getValue());
                CommitInfo initial = gitService.getInitialCommit(repoDir);

                databaseService.saveMetrics(currentStats);

                Platform.runLater(() -> {
                    statsTable.setItems(FXCollections.observableArrayList(currentStats));
                    updateCharts(currentStats);
                    commitList.getItems().clear();
                    for (CommitInfo ci : recentCommits) {
                        String langStr = formatLanguages(ci.languageBreakdown());
                        String aiStr = String.format("[AI: %.0f%%]", ci.aiProbability() * 100);
                        commitList.getItems().add(String.format("[%s] %s: %s (%s) %s", ci.id(), ci.authorName(), ci.message(), langStr, aiStr));
                    }
                    if (initial != null) {
                        initialCommitLabel.setText("Initial: [" + initial.id() + "] by " + initial.authorName() + " (" + formatLanguages(initial.languageBreakdown()) + ") " + String.format("[AI: %.0f%%]", initial.aiProbability() * 100));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Error", "Analysis failed: " + e.getMessage()));
            }
        }).start();
    }

    private void updateCharts(List<ContributorStats> stats) {
        // Pie Chart
        List<PieChart.Data> pieData = stats.stream()
                .map(s -> new PieChart.Data(s.name(), s.commitCount()))
                .toList();
        commitPieChart.setData(FXCollections.observableArrayList(pieData));

        // Bar Chart
        impactBarChart.getData().clear();
        XYChart.Series<String, Number> addedSeries = new XYChart.Series<>();
        addedSeries.setName("Added");
        XYChart.Series<String, Number> deletedSeries = new XYChart.Series<>();
        deletedSeries.setName("Deleted");

        for (ContributorStats s : stats) {
            addedSeries.getData().add(new XYChart.Data<>(s.name(), s.linesAdded()));
            deletedSeries.getData().add(new XYChart.Data<>(s.name(), s.linesDeleted()));
        }
        impactBarChart.getData().addAll(addedSeries, deletedSeries);
    }

    private void exportToPdf(Stage stage) {
        if (currentStats == null || currentStats.isEmpty()) {
            showAlert("Warning", "No data to export. Run analysis first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                // Take snapshots of charts
                File pieFile = new File("pie_chart.png");
                File barFile = new File("bar_chart.png");
                
                saveNodeSnapshot(commitPieChart, pieFile);
                saveNodeSnapshot(impactBarChart, barFile);

                exportService.exportToPdf(currentStats, file.getAbsolutePath(), pieFile.getAbsolutePath(), barFile.getAbsolutePath());
                
                // Cleanup temp files
                pieFile.delete();
                barFile.delete();

                showAlert("Success", "Report exported to " + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Export failed: " + e.getMessage());
            }
        }
    }

    private void saveNodeSnapshot(javafx.scene.Node node, File file) throws Exception {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    }

    private String formatLanguages(Map<String, Integer> languages) {
        if (languages == null || languages.isEmpty()) return "N/A";
        return languages.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
