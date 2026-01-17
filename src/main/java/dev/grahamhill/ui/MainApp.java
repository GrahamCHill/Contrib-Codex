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
        commitLimitSpinner = new Spinner<>(0, 10000, 100);
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
        nameCol.setCellValueFactory(data -> {
            String name = data.getValue().name();
            if (name.contains("<") && name.contains(">")) {
                name = name.substring(0, name.indexOf("<")).trim();
            }
            return new SimpleStringProperty(name);
        });
        nameCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        nameCol.setEditable(true);
        nameCol.setOnEditCommit(event -> {
            ContributorStats stat = event.getRowValue();
            updateContributorName(stat, event.getNewValue());
        });
        TableColumn<ContributorStats, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().email()));
        emailCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        emailCol.setEditable(true);
        emailCol.setOnEditCommit(event -> {
            ContributorStats stat = event.getRowValue();
            updateContributorEmail(stat, event.getNewValue());
        });
        
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
        aiCol.visibleProperty().bind(aiReviewCheckBox.selectedProperty());

        TableColumn<ContributorStats, String> scoreCol = new TableColumn<>("Meaningful Score");
        scoreCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.1f/100", data.getValue().meaningfulChangeScore())));
        scoreCol.visibleProperty().bind(aiReviewCheckBox.selectedProperty());

        statsTable.getColumns().addAll(nameCol, emailCol, genderCol, commitsCol, mergesCol, addedCol, deletedCol, fNewCol, fEditCol, fDelCol, languagesCol, aiCol, scoreCol);
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
        impactBarChart.setMinWidth(1770); // +5% of 1686
        impactBarChart.setPrefWidth(2186); // +5% of 2082
        impactBarChart.setMinHeight(990); 
        impactBarChart.setCategoryGap(20);
        impactBarChart.setLegendSide(javafx.geometry.Side.RIGHT);
        impactBarChart.setPadding(new Insets(0, 100, 0, 0)); // Extra internal right padding
        xAxis.setLabel("Contributor");
        xAxis.setTickLabelRotation(45); // Prevent overlap
        xAxis.setTickLabelGap(10);
        xAxis.setTickLength(10);
        yAxis.setLabel("Lines of Code");

        CategoryAxis lxAxis = new CategoryAxis();
        NumberAxis lyAxis = new NumberAxis();
        activityLineChart = new LineChart<>(lxAxis, lyAxis);
        activityLineChart.setTitle("Recent Commit Activity");
        activityLineChart.setMinWidth(1770);
        activityLineChart.setPrefWidth(2186);
        activityLineChart.setMinHeight(990);
        activityLineChart.setLegendSide(javafx.geometry.Side.RIGHT);
        activityLineChart.setPadding(new Insets(0, 100, 0, 0));
        lxAxis.setLabel("Commit ID");
        lxAxis.setTickLabelRotation(45); // Prevent overlap
        lxAxis.setTickLabelGap(10);
        lxAxis.setTickLength(10);
        lyAxis.setLabel("Lines Added");

        CategoryAxis cxAxis = new CategoryAxis();
        NumberAxis cyAxis = new NumberAxis();
        calendarActivityChart = new LineChart<>(cxAxis, cyAxis);
        calendarActivityChart.setTitle("Daily Activity (Total Impact)");
        calendarActivityChart.setMinWidth(1770);
        calendarActivityChart.setPrefWidth(2186);
        calendarActivityChart.setMinHeight(990);
        calendarActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        calendarActivityChart.setPadding(new Insets(0, 100, 0, 0));
        cxAxis.setLabel("Date");
        cxAxis.setTickLabelRotation(45); // Prevent overlap
        cxAxis.setTickLabelGap(10);
        cxAxis.setTickLength(10);
        cyAxis.setLabel("Total Lines Added");

        CategoryAxis caxAxis = new CategoryAxis();
        NumberAxis cayAxis = new NumberAxis();
        contributorActivityChart = new LineChart<>(caxAxis, cayAxis);
        contributorActivityChart.setTitle("Daily Activity per Contributor");
        contributorActivityChart.setMinWidth(1770);
        contributorActivityChart.setPrefWidth(2186);
        contributorActivityChart.setMinHeight(990);
        contributorActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        contributorActivityChart.setPadding(new Insets(0, 100, 0, 0));
        caxAxis.setLabel("Date");
        caxAxis.setTickLabelRotation(45);
        caxAxis.setTickLabelGap(10);
        caxAxis.setTickLength(10);
        cayAxis.setLabel("Lines Added");

        CategoryAxis cpdXAxis = new CategoryAxis();
        NumberAxis cpdYAxis = new NumberAxis();
        commitsPerDayChart = new LineChart<>(cpdXAxis, cpdYAxis);
        commitsPerDayChart.setTitle("Commits per Day");
        commitsPerDayChart.setMinWidth(1770);
        commitsPerDayChart.setPrefWidth(2186);
        commitsPerDayChart.setMinHeight(990);
        commitsPerDayChart.setLegendSide(javafx.geometry.Side.RIGHT);
        commitsPerDayChart.setPadding(new Insets(0, 100, 0, 0));
        cpdXAxis.setLabel("Date");
        cpdXAxis.setTickLabelRotation(45);
        cpdXAxis.setTickLabelGap(10);
        cpdXAxis.setTickLength(10);
        cpdYAxis.setLabel("Commit Count");

        CategoryAxis cpdPerXAxis = new CategoryAxis();
        NumberAxis cpdPerYAxis = new NumberAxis();
        cpdPerContributorChart = new LineChart<>(cpdPerXAxis, cpdPerYAxis);
        cpdPerContributorChart.setTitle("Commits per Day per Contributor");
        cpdPerContributorChart.setMinWidth(1770);
        cpdPerContributorChart.setPrefWidth(2186);
        cpdPerContributorChart.setMinHeight(990);
        cpdPerContributorChart.setLegendSide(javafx.geometry.Side.RIGHT);
        cpdPerContributorChart.setPadding(new Insets(0, 100, 0, 0));
        cpdPerXAxis.setLabel("Date");
        cpdPerXAxis.setTickLabelRotation(45);
        cpdPerXAxis.setTickLabelGap(10);
        cpdPerXAxis.setTickLength(10);
        cpdPerYAxis.setLabel("Commit Count");

        chartsBox.getChildren().addAll(commitPieChart, impactBarChart, activityLineChart, calendarActivityChart, contributorActivityChart, commitsPerDayChart, cpdPerContributorChart);
        HBox.setHgrow(commitPieChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(impactBarChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(activityLineChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(calendarActivityChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(contributorActivityChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(commitsPerDayChart, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(cpdPerContributorChart, javafx.scene.layout.Priority.ALWAYS);
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
        systemPromptArea = new TextArea("You are a senior software engineer, code auditor, and repository forensics analyst.\n" +
                "Analyze the GIT METRICS provided below and produce a professional technical audit report.\n" +
                "\n" +
                "CRITICAL CONSTRAINTS (non-negotiable):\n" +
                "1) Use ONLY the contributor names, commit refs, file paths, and numeric values explicitly provided in the METRICS.\n" +
                "   - Do NOT invent or assume any contributor, commit id, branch name, tag, file name, or metric.\n" +
                "   - If a value is missing from METRICS, write \"Not provided in metrics\" (do not guess).\n" +
                "2) All rankings (highest/lowest) MUST be numerically correct.\n" +
                "   - CONSISTENCY CHECK: Before writing conclusions, verify that \"highest\" corresponds to the largest numeric value and \"lowest\" to the smallest.\n" +
                "   - If the provided METRICS labels contradict the numbers, explicitly flag it as: \"Metrics inconsistency detected\" and correct the ranking using the numeric values.\n" +
                "3) Do not repeat the same points across sections. Prefer dense, high-signal writing.\n" +
                "\n" +
                "RISK MODEL (Lines Added per Commit):\n" +
                "- Compute lines_added_per_commit = total_lines_added / total_commits (per contributor).\n" +
                "- Higher lines_added_per_commit = HIGHER RISK. Lower = more iterative, lower risk.\n" +
                "- DO NOT use file length as risk. Risk is based on changed lines per commit.\n" +
                "- REFACTORING VS BLOAT:\n" +
                "  - Refactoring (high deletions relative to additions) is NOT bloat. It reduces future technical debt.\n" +
                "  - If a contributor has significant deletions (e.g., deletions > 50% of additions), acknowledge this as high-value refactoring and adjust risk downward.\n" +
                "- MERGE COMMITS:\n" +
                "  - Flag merge commits (multi-parent) as distinct from regular commits. They often represent integration rather than new code bloat.\n" +
                "- Reduce risk if tests are present:\n" +
                "  - If contributor changed files in test folders (\"test\", \"tests\", \"__tests__\"), apply a risk reduction and note it.\n" +
                "- Contextual adjustment:\n" +
                "  - Initial project scaffolding can be less risky IF changes are mostly config/docs/build setup; do NOT apply this adjustment to core logic changes.\n" +
                "\n" +
                "RISK SCALE (Lines Added per Commit):\n" +
                "- 1500+: VERY HIGH RISK\n" +
                "- 1000–1500: HIGH RISK\n" +
                "- 750–1000: MEDIUM–HIGH RISK\n" +
                "- 500–750: MEDIUM RISK\n" +
                "- 250–500: LOW–MEDIUM RISK\n" +
                "- <250: LOW RISK (healthy iterative development)\n" +
                "\n" +
                "PRONOUN RULE:\n" +
                "Use the \"Gender\" field exactly:\n" +
                "- male -> he/him\n" +
                "- female -> she/her\n" +
                "- non-binary/they/unknown -> they/them\n" +
                "\n" +
                "REPORT OUTPUT REQUIREMENTS:\n" +
                "- Use clear headings and tables.\n" +
                "- Use metrics-driven, evidence-based language.\n" +
                "- Every claim must cite the metric that supports it (e.g., \"X lines/commit\", \"Y commits\", \"Top changed files: ...\").\n" +
                "- If you mention any commit ref or tag, you MUST also state the commit author(s) exactly as listed in METRICS.\n" +
                "- Explicitly distinguish between regular commits and merge commits in your analysis.\n" +
                "\n" +
                "MANDATORY SECTIONS:\n" +
                "1) Executive Summary\n" +
                "   - Key findings (3–7 bullets)\n" +
                "   - Highest risk contributor (numerically verified)\n" +
                "   - Most iterative / lowest risk contributor (numerically verified)\n" +
                "   - Refactoring impact: Who is reducing technical debt?\n" +
                "   - Any suspicious patterns (bulk changes, generated artifacts, formatting churn)\n" +
                "\n" +
                "2) Repository Overview\n" +
                "   - Total commits, contributors, time range (if provided)\n" +
                "   - High-level change distribution by file type/location (if provided)\n" +
                "\n" +
                "3) Contributor Breakdown (one section per contributor)\n" +
                "   Include a table with:\n" +
                "   - total_commits (regular)\n" +
                "   - total_merges\n" +
                "   - total_lines_added\n" +
                "   - total_lines_deleted (refactoring indicator)\n" +
                "   - lines_added_per_commit\n" +
                "   - tests_touched (yes/no + count if available)\n" +
                "   - top directories / file types modified\n" +
                "   Then provide:\n" +
                "   - impact summary (what areas they changed)\n" +
                "   - code quality signals (tests, granularity, churn, refactoring activity)\n" +
                "   - risk rating + justification (considering refactoring as a positive factor)\n" +
                "\n" +
                "4) Suspicious / Low-Signal Change Detection\n" +
                "   - Identify bulk/generated artifacts (e.g., dist/, build/, *.map, minified, lockfiles)\n" +
                "   - Formatting/whitespace churn (if diff -w metrics are provided)\n" +
                "   - Rename/move-only patterns (if name-status metrics are provided)\n" +
                "\n" +
                "5) Requirements Alignment (if requirements are present in METRICS)\n" +
                "   - Map contributor changes to requirements\n" +
                "   - Note gaps or misalignment\n" +
                "\n" +
                "6) Final Conclusions\n" +
                "   - Most valuable contributor = lowest lines_added_per_commit + quality signals + refactoring impact + requirements alignment\n" +
                "   - Specific next actions for review/testing\n");
        systemPromptArea.setPrefHeight(60);
        userPromptArea = new TextArea("Summarize overall team performance and identify key contributors using ONLY the provided METRICS.\n" +
                "Provide a professional technical report with clear Markdown sections and detailed tables (high-signal, not repetitive).\n" +
                "\n" +
                "NON-NEGOTIABLE RULES:\n" +
                "- Use ONLY contributor names, commit refs, file paths, and numeric values explicitly provided in METRICS.\n" +
                "- Do NOT invent/hallucinate names, commits, metrics, tags, branches, or files.\n" +
                "- If a value is missing, write: \"Not provided in metrics\" (do not guess).\n" +
                "- CONSISTENCY CHECK: Any \"highest/lowest\" ranking MUST be numerically correct. If labels conflict with numbers, flag it as:\n" +
                "  \"Metrics inconsistency detected\" and correct the ranking based on numeric values.\n" +
                "\n" +
                "REFACTORING VS BLOAT:\n" +
                "- Acknowledge refactoring (high deletions) as a positive quality signal that reduces technical debt.\n" +
                "- Distinguish between new code bloat and high-value architectural cleanup.\n" +
                "\n" +
                "MERGE COMMITS:\n" +
                "- Flag merge commits as distinct events that represent integration rather than individual code contribution spikes.\n" +
                "\n" +
                "RISK ASSESSMENT:\n" +
                "- Calculate risk using: lines_added_per_commit = total_lines_added / total_commits (per contributor).\n" +
                "- Higher lines_added_per_commit = HIGHER RISK (mathematically).\n" +
                "- Apply the defined risk scale exactly.\n" +
                "- Adjust risk downward if tests are present (changes in folders containing: test, tests, __tests__).\n" +
                "- Consider language/file context and project phase (initial scaffolding can be lower risk only if changes are mostly config/docs/build setup, not core logic).\n" +
                "\n" +
                "OUTPUT REQUIREMENTS:\n" +
                "- Use Markdown headings and tables.\n" +
                "- Explain reasoning behind risk assessments with metric-backed evidence.\n" +
                "- Avoid repetition across sections.\n" +
                "\n" +
                "MANDATORY FINAL SECTION:\n" +
                "- Include a \"Conclusion\" section identifying the most valuable contributor based on:\n" +
                "  iterative development (LOW lines_added_per_commit), quality signals (tests, granularity, refactoring), and requirements alignment (if provided).\n");
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

    private void updateContributorName(ContributorStats stat, String newName) {
        String currentAliases = aliasesArea.getText();
        String mapping = stat.email() + "=" + newName;
        
        String[] lines = currentAliases.split("\n");
        StringBuilder newAliases = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith(stat.email() + "=")) {
                newAliases.append(mapping).append("\n");
                found = true;
            } else {
                newAliases.append(line).append("\n");
            }
        }
        if (!found) {
            newAliases.append(mapping).append("\n");
        }
        aliasesArea.setText(newAliases.toString().trim());
        saveSettings();
        analyzeRepo();
    }

    private void updateContributorEmail(ContributorStats stat, String newEmail) {
        // We can use the same aliasing logic to override/map emails.
        // User said: "allow setting of their email address as they may use different email addresses to what git uses"
        // If we put it in the aliasesArea as email=combinedName, it might not work if they just want to change email.
        // Let's create a separate email override map.
        String currentEmails = prefs().get("emailOverrides", "");
        String mapping = stat.name() + "=" + newEmail; // Use name as key to override email
        
        String[] lines = currentEmails.split("\n");
        StringBuilder newEmails = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith(stat.name() + "=")) {
                newEmails.append(mapping).append("\n");
                found = true;
            } else {
                newEmails.append(line).append("\n");
            }
        }
        if (!found) {
            newEmails.append(mapping).append("\n");
        }
        prefs().put("emailOverrides", newEmails.toString().trim());
        analyzeRepo();
    }

    private java.util.prefs.Preferences prefs() {
        return java.util.prefs.Preferences.userNodeForPackage(MainApp.class);
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

        // Load email overrides for prompt consistency
        Map<String, String> emailOverrides = new HashMap<>();
        String overridesStr = prefs().get("emailOverrides", "");
        for (String line : overridesStr.split("\n")) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                emailOverrides.put(parts[0].trim(), parts[1].trim());
            }
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
            String name = s.name();
            if (name.contains("<") && name.contains(">")) {
                name = name.substring(0, name.indexOf("<")).trim();
            }
            String email = emailOverrides.getOrDefault(s.name(), s.email());
            metricsText.append(String.format("- %s (%s, %s):\n", name, email, s.gender()));
            metricsText.append(String.format("  Stats: %d commits, %d merges, +%d/-%d lines, %d new/%d edited/%d deleted files\n",
                s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), 
                s.filesAdded(), s.filesEdited(), s.filesDeletedCount()));
            metricsText.append(String.format("  Risk Profile: AI Probability %.1f%%, Meaningful Score %.1f/100%s\n",
                s.averageAiProbability() * 100, s.meaningfulChangeScore(), hasTests ? " [INCLUDES TESTS]" : ""));
            metricsText.append("  Language breakdown: ").append(s.languageBreakdown()).append("\n");
        }

        metricsText.append("\nREPOSITORY SUMMARY METRICS:\n");
        if (currentMeaningfulAnalysis != null) {
            metricsText.append(String.format("Total Range: %s\n", currentMeaningfulAnalysis.commitRange()));
            metricsText.append(String.format("Total Insertions: %d, Total Deletions: %d, Whitespace Churn: %d\n",
                currentMeaningfulAnalysis.totalInsertions(), currentMeaningfulAnalysis.totalDeletions(), 
                currentMeaningfulAnalysis.whitespaceChurn()));
            
            metricsText.append("Category Breakdown:\n");
            currentMeaningfulAnalysis.categoryBreakdown().forEach((cat, m) -> {
                if (m.fileCount() > 0) {
                    metricsText.append(String.format("  * %s: %d files, +%d/-%d lines\n", cat, m.fileCount(), m.insertions(), m.deletions()));
                }
            });
            
            if (!currentMeaningfulAnalysis.warnings().isEmpty()) {
                metricsText.append("Structural Observations: ").append(String.join("; ", currentMeaningfulAnalysis.warnings())).append("\n");
            }

            metricsText.append("Top 50 Impactful Files:\n");
            currentMeaningfulAnalysis.topChangedFiles().stream().limit(50).forEach(f -> {
                metricsText.append(String.format("  * %s (+%d/-%d) [%s] Type: %s\n", f.path(), f.insertions(), f.deletions(), f.category(), f.changeType()));
            });
        }

        // Add full commit history for context
        try {
            List<CommitInfo> allCommits = gitService.getLastCommits(repoDir, 1000, aliasesMap());
            metricsText.append("\nCOMPLETE COMMIT HISTORY (LATEST 1000 COMMITS, LATEST FIRST):\n");
            for (CommitInfo ci : allCommits) {
                metricsText.append(String.format("[%s] %s: %s (%s) +%d/-%d l, %d n/%d e/%d d f, AI: %.0f%%\n",
                    ci.id(), ci.authorName(), ci.message(), formatLanguages(ci.languageBreakdown()),
                    ci.linesAdded(), ci.linesDeleted(), ci.filesAdded(), ci.filesEdited(), ci.filesDeleted(),
                    ci.aiProbability() * 100));
            }
        } catch (Exception e) {
            metricsText.append("\nCould not retrieve commit history: ").append(e.getMessage()).append("\n");
        }

        metricsText.append("\nRISK RULES: CALCULATE 'Lines Added/Commit' = (Total Lines Added / Total Commits).\n");
        metricsText.append("Scale: 1500+ VERY HIGH, 1000-1500 HIGH, 750-1000 MED-HIGH, 500-750 MED, 250-500 LOW-MED, <250 LOW.\n");
        metricsText.append("Higher = Higher Risk. Don't subtract deletions.\n");

        String reqFeatures = readRequiredFeatures();
        if (!reqFeatures.isEmpty()) {
            metricsText.append("\nFeatures:\n").append(reqFeatures).append("\n");
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
                        String formattedTitle = formatSectionTitle(sectionTitle);
                        String sectionInstructions = entry.getValue();
                        
                        String basePrompt = userPromptArea.getText() + "\n\n" + 
                                       "FOCUS SECTION: " + formattedTitle + "\n" +
                                       "SECTION INSTRUCTIONS: " + sectionInstructions + "\n\n" +
                                       "IMPORTANT: Provide ONLY the content for this section as defined by the instructions. " +
                                       "Do not include information that belongs in other sections. " +
                                       "Use the provided metrics to inform your analysis for this specific section.\n" +
                                       "HEADER NESTING: The application will prepend a top-level header (# " + formattedTitle + ") for this section. " +
                                       "Ensure all headers in your response use at least TWO hashes (##) so they are correctly nested under the section header.";
                        
                        // No more chunking metrics. Pass full baseMetrics.
                        String fullPrompt = basePrompt + "\n\n" + baseMetrics;
                                           
                        String sectionResponse = callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), fullPrompt);
                        
                        // Sanitize sectionResponse to remove markdown code blocks and duplicate titles
                        sectionResponse = sectionResponse.replaceAll("```markdown", "").replaceAll("```", "").trim();
                        
                        // Remove repeated section titles if the LLM provided them at the start of the response
                        String trimmedResponse = sectionResponse;
                        
                        // More aggressive multi-line title removal
                        String[] responseLines = sectionResponse.split("\n", 5);
                        boolean titleFound = false;
                        String normalizedTitle = formattedTitle.toLowerCase().trim();
                        
                        for (int lineIdx = 0; lineIdx < Math.min(responseLines.length, 3); lineIdx++) {
                            String line = responseLines[lineIdx].trim().toLowerCase();
                            if (line.isEmpty()) continue;
                            
                            // Check for common header formats
                            if (line.startsWith("#")) {
                                line = line.replaceAll("^#+\\s*", "");
                            }
                            
                            if (line.equals(normalizedTitle) || line.contains(normalizedTitle)) {
                                // Found the title, let's remove everything up to this line + next empty line
                                int charsToSkip = 0;
                                for (int j = 0; j <= lineIdx; j++) {
                                    charsToSkip += responseLines[j].length() + 1;
                                }
                                if (sectionResponse.length() > charsToSkip) {
                                    trimmedResponse = sectionResponse.substring(charsToSkip).trim();
                                } else {
                                    trimmedResponse = "";
                                }
                                titleFound = true;
                                break;
                            }
                        }
                        
                        if (!titleFound) {
                            // Fallback to simpler check if multi-line check didn't find it
                            String normalizedResponse = sectionResponse.toLowerCase();
                            if (normalizedResponse.startsWith("# " + normalizedTitle) || 
                                normalizedResponse.startsWith("## " + normalizedTitle) ||
                                normalizedResponse.startsWith("### " + normalizedTitle) ||
                                normalizedResponse.startsWith("#### " + normalizedTitle)) {
                                int firstNewline = sectionResponse.indexOf("\n");
                                if (firstNewline != -1) {
                                    trimmedResponse = sectionResponse.substring(firstNewline + 1).trim();
                                }
                            }
                        }
                        
                        // Shift headers in sectionResponse
                        sectionResponse = demoteMarkdownHeaders(sectionResponse);

                        // Add the section title
                        fullReport.append("# ").append(formattedTitle).append("\n\n");
                        fullReport.append(sectionResponse).append("\n\n");
                        
                        String progressMsg = String.format("Generated section: %s...", formattedTitle);
                        Platform.runLater(() -> llmResponseArea.setText(progressMsg));
                    }
                } else {
                    // Default behavior if no MD sections
                    String response = callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), userPromptArea.getText() + "\n\n" + baseMetrics);
                    response = response.replaceAll("```markdown", "").replaceAll("```", "").trim();
                    fullReport.append(response).append("\n\n");
                    
                    Platform.runLater(() -> llmResponseArea.setText("Generating report..."));
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

    private String formatSectionTitle(String title) {
        if (title == null || title.isEmpty()) return "";
        String formatted = title.replace("_", " ");
        // Put a fullstop after the first number that appears
        formatted = formatted.replaceFirst("(\\d+)", "$1.");
        return formatted;
    }

    private String demoteMarkdownHeaders(String content) {
        if (content == null || content.isEmpty()) return "";
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("#")) {
                result.append("#").append(line).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }
        return result.toString().trim();
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
            File templateFolder = new File("default_markdown");
            if (templateFolder.exists() && templateFolder.isDirectory()) {
                File[] templates = templateFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".md"));
                if (templates != null) {
                    for (File template : templates) {
                        java.nio.file.Files.copy(template.toPath(), new File(folder, template.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } else {
                // Fallback to hardcoded defaults if folder doesn't exist
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
                    "- Detail the 'Meaningful Change' score logic:\n" +
                    "  - Repository-wide score: Weighted by Source Code (70%) and Tests (30%) insertions.\n" +
                    "  - Contributor-level score: Evaluates iterative development (bonus for <250-500 lines per commit), testing activity, and requirements alignment.\n" +
                    "  - Filters out boilerplate, generated artifacts, and documentation noise.");

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
            }

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
                
                // Load email overrides
                Map<String, String> emailOverrides = new HashMap<>();
                String overridesStr = prefs().get("emailOverrides", "");
                for (String line : overridesStr.split("\n")) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        emailOverrides.put(parts[0].trim(), parts[1].trim());
                    }
                }

                currentStats = gitService.getContributorStats(repoDir, currentAliases, genderMap, ignoredExtensions, ignoredFolders, reqFeatures);
                
                // Apply email overrides to stats
                currentStats = currentStats.stream().map(s -> {
                    if (emailOverrides.containsKey(s.name())) {
                        return new ContributorStats(s.name(), emailOverrides.get(s.name()), s.gender(), 
                            s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), 
                            s.languageBreakdown(), s.averageAiProbability(), s.filesAdded(), 
                            s.filesEdited(), s.filesDeletedCount(), s.meaningfulChangeScore());
                    }
                    return s;
                }).collect(Collectors.toList());

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
                    
                        String authorName = ci.authorName();
                        if (authorName.contains("<") && authorName.contains(">")) {
                            authorName = authorName.substring(0, authorName.indexOf("<")).trim();
                        }
                    
                        commitList.getItems().add(String.format("[%s] %s: %s (%s) %s", ci.id(), authorName, ci.message(), langStr, aiStr));
                    }
                    if (initial != null) {
                        String initialAuthor = initial.authorName();
                        if (initialAuthor.contains("<") && initialAuthor.contains(">")) {
                            initialAuthor = initialAuthor.substring(0, initialAuthor.indexOf("<")).trim();
                        }
                        initialCommitLabel.setText("Initial: [" + initial.id() + "] by " + initialAuthor + " (" + formatLanguages(initial.languageBreakdown()) + ") " + String.format("[AI: %.0f%%]", initial.aiProbability() * 100));
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
                        String displayName = s.name();
                        if (displayName.contains("<") && displayName.contains(">")) {
                            displayName = displayName.substring(0, displayName.indexOf("<")).trim();
                        }
                        PieChart.Data data = new PieChart.Data(String.format("%s (%.1f%%)", displayName, percentage), s.commitCount() + s.mergeCount());
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
                String displayName = s.name();
                if (displayName.contains("<") && displayName.contains(">")) {
                    displayName = displayName.substring(0, displayName.indexOf("<")).trim();
                }
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
                TreeMap<java.time.LocalDate, Integer> dailyImpact = new TreeMap<>();
                for (CommitInfo ci : recentCommits) {
                    java.time.LocalDate date = ci.timestamp().toLocalDate();
                    dailyImpact.merge(date, ci.linesAdded(), Integer::sum);
                }
                
                // Ensure all dates in the range are present with 0 if no activity
                if (!dailyImpact.isEmpty()) {
                    java.time.LocalDate firstDate = dailyImpact.firstKey();
                    java.time.LocalDate lastDate = dailyImpact.lastKey();
                    java.time.LocalDate current = firstDate;
                    while (!current.isAfter(lastDate)) {
                        dailyImpact.putIfAbsent(current, 0);
                        current = current.plusDays(1);
                    }
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
                TreeMap<java.time.LocalDate, Integer> dailyTotalCommits = new TreeMap<>();
                for (CommitInfo ci : recentCommits) {
                    java.time.LocalDate date = ci.timestamp().toLocalDate();
                    dailyTotalCommits.merge(date, 1, Integer::sum);
                }

                // Ensure all dates in the range are present with 0 if no activity
                if (!dailyTotalCommits.isEmpty()) {
                    java.time.LocalDate first = dailyTotalCommits.firstKey();
                    java.time.LocalDate last = dailyTotalCommits.lastKey();
                    java.time.LocalDate curr = first;
                    while (!curr.isAfter(last)) {
                        dailyTotalCommits.putIfAbsent(curr, 0);
                        curr = curr.plusDays(1);
                    }
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
                    String displayName = author;
                    if (displayName.contains("<") && displayName.contains(">")) {
                        displayName = displayName.substring(0, displayName.indexOf("<")).trim();
                    }
                    series.setName(displayName);
                    for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
                        java.time.LocalDate date = entry.getKey();
                        int count = dailyCommitsPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                        series.getData().add(new XYChart.Data<>(date.toString(), count));
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
                cpdPerContributorChart.layout();
                
            // Explicitly show legends for ALL charts
            commitPieChart.setLegendVisible(false);
            impactBarChart.setLegendVisible(true);
            activityLineChart.setLegendVisible(true);
            calendarActivityChart.setLegendVisible(true);
            contributorActivityChart.setLegendVisible(true);
            commitsPerDayChart.setLegendVisible(true);
            cpdPerContributorChart.setLegendVisible(true);

            // Set legend side to RIGHT for all except pie
            impactBarChart.setLegendSide(javafx.geometry.Side.RIGHT);
            activityLineChart.setLegendSide(javafx.geometry.Side.RIGHT);
            calendarActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
            contributorActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
            commitsPerDayChart.setLegendSide(javafx.geometry.Side.RIGHT);
            cpdPerContributorChart.setLegendSide(javafx.geometry.Side.RIGHT);

            // Ensure charts have enough internal padding for legends
            impactBarChart.setPadding(new Insets(10, 400, 10, 10));
            activityLineChart.setPadding(new Insets(10, 400, 10, 10));
            calendarActivityChart.setPadding(new Insets(10, 400, 10, 10));
            contributorActivityChart.setPadding(new Insets(10, 400, 10, 10));
            commitsPerDayChart.setPadding(new Insets(10, 400, 10, 10));
            cpdPerContributorChart.setPadding(new Insets(10, 400, 10, 10));
                
            // Adjust layout to avoid overlapping
            impactBarChart.requestLayout();
                activityLineChart.requestLayout();
                calendarActivityChart.requestLayout();
                contributorActivityChart.requestLayout();
                commitsPerDayChart.requestLayout();
                cpdPerContributorChart.requestLayout();

                commitPieChart.requestLayout();
            });
        });
    }

    private void updateContributorActivityChart(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        contributorActivityChart.getData().clear();
        contributorActivityChart.setAnimated(false);
        if (recentCommits == null || recentCommits.isEmpty()) return;

        // TreeMap keeps it chronological by LocalDate
        TreeMap<java.time.LocalDate, Integer> dailyTotalImpact = new TreeMap<>();
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

        // Ensure all dates in range are present
        if (!dailyTotalImpact.isEmpty()) {
            java.time.LocalDate first = dailyTotalImpact.firstKey();
            java.time.LocalDate last = dailyTotalImpact.lastKey();
            java.time.LocalDate curr = first;
            while (!curr.isAfter(last)) {
                dailyTotalImpact.putIfAbsent(curr, 0);
                curr = curr.plusDays(1);
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
            String displayName = author;
            if (displayName.contains("<") && displayName.contains(">")) {
                displayName = displayName.substring(0, displayName.indexOf("<")).trim();
            }
            series.setName(displayName);
            for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalImpact.entrySet()) {
                java.time.LocalDate date = entry.getKey();
                Integer impact = dailyImpactPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                series.getData().add(new XYChart.Data<>(date.toString(), impact));
            }
            contributorActivityChart.getData().add(series);
        }
    }

    private void updateCpdPerContributorChart(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        cpdPerContributorChart.getData().clear();
        cpdPerContributorChart.setAnimated(false);
        if (recentCommits == null || recentCommits.isEmpty()) return;

        // TreeMap keeps it chronological by LocalDate
        TreeMap<java.time.LocalDate, Integer> dailyTotalCommits = new TreeMap<>();
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

        // Ensure all dates in range are present
        if (!dailyTotalCommits.isEmpty()) {
            java.time.LocalDate first = dailyTotalCommits.firstKey();
            java.time.LocalDate last = dailyTotalCommits.lastKey();
            java.time.LocalDate curr = first;
            while (!curr.isAfter(last)) {
                dailyTotalCommits.putIfAbsent(curr, 0);
                curr = curr.plusDays(1);
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
            String displayName = author;
            if (displayName.contains("<") && displayName.contains(">")) {
                displayName = displayName.substring(0, displayName.indexOf("<")).trim();
            }
            series.setName(displayName);
            for (Map.Entry<java.time.LocalDate, Integer> entry : dailyTotalCommits.entrySet()) {
                java.time.LocalDate date = entry.getKey();
                Integer count = dailyCommitsPerAuthor.getOrDefault(date, Collections.emptyMap()).getOrDefault(author, 0);
                series.getData().add(new XYChart.Data<>(date.toString(), count));
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
            File cpdPerFile = new File("cpd_per_contributor.png");
            saveNodeSnapshot(cpdPerContributorChart, cpdPerFile);
            
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
                cpdPerFile.getAbsolutePath(), 
                aiReport, mdSections, coverHtml, coverBasePath, tableLimitSpinner.getValue());
            
            // Cleanup temp files
            pieFile.delete();
            barFile.delete();
            lineFile.delete();
            calendarFile.delete();
            contribFile.delete();
            cpdFile.delete();
            cpdPerFile.delete();

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
