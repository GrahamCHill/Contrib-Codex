package dev.grahamhill.ui;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;
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
    private MeaningfulChangeAnalysis currentMeaningfulAnalysis;

    private TextArea systemPromptArea;
    private TextArea userPromptArea;
    private TextArea llmResponseArea;

    private String openAiKey = "";
    private String groqKey = "";
    private String ollamaUrl = "http://localhost:11434";
    private String selectedProvider = "OpenAI";

    private CheckBox aiReviewCheckBox;

    @Override
    public void start(Stage primaryStage) {
        try {
            databaseService = new DatabaseService();
        } catch (Exception e) {
            e.printStackTrace();
        }

        primaryStage.setTitle("Git Contributor Metrics");

        BorderPane root = new BorderPane();
        
        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu settingsMenu = new Menu("Settings");
        MenuItem apiKeysItem = new MenuItem("API Keys...");
        apiKeysItem.setOnAction(e -> showApiKeysDialog());
        settingsMenu.getItems().add(apiKeysItem);
        menuBar.getMenus().add(settingsMenu);
        root.setTop(new VBox(menuBar));

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));
        root.setCenter(contentBox);

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
        commitLimitSpinner = new Spinner<>(0, 1000, 10);
        commitLimitSpinner.setEditable(true);
        ignoredExtensionsField = new TextField("json,csv");
        ignoredExtensionsField.setPromptText("e.g. json,csv");
        aiReviewCheckBox = new CheckBox("Include AI Review in PDF");
        Button exportButton = new Button("Export to PDF");
        exportButton.setOnAction(e -> exportToPdf(primaryStage));
        settingsBox.getChildren().addAll(
                new Label("Git Tree Commits:"), commitLimitSpinner,
                new Label("Ignore Extensions:"), ignoredExtensionsField,
                aiReviewCheckBox,
                exportButton
        );

        VBox aliasBox = new VBox(5);
        aliasesArea = new TextArea();
        aliasesArea.setPromptText("Enter email=Name mappings (one per line)");
        aliasesArea.setPrefHeight(60);
        aliasBox.getChildren().addAll(new Label("User Aliases (email=Combined Name):"), aliasesArea);

        topBox.getChildren().addAll(repoBox, settingsBox, aliasBox);
        contentBox.getChildren().add(topBox);

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

        TableColumn<ContributorStats, String> languagesCol = new TableColumn<>("File Types");
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
        systemPromptArea = new TextArea("You are a senior software engineer. Analyze the following git metrics and provide a detailed report on contributor activity, code quality, and any suspicious AI-generated patterns. Finally, provide a clear conclusion identifying who added the most valuable features to the project and highlight other valuable metrics.");
        systemPromptArea.setPrefHeight(60);
        userPromptArea = new TextArea("Please summarize the performance of the team and identify the key contributors. Make sure to include a 'Conclusion' section at the end identifying the most valuable contributor.");
        userPromptArea.setPrefHeight(60);
        llmResponseArea = new TextArea();
        llmResponseArea.setEditable(false);
        llmResponseArea.setPromptText("LLM Report will appear here...");
        
        HBox llmActionBox = new HBox(10);
        ComboBox<String> providerCombo = new ComboBox<>(FXCollections.observableArrayList("OpenAI", "Groq", "Ollama"));
        providerCombo.setValue(selectedProvider);
        providerCombo.setOnAction(e -> selectedProvider = providerCombo.getValue());
        
        Button generateLlmReportBtn = new Button("Generate LLM Report");
        generateLlmReportBtn.setOnAction(e -> generateLlmReport());
        llmActionBox.getChildren().addAll(new Label("Provider:"), providerCombo, generateLlmReportBtn);

        llmPanel.getChildren().addAll(
            new Label("System Prompt:"), systemPromptArea,
            new Label("User Prompt:"), userPromptArea,
            llmActionBox,
            new Label("LLM Response:"), llmResponseArea
        );
        VBox.setVgrow(llmResponseArea, javafx.scene.layout.Priority.ALWAYS);

        mainSplit.getItems().addAll(horizontalSplit, llmPanel);
        mainSplit.setDividerPositions(0.6);

        contentBox.getChildren().add(mainSplit);

        Scene scene = new Scene(root, 1100, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showApiKeysDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("API Keys Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField openAiField = new TextField(openAiKey);
        openAiField.setPromptText("OpenAI API Key");
        TextField groqField = new TextField(groqKey);
        groqField.setPromptText("Groq API Key");
        TextField ollamaField = new TextField(ollamaUrl);
        ollamaField.setPromptText("Ollama URL (default: http://localhost:11434)");

        grid.add(new Label("OpenAI API Key:"), 0, 0);
        grid.add(openAiField, 1, 0);
        grid.add(new Label("Groq API Key:"), 0, 1);
        grid.add(groqField, 1, 1);
        grid.add(new Label("Ollama URL:"), 0, 2);
        grid.add(ollamaField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                openAiKey = openAiField.getText();
                groqKey = groqField.getText();
                ollamaUrl = ollamaField.getText();
            }
        });
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

        String apiKey;
        String url;
        String model;

        if (selectedProvider.equals("OpenAI")) {
            apiKey = openAiKey;
            url = "https://api.openai.com/v1/chat/completions";
            model = "gpt-4o";
        } else if (selectedProvider.equals("Groq")) {
            apiKey = groqKey;
            url = "https://api.groq.com/openai/v1/chat/completions";
            model = "mixtral-8x7b-32768";
        } else { // Ollama
            apiKey = "ollama"; // dummy
            url = ollamaUrl + "/v1/chat/completions";
            model = "llama3";
        }

        if (apiKey.isEmpty() && !selectedProvider.equals("Ollama")) {
            showAlert("Error", "API Key for " + selectedProvider + " is not set.");
            return;
        }

        StringBuilder metricsText = new StringBuilder("Git Metrics Data:\n");
        for (ContributorStats s : currentStats) {
            metricsText.append(String.format("- %s (%s): %d commits, +%d/-%d lines, %d new/%d edited/%d deleted files, AI Prob: %.1f%%\n",
                s.name(), s.email(), s.commitCount(), s.linesAdded(), s.linesDeleted(), s.filesAdded(), s.filesEdited(), s.filesDeletedCount(), s.averageAiProbability() * 100));
        }

        if (currentMeaningfulAnalysis != null) {
            metricsText.append("\nMeaningful Change Analysis:\n");
            metricsText.append(String.format("- Commit Range: %s\n", currentMeaningfulAnalysis.commitRange()));
            metricsText.append(String.format("- Total Insertions: %d\n", currentMeaningfulAnalysis.totalInsertions()));
            metricsText.append(String.format("- Total Deletions: %d\n", currentMeaningfulAnalysis.totalDeletions()));
            metricsText.append(String.format("- Whitespace Churn: %d\n", currentMeaningfulAnalysis.whitespaceChurn()));
            metricsText.append(String.format("- Meaningful Change Score: %.1f/100\n", currentMeaningfulAnalysis.meaningfulChangeScore()));
            metricsText.append(String.format("- Summary: %s\n", currentMeaningfulAnalysis.summary()));
            
            metricsText.append("- Category Breakdown:\n");
            currentMeaningfulAnalysis.categoryBreakdown().forEach((cat, m) -> {
                if (m.fileCount() > 0) {
                    metricsText.append(String.format("  * %s: %d files, +%d/-%d lines\n", cat, m.fileCount(), m.insertions(), m.deletions()));
                }
            });

            if (!currentMeaningfulAnalysis.warnings().isEmpty()) {
                metricsText.append("- Warnings:\n");
                for (String warning : currentMeaningfulAnalysis.warnings()) {
                    metricsText.append(String.format("  ! %s\n", warning));
                }
            }
            
            metricsText.append("- Top 10 Changed Files (LOC):\n");
            currentMeaningfulAnalysis.topChangedFiles().stream().limit(10).forEach(f -> {
                metricsText.append(String.format("  * %s (+%d/-%d) [%s]\n", f.path(), f.insertions(), f.deletions(), f.category()));
            });
        }

        llmResponseArea.setText("Generating report using " + selectedProvider + "...");

        String finalPrompt = "SYSTEM: " + systemPromptArea.getText() + "\n\nUSER: " + userPromptArea.getText() + "\n\nDATA: " + metricsText.toString();

        new Thread(() -> {
            try {
                String response = callLlmApi(url, apiKey, model, systemPromptArea.getText(), userPromptArea.getText() + "\n\n" + metricsText.toString());
                Platform.runLater(() -> llmResponseArea.setText(response));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> llmResponseArea.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private String callLlmApi(String apiUrl, String apiKey, String model, String system, String user) throws Exception {
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (!apiKey.equals("ollama")) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setDoOutput(true);

        String json = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "system", "content": "%s"},
                {"role": "user", "content": "%s"}
              ]
            }
            """, model, escapeJson(system), escapeJson(user));

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        if (conn.getResponseCode() != 200) {
            try (java.io.InputStream is = conn.getErrorStream()) {
                if (is == null) return "Error code: " + conn.getResponseCode();
                return "Error: " + new String(is.readAllBytes());
            }
        }

        try (java.io.InputStream is = conn.getInputStream()) {
            String response = new String(is.readAllBytes());
            // Improved basic JSON parsing for OpenAI/Groq format
            int start = response.indexOf("\"content\": \"");
            if (start != -1) {
                start += 12;
                // Find the end of the content string, respecting escaped quotes
                int end = -1;
                for (int i = start; i < response.length(); i++) {
                    if (response.charAt(i) == '\"' && response.charAt(i - 1) != '\\') {
                        end = i;
                        break;
                    }
                }
                if (end > start) {
                    return response.substring(start, end)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
            }
            return response;
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
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
                currentMeaningfulAnalysis = gitService.performMeaningfulChangeAnalysis(repoDir, commitLimitSpinner.getValue());
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
        Platform.runLater(() -> {
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

            // Force layout pass and refresh to ensure charts are rendered correctly
            Platform.runLater(() -> {
                commitPieChart.layout();
                impactBarChart.layout();
                commitPieChart.requestLayout();
                impactBarChart.requestLayout();
            });
        });
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

                String aiReport = null;
                if (aiReviewCheckBox.isSelected() && !llmResponseArea.getText().isEmpty() && !llmResponseArea.getText().startsWith("Generating report")) {
                    aiReport = llmResponseArea.getText();
                }

                exportService.exportToPdf(currentStats, currentMeaningfulAnalysis, file.getAbsolutePath(), pieFile.getAbsolutePath(), barFile.getAbsolutePath(), aiReport);
                
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
                .limit(5)
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
