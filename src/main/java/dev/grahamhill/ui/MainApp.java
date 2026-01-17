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
    private Spinner<Integer> tableLimitSpinner;
    private TextArea aliasesArea;
    private TextField ignoredExtensionsField;
    private TextField ignoredFoldersField;
    private TextField mdFolderPathField;
    private TextField requiredFeaturesPathField;
    private TextField coverPagePathField;

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
    private String ollamaModel = "llama3";
    private List<String> availableOllamaModels = new ArrayList<>();
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
        tableLimitSpinner = new Spinner<>(1, 100, 20);
        tableLimitSpinner.setEditable(true);
        ignoredExtensionsField = new TextField("json,csv");
        ignoredExtensionsField.setPromptText("e.g. json,csv");
        ignoredFoldersField = new TextField("node_modules,target");
        ignoredFoldersField.setPromptText("e.g. node_modules,target");
        settingsBox.getChildren().addAll(
                new Label("Git Tree Commits:"), commitLimitSpinner,
                new Label("Table Limit:"), tableLimitSpinner,
                new Label("Ignore Extensions:"), ignoredExtensionsField,
                new Label("Ignore Folders:"), ignoredFoldersField
        );
        
        HBox settingsBox2 = new HBox(10);
        mdFolderPathField = new TextField();
        mdFolderPathField.setPromptText("Path to .md sections folder");
        Button browseMdButton = new Button("Browse...");
        browseMdButton.setOnAction(e -> browseMdFolder(primaryStage));
        Button openMdButton = new Button("Open");
        openMdButton.setOnAction(e -> openMdFolder());

        requiredFeaturesPathField = new TextField();
        requiredFeaturesPathField.setPromptText("Path to features.md/csv");
        Button browseReqButton = new Button("Browse...");
        browseReqButton.setOnAction(e -> browseRequiredFeatures(primaryStage));
        Button genReqButton = new Button("Gen Template");
        genReqButton.setOnAction(e -> generateRequiredFeaturesTemplate());

        coverPagePathField = new TextField();
        coverPagePathField.setPromptText("Path to coverpage.html");
        Button browseCoverButton = new Button("Browse...");
        browseCoverButton.setOnAction(e -> browseCoverPage(primaryStage));

        aiReviewCheckBox = new CheckBox("Include AI Review in PDF");
        Button exportButton = new Button("Export to PDF");
        exportButton.setOnAction(e -> exportToPdf(primaryStage));
        
        settingsBox2.getChildren().addAll(
                new Label("MD Folder:"), mdFolderPathField, browseMdButton, openMdButton,
                new Label("Features:"), requiredFeaturesPathField, browseReqButton, genReqButton,
                new Label("Coverpage:"), coverPagePathField, browseCoverButton,
                aiReviewCheckBox,
                exportButton
        );

        VBox aliasBox = new VBox(5);
        aliasesArea = new TextArea();
        aliasesArea.setPromptText("Enter email=Name mappings (one per line)");
        aliasesArea.setPrefHeight(60);
        aliasBox.getChildren().addAll(new Label("User Aliases (email=Combined Name):"), aliasesArea);

        topBox.getChildren().addAll(repoBox, settingsBox, settingsBox2, aliasBox);
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
        TableColumn<ContributorStats, Integer> mergesCol = new TableColumn<>("Merges");
        mergesCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().mergeCount()).asObject());
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

        statsTable.getColumns().addAll(nameCol, commitsCol, mergesCol, addedCol, deletedCol, fNewCol, fEditCol, fDelCol, languagesCol, aiCol);
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
        generateLlmReportBtn.setOnAction(e -> generateLlmReport(null));
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
        
        ComboBox<String> ollamaModelCombo = new ComboBox<>(FXCollections.observableArrayList(availableOllamaModels));
        if (availableOllamaModels.isEmpty()) {
            ollamaModelCombo.getItems().add(ollamaModel);
        }
        ollamaModelCombo.setValue(ollamaModel);
        ollamaModelCombo.setEditable(true);
        ollamaModelCombo.setPrefWidth(200);

        Button fetchModelsBtn = new Button("Fetch Models");
        fetchModelsBtn.setOnAction(e -> {
            List<String> models = fetchOllamaModels(ollamaField.getText());
            if (!models.isEmpty()) {
                availableOllamaModels = models;
                ollamaModelCombo.setItems(FXCollections.observableArrayList(models));
                ollamaModelCombo.setValue(models.get(0));
            }
        });

        grid.add(new Label("OpenAI API Key:"), 0, 0);
        grid.add(openAiField, 1, 0);
        grid.add(new Label("Groq API Key:"), 0, 1);
        grid.add(groqField, 1, 1);
        grid.add(new Label("Ollama URL:"), 0, 2);
        grid.add(ollamaField, 1, 2);
        grid.add(new Label("Ollama Model:"), 0, 3);
        HBox ollamaModelBox = new HBox(5, ollamaModelCombo, fetchModelsBtn);
        grid.add(ollamaModelBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                openAiKey = openAiField.getText();
                groqKey = groqField.getText();
                ollamaUrl = ollamaField.getText();
                ollamaModel = ollamaModelCombo.getValue();
            }
        });
    }

    private List<String> fetchOllamaModels(String baseUrl) {
        List<String> models = new ArrayList<>();
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/api/tags");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (java.io.InputStream is = conn.getInputStream()) {
                    String response = new String(is.readAllBytes());
                    // Rough manual parsing of JSON like {"models":[{"name":"llama3:latest",...},...]}
                    int idx = 0;
                    while ((idx = response.indexOf("\"name\":\"", idx)) != -1) {
                        idx += 8;
                        int end = response.indexOf("\"", idx);
                        if (end != -1) {
                            models.add(response.substring(idx, end));
                            idx = end;
                        }
                    }
                }
            } else {
                showAlert("Error", "Failed to fetch Ollama models. Status: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            showAlert("Error", "Could not connect to Ollama: " + e.getMessage());
        }
        return models;
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

    private void generateLlmReport(Runnable onComplete) {
        if (currentStats == null || currentStats.isEmpty()) {
            showAlert("Error", "No metrics to analyze. Run analysis first.");
            if (onComplete != null) onComplete.run();
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
            model = ollamaModel;
        }

        if (apiKey.isEmpty() && !selectedProvider.equals("Ollama")) {
            showAlert("Error", "API Key for " + selectedProvider + " is not set.");
            if (onComplete != null) onComplete.run();
            return;
        }

        Map<String, String> mdSections = readMdSections();
        
        StringBuilder metricsText = new StringBuilder("Git Metrics Data:\n");
        for (ContributorStats s : currentStats) {
            metricsText.append(String.format("- %s (%s): %d commits, %d merges, +%d/-%d lines, %d new/%d edited/%d deleted files, AI Prob: %.1f%%\n",
                s.name(), s.email(), s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), s.filesAdded(), s.filesEdited(), s.filesDeletedCount(), s.averageAiProbability() * 100));
        }

        String reqFeatures = readRequiredFeatures();
        if (!reqFeatures.isEmpty()) {
            metricsText.append("\nRequired Features for Evaluation:\n");
            metricsText.append(reqFeatures).append("\n");
        }

        if (!mdSections.isEmpty()) {
            metricsText.append("\nAdditional Context (MD Sections):\n");
            mdSections.forEach((title, content) -> {
                metricsText.append(String.format("### %s\n%s\n\n", title, content));
            });
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

        Platform.runLater(() -> llmResponseArea.setText("Generating report using " + selectedProvider + "..."));

        new Thread(() -> {
            try {
                String response = callLlmApi(url, apiKey, model, systemPromptArea.getText(), userPromptArea.getText() + "\n\n" + metricsText.toString());
                Platform.runLater(() -> {
                    llmResponseArea.setText(response);
                    if (onComplete != null) onComplete.run();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    llmResponseArea.setText("Error: " + e.getMessage());
                    if (onComplete != null) onComplete.run();
                });
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
            // Improved basic JSON parsing for OpenAI/Groq/Ollama format
            // Handle both "content": "..." and "content":"..."
            int start = response.indexOf("\"content\":");
            if (start != -1) {
                start = response.indexOf("\"", start + 10); // Find the opening quote of the content value
                if (start != -1) {
                    start += 1; // Move past the opening quote
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
                                .replace("\\\\", "\\")
                                .replace("\\u003c", "<")
                                .replace("\\u003e", ">");
                    }
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

    private void browseMdFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            mdFolderPathField.setText(selected.getAbsolutePath());
        }
    }

    private void browseRequiredFeatures(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown files", "*.md"),
                new FileChooser.ExtensionFilter("CSV files", "*.csv")
        );
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            requiredFeaturesPathField.setText(selected.getAbsolutePath());
        }
    }

    private void generateRequiredFeaturesTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Required Features Template");
        chooser.setInitialFileName("required_features.md");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown files", "*.md"));
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                String template = "# Required Features\n" +
                        "List the features required for this project below. The AI will use this to evaluate contributor impact.\n\n" +
                        "- Feature 1: Description\n" +
                        "- Feature 2: Description\n";
                java.nio.file.Files.writeString(file.toPath(), template);
                requiredFeaturesPathField.setText(file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Could not generate template: " + e.getMessage());
            }
        }
    }

    private void openMdFolder() {
        String path = mdFolderPathField.getText();
        if (path == null || path.isEmpty()) {
            showAlert("Error", "MD Folder path is empty.");
            return;
        }
        File folder = new File(path);
        if (!folder.exists()) {
            if (confirmDialog("Folder does not exist", "The MD folder does not exist. Would you like to create it and add default sections?")) {
                folder.mkdirs();
                createDefaultMdFiles(folder);
            } else {
                return;
            }
        }
        getHostServices().showDocument(folder.toURI().toString());
    }

    private void createDefaultMdFiles(File folder) {
        try {
            java.nio.file.Files.writeString(new File(folder, "01_Introduction.md").toPath(), "# Introduction\nThis report analyzes the git repository and contributor activity.");
            java.nio.file.Files.writeString(new File(folder, "02_Methodology.md").toPath(), "# Methodology\nWe use JGit for analysis and AI heuristics for detecting code patterns.");
            
            // Also update the UI field to show the path if it was empty
            if (mdFolderPathField.getText().isEmpty()) {
                mdFolderPathField.setText(folder.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, String> readMdSections() {
        Map<String, String> sections = new TreeMap<>();
        String path = mdFolderPathField.getText();
        if (path == null || path.isEmpty()) return sections;

        File folder = new File(path);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".md"));
            if (files != null) {
                for (File f : files) {
                    try {
                        String content = java.nio.file.Files.readString(f.toPath());
                        sections.put(f.getName(), content);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return sections;
    }

    private String readRequiredFeatures() {
        String path = requiredFeaturesPathField.getText();
        if (path == null || path.isEmpty()) return "";
        File f = new File(path);
        if (f.exists() && f.isFile()) {
            try {
                return java.nio.file.Files.readString(f.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    private void browseCoverPage(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            coverPagePathField.setText(selected.getAbsolutePath());
        }
    }

    private boolean confirmDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        return alert.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
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

        Set<String> ignoredFolders = Arrays.stream(ignoredFoldersField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        new Thread(() -> {
            try {
                currentStats = gitService.getContributorStats(repoDir, aliases, ignoredExtensions, ignoredFolders);
                currentMeaningfulAnalysis = gitService.performMeaningfulChangeAnalysis(repoDir, commitLimitSpinner.getValue(), ignoredFolders);
                List<CommitInfo> recentCommits = gitService.getLastCommits(repoDir, commitLimitSpinner.getValue());
                CommitInfo initial = gitService.getInitialCommit(repoDir);

                databaseService.saveMetrics(currentStats);

                Platform.runLater(() -> {
                    List<ContributorStats> tableStats = groupOthers(currentStats, tableLimitSpinner.getValue());
                    statsTable.setItems(FXCollections.observableArrayList(tableStats));
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

    private List<ContributorStats> groupOthers(List<ContributorStats> stats, int limit) {
        if (stats.size() <= limit) return stats;

        List<ContributorStats> top = stats.stream().limit(limit).collect(Collectors.toList());
        List<ContributorStats> others = stats.stream().skip(limit).toList();

        int oCommits = others.stream().mapToInt(ContributorStats::commitCount).sum();
        int oMerges = others.stream().mapToInt(ContributorStats::mergeCount).sum();
        int oAdded = others.stream().mapToInt(ContributorStats::linesAdded).sum();
        int oDeleted = others.stream().mapToInt(ContributorStats::linesDeleted).sum();
        int oFAdded = others.stream().mapToInt(ContributorStats::filesAdded).sum();
        int oFEdited = others.stream().mapToInt(ContributorStats::filesEdited).sum();
        int oFDeleted = others.stream().mapToInt(ContributorStats::filesDeletedCount).sum();
        double avgAi = others.stream().mapToDouble(ContributorStats::averageAiProbability).average().orElse(0.0);

        Map<String, Integer> oLangs = new HashMap<>();
        others.forEach(s -> s.languageBreakdown().forEach((k, v) -> oLangs.merge(k, v, Integer::sum)));

        top.add(new ContributorStats("Others", "others@example.com", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted));
        return top;
    }

    private void updateCharts(List<ContributorStats> stats) {
        Platform.runLater(() -> {
            // Limited to Top 5 for visuals
            List<ContributorStats> top5 = stats.stream().limit(5).collect(Collectors.toList());

            // Pie Chart
            List<PieChart.Data> pieData = top5.stream()
                    .map(s -> new PieChart.Data(s.name(), s.commitCount() + s.mergeCount()))
                    .toList();
            commitPieChart.setData(FXCollections.observableArrayList(pieData));

            // Bar Chart
            impactBarChart.getData().clear();
            XYChart.Series<String, Number> addedSeries = new XYChart.Series<>();
            addedSeries.setName("Added");
            XYChart.Series<String, Number> deletedSeries = new XYChart.Series<>();
            deletedSeries.setName("Deleted");

            for (ContributorStats s : top5) {
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
            if (aiReviewCheckBox.isSelected()) {
                generateLlmReport(() -> performPdfExport(file));
            } else {
                performPdfExport(file);
            }
        }
    }

    private void performPdfExport(File file) {
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

            String coverHtml = null;
            if (coverPagePathField.getText() != null && !coverPagePathField.getText().isEmpty()) {
                File coverFile = new File(coverPagePathField.getText());
                if (coverFile.exists()) {
                    coverHtml = java.nio.file.Files.readString(coverFile.toPath());
                    coverHtml = coverHtml.replace("{{generated_on}}", java.time.LocalDate.now().toString())
                            .replace("{{user}}", System.getProperty("user.name"))
                            .replace("{{project}}", new File(repoPathField.getText()).getName());
                }
            }

            exportService.exportToPdf(currentStats, currentMeaningfulAnalysis, file.getAbsolutePath(), pieFile.getAbsolutePath(), barFile.getAbsolutePath(), aiReport, readMdSections(), coverHtml, tableLimitSpinner.getValue());
            
            // Cleanup temp files
            pieFile.delete();
            barFile.delete();

            Platform.runLater(() -> showAlert("Success", "Report exported to " + file.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showAlert("Error", "Export failed: " + e.getMessage()));
        }
    }

    private void saveNodeSnapshot(javafx.scene.Node node, File file) throws Exception {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(javafx.scene.transform.Transform.scale(2, 2)); // Increase resolution
        WritableImage image = node.snapshot(params, null);
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
