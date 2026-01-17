package dev.grahamhill.ui;

import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;
import dev.grahamhill.service.DatabaseService;
import dev.grahamhill.service.EncryptionService;
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
    private EncryptionService encryptionService;

    private TableView<ContributorStats> statsTable;
    private ListView<String> commitList;
    private Label initialCommitLabel;
    private TextField repoPathField;
    private Spinner<Integer> commitLimitSpinner;
    private Spinner<Integer> tableLimitSpinner;
    private TextArea aliasesArea;
    private String gendersData = "";
    private TextField ignoredExtensionsField;
    private TextField ignoredFoldersField;
    private TextField mdFolderPathField;
    private TextField requiredFeaturesPathField;
    private TextField coverPagePathField;

    private PieChart commitPieChart;
    private StackedBarChart<String, Number> impactBarChart;
    private LineChart<String, Number> activityLineChart;
    private LineChart<String, Number> calendarActivityChart;
    private LineChart<String, Number> contributorActivityChart;
    private LineChart<String, Number> commitsPerDayChart;
    private LineChart<String, Number> cpdPerContributorChart;

    private List<ContributorStats> currentStats;
    private MeaningfulChangeAnalysis currentMeaningfulAnalysis;

    private TextArea systemPromptArea;
    private TextArea userPromptArea;
    private TextArea llmResponseArea;

    private String openAiKey = "";
    private String openAiModel = "gpt-4o";
    private String groqKey = "";
    private String groqModel = "mixtral-8x7b-32768";
    private String ollamaUrl = "http://localhost:11434";
    private String ollamaModel = "llama3";
    private List<String> availableOllamaModels = new ArrayList<>();
    private String selectedProvider = "OpenAI";

    private CheckBox aiReviewCheckBox;

    @Override
    public void start(Stage primaryStage) {
        // Initialize UI components before loading settings to avoid NPE
        repoPathField = new TextField();
        commitLimitSpinner = new Spinner<>(0, 1000, 10);
        commitLimitSpinner.setEditable(true);
        tableLimitSpinner = new Spinner<>(1, 100, 20);
        tableLimitSpinner.setEditable(true);
        ignoredExtensionsField = new TextField("json,csv,lock,txt,package-lock.json,yarn.lock,pnpm-lock.yaml");
        ignoredFoldersField = new TextField("node_modules,target,build,dist,.git");
        mdFolderPathField = new TextField();
        requiredFeaturesPathField = new TextField();
        coverPagePathField = new TextField();
        aliasesArea = new TextArea();

        try {
            databaseService = new DatabaseService();
            encryptionService = new EncryptionService();
            loadSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Add listeners after loading settings to avoid multiple save calls during initialization
        ignoredExtensionsField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        ignoredFoldersField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        commitLimitSpinner.valueProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        tableLimitSpinner.valueProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        repoPathField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        aliasesArea.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        mdFolderPathField.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::saveSettings);
        });
        requiredFeaturesPathField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        coverPagePathField.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::saveSettings);
        });

        primaryStage.setTitle("Contrib Codex");

        BorderPane root = new BorderPane();
        
        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu settingsMenu = new Menu("Settings");
        MenuItem apiKeysItem = new MenuItem("API Keys...");
        apiKeysItem.setOnAction(e -> showApiKeysDialog());
        MenuItem genDefaultMdItem = new MenuItem("Generate Default MDs");
        genDefaultMdItem.setOnAction(e -> generateDefaultMds());
        MenuItem genDefaultCoverItem = new MenuItem("Generate Default Cover");
        genDefaultCoverItem.setOnAction(e -> generateDefaultCoverPage());
        MenuItem genFeaturesTemplateItem = new MenuItem("Generate Features Template");
        genFeaturesTemplateItem.setOnAction(e -> generateRequiredFeaturesTemplate());
        settingsMenu.getItems().addAll(apiKeysItem, new SeparatorMenuItem(), genDefaultMdItem, genDefaultCoverItem, genFeaturesTemplateItem);
        menuBar.getMenus().add(settingsMenu);
        root.setTop(new VBox(menuBar));

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));
        root.setCenter(contentBox);

        // Top: Repo Selection and Settings
        VBox topBox = new VBox(10);
        HBox repoBox = new HBox(10);
        repoPathField.setPrefWidth(400);
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseRepo(primaryStage));
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> analyzeRepo());
        repoBox.getChildren().addAll(new Label("Repo Path:"), repoPathField, browseButton, analyzeButton);

        HBox settingsBox = new HBox(10);
        ignoredExtensionsField.setPromptText("e.g. json,csv");
        ignoredFoldersField.setPromptText("e.g. node_modules,target");
        
        settingsBox.getChildren().addAll(
                new Label("Git Tree Commits:"), commitLimitSpinner,
                new Label("Table Limit:"), tableLimitSpinner,
                new Label("Ignore Extensions:"), ignoredExtensionsField,
                new Label("Ignore Folders:"), ignoredFoldersField
        );
        
        HBox settingsBox2 = new HBox(10);
        settingsBox2.setPadding(new Insets(0, 0, 5, 0));
        mdFolderPathField.setPromptText("Path to .md sections folder");
        Button browseMdButton = new Button("Browse...");
        browseMdButton.setOnAction(e -> {
            browseMdFolder(primaryStage);
            saveSettings();
        });
        Button openMdButton = new Button("Open");
        openMdButton.setOnAction(e -> openMdFolder());
        settingsBox2.getChildren().addAll(
                new Label("MD Folder:"), mdFolderPathField, browseMdButton, openMdButton
        );

        HBox settingsBox3 = new HBox(10);
        settingsBox3.setPadding(new Insets(0, 0, 5, 0));
        requiredFeaturesPathField.setPromptText("Path to features.md/csv");
        Button browseReqButton = new Button("Browse...");
        browseReqButton.setOnAction(e -> browseRequiredFeatures(primaryStage));

        coverPagePathField.setPromptText("Path to coverpage.html");
        Button browseCoverButton = new Button("Browse...");
        browseCoverButton.setOnAction(e -> {
            browseCoverPage(primaryStage);
            saveSettings();
        });

        aiReviewCheckBox = new CheckBox("Include AI Review in PDF");
        Button exportButton = new Button("Export to PDF");
        exportButton.setOnAction(e -> exportToPdf(primaryStage));
        
        settingsBox3.getChildren().addAll(
                new Label("Features:"), requiredFeaturesPathField, browseReqButton,
                new Label("Coverpage:"), coverPagePathField, browseCoverButton,
                aiReviewCheckBox,
                exportButton
        );

        VBox aliasBox = new VBox(5);
        aliasesArea.setPromptText("Enter email=Name mappings (one per line)");
        aliasesArea.setPrefHeight(60);
        aliasBox.getChildren().addAll(new Label("User Aliases (email=Combined Name):"), aliasesArea);
        HBox.setHgrow(aliasBox, javafx.scene.layout.Priority.ALWAYS);

        contentBox.getChildren().addAll(repoBox, settingsBox, settingsBox2, settingsBox3, aliasBox);

        // SplitPane for Main Content and LLM Panel
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Center: Stats Table and Charts in Tabs
        TabPane statsTabPane = new TabPane();
        Tab statsTab = new Tab("Statistics");
        statsTab.setClosable(false);
        VBox statsBox = new VBox(10);
        statsBox.setPadding(new Insets(10));
        
        statsTable = new TableView<>();
        statsTable.setEditable(true);
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
        
        TableColumn<ContributorStats, String> genderCol = new TableColumn<>("Gender");
        genderCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().gender()));
        genderCol.setCellFactory(javafx.scene.control.cell.ComboBoxTableCell.forTableColumn("male", "female", "non-binary", "they", "unknown"));
        genderCol.setOnEditCommit(event -> {
            ContributorStats stat = event.getRowValue();
            String newGender = event.getNewValue();
            updateContributorGender(stat, newGender);
        });

        TableColumn<ContributorStats, String> aiCol = new TableColumn<>("AI Prob");
        aiCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.1f%%", data.getValue().averageAiProbability() * 100)));

        TableColumn<ContributorStats, String> scoreCol = new TableColumn<>("Meaningful Score");
        scoreCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.1f/100", data.getValue().meaningfulChangeScore())));

        statsTable.getColumns().addAll(nameCol, genderCol, commitsCol, mergesCol, addedCol, deletedCol, fNewCol, fEditCol, fDelCol, languagesCol, aiCol, scoreCol);
        setupStatsTableContextMenu();

        statsBox.getChildren().addAll(new Label("Top 10 Contributors (Double-click Gender to edit):"), statsTable);
        VBox.setVgrow(statsTable, javafx.scene.layout.Priority.ALWAYS);
        statsTab.setContent(statsBox);

        Tab visualsTab = new Tab("Visuals");
        visualsTab.setClosable(false);
        ScrollPane visualsScrollPane = new ScrollPane();
        visualsScrollPane.setFitToWidth(true);
        visualsScrollPane.setFitToHeight(true);
        HBox chartsBox = new HBox(10);
        chartsBox.setPadding(new Insets(10));
        visualsScrollPane.setContent(chartsBox);

        commitPieChart = new PieChart();
        commitPieChart.setTitle("Commits by Contributor");
        commitPieChart.setMinWidth(810); // +35% of 600
        commitPieChart.setPrefWidth(1080); // +35% of 800
        commitPieChart.setLegendVisible(false); // Labels on graph instead

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        impactBarChart = new StackedBarChart<>(xAxis, yAxis);
        impactBarChart.setTitle("Impact (Lines Added/Deleted)");
        impactBarChart.setMinWidth(1260); // -10% of 1400
        impactBarChart.setPrefWidth(1620); // -10% of 1800
        impactBarChart.setMinHeight(900); // -10% of 1000
        impactBarChart.setCategoryGap(20);
        impactBarChart.setLegendSide(javafx.geometry.Side.RIGHT);
        xAxis.setLabel("Contributor");
        xAxis.setTickLabelRotation(45); // Prevent overlap
        yAxis.setLabel("Lines of Code");

        CategoryAxis lxAxis = new CategoryAxis();
        NumberAxis lyAxis = new NumberAxis();
        activityLineChart = new LineChart<>(lxAxis, lyAxis);
        activityLineChart.setTitle("Recent Commit Activity");
        activityLineChart.setMinWidth(1260);
        activityLineChart.setPrefWidth(1620);
        activityLineChart.setMinHeight(900);
        activityLineChart.setLegendSide(javafx.geometry.Side.RIGHT);
        lxAxis.setLabel("Commit ID");
        lxAxis.setTickLabelRotation(45); // Prevent overlap
        lyAxis.setLabel("Lines Added");

        CategoryAxis cxAxis = new CategoryAxis();
        NumberAxis cyAxis = new NumberAxis();
        calendarActivityChart = new LineChart<>(cxAxis, cyAxis);
        calendarActivityChart.setTitle("Daily Activity (Total Impact)");
        calendarActivityChart.setMinWidth(1260);
        calendarActivityChart.setPrefWidth(1620);
        calendarActivityChart.setMinHeight(900);
        calendarActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        cxAxis.setLabel("Date");
        cxAxis.setTickLabelRotation(45); // Prevent overlap
        cyAxis.setLabel("Total Lines Added");

        CategoryAxis caxAxis = new CategoryAxis();
        NumberAxis cayAxis = new NumberAxis();
        contributorActivityChart = new LineChart<>(caxAxis, cayAxis);
        contributorActivityChart.setTitle("Daily Activity per Contributor");
        contributorActivityChart.setMinWidth(1260);
        contributorActivityChart.setPrefWidth(1620);
        contributorActivityChart.setMinHeight(900);
        contributorActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        caxAxis.setLabel("Date");
        caxAxis.setTickLabelRotation(45);
        cayAxis.setLabel("Lines Added");

        CategoryAxis cpdXAxis = new CategoryAxis();
        NumberAxis cpdYAxis = new NumberAxis();
        commitsPerDayChart = new LineChart<>(cpdXAxis, cpdYAxis);
        commitsPerDayChart.setTitle("Commits per Day");
        commitsPerDayChart.setMinWidth(1260);
        commitsPerDayChart.setPrefWidth(1620);
        commitsPerDayChart.setMinHeight(900);
        commitsPerDayChart.setLegendSide(javafx.geometry.Side.RIGHT);
        cpdXAxis.setLabel("Date");
        cpdXAxis.setTickLabelRotation(45);
        cpdYAxis.setLabel("Commit Count");

        chartsBox.getChildren().addAll(commitPieChart, impactBarChart, activityLineChart, calendarActivityChart, contributorActivityChart, commitsPerDayChart);
        HBox.setHgrow(commitPieChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(impactBarChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(activityLineChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(calendarActivityChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(contributorActivityChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(commitsPerDayChart, javafx.scene.layout.Priority.ALWAYS);
        visualsTab.setContent(visualsScrollPane);

        statsTabPane.getTabs().addAll(statsTab, visualsTab);

        // Right side: Commits
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10, 10, 10, 10));
        commitList = new ListView<>();
        initialCommitLabel = new Label("Initial Commit: N/A");
        rightBox.getChildren().addAll(new Label("Recent Commits:"), commitList, initialCommitLabel);
        VBox.setVgrow(commitList, javafx.scene.layout.Priority.ALWAYS);

        SplitPane horizontalSplit = new SplitPane(statsTabPane, rightBox);
        horizontalSplit.setDividerPositions(0.7);

        // LLM Panel
        VBox llmPanel = new VBox(10);
        llmPanel.setPadding(new Insets(10));
        systemPromptArea = new TextArea("You are a senior software engineer and code auditor. Analyze git metrics and provide a professional technical report.\n" +
                "Be extremely verbose and provide exhaustive analyses and feedback on the repos. Use tables and sections to organize information.\n" +
                "For each contributor, provide a detailed breakdown of their impact, code quality patterns, and specific areas of contribution.\n" +
                "STRICT RULE: Use ONLY the contributor names and data provided in the METRICS section. Do NOT hallucinate names, metrics, or commit IDs.\n" +
                "The mapping of files and impact to contributors is provided in the metrics; use it directly. Do NOT assume or infer names that are not in the list.\n" +
                "Avoid repetition and ensure you do not hallucinate names or metrics. Use only the provided data.\n" +
                "CRITICAL RISK SCALE (Lines Added per Commit):\n" +
                "- 1500+: VERY HIGH; 1000-1500: HIGH; 750-1000: MED-HIGH; 500-750: MED; 250-500: LOW-MED; <250: LOW (Healthy).\n" +
                "RULE: Higher lines added per commit = HIGHER RISK. Divide Total Lines Added by Total Commits. Do NOT use lines in a file as risk.\n" +
                "FACTOR: Presence of tests (files in 'test' folders) SHOULD REDUCE the risk score as it shows better quality assurance.\n" +
                "NOTE: Initial setup commits have lower risk. Use 'Gender' field for pronouns.\n" +
                "NOTE: If you provide a commit ref or tag, you MUST state who the authors of said commit are.\n" +
                "Identify the most valuable contributor based on iterative development and quality.");
        systemPromptArea.setPrefHeight(60);
        userPromptArea = new TextArea("Summarize team performance and identify key contributors. " +
                "Provide an exhaustive and verbose feedback with detailed tables and sections for deep analysis. " +
                "Explain the reasoning behind the risk assessments in detail. " +
                "Calculate 'Lines Added per Commit' for risk. Use the defined scale and adjust for presence of tests. " +
                "STRICT RULE: Use ONLY provided contributor names. Do NOT invent names. " +
                "Professional report format. Consider language context and project phase. " +
                "Follow Markdown sections and requirements as directives. " +
                "Do NOT repeat yourself across sections. " +
                "Include a 'Conclusion' section identifying the most valuable contributor.");
        userPromptArea.setPrefHeight(60);
        llmResponseArea = new TextArea();
        llmResponseArea.setEditable(false);
        llmResponseArea.setPromptText("LLM Report will appear here...");
        
        HBox llmActionBox = new HBox(10);
        ComboBox<String> providerCombo = new ComboBox<>(FXCollections.observableArrayList("OpenAI", "Groq", "Ollama"));
        providerCombo.setValue(selectedProvider);
        providerCombo.setOnAction(e -> {
            selectedProvider = providerCombo.getValue();
            saveSettings();
        });
        
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
        
        TextField openAiModelField = new TextField(openAiModel);
        openAiModelField.setPromptText("OpenAI Model (e.g. gpt-4o)");
        TextField groqModelField = new TextField(groqModel);
        groqModelField.setPromptText("Groq Model (e.g. mixtral-8x7b-32768)");

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
        grid.add(new Label("OpenAI Model:"), 0, 1);
        grid.add(openAiModelField, 1, 1);

        grid.add(new Label("Groq API Key:"), 0, 2);
        grid.add(groqField, 1, 2);
        grid.add(new Label("Groq Model:"), 0, 3);
        grid.add(groqModelField, 1, 3);

        grid.add(new Label("Ollama URL:"), 0, 4);
        grid.add(ollamaField, 1, 4);
        grid.add(new Label("Ollama Model:"), 0, 5);
        HBox ollamaModelBox = new HBox(5, ollamaModelCombo, fetchModelsBtn);
        grid.add(ollamaModelBox, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                openAiKey = openAiField.getText();
                openAiModel = openAiModelField.getText();
                groqKey = groqField.getText();
                groqModel = groqModelField.getText();
                ollamaUrl = ollamaField.getText();
                ollamaModel = ollamaModelCombo.getValue();
                saveSettings();
            }
        });
    }

    private void saveSettings() {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainApp.class);
        
        String encOpenAiKey = (encryptionService != null) ? encryptionService.encrypt(openAiKey) : openAiKey;
        String encGroqKey = (encryptionService != null) ? encryptionService.encrypt(groqKey) : groqKey;

        prefs.put("openAiKey", encOpenAiKey != null ? encOpenAiKey : "");
        prefs.put("openAiModel", openAiModel);
        prefs.put("groqKey", encGroqKey != null ? encGroqKey : "");
        prefs.put("groqModel", groqModel);
        prefs.put("ollamaUrl", ollamaUrl);
        prefs.put("ollamaModel", ollamaModel);
        prefs.put("selectedProvider", selectedProvider);
        prefs.put("aliases", aliasesArea.getText());
        prefs.put("genders", gendersData);
        
        prefs.put("repoPath", repoPathField.getText());
        prefs.put("commitLimit", String.valueOf(commitLimitSpinner.getValue()));
        prefs.put("tableLimit", String.valueOf(tableLimitSpinner.getValue()));
        prefs.put("ignoredExtensions", ignoredExtensionsField.getText());
        prefs.put("ignoredFolders", ignoredFoldersField.getText());
        
        // Save global settings to database as well
        if (databaseService != null) {
            try {
                databaseService.saveGlobalSetting("mdFolderPath", mdFolderPathField.getText());
                databaseService.saveGlobalSetting("coverPagePath", coverPagePathField.getText());
                databaseService.saveGlobalSetting("requiredFeaturesPath", requiredFeaturesPathField.getText());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadSettings() {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainApp.class);
        
        String savedOpenAiKey = prefs.get("openAiKey", "");
        String savedGroqKey = prefs.get("groqKey", "");
        
        if (encryptionService != null && !savedOpenAiKey.isEmpty()) {
            String decrypted = encryptionService.decrypt(savedOpenAiKey);
            openAiKey = (decrypted != null) ? decrypted : savedOpenAiKey;
        } else {
            openAiKey = savedOpenAiKey;
        }

        if (encryptionService != null && !savedGroqKey.isEmpty()) {
            String decrypted = encryptionService.decrypt(savedGroqKey);
            groqKey = (decrypted != null) ? decrypted : savedGroqKey;
        } else {
            groqKey = savedGroqKey;
        }

        openAiModel = prefs.get("openAiModel", "gpt-4o");
        groqModel = prefs.get("groqModel", "mixtral-8x7b-32768");
        ollamaUrl = prefs.get("ollamaUrl", "http://localhost:11434");
        ollamaModel = prefs.get("ollamaModel", "llama3");
        selectedProvider = prefs.get("selectedProvider", "OpenAI");
        aliasesArea.setText(prefs.get("aliases", ""));
        gendersData = prefs.get("genders", "");
        
        repoPathField.setText(prefs.get("repoPath", ""));
        commitLimitSpinner.getValueFactory().setValue(Integer.parseInt(prefs.get("commitLimit", "10")));
        tableLimitSpinner.getValueFactory().setValue(Integer.parseInt(prefs.get("tableLimit", "20")));
        ignoredExtensionsField.setText(prefs.get("ignoredExtensions", "json,csv,lock,txt,package-lock.json,yarn.lock,pnpm-lock.yaml"));
        ignoredFoldersField.setText(prefs.get("ignoredFolders", "node_modules,target,build,dist,.git"));

        // Load global settings from database
        if (databaseService != null) {
            try {
                String mdPath = databaseService.getGlobalSetting("mdFolderPath");
                if (mdPath != null) mdFolderPathField.setText(mdPath);
                
                String coverPath = databaseService.getGlobalSetting("coverPagePath");
                if (coverPath != null) coverPagePathField.setText(coverPath);

                String featuresPath = databaseService.getGlobalSetting("requiredFeaturesPath");
                if (featuresPath != null) requiredFeaturesPathField.setText(featuresPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

    private void updateContributorGender(ContributorStats stat, String newGender) {
        String currentGenders = gendersData;
        String mapping = stat.email() + "=" + newGender;
        
        String[] lines = currentGenders.split("\n");
        StringBuilder newGenders = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith(stat.email() + "=") || line.startsWith(stat.name() + "=")) {
                newGenders.append(mapping).append("\n");
                found = true;
            } else {
                newGenders.append(line).append("\n");
            }
        }
        if (!found) {
            newGenders.append(mapping).append("\n");
        }
        gendersData = newGenders.toString().trim();
        saveSettings();
        analyzeRepo(); // Refresh data with new gender
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
            model = openAiModel;
        } else if (selectedProvider.equals("Groq")) {
            apiKey = groqKey;
            url = "https://api.groq.com/openai/v1/chat/completions";
            model = groqModel;
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
        
        StringBuilder metricsText = new StringBuilder("METRICS:\n");

        File repoDir = new File(repoPathField.getText());
        Set<String> ignoredFolders = Arrays.stream(ignoredFoldersField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        String structure = gitService.getProjectStructure(repoDir, ignoredFolders);
        metricsText.append(structure).append("\n");

        for (ContributorStats s : currentStats) {
            boolean hasTests = s.languageBreakdown().containsKey("test") || 
                              s.languageBreakdown().keySet().stream().anyMatch(l -> l.toLowerCase().contains("test"));
            metricsText.append(String.format("- %s (%s, %s): %d c, %d m, +%d/-%d l, %d n/%d e/%d d f, %s%s\n",
                s.name(), s.email(), s.gender(), s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), 
                s.filesAdded(), s.filesEdited(), s.filesDeletedCount(), formatLanguages(s.languageBreakdown()),
                hasTests ? " [INCLUDES TESTS]" : ""));
        }

        metricsText.append("\nRISK RULES: CALCULATE 'Lines Added/Commit' = (Total Lines Added / Total Commits).\n");
        metricsText.append("Scale: 1500+ VERY HIGH, 1000-1500 HIGH, 750-1000 MED-HIGH, 500-750 MED, 250-500 LOW-MED, <250 LOW.\n");
        metricsText.append("Higher = Higher Risk. Don't subtract deletions.\n");

        String reqFeatures = readRequiredFeatures();
        if (!reqFeatures.isEmpty()) {
            metricsText.append("\nFeatures:\n").append(reqFeatures).append("\n");
        }

        if (currentMeaningfulAnalysis != null) {
            metricsText.append("\nMEANINGFUL ANALYSIS:\n");
            metricsText.append(String.format("- Range: %s, +%d/-%d, WS: %d\n", 
                currentMeaningfulAnalysis.commitRange(), currentMeaningfulAnalysis.totalInsertions(), 
                currentMeaningfulAnalysis.totalDeletions(), currentMeaningfulAnalysis.whitespaceChurn()));
            
            metricsText.append("- Categories:\n");
            currentMeaningfulAnalysis.categoryBreakdown().forEach((cat, m) -> {
                if (m.fileCount() > 0) {
                    metricsText.append(String.format("  * %s: %d f, +%d/-%d l\n", cat, m.fileCount(), m.insertions(), m.deletions()));
                }
            });

            if (!currentMeaningfulAnalysis.warnings().isEmpty()) {
                metricsText.append("- Observations: ").append(String.join("; ", currentMeaningfulAnalysis.warnings())).append("\n");
            }
            
            metricsText.append("- Top Files:\n");
            currentMeaningfulAnalysis.topChangedFiles().stream().limit(10).forEach(f -> {
                metricsText.append(String.format("  * %s (+%d/-%d) [%s]\n", f.path(), f.insertions(), f.deletions(), f.category()));
            });
        }

        final String finalUrl = url;
        final String finalApiKey = apiKey;
        final String finalModel = model;
        final String baseMetrics = metricsText.toString();

        Platform.runLater(() -> llmResponseArea.setText("Generating multi-section report using " + selectedProvider + "..."));

        new Thread(() -> {
            try {
                StringBuilder fullReport = new StringBuilder();
                
                // If there are MD sections, call LLM for each one
                if (!mdSections.isEmpty()) {
                    for (Map.Entry<String, String> entry : mdSections.entrySet()) {
                        String sectionTitle = entry.getKey().replace(".md", "");
                        String sectionInstructions = entry.getValue();
                        
                        String basePrompt = userPromptArea.getText() + "\n\n" + 
                                       "FOCUS SECTION: " + sectionTitle + "\n" +
                                       "SECTION INSTRUCTIONS: " + sectionInstructions + "\n\n" +
                                       "IMPORTANT: Provide ONLY the content for this section as defined by the instructions. " +
                                       "Do not include information that belongs in other sections. " +
                                       "Use the provided metrics to inform your analysis for this specific section.";
                        
                        // Chunking metrics if they are too long instead of truncating
                        List<String> metricsChunks = chunkMetrics(baseMetrics, 3500);
                        
                        for (int i = 0; i < metricsChunks.size(); i++) {
                            String chunk = metricsChunks.get(i);
                            String chunkInfo = metricsChunks.size() > 1 ? String.format("\n[METRICS CHUNK %d/%d - use this for context if not already covered]\n", i + 1, metricsChunks.size()) : "";
                            
                            String fullPrompt = basePrompt + "\n\n" + 
                                           chunkInfo + chunk;
                                           
                            String sectionResponse = callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), fullPrompt);
                            
                            // Sanitize sectionResponse to remove markdown code blocks
                            sectionResponse = sectionResponse.replaceAll("```markdown", "").replaceAll("```", "").trim();

                            fullReport.append(sectionResponse).append("\n\n");
                            
                            String progressMsg = String.format("Generated section: %s (Chunk %d/%d)...", sectionTitle, i + 1, metricsChunks.size());
                            Platform.runLater(() -> llmResponseArea.setText(progressMsg));
                        }
                    }
                } else {
                    // Default behavior if no MD sections
                    List<String> metricsChunks = chunkMetrics(baseMetrics, 3500);
                    for (int i = 0; i < metricsChunks.size(); i++) {
                        String chunk = metricsChunks.get(i);
                        String chunkInfo = metricsChunks.size() > 1 ? String.format("\n[METRICS CHUNK %d/%d]\n", i + 1, metricsChunks.size()) : "";
                        
                        String response = callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), userPromptArea.getText() + chunkInfo + "\n\n" + chunk);
                        response = response.replaceAll("```markdown", "").replaceAll("```", "").trim();
                        fullReport.append(response).append("\n\n");
                        
                        String progressMsg = String.format("Generating report (Chunk %d/%d)...", i + 1, metricsChunks.size());
                        Platform.runLater(() -> llmResponseArea.setText(progressMsg));
                    }
                }

                Platform.runLater(() -> {
                    llmResponseArea.setText(fullReport.toString().trim());
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

    private List<String> chunkMetrics(String metrics, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (metrics.length() <= maxLength) {
            chunks.add(metrics);
            return chunks;
        }

        String[] lines = metrics.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > maxLength) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                // If a single line is longer than maxLength (unlikely), force split it
                if (line.length() > maxLength) {
                    int start = 0;
                    while (start < line.length()) {
                        int end = Math.min(start + maxLength, line.length());
                        chunks.add(line.substring(start, end));
                        start = end;
                    }
                    continue;
                }
            }
            currentChunk.append(line).append("\n");
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
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

        String combinedUserContent = system + "\n\n" + user;

        String json = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "user", "content": "%s"}
              ]
            }
            """, model, escapeJson(combinedUserContent));

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
                                .replace("\\u0026", "&")
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

    private void generateDefaultMds() {
        String path = mdFolderPathField.getText();
        if (path == null || path.isEmpty()) {
            // Default to 'md_sections' in current directory if empty
            path = "md_sections";
            mdFolderPathField.setText(path);
        }
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        createDefaultMdFiles(folder);
        showAlert("Success", "Default Markdown files created in: " + folder.getAbsolutePath());
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
            java.nio.file.Files.writeString(new File(folder, "01_Introduction.md").toPath(), 
                "# Introduction\n" +
                "This report provides an exhaustive and technical analysis of the git repository's development history and contributor activity.\n" +
                "The focus is on identifying high-value contributions, assessing project stability, and evaluating technical risk across the codebase.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Provide a high-level executive summary of the project's current state.\n" +
                "- Analyze the repository structure to identify core backend, frontend, and infrastructure components.\n" +
                "- Reference specific directory patterns to explain the architectural distribution of work.");
            
            java.nio.file.Files.writeString(new File(folder, "02_Methodology.md").toPath(), 
                "# Analysis Methodology\n" +
                "The analysis utilizes JGit for precise metric extraction and AI-driven heuristics to interpret qualitative development patterns.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Explain the 'Lines Added per Commit' risk scoring system (1500+ VERY HIGH to <250 LOW).\n" +
                "- Describe how the presence of tests (files in 'test' directories) mitigates risk scores.\n" +
                "- Explicitly state that risk is based on average lines added per commit, not lines in a single file.\n" +
                "- Detail the 'Meaningful Change' detection logic which filters out boilerplate and automated code generation.");

            java.nio.file.Files.writeString(new File(folder, "03_Contributor_Deep_Dive.md").toPath(), 
                "# Contributor Impact Analysis\n" +
                "A detailed evaluation of individual contributions based on commit frequency, impact volume, and code quality.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- For EVERY major contributor listed in the METRICS, provide a dedicated technical subsection.\n" +
                "- Use the 'Gender' field for correct pronouns.\n" +
                "- Analyze their specific 'Impact Analysis' (Added vs Deleted lines) and the types of files they touched as shown in their metrics.\n" +
                "- Do NOT invent or assume names; attribute impact ONLY to the names provided in the metrics.\n" +
                "- Identify their 'Most Valuable Contributor' potential based on iterative development rather than just bulk LOC.");

            java.nio.file.Files.writeString(new File(folder, "04_Risk_and_Quality_Assessment.md").toPath(), 
                "# Risk & Quality Assessment\n" +
                "Evaluation of project stability and potential technical debt based on commit patterns.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Create a detailed Risk Table for all contributors.\n" +
                "- Explain the reasoning behind each risk level.\n" +
                "- Identify patterns of 'Bulk Commits' vs 'Iterative Refinement'.\n" +
                "- Highlighting areas where test coverage is lacking relative to feature complexity.");

            java.nio.file.Files.writeString(new File(folder, "05_Conclusion.md").toPath(), 
                "# Conclusion & Recommendations\n" +
                "Final synthesis of findings and strategic recommendations for the project.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Identify the overall 'Most Valuable Contributor' with a detailed justification.\n" +
                "- Summarize the top 3 technical risks found in the repo.\n" +
                "- Provide 3 actionable recommendations for improving code quality or team velocity.");
            
            // Also update the UI field to show the path if it was empty
            if (mdFolderPathField.getText().isEmpty()) {
                mdFolderPathField.setText(folder.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, String> readMdSections() {
        Map<String, String> sections = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order
        String path = mdFolderPathField.getText();
        if (path == null || path.isEmpty()) return sections;

        File folder = new File(path);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".md"));
            if (files != null) {
                // Sort files by name to ensure consistent order
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    try {
                        String content = java.nio.file.Files.readString(f.toPath());
                        String title = f.getName().replace(".md", "");
                        sections.put(title, content);
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

    private void generateDefaultCoverPage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Default Cover Page");
        chooser.setInitialFileName("coverpage.html");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html"));
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                String template = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                        body { font-family: 'Helvetica', sans-serif; text-align: center; padding-top: 50px; }
                        h1 { color: #2c3e50; font-size: 36px; }
                        .info { font-size: 18px; color: #7f8c8d; margin-top: 20px; }
                        .footer { margin-top: 100px; font-style: italic; color: #bdc3c7; }
                        .logo { width: 200px; margin-bottom: 30px; }
                    </style>
                    </head>
                    <body>
                        <div class="logo">
                            <!-- You can add an image tag here: <img src="path/to/logo.png" width="200" /> -->
                            <h2 style="color: #3498db;">[Project Logo Placeholder]</h2>
                        </div>
                        <h1>Git Metrics Report</h1>
                        <div class="info">
                            <p><strong>Project:</strong> {{project}}</p>
                            <p><strong>Generated By:</strong> {{user}}</p>
                            <p><strong>Date:</strong> {{generated_on}}</p>
                        </div>
                        <div class="footer">
                            <p>Generated by Contrib Codex</p>
                        </div>
                    </body>
                    </html>
                    """;
                java.nio.file.Files.writeString(file.toPath(), template);
                coverPagePathField.setText(file.getAbsolutePath());
                showAlert("Success", "Default cover page template created.");
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Could not generate cover page: " + e.getMessage());
            }
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

        Map<String, String> genderMap = new HashMap<>();
        String[] gLines = gendersData.split("\n");
        for (String line : gLines) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                genderMap.put(parts[0].trim(), parts[1].trim());
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
                String reqFeatures = readRequiredFeatures();
                Map<String, String> currentAliases = aliasesMap();
                currentStats = gitService.getContributorStats(repoDir, currentAliases, genderMap, ignoredExtensions, ignoredFolders, reqFeatures);
                currentMeaningfulAnalysis = gitService.performMeaningfulChangeAnalysis(repoDir, commitLimitSpinner.getValue(), ignoredFolders);
                List<CommitInfo> recentCommits = gitService.getLastCommits(repoDir, commitLimitSpinner.getValue(), currentAliases);
                CommitInfo initial = gitService.getInitialCommit(repoDir, currentAliases);

                databaseService.saveMetrics(currentStats);

                Platform.runLater(() -> {
                    List<ContributorStats> tableStats = groupOthers(currentStats, tableLimitSpinner.getValue());
                    statsTable.setItems(FXCollections.observableArrayList(tableStats));
                    updateCharts(currentStats, recentCommits);
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

        double avgMeaningful = others.stream().mapToDouble(ContributorStats::meaningfulChangeScore).average().orElse(0.0);

        Map<String, Integer> oLangs = new HashMap<>();
        others.forEach(s -> s.languageBreakdown().forEach((k, v) -> oLangs.merge(k, v, Integer::sum)));

        top.add(new ContributorStats("Others", "others@example.com", "unknown", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted, avgMeaningful));
        return top;
    }

    private void updateCharts(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        Platform.runLater(() -> {
            // Limited to Top 5 for visuals
            List<ContributorStats> top5 = stats.stream().limit(5).collect(Collectors.toList());

            // Pie Chart with percentages
            commitPieChart.getData().clear();
            commitPieChart.setAnimated(false);
            commitPieChart.setLabelsVisible(true); // Enable labels to show contributor names
            int totalCommits = stats.stream().mapToInt(s -> s.commitCount() + s.mergeCount()).sum();
            List<PieChart.Data> pieData = stats.stream()
                    .limit(10) // Show more on pie if labels are visible
                    .map(s -> {
                        double percentage = (totalCommits > 0) ? (double)(s.commitCount() + s.mergeCount()) / totalCommits * 100 : 0;
                        PieChart.Data data = new PieChart.Data(String.format("%s (%.1f%%)", s.name(), percentage), s.commitCount() + s.mergeCount());
                        return data;
                    })
                    .toList();
            commitPieChart.setData(FXCollections.observableArrayList(pieData));
            commitPieChart.setLegendVisible(false);

            // Stacked Bar Chart for Impact
            impactBarChart.getData().clear();
            impactBarChart.setAnimated(false); // Disable animations for snapshots
            XYChart.Series<String, Number> addedSeries = new XYChart.Series<>();
            addedSeries.setName("Added");
            XYChart.Series<String, Number> deletedSeries = new XYChart.Series<>();
            deletedSeries.setName("Deleted");

            for (ContributorStats s : top5) {
                // Ensure values are added as positive for stacked bar if we want them aligned at 0, 
                // but usually StackedBarChart handles positive/negative. 
                // User says "bars are not aligned at 0 they are floating too high". 
                // This might happen if there's a mix or if the axis is weird.
                // Let's ensure we are not adding negative values that cause floating if not intended.
                XYChart.Data<String, Number> addedData = new XYChart.Data<>(s.name(), Math.max(0, s.linesAdded()));
                XYChart.Data<String, Number> deletedData = new XYChart.Data<>(s.name(), Math.max(0, s.linesDeleted()));
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
                // recentCommits is newest first, so reverse it for chronological order
                List<CommitInfo> chronological = new ArrayList<>(recentCommits);
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
                
                // TreeMap keeps it chronological by LocalDate
                Map<java.time.LocalDate, Integer> dailyImpact = new TreeMap<>();
                for (CommitInfo ci : recentCommits) {
                    java.time.LocalDate date = ci.timestamp().toLocalDate();
                    dailyImpact.merge(date, ci.linesAdded(), Integer::sum);
                }
                
                for (Map.Entry<java.time.LocalDate, Integer> entry : dailyImpact.entrySet()) {
                    calSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
                }
                calendarActivityChart.getData().add(calSeries);
            }

            // Commits per Day Chart
            commitsPerDayChart.getData().clear();
            commitsPerDayChart.setAnimated(false);
            if (recentCommits != null && !recentCommits.isEmpty()) {
                XYChart.Series<String, Number> totalCpdSeries = new XYChart.Series<>();
                totalCpdSeries.setName("Total Commits");

                // TreeMap keeps it chronological by LocalDate
                Map<java.time.LocalDate, Integer> dailyTotalCommits = new TreeMap<>();
                for (CommitInfo ci : recentCommits) {
                    java.time.LocalDate date = ci.timestamp().toLocalDate();
                    dailyTotalCommits.merge(date, 1, Integer::sum);
                }

                for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
                    totalCpdSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
                }
                commitsPerDayChart.getData().add(totalCpdSeries);

                // Add per-contributor overlay (top 5)
                Map<java.time.LocalDate, Map<String, Integer>> dailyCommitsPerAuthor = new TreeMap<>();
                Set<String> topAuthorNames = stats.stream().limit(5).map(ContributorStats::name).collect(Collectors.toSet());

                for (CommitInfo ci : recentCommits) {
                    String author = ci.authorName();
                    if (!topAuthorNames.contains(author)) continue;
                    java.time.LocalDate date = ci.timestamp().toLocalDate();
                    dailyCommitsPerAuthor.computeIfAbsent(date, k -> new HashMap<>()).merge(author, 1, Integer::sum);
                }

                for (String author : topAuthorNames) {
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName(author);
                    for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
                        java.time.LocalDate date = entry.getKey();
                        int count = dailyCommitsPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                        if (count > 0) {
                            series.getData().add(new XYChart.Data<>(date.toString(), count));
                        }
                    }
                    commitsPerDayChart.getData().add(series);
                }
            }

            updateContributorActivityChart(stats, recentCommits);
            updateCpdPerContributorChart(stats, recentCommits);

            // Force layout pass and refresh to ensure charts are rendered correctly
            Platform.runLater(() -> {
                commitPieChart.layout();
                impactBarChart.layout();
                activityLineChart.layout();
                calendarActivityChart.layout();
                contributorActivityChart.layout();
                commitsPerDayChart.layout();
                
            // Explicitly show legends for ALL charts
            commitPieChart.setLegendVisible(false);
            impactBarChart.setLegendVisible(true);
            activityLineChart.setLegendVisible(true);
            calendarActivityChart.setLegendVisible(true);
            contributorActivityChart.setLegendVisible(true);
            commitsPerDayChart.setLegendVisible(true);

            // Set legend side to RIGHT for all except pie
            impactBarChart.setLegendSide(javafx.geometry.Side.RIGHT);
            activityLineChart.setLegendSide(javafx.geometry.Side.RIGHT);
            calendarActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
            contributorActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
            commitsPerDayChart.setLegendSide(javafx.geometry.Side.RIGHT);
                
                // Increase legend spacing/size if possible or ensure it has enough room
                // In JavaFX, Legend is a skin property, hard to access directly easily, 
                // but setting LegendSide to BOTTOM helps.
                
                // Adjust layout to avoid overlapping
                impactBarChart.requestLayout();
                activityLineChart.requestLayout();
                calendarActivityChart.requestLayout();
                contributorActivityChart.requestLayout();
                commitsPerDayChart.requestLayout();

                commitPieChart.requestLayout();
            });
        });
    }

    private void updateContributorActivityChart(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        contributorActivityChart.getData().clear();
        contributorActivityChart.setAnimated(false);
        if (recentCommits == null || recentCommits.isEmpty()) return;

        // TreeMap keeps it chronological by LocalDate
        Map<java.time.LocalDate, Integer> dailyTotalImpact = new TreeMap<>();
        Map<java.time.LocalDate, Map<String, Integer>> dailyImpactPerAuthor = new TreeMap<>();
        Set<String> topAuthorNames = stats.stream().limit(5).map(ContributorStats::name).collect(Collectors.toSet());

        for (CommitInfo ci : recentCommits) {
            java.time.LocalDate date = ci.timestamp().toLocalDate();
            dailyTotalImpact.merge(date, ci.linesAdded(), Integer::sum);
            
            String author = ci.authorName();
            if (topAuthorNames.contains(author)) {
                dailyImpactPerAuthor.computeIfAbsent(date, k -> new HashMap<>())
                                    .merge(author, ci.linesAdded(), Integer::sum);
            }
        }

        // Add Total series
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Impact");
        for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalImpact.entrySet()) {
            totalSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
        }
        contributorActivityChart.getData().add(totalSeries);

        // Initialize series for each top author
        for (String author : topAuthorNames) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(author);
            for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalImpact.entrySet()) {
                java.time.LocalDate date = entry.getKey();
                Integer impact = dailyImpactPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                if (impact > 0) {
                    series.getData().add(new XYChart.Data<>(date.toString(), impact));
                }
            }
            contributorActivityChart.getData().add(series);
        }
    }

    private void updateCpdPerContributorChart(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        cpdPerContributorChart.getData().clear();
        cpdPerContributorChart.setAnimated(false);
        if (recentCommits == null || recentCommits.isEmpty()) return;

        // TreeMap keeps it chronological by LocalDate
        Map<java.time.LocalDate, Integer> dailyTotalCommits = new TreeMap<>();
        Map<java.time.LocalDate, Map<String, Integer>> dailyCommitsPerAuthor = new TreeMap<>();
        Set<String> topAuthorNames = stats.stream().limit(5).map(ContributorStats::name).collect(Collectors.toSet());

        for (CommitInfo ci : recentCommits) {
            java.time.LocalDate date = ci.timestamp().toLocalDate();
            dailyTotalCommits.merge(date, 1, Integer::sum);
            
            String author = ci.authorName();
            if (topAuthorNames.contains(author)) {
                dailyCommitsPerAuthor.computeIfAbsent(date, k -> new HashMap<>())
                                     .merge(author, 1, Integer::sum);
            }
        }

        // Add Total series
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Commits");
        for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
            totalSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
        }
        cpdPerContributorChart.getData().add(totalSeries);

        // Initialize series for each top author
        for (String author : topAuthorNames) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(author);
            for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
                java.time.LocalDate date = entry.getKey();
                Integer count = dailyCommitsPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                if (count > 0) {
                    series.getData().add(new XYChart.Data<>(date.toString(), count));
                }
            }
            cpdPerContributorChart.getData().add(series);
        }
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
            File lineFile = new File("line_chart.png");
            File calendarFile = new File("calendar_chart.png");
            File contribFile = new File("contrib_activity.png");
            File cpdFile = new File("commits_per_day.png");
            
            saveNodeSnapshot(commitPieChart, pieFile);
            saveNodeSnapshot(impactBarChart, barFile);
            saveNodeSnapshot(activityLineChart, lineFile);
            saveNodeSnapshot(calendarActivityChart, calendarFile);
            saveNodeSnapshot(contributorActivityChart, contribFile);
            saveNodeSnapshot(commitsPerDayChart, cpdFile);

            // Re-take snapshots with much larger dimensions to prevent overlap and ensure high resolution
            // We temporarily increase the size, take snapshot, and restore (though restore is not strictly needed as it is just for export)
            // But actually, the UI sizes are already increased. Let's just use saveNodeSnapshot.
            
            String aiReport = null;
            if (aiReviewCheckBox.isSelected() && !llmResponseArea.getText().isEmpty() && !llmResponseArea.getText().startsWith("Generating report")) {
                aiReport = llmResponseArea.getText();
            }

            Map<String, String> mdSections = readMdSections();
            String coverHtml = null;
            String coverBasePath = null;
            if (coverPagePathField.getText() != null && !coverPagePathField.getText().isEmpty()) {
                File coverFile = new File(coverPagePathField.getText());
                if (coverFile.exists()) {
                    coverHtml = java.nio.file.Files.readString(coverFile.toPath());
                    coverHtml = coverHtml.replace("{{generated_on}}", java.time.LocalDate.now().toString())
                            .replace("{{user}}", System.getProperty("user.name"))
                            .replace("{{project}}", new File(repoPathField.getText()).getName());
                    
                    // Convert relative image paths to absolute paths so OpenPDF can find them
                    coverBasePath = coverFile.getParentFile().getAbsolutePath();
                    String pattern = "(<img\\s+[^>]*src=\")([^\"]+)(\"[^>]*>)";
                    java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
                    java.util.regex.Matcher m = r.matcher(coverHtml);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        String src = m.group(2);
                        if (!src.startsWith("http") && !src.startsWith("file:") && !new File(src).isAbsolute()) {
                            src = new File(coverBasePath, src).getAbsolutePath();
                        }
                        m.appendReplacement(sb, m.group(1) + src + m.group(3));
                    }
                    m.appendTail(sb);
                    coverHtml = sb.toString();
                }
            }

            exportService.exportToPdf(currentStats, gitService.getLastCommits(new File(repoPathField.getText()), commitLimitSpinner.getValue(), aliasesMap()), currentMeaningfulAnalysis, file.getAbsolutePath(), 
                pieFile.getAbsolutePath(), barFile.getAbsolutePath(), lineFile.getAbsolutePath(), 
                calendarFile.getAbsolutePath(), contribFile.getAbsolutePath(), cpdFile.getAbsolutePath(),
                null, // cpdPerContributorPath removed
                aiReport, mdSections, coverHtml, coverBasePath, tableLimitSpinner.getValue());
            
            // Cleanup temp files
            pieFile.delete();
            barFile.delete();
            lineFile.delete();
            calendarFile.delete();
            contribFile.delete();
            cpdFile.delete();

            Platform.runLater(() -> showAlert("Success", "Report exported to " + file.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showAlert("Error", "Export failed: " + e.getMessage()));
        }
    }

    private Map<String, String> aliasesMap() {
        Map<String, String> aliases = new HashMap<>();
        String[] lines = aliasesArea.getText().split("\n");
        for (String line : lines) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                aliases.put(parts[0].trim(), parts[1].trim());
            }
        }
        return aliases;
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
