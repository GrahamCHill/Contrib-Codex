package dev.grahamhill.ui;

import dev.grahamhill.model.CompanyMetric;
import dev.grahamhill.model.CommitInfo;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;
import dev.grahamhill.model.ReportHistory;
import dev.grahamhill.service.DatabaseService;
import dev.grahamhill.service.EncryptionService;
import dev.grahamhill.service.ExportService;
import dev.grahamhill.service.GitService;
import dev.grahamhill.util.ConfigObfuscator;
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

import dev.grahamhill.service.*;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;

public class MainApp extends Application {

    private final GitService gitService = new GitService();
    private DatabaseService databaseService;
    private final ExportService exportService = new ExportService();
    private EncryptionService encryptionService;
    private final LlmService llmService = new LlmService();
    private final ConfigManager configManager = new ConfigManager();
    private final ChartManager chartManager = new ChartManager();

    private TableView<ContributorStats> statsTable;
    private ListView<String> commitList;
    private Label initialCommitLabel;
    private TextField repoPathField;
    private TextField mainBranchField;
    private TextField manualVersionField;
    private TextArea manualDescriptionArea;
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
    private PieChart languagePieChart;
    private PieChart devPieChart;
    private PieChart projectLangPieChart;
    private StackedBarChart<String, Number> contribLanguageBarChart;
    private LineChart<String, Number> commitsPerDayLineChart;
    private StackedBarChart<String, Number> impactBarChart;
    private LineChart<String, Number> activityLineChart;
    private LineChart<String, Number> calendarActivityChart;
    private LineChart<String, Number> contributorActivityChart;

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
    private List<String> availableGroqModels = new ArrayList<>();
    private String selectedProvider = "OpenAI";

    private CheckBox aiReviewCheckBox;
    private TabPane mainTopTabPane;
    private Tab repoModeTab;
    private Tab companyReviewTab;
    private TabPane statsTabPane;
    private Tab statsTab;
    private Tab visualsTab;
    private ListView<CompanyMetricSelection> repoSelectionList;
    private TextField companyReviewMdPathField;
    private VBox visualsOuterBox;
    private Tab companyBreakdownTab;
    private ListView<String> companyBreakdownList;

    private Map<String, String> envConfig = new HashMap<>();

    public static class CompanyMetricSelection {
        private final CompanyMetric metric;
        private final javafx.beans.property.BooleanProperty selected = new javafx.beans.property.SimpleBooleanProperty(true);

        public CompanyMetricSelection(CompanyMetric metric) {
            this.metric = metric;
        }

        public CompanyMetric getMetric() { return metric; }
        public boolean isSelected() { return selected.get(); }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean s) { selected.set(s); }
    }

    @Override
    public void start(Stage primaryStage) {
        loadDotenv();
        // Initialize UI components before loading settings to avoid NPE
        repoPathField = new TextField();
        mainBranchField = new TextField();
        mainBranchField.setPromptText("Main Branch (e.g. main, master)");
        mainBranchField.textProperty().addListener((obs, oldVal, newVal) -> analyzeRepo());
        commitLimitSpinner = new Spinner<>(0, 10000, 100);
        commitLimitSpinner.setEditable(true);
        commitLimitSpinner.valueProperty().addListener((obs, oldVal, newVal) -> analyzeRepo());
        tableLimitSpinner = new Spinner<>(1, 100, 20);
        tableLimitSpinner.setEditable(true);
        tableLimitSpinner.valueProperty().addListener((obs, oldVal, newVal) -> analyzeRepo());
        ignoredExtensionsField = new TextField("json,xml,csv,lock,txt,package-lock.json,yarn.lock,pnpm-lock.yaml");
        ignoredFoldersField = new TextField("node_modules,target,build,dist,.git");
        mdFolderPathField = new TextField();
        requiredFeaturesPathField = new TextField();
        coverPagePathField = new TextField();
        companyReviewMdPathField = new TextField();
        

        manualVersionField = new TextField();
        manualVersionField.setPromptText("Manual Version (e.g. 1.1 or 2.0)");
        manualDescriptionArea = new TextArea();
        manualDescriptionArea.setPromptText("Manual Description for this report generation");
        manualDescriptionArea.setPrefHeight(100);
        aliasesArea = new TextArea();

        // Initialize company-level charts here as well
        // We will initialize them later in the layout section to avoid duplication
        
        try {
            databaseService = new DatabaseService();
        } catch (Exception e) {
            System.err.println("Could not initialize DatabaseService: " + e.getMessage());
            e.printStackTrace();
        }

        try {
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
        mainBranchField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        aliasesArea.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        mdFolderPathField.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::saveSettings);
        });
        requiredFeaturesPathField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        coverPagePathField.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::saveSettings);
        });

        companyReviewMdPathField.textProperty().addListener((obs, oldVal, newVal) -> saveSettings());
        primaryStage.setTitle("Contrib Codex");
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Could not load app icon: " + e.getMessage());
        }

        BorderPane root = new BorderPane();
        
        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu settingsMenu = new Menu("Settings");
        MenuItem apiKeysItem = new MenuItem("API Keys...");
        apiKeysItem.setOnAction(e -> showApiKeysDialog());
        MenuItem genRepoMdItem = new MenuItem("Generate Repo MDs");
        genRepoMdItem.setOnAction(e -> generateRepoMds());
        MenuItem genCompanyMdItem = new MenuItem("Generate Company Review MDs");
        genCompanyMdItem.setOnAction(e -> generateCompanyReviewMds());
        MenuItem genDefaultCoverItem = new MenuItem("Generate Default Cover");
        genDefaultCoverItem.setOnAction(e -> generateDefaultCoverPage());
        MenuItem genFeaturesTemplateItem = new MenuItem("Generate Features Template");
        genFeaturesTemplateItem.setOnAction(e -> generateRequiredFeaturesTemplate());
        MenuItem dbLocationItem = new MenuItem("Open Database Location");
        dbLocationItem.setOnAction(e -> {
            String dbPath = DatabaseService.getDbPath();
            File dbFile = new File(dbPath);
            getHostServices().showDocument(dbFile.getParentFile().toURI().toString());
        });
        settingsMenu.getItems().addAll(apiKeysItem, new SeparatorMenuItem(), genRepoMdItem, genCompanyMdItem, genDefaultCoverItem, genFeaturesTemplateItem, new SeparatorMenuItem(), dbLocationItem);
        
        Menu infoMenu = new Menu("Info");
        MenuItem websiteItem = new MenuItem("Website");
        websiteItem.setOnAction(e -> getHostServices().showDocument(envConfig.getOrDefault("APP_WEBSITE_URL", "https://github.com/grahamhill/contrib_metric")));
        
        MenuItem aboutItem = new MenuItem("App Info");
        aboutItem.setOnAction(e -> showAppInfoDialog());
        
        infoMenu.getItems().addAll(websiteItem, aboutItem);

        menuBar.getMenus().addAll(settingsMenu, infoMenu);
        root.setTop(new VBox(menuBar));

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));
        root.setCenter(contentBox);

        mainTopTabPane = new TabPane();
        repoModeTab = new Tab("Repo Analysis");
        repoModeTab.setClosable(false);
        companyReviewTab = new Tab("Company Review");
        companyReviewTab.setClosable(false);
        mainTopTabPane.getTabs().addAll(repoModeTab, companyReviewTab);

        // Define results container 
        VBox sharedResultsBox = new VBox(10);

        // --- Repo Mode Content ---
        VBox repoModeBox = new VBox(10);
        repoModeBox.setPadding(new Insets(10));

        // Top: Repo Selection and Settings
        VBox topBox = new VBox(10);
        HBox repoBox = new HBox(10);
        repoPathField.setPrefWidth(400);
        mainBranchField.setPrefWidth(100);
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseRepo(primaryStage));
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> analyzeRepo());
        repoBox.getChildren().addAll(new Label("Repo Path:"), repoPathField, browseButton, analyzeButton, new Label("Main Branch:"), mainBranchField);

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
        mdFolderPathField.setPromptText("Path to repo_markdown folder");
        Button browseMdButton = new Button("Browse...");
        browseMdButton.setOnAction(e -> {
            browseMdFolder(primaryStage);
            saveSettings();
        });
        Button openMdButton = new Button("Open");
        openMdButton.setOnAction(e -> openMdFolder());
        settingsBox2.getChildren().addAll(
                new Label("Repo MD:"), mdFolderPathField, browseMdButton, openMdButton
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

        Button exportCsvButton = new Button("Export to CSV");
        exportCsvButton.setOnAction(e -> exportToCsv(primaryStage));
        
        settingsBox3.getChildren().addAll(
                new Label("Features:"), requiredFeaturesPathField, browseReqButton,
                new Label("Coverpage:"), coverPagePathField, browseCoverButton,
                aiReviewCheckBox,
                exportButton,
                exportCsvButton
        );

        VBox aliasBox = new VBox(5);
        aliasesArea.setPromptText("Enter email=Name mappings (one per line)");
        aliasesArea.setPrefHeight(60);
        aliasBox.getChildren().addAll(new Label("User Aliases (email=Combined Name):"), aliasesArea);
        HBox.setHgrow(aliasBox, javafx.scene.layout.Priority.ALWAYS);

        repoModeBox.getChildren().addAll(repoBox, settingsBox, settingsBox2, settingsBox3, aliasBox);
        repoModeTab.setContent(repoModeBox);

        // --- Company Mode Content ---
        VBox companyModeBox = new VBox(10);
        companyModeBox.setPadding(new Insets(10));
        
        HBox companyReviewActions = new HBox(10);
        Button loadCsvsButton = new Button("Load Metrics CSVs");
        loadCsvsButton.setOnAction(e -> loadCompanyCsvs(primaryStage));
        
        companyReviewMdPathField.setPromptText("Path to company_review markdown folder");
        companyReviewMdPathField.setPrefWidth(300);
        Button browseCompanyMdButton = new Button("Browse...");
        browseCompanyMdButton.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Company Review Markdown Folder");
            File selected = dc.showDialog(primaryStage);
            if (selected != null) {
                companyReviewMdPathField.setText(selected.getAbsolutePath());
                saveSettings();
            }
        });

        Button exportCompanyPdfButton = new Button("Export Company PDF");
        exportCompanyPdfButton.setOnAction(e -> exportCompanyToPdf(primaryStage));

        Button analyzeCompanyButton = new Button("Analyze Selected Repos");
        analyzeCompanyButton.setOnAction(e -> refreshCompanyReviewData(true));

        companyReviewActions.getChildren().addAll(loadCsvsButton, new Label("Company Review MD:"), companyReviewMdPathField, browseCompanyMdButton, analyzeCompanyButton, exportCompanyPdfButton);
        
        CheckBox selectAllCheckBox = new CheckBox("Select All");
        selectAllCheckBox.setSelected(true);

        repoSelectionList = new ListView<>();
        repoSelectionList.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            
            {
                // Unbind when cell is reused to avoid toggling multiple items
                itemProperty().addListener((obs, oldItem, newItem) -> {
                    if (oldItem != null) {
                        checkBox.selectedProperty().unbindBidirectional(oldItem.selectedProperty());
                    }
                    if (newItem != null) {
                        checkBox.selectedProperty().bindBidirectional(newItem.selectedProperty());
                    }
                });
            }

            @Override
            protected void updateItem(CompanyMetricSelection item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String repoName = new File(item.getMetric().repoName()).getName();
                    setText(repoName + " (" + item.getMetric().repoName() + ")");
                    setGraphic(checkBox);
                }
            }
        });
        repoSelectionList.setPrefHeight(150);

        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            for (CompanyMetricSelection item : repoSelectionList.getItems()) {
                item.setSelected(selected);
            }
        });

        companyModeBox.getChildren().addAll(new Label("Select Repositories for Company Review:"), selectAllCheckBox, repoSelectionList, companyReviewActions);
        
        // Add Company Review UI
        TabPane companyTabPane = new TabPane();
        Tab companyTableTab = new Tab("Repositories");
        companyTableTab.setClosable(false);
        VBox companyTableBox = new VBox(5, selectAllCheckBox, repoSelectionList);
        companyTableTab.setContent(companyTableBox);
        
        companyTabPane.getTabs().add(companyTableTab);
        companyModeBox.getChildren().clear();
        companyModeBox.getChildren().addAll(new Label("Company Review Dashboard:"), companyTabPane, companyReviewActions);
        VBox.setVgrow(companyTabPane, javafx.scene.layout.Priority.ALWAYS);

        companyReviewTab.setContent(companyModeBox);
        
        companyReviewTab.setOnSelectionChanged(e -> {
            if (companyReviewTab.isSelected()) {
                refreshCompanyReviewData(false);
            }
        });

        contentBox.getChildren().add(mainTopTabPane);

        // SplitPane for Main Content and LLM Panel
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        statsTabPane = new TabPane();
        statsTab = new Tab("Statistics");
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

        statsBox.getChildren().addAll(new Label("Top 10 Contributors (Double-click Gender to edit; used for LLM pronouns only):"), statsTable);
        VBox.setVgrow(statsTable, javafx.scene.layout.Priority.ALWAYS);
        statsTab.setContent(statsBox);

        visualsTab = new Tab("Visuals");
        visualsTab.setClosable(false);

        companyBreakdownTab = new Tab("Company Breakdown");
        companyBreakdownTab.setClosable(false);
        companyBreakdownList = new ListView<>();
        companyBreakdownTab.setContent(new VBox(10, new Label("Company Breakdown (Scrollable List):"), companyBreakdownList));
        VBox.setVgrow(companyBreakdownList, javafx.scene.layout.Priority.ALWAYS);
        
        visualsOuterBox = new VBox(5);
        HBox zoomControls = new HBox(10);
        zoomControls.setPadding(new Insets(5));
        zoomControls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Slider zoomSlider = new Slider(0.1, 2.0, 1.0);
        Label zoomLabel = new Label("Zoom: 100%");
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomLabel.setText(String.format("Zoom: %.0f%%", newVal.doubleValue() * 100));
        });
        zoomControls.getChildren().addAll(new Label("Zoom:"), zoomSlider, zoomLabel);

        commitPieChart = new PieChart();
        commitPieChart.setTitle("Commits by Contributor");
        commitPieChart.setMinWidth(1080);
        commitPieChart.setMaxWidth(1080);
        commitPieChart.setPrefWidth(1080);
        commitPieChart.setMinHeight(1080);
        commitPieChart.setMaxHeight(1080);
        commitPieChart.setPrefHeight(1080);
        commitPieChart.setLegendVisible(true);

        languagePieChart = new PieChart();
        languagePieChart.setTitle("Language Breakdown (Overall)");
        languagePieChart.setMinWidth(1080);
        languagePieChart.setMaxWidth(1080);
        languagePieChart.setPrefWidth(1080);
        languagePieChart.setMinHeight(1080);
        languagePieChart.setMaxHeight(1080);
        languagePieChart.setPrefHeight(1080);
        languagePieChart.setLabelsVisible(true);
        languagePieChart.setLabelLineLength(25);
        languagePieChart.setLegendVisible(true);

        CategoryAxis clX = new CategoryAxis();
        NumberAxis clY = new NumberAxis();
        contribLanguageBarChart = new StackedBarChart<>(clX, clY);
        contribLanguageBarChart.setTitle("Languages by Contributor");
        contribLanguageBarChart.setMinWidth(1080);
        contribLanguageBarChart.setMaxWidth(1080);
        contribLanguageBarChart.setPrefWidth(1080);
        contribLanguageBarChart.setMinHeight(1080);
        contribLanguageBarChart.setMaxHeight(1080);
        contribLanguageBarChart.setPrefHeight(1080);
        contribLanguageBarChart.setLegendVisible(true);
        clX.setLabel("Contributor");
        clY.setLabel("Files count");

        devPieChart = new PieChart();
        devPieChart.setTitle("Code by Developer");
        devPieChart.setMinWidth(1080);
        devPieChart.setMaxWidth(1080);
        devPieChart.setPrefWidth(1080);
        devPieChart.setMinHeight(1080);
        devPieChart.setMaxHeight(1080);
        devPieChart.setPrefHeight(1080);
        devPieChart.setLabelsVisible(true);
        devPieChart.setLegendVisible(true);

        projectLangPieChart = new PieChart();
        projectLangPieChart.setTitle("Language of Projects");
        projectLangPieChart.setMinWidth(1080);
        projectLangPieChart.setMaxWidth(1080);
        projectLangPieChart.setPrefWidth(1080);
        projectLangPieChart.setMinHeight(1080);
        projectLangPieChart.setMaxHeight(1080);
        projectLangPieChart.setPrefHeight(1080);
        projectLangPieChart.setLabelsVisible(true);
        projectLangPieChart.setLegendVisible(true);

        CategoryAxis cpdX = new CategoryAxis();
        NumberAxis cpdY = new NumberAxis();
        commitsPerDayLineChart = new LineChart<>(cpdX, cpdY);
        commitsPerDayLineChart.setTitle("Commits per Day");
        commitsPerDayLineChart.setMinWidth(1080);
        commitsPerDayLineChart.setMaxWidth(1080);
        commitsPerDayLineChart.setPrefWidth(1080);
        commitsPerDayLineChart.setMinHeight(1080);
        commitsPerDayLineChart.setMaxHeight(1080);
        commitsPerDayLineChart.setPrefHeight(1080);
        commitsPerDayLineChart.setLegendVisible(true);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        impactBarChart = new StackedBarChart<>(xAxis, yAxis);
        impactBarChart.setTitle("Impact (Lines Added/Deleted)");
        impactBarChart.setMinWidth(1080);
        impactBarChart.setMaxWidth(1080);
        impactBarChart.setPrefWidth(1080);
        impactBarChart.setMinHeight(1080);
        impactBarChart.setMaxHeight(1080);
        impactBarChart.setPrefHeight(1080);
        impactBarChart.setLegendSide(javafx.geometry.Side.RIGHT);
        xAxis.setLabel("Contributor");
        xAxis.setTickLabelRotation(45); // Prevent overlap
        xAxis.setTickLabelGap(20); // Increased from 10 to move labels down
        xAxis.setTickLength(10);
        yAxis.setLabel("Lines of Code");

        CategoryAxis lxAxis = new CategoryAxis();
        NumberAxis lyAxis = new NumberAxis();
        activityLineChart = new LineChart<>(lxAxis, lyAxis);
        activityLineChart.setTitle("Recent Commit Activity");
        activityLineChart.setMinWidth(1080);
        activityLineChart.setMaxWidth(1080);
        activityLineChart.setPrefWidth(1080);
        activityLineChart.setMinHeight(1080);
        activityLineChart.setMaxHeight(1080);
        activityLineChart.setPrefHeight(1080);
        activityLineChart.setLegendSide(javafx.geometry.Side.RIGHT);
        lxAxis.setLabel("Commit ID");
        lxAxis.setTickLabelRotation(45); // Prevent overlap
        lxAxis.setTickLabelGap(20);
        lxAxis.setTickLength(10);
        lyAxis.setLabel("Lines Added");

        CategoryAxis cxAxis = new CategoryAxis();
        NumberAxis cyAxis = new NumberAxis();
        calendarActivityChart = new LineChart<>(cxAxis, cyAxis);
        calendarActivityChart.setTitle("Daily Activity (Total Impact)");
        calendarActivityChart.setMinWidth(1080);
        calendarActivityChart.setMaxWidth(1080);
        calendarActivityChart.setPrefWidth(1080);
        calendarActivityChart.setMinHeight(1080);
        calendarActivityChart.setMaxHeight(1080);
        calendarActivityChart.setPrefHeight(1080);
        calendarActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        cxAxis.setLabel("Date");
        cxAxis.setTickLabelRotation(45); // Prevent overlap
        cxAxis.setTickLabelGap(20);
        cxAxis.setTickLength(10);
        cyAxis.setLabel("Total Lines Added");

        CategoryAxis caxAxis = new CategoryAxis();
        NumberAxis cayAxis = new NumberAxis();
        contributorActivityChart = new LineChart<>(caxAxis, cayAxis);
        contributorActivityChart.setTitle("Daily Activity per Contributor (Lines Added)");
        contributorActivityChart.setMinWidth(1080);
        contributorActivityChart.setMaxWidth(1080);
        contributorActivityChart.setPrefWidth(1080);
        contributorActivityChart.setMinHeight(1080);
        contributorActivityChart.setMaxHeight(1080);
        contributorActivityChart.setPrefHeight(1080);
        contributorActivityChart.setLegendSide(javafx.geometry.Side.RIGHT);
        caxAxis.setLabel("Date");
        caxAxis.setTickLabelRotation(45);
        caxAxis.setTickLabelGap(20);
        caxAxis.setTickLength(10);
        cayAxis.setLabel("Lines Added");



        ScrollPane visualsScrollPane = new ScrollPane();
        visualsScrollPane.setFitToWidth(true);
        visualsScrollPane.setFitToHeight(false);
        VBox chartsBox = new VBox(10);
        chartsBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        chartsBox.setPadding(new Insets(10));
        
        javafx.scene.Group zoomGroup = new javafx.scene.Group(chartsBox);
        visualsScrollPane.setContent(zoomGroup);
        
        javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(1, 1, 0, 0);
        zoomGroup.getTransforms().add(scale);
        scale.xProperty().bind(zoomSlider.valueProperty());
        scale.yProperty().bind(zoomSlider.valueProperty());

        visualsScrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                double zoomFactor = 1.05;
                if (delta < 0) zoomFactor = 1 / zoomFactor;
                double newScale = zoomSlider.getValue() * zoomFactor;
                zoomSlider.setValue(Math.max(0.1, Math.min(2.0, newScale)));
                event.consume();
            }
        });

        visualsScrollPane.setOnZoom(event -> {
            double zoomFactor = event.getZoomFactor();
            double newScale = zoomSlider.getValue() * zoomFactor;
            zoomSlider.setValue(Math.max(0.1, Math.min(2.0, newScale)));
            event.consume();
        });


        // Set a fixed height for all charts in the visuals box to ensure consistency
        commitPieChart.setMinHeight(1080);
        commitPieChart.setMaxHeight(1080);
        commitPieChart.setPrefHeight(1080);
        languagePieChart.setMinHeight(1080);
        languagePieChart.setMaxHeight(1080);
        languagePieChart.setPrefHeight(1080);
        contribLanguageBarChart.setMinHeight(1080);
        contribLanguageBarChart.setMaxHeight(1080);
        contribLanguageBarChart.setPrefHeight(1080);
        devPieChart.setMinHeight(1080);
        devPieChart.setMaxHeight(1080);
        devPieChart.setPrefHeight(1080);
        projectLangPieChart.setMinHeight(1080);
        projectLangPieChart.setMaxHeight(1080);
        projectLangPieChart.setPrefHeight(1080);
        impactBarChart.setMinHeight(1080);
        impactBarChart.setMaxHeight(1080);
        impactBarChart.setPrefHeight(1080);
        activityLineChart.setMinHeight(1080);
        activityLineChart.setMaxHeight(1080);
        activityLineChart.setPrefHeight(1080);
        calendarActivityChart.setMinHeight(1080);
        calendarActivityChart.setMaxHeight(1080);
        calendarActivityChart.setPrefHeight(1080);
        contributorActivityChart.setMinHeight(1080);
        contributorActivityChart.setMaxHeight(1080);
        contributorActivityChart.setPrefHeight(1080);
        commitsPerDayLineChart.setMinHeight(1080);
        commitsPerDayLineChart.setMaxHeight(1080);
        commitsPerDayLineChart.setPrefHeight(1080);

        chartsBox.getChildren().addAll(commitPieChart, languagePieChart, contribLanguageBarChart, devPieChart, projectLangPieChart, commitsPerDayLineChart, impactBarChart, activityLineChart, calendarActivityChart, contributorActivityChart);
        visualsOuterBox.getChildren().addAll(zoomControls, visualsScrollPane);
        VBox.setVgrow(visualsScrollPane, javafx.scene.layout.Priority.ALWAYS);
        visualsTab.setContent(visualsOuterBox);
        setupStatsTableContextMenu();

        Tab docControlTab = new Tab("Document Control");
        docControlTab.setClosable(false);
        VBox docControlBox = new VBox(10);
        docControlBox.setPadding(new Insets(10));
        docControlBox.getChildren().addAll(
                new Label("Manual Version Override:"), manualVersionField,
                new Label("Manual Description Override:"), manualDescriptionArea,
                new Label("(Leave blank to use automatic versioning and description)")
        );
        docControlTab.setContent(docControlBox);

        statsTabPane.getTabs().addAll(statsTab, companyBreakdownTab, visualsTab, docControlTab);

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
                "   - IGNORE ALL 'package-lock.json' mentions as a contributing factor for individuals. It should not influence risk or impact assessments.\n" +
                "2) All rankings (highest/lowest) MUST be numerically correct.\n" +
                "   - CONSISTENCY CHECK: Before writing conclusions, verify that \"highest\" corresponds to the largest numeric value and \"lowest\" to the smallest.\n" +
                "   - NUMERIC LOGIC: Explicitly verify that you understand 327 is lower than 1160.\n" +
                "   - If the provided METRICS labels contradict the numbers, explicitly flag it as: \"Metrics inconsistency detected\" and correct the ranking using the numeric values.\n" +
                "3) Do not repeat the same points across sections. Prefer dense, high-signal writing.\n" +
                "4) HARSHNESS ON LOW-VALUE WORK: In sections discussing contributor responsibility and value added, be direct and critical. If a contributor's work is primarily frontend styling or cosmetic, state that clearly as low functional impact.\n" +
                "5) BACKEND VS FRONTEND VALUE: Explicitly weight backend logic as MORE VALUABLE than frontend styling. Backend handles the core logic and system functionality, whereas frontend styling is primarily aesthetic.\n" +
                "6) DOCUMENTATION REVIEW: Feed the provided 'Documentation Lines Added' metric to assess who is contributing to the project's documentation. Review and state who is NOT contributing to documentation.\n" +
                "7) MEANINGFUL SCORE PENALTY: Heavily punish (lower) the 'Meaningful Score' if a contributor pushes build or auto-generated files (e.g., dist/, build/, *.map, minified, lockfiles).\n" +
                "8) STRICT DATA STRUCTURE: Tables MUST contain the columns exactly as requested. Do not add or remove columns from any generated tables. Do not merge cells or use complex layouts.\n" +
                "\n" +
                "RISK MODEL (Primary: Lines Added per Commit, Secondary: Other Metrics):\n" +
                "- PRIMARY RISK METRIC: Compute lines_added_per_commit = total_lines_added / total_commits (per contributor).\n" +
                "- Higher lines_added_per_commit = HIGHER RISK. Lower = more iterative, lower risk.\n" +
                "- SECONDARY RISK FACTORS: Risk is also increased by high code churn, low test coverage, and high AI-generated probability (if provided).\n" +
                "- DO NOT use file length as risk. Risk is based on changed lines per commit and the secondary metrics mentioned above.\n" +
                "\n" +
                "KEY MAN RISK ASSESSMENT:\n" +
                "- TOTAL LINES COMMITTED: High total lines added/deleted indicate a POTENTIAL Key Man risk (high impact/knowledge concentration).\n" +
                "- DEFINITIVE KEY MAN: A contributor is a definitive Key Man if they are solely or primarily responsible for specific sections/modules/directories that other contributors have not touched or have minimally touched.\n" +
                "- MEDIUM RISK: If an individual leaves, it does not automatically mean they are a keyman. Evaluate their 'Key Man' status by looking at where all other contributors have committed. If other contributors did not touch that section, then the original is a possible keyman; if they appear to be solely responsible, they ARE a keyman.\n" +
                "\n" +
                "REFACTORING VS BLOAT:\n" +
                "  - Refactoring (high deletions relative to additions) is NOT bloat. It reduces future technical debt.\n" +
                "  - If a contributor has significant deletions (e.g., deletions > 50% of additions), acknowledge this as high-value refactoring and adjust risk downward.\n" +
                "- MERGE COMMITS:\n" +
                "  - Flag merge commits (multi-parent) as distinct from regular commits. They often represent integration rather than new code bloat.\n" +
                "  - In the provided commit history, merge commits are marked with [MERGE].\n" +
                "  - IMPORTANT: Lines added in merge commits are NOT attributed to the contributor's 'Total lines added' in the summary stats to avoid skewing metrics. Use the [MERGE] commits only for context on what was integrated.\n" +
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
                "2) Repository Overview & Architecture\n" +
                "   - Total commits, contributors, time range (if provided)\n" +
                "   - High-level change distribution by file type/location (if provided)\n" +
                "   - SOFTWARE ARCHITECTURE & DESIGN: Analyze the directory structure and file distribution. \n" +
                "     Comment on the overall design patterns (e.g., MVC, Microservices, Layered), tech stack usage, \n" +
                "     and assess the long-term maintainability of these choices.\n" +
                "\n" +
                "3) Contributor Breakdown (one section per contributor)\n" +
                "   Include a table with:\n" +
                "   - total_commits (regular)\n" +
                "   - total_merges\n" +
                "   - total_lines_added\n" +
                "   - total_lines_deleted (refactoring indicator)\n" +
                "   - lines_added_per_commit\n" +
                "   - tests_touched (Yes/No + count if available). STRICT RULE: Use 'No' if there is no evidence of test changes in the provided commit details for this contributor.\n" +
                "   - top directories / file types modified\n" +
                "   Then provide:\n" +
                "   - impact summary (what areas they changed)\n" +
                "   - MEANINGFUL COMMIT NAMES: Evaluate if the contributor's commit messages are descriptive and follow good practices (e.g., prefixing with type, clear intent) versus being vague (e.g., \"update\", \"fix\").\n" +
                "   - OWNERSHIP ANALYSIS: Use the 'Creator' information in Top Files to identify if a contributor created a file or only edited it. Foundational ownership (creation) is a strong signal of expertise in that module.\n" +
                "   - FUNCTIONAL VS VISUAL/STYLING: Distinguish if their work was primarily functional logic or visual/styling (CSS, HTML, UI components in React/Vue). Be smart about detecting styling even in component files. Weight backend logic as MORE VALUABLE than frontend styling.\n" +
                "   - DOCUMENTATION CONTRIBUTION: Analyze the 'Documentation Lines Added' metric and comment on the contributor's effort towards project documentation. Explicitly state if they are NOT contributing to documentation.\n" +
                "   - MEANINGFUL SCORE: Provide a score 0-100 that takes into account commit messages, iterative patterns, and qualitative work. Include a MANDATORY tag: [MEANINGFUL_SCORE: Name=XX/100] at the end of each contributor section.\n" +
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
                "- IGNORE ALL 'package-lock.json' mentions as a contributing factor for individuals.\n" +
                "- STRICT DATA STRUCTURE: Tables MUST contain the columns exactly as requested. Do not add or remove columns.\n" +
                "- CONSISTENCY CHECK: Any \"highest/lowest\" ranking MUST be numerically correct. If labels conflict with numbers, flag it as:\n" +
                "  \"Metrics inconsistency detected\" and correct the ranking based on numeric values.\n" +
                "- NUMERIC LOGIC: Explicitly verify that you understand 327 is lower than 1160.\n" +
                "- HARSHNESS ON LOW-VALUE WORK: Be direct and critical in evaluations. If a contributor's work is primarily frontend styling or cosmetic, state that clearly.\n" +
                "- MEANINGFUL SCORE PENALTY: Heavily punish (lower) the 'Meaningful Score' if a contributor pushes build or auto-generated files (e.g., dist/, build/, *.map, minified, lockfiles).\n" +
                "\n" +
                "REFACTORING VS BLOAT:\n" +
                "- Acknowledge refactoring (high deletions) as a positive quality signal that reduces technical debt.\n" +
                "- Distinguish between new code bloat and high-value architectural cleanup.\n" +
                "\n" +
                "RISK ASSESSMENT (Primary: Lines Added per Commit, Secondary: Other Metrics):\n" +
                "- Calculate risk using: lines_added_per_commit = total_lines_added / total_commits (per contributor).\n" +
                "- Higher lines_added_per_commit = HIGHER RISK (mathematically).\n" +
                "- SECONDARY RISK FACTORS: Increase risk if there is high code churn, low test coverage, or high AI-generated probability.\n" +
                "- Apply the defined risk scale exactly, then adjust based on secondary factors.\n" +
                "- Adjust risk downward if tests are present (changes in folders containing: test, tests, __tests__).\n" +
                "- Consider language/file context and project phase (initial scaffolding can be lower risk only if changes are mostly config/docs/build setup, not core logic).\n" +
                "\n" +
                "KEY MAN RISK ASSESSMENT:\n" +
                "- TOTAL LINES COMMITTED: High total lines added/deleted indicate a POTENTIAL Key Man risk.\n" +
                "- DEFINITIVE KEY MAN: A contributor is a definitive Key Man if they are solely or primarily responsible for specific sections/modules/directories that other contributors have not touched or have minimally touched.\n" +
                "- MEDIUM RISK: If an individual leaves, it does not automatically mean they are a keyman. Evaluate their 'Key Man' status by looking at where all other contributors have committed. If other contributors did not touch that section, then the original is a possible keyman; if they appear to be solely responsible, they ARE a keyman.\n" +
                "\n" +
                "MERGE COMMITS:\n" +
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
        generateLlmReportBtn.setOnAction(e -> {
            if (mainTopTabPane.getSelectionModel().getSelectedItem() == companyReviewTab) {
                List<CompanyMetric> selectedMetrics = repoSelectionList.getItems().stream()
                        .filter(CompanyMetricSelection::isSelected)
                        .map(CompanyMetricSelection::getMetric)
                        .collect(Collectors.toList());
                if (selectedMetrics.isEmpty()) {
                    showAlert("Warning", "No repositories selected for LLM analysis.");
                    return;
                }
                generateCompanyLlmReport(null);
            } else {
                generateLlmReport(null);
            }
        });
        llmActionBox.getChildren().addAll(new Label("Provider:"), providerCombo, generateLlmReportBtn);

        llmPanel.getChildren().addAll(
            new Label("System Prompt:"), systemPromptArea,
            new Label("User Prompt:"), userPromptArea,
            llmActionBox,
            new Label("LLM Response:"), llmResponseArea
        );
        VBox.setVgrow(llmResponseArea, javafx.scene.layout.Priority.ALWAYS);

        sharedResultsBox.getChildren().add(horizontalSplit);
        VBox.setVgrow(horizontalSplit, javafx.scene.layout.Priority.ALWAYS);

        repoModeTab.setContent(repoModeBox);
        companyReviewTab.setContent(companyModeBox);

        SplitPane verticalContentSplit = new SplitPane(mainTopTabPane, sharedResultsBox);
        verticalContentSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalContentSplit.setDividerPositions(0.4);

        mainSplit.getItems().addAll(verticalContentSplit, llmPanel);
        
        mainTopTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == repoModeTab) {
                if (!rightBox.getChildren().contains(commitList)) {
                    rightBox.getChildren().addAll(new Label("Recent Commits:"), commitList, initialCommitLabel);
                }
            } else if (newTab == companyReviewTab) {
                rightBox.getChildren().clear();
                rightBox.getChildren().add(new Label("Company Context (Select repo to analyze)"));
            }
        });
        mainSplit.setDividerPositions(0.6);

        contentBox.getChildren().add(mainSplit);

        Scene scene = new Scene(root, 1100, 1000);
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
        
        ComboBox<String> groqModelCombo = new ComboBox<>(FXCollections.observableArrayList(availableGroqModels));
        if (availableGroqModels.isEmpty()) {
            groqModelCombo.getItems().add(groqModel);
        }
        groqModelCombo.setValue(groqModel);
        groqModelCombo.setEditable(true);
        groqModelCombo.setPrefWidth(200);

        Button fetchGroqBtn = new Button("Fetch Models");
        fetchGroqBtn.setOnAction(e -> {
            List<String> models = fetchGroqModels(groqField.getText());
            if (!models.isEmpty()) {
                availableGroqModels = models;
                groqModelCombo.setItems(FXCollections.observableArrayList(models));
                groqModelCombo.setValue(models.get(0));
            }
        });

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
        HBox groqModelBox = new HBox(5, groqModelCombo, fetchGroqBtn);
        grid.add(groqModelBox, 1, 3);

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
                groqModel = groqModelCombo.getValue();
                ollamaUrl = ollamaField.getText();
                ollamaModel = ollamaModelCombo.getValue();
                saveSettings();
            }
        });
    }

    private void saveSettings() {
        String encOpenAiKey = (encryptionService != null) ? encryptionService.encrypt(openAiKey) : openAiKey;
        String encGroqKey = (encryptionService != null) ? encryptionService.encrypt(groqKey) : groqKey;

        configManager.saveSetting("openAiKey", encOpenAiKey != null ? encOpenAiKey : "");
        configManager.saveSetting("openAiModel", openAiModel);
        configManager.saveSetting("groqKey", encGroqKey != null ? encGroqKey : "");
        configManager.saveSetting("groqModel", groqModel);
        configManager.saveSetting("ollamaUrl", ollamaUrl);
        configManager.saveSetting("ollamaModel", ollamaModel);
        configManager.saveSetting("selectedProvider", selectedProvider);
        configManager.saveSetting("aliases", aliasesArea.getText());
        configManager.saveSetting("companyReviewMdPath", companyReviewMdPathField.getText());
        configManager.saveSetting("genders", gendersData);
        
        configManager.saveSetting("repoPath", repoPathField.getText());
        configManager.saveSetting("mainBranch", mainBranchField.getText());
        configManager.saveSetting("commitLimit", String.valueOf(commitLimitSpinner.getValue()));
        configManager.saveSetting("tableLimit", String.valueOf(tableLimitSpinner.getValue()));
        configManager.saveSetting("ignoredExtensions", ignoredExtensionsField.getText());
        configManager.saveSetting("ignoredFolders", ignoredFoldersField.getText());
        
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
        String savedOpenAiKey = configManager.getSetting("openAiKey", "");
        String savedGroqKey = configManager.getSetting("groqKey", "");
        
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

        openAiModel = configManager.getSetting("openAiModel", "gpt-4o");
        groqModel = configManager.getSetting("groqModel", "mixtral-8x7b-32768");
        ollamaUrl = configManager.getSetting("ollamaUrl", "http://localhost:11434");
        ollamaModel = configManager.getSetting("ollamaModel", "llama3");
        selectedProvider = configManager.getSetting("selectedProvider", "OpenAI");
        aliasesArea.setText(configManager.getSetting("aliases", ""));

        String companyMdPath = configManager.getSetting("companyReviewMdPath", "");
        companyReviewMdPathField.setText(companyMdPath);
        if (companyMdPath.isEmpty()) {
            String appDir = DatabaseService.getAppDir();
            File defaultDir = new File(appDir, "company_review");
            if (!defaultDir.exists()) {
                defaultDir.mkdirs();
                createDefaultMdFiles(defaultDir, "company_review");
            }
            companyReviewMdPathField.setText(defaultDir.getAbsolutePath());
        }

        gendersData = configManager.getSetting("genders", "");
        
        repoPathField.setText(configManager.getSetting("repoPath", ""));
        mainBranchField.setText(configManager.getSetting("mainBranch", ""));
        commitLimitSpinner.getValueFactory().setValue(Integer.parseInt(configManager.getSetting("commitLimit", "10")));
        tableLimitSpinner.getValueFactory().setValue(Integer.parseInt(configManager.getSetting("tableLimit", "20")));
        ignoredExtensionsField.setText(configManager.getSetting("ignoredExtensions", "json,xml,csv,lock,txt,package-lock.json,yarn.lock,pnpm-lock.yaml"));
        ignoredFoldersField.setText(configManager.getSetting("ignoredFolders", "node_modules,target,build,dist,.git"));

        // Set default paths based on app data directory
        String appDir = DatabaseService.getAppDir();
        String defaultMdPath = appDir + File.separator + "repo_markdown";
        String defaultCoverPath = appDir + File.separator + "coverpage.html";

        mdFolderPathField.setText(defaultMdPath);
        coverPagePathField.setText(defaultCoverPath);

        // Load global settings from database (overwrites defaults if present)
        if (databaseService != null) {
            try {
                String mdPath = databaseService.getGlobalSetting("mdFolderPath");
                if (mdPath != null && !mdPath.isEmpty()) mdFolderPathField.setText(mdPath);
                
                String coverPath = databaseService.getGlobalSetting("coverPagePath");
                if (coverPath != null && !coverPath.isEmpty()) coverPagePathField.setText(coverPath);

                String featuresPath = databaseService.getGlobalSetting("requiredFeaturesPath");
                if (featuresPath != null) requiredFeaturesPathField.setText(featuresPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Ensure default files exist on startup
        ensureDefaultFilesExist();
    }

    private void ensureDefaultFilesExist() {
        String mdPath = mdFolderPathField.getText();
        if (mdPath != null && !mdPath.isEmpty()) {
            File mdFolder = new File(mdPath);
            if (!mdFolder.exists()) {
                mdFolder.mkdirs();
            }
            createDefaultMdFiles(mdFolder, "repo_markdown");
        }

        String coverPath = coverPagePathField.getText();
        if (coverPath != null && !coverPath.isEmpty()) {
            File coverFile = new File(coverPath);
            if (!coverFile.exists()) {
                autoGenerateCoverPage(coverFile);
            }
        }
    }

    private void autoGenerateCoverPage(File file) {
        try {
            try (java.io.InputStream is = getClass().getResourceAsStream("/coverpage.html")) {
                if (is != null) {
                    java.nio.file.Files.copy(is, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    File templateFile = new File("coverpage.html");
                    if (templateFile.exists()) {
                        java.nio.file.Files.copy(templateFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
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
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    private List<String> fetchGroqModels(String apiKey) {
        List<String> models = new ArrayList<>();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showAlert("Error", "Groq API Key is required to fetch models.");
            return models;
        }
        try {
            java.net.URL url = new java.net.URL("https://api.groq.com/openai/v1/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            
            if (conn.getResponseCode() == 200) {
                try (java.io.InputStream is = conn.getInputStream()) {
                    String response = new String(is.readAllBytes());
                    // Rough manual parsing of JSON like {"data":[{"id":"mixtral-8x7b-32768",...},...]}
                    int idx = 0;
                    while ((idx = response.indexOf("\"id\":\"", idx)) != -1) {
                        idx += 6;
                        int end = response.indexOf("\"", idx);
                        if (end != -1) {
                            models.add(response.substring(idx, end));
                            idx = end;
                        }
                    }
                }
            } else {
                try (java.io.InputStream is = conn.getErrorStream()) {
                    String err = is != null ? new String(is.readAllBytes()) : "Unknown error";
                    showAlert("Error", "Failed to fetch Groq models. Status: " + conn.getResponseCode() + "\n" + err);
                }
            }
        } catch (Exception e) {
            showAlert("Error", "Could not connect to Groq: " + e.getMessage());
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
        String mapping = stat.email() + "=" + newGender;
        
        String[] lines = gendersData.split("\n");
        StringBuilder newGenders = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;
            
            // Match by either email or name to avoid duplicates
            if (trimmedLine.startsWith(stat.email() + "=") || trimmedLine.startsWith(stat.name() + "=")) {
                if (!found) {
                    newGenders.append(mapping).append("\n");
                    found = true;
                }
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
        String currentEmails = configManager.getSetting("emailOverrides", "");
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
        configManager.saveSetting("emailOverrides", newEmails.toString().trim());
        analyzeRepo();
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
            saveSettings();
            if (mainTopTabPane.getSelectionModel().getSelectedItem() == companyReviewTab) {
                refreshCompanyReviewData(true);
            } else {
                analyzeRepo(); // Re-analyze with new alias
            }
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
        String overridesStr = configManager.getSetting("emailOverrides", "");
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
        File repoDir = new File(repoPathField.getText());
        Set<String> ignoredFolders = Arrays.stream(ignoredFoldersField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        String structure = gitService.getProjectStructure(repoDir, ignoredFolders, aliasesMap());

        List<CommitInfo> allCommits = null;
        Map<String, List<FileChange>> contributorFiles = null;
        try {
            int commitLimit = selectedProvider.equals("Groq") ? 300 : 1000;
            allCommits = gitService.getLastCommits(repoDir, commitLimit, aliasesMap(), mainBranchField.getText());
            allCommits = allCommits.stream()
                    .sorted(Comparator.comparing(CommitInfo::timestamp).reversed())
                    .limit(commitLimit)
                    .toList();

            int topFileLimit = selectedProvider.equals("Groq") ? 3 : 5;
            contributorFiles = gitService.getTopFilesPerContributor(repoDir, topFileLimit, aliasesMap());
        } catch (Exception e) {
            e.printStackTrace();
        }

        String reqFeatures = readRequiredFeatures();
        
        final List<CommitInfo> finalAllCommits = allCommits;
        final Map<String, List<FileChange>> finalContributorFiles = contributorFiles;

        final String finalUrl = url;
        final String finalApiKey = apiKey;
        final String finalModel = model;

        Platform.runLater(() -> llmResponseArea.setText("Generating multi-section report using " + selectedProvider + "..."));

        new Thread(() -> {
            try {
                StringBuilder fullReport = new StringBuilder();
                if (!mdSections.isEmpty()) {
                    for (Map.Entry<String, String> entry : mdSections.entrySet()) {
                        String sectionTitle = entry.getKey().replace(".md", "");
                        String formattedTitle = llmService.formatSectionTitle(sectionTitle);
                        String sectionInstructions = entry.getValue();
                        
                        boolean needsDiffs = entry.getKey().toLowerCase().contains("contributor_deep_dive") || 
                                           entry.getKey().toLowerCase().contains("requirements_and_alignment");
                        
                        // When processing a specific section, we can further reduce the token load
                        // by only sending the most relevant metrics. For now, we selectively include diffs.
                        
                        // Aggressively limit commit history for sections that don't need the full timeline
                        int sectionCommitLimit = 0; // 0 means no additional limit beyond the initial fetch
                        if (entry.getKey().toLowerCase().contains("introduction") || 
                            entry.getKey().toLowerCase().contains("methodology") ||
                            entry.getKey().toLowerCase().contains("bus_factor") ||
                            entry.getKey().toLowerCase().contains("conclusion")) {
                            sectionCommitLimit = 50; // These sections only need recent context
                        } else if (entry.getKey().toLowerCase().contains("review_hygiene")) {
                            sectionCommitLimit = 100;
                        }
                        
                        String sectionMetrics = llmService.buildMetricsText(repoDir, currentStats, currentMeaningfulAnalysis, 
                                                        finalAllCommits, finalContributorFiles, structure, reqFeatures, emailOverrides, needsDiffs, sectionCommitLimit);
                        
                        // Limit commit history length for specific sections if still too large
                        // but buildMetricsText currently includes all commits from finalAllCommits.
                        
                        String basePrompt = userPromptArea.getText() + "\n\n" + 
                                       "FOCUS SECTION: " + formattedTitle + "\n" +
                                       "SECTION INSTRUCTIONS: " + sectionInstructions + "\n\n" +
                                       "IMPORTANT: Provide ONLY the content for this section as defined by the instructions. " +
                                       "Do not include information that belongs in other sections. " +
                                       "Use the provided metrics to inform your analysis for this specific section.\n" +
                                       "HEADER NESTING: The application will prepend a top-level header (# " + formattedTitle + ") for this section. " +
                                       "Ensure all headers in your response use at least TWO hashes (##) so they are correctly nested under the section header.";
                        
                        String fullPrompt = basePrompt + "\n\n" + sectionMetrics;
                        String sectionResponse = llmService.callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), fullPrompt);
                        
                        sectionResponse = sectionResponse.replaceAll("```markdown", "").replaceAll("```", "").trim();
                        String titlePattern = "^#\\s+" + java.util.regex.Pattern.quote(formattedTitle) + "\\s*\\n+";
                        sectionResponse = sectionResponse.replaceFirst("(?i)" + titlePattern, "");
                        sectionResponse = llmService.demoteMarkdownHeaders(sectionResponse);

                        fullReport.append("# ").append(formattedTitle).append("\n\n");
                        fullReport.append(sectionResponse).append("\n\n");
                        
                        String progressMsg = String.format("Generated section: %s...", formattedTitle);
                        Platform.runLater(() -> llmResponseArea.setText(progressMsg));

                        try {
                            Thread.sleep(2000); 
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    String baseMetrics = llmService.buildMetricsText(repoDir, currentStats, currentMeaningfulAnalysis, 
                                                                    finalAllCommits, finalContributorFiles, structure, reqFeatures, emailOverrides, true, 0);
                    String response = llmService.callLlmApi(finalUrl, finalApiKey, finalModel, systemPromptArea.getText(), userPromptArea.getText() + "\n\n" + baseMetrics);
                    response = response.replaceAll("```markdown", "").replaceAll("```", "").trim();
                    fullReport.append(response).append("\n\n");
                    Platform.runLater(() -> llmResponseArea.setText("Generating report..."));
                }

                Platform.runLater(() -> {
                    llmResponseArea.setText(fullReport.toString().trim());
                    updateStatsWithAiScores(fullReport.toString());
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

    private void generateCompanyLlmReport(Runnable onComplete) {
        if (databaseService == null) return;
        
        List<CompanyMetric> selectedMetricsForLlm = repoSelectionList.getItems().stream()
                .filter(CompanyMetricSelection::isSelected)
                .map(CompanyMetricSelection::getMetric)
                .collect(Collectors.toList());

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
            apiKey = "ollama";
            url = ollamaUrl + "/v1/chat/completions";
            model = ollamaModel;
        }

        if (apiKey.isEmpty() && !selectedProvider.equals("Ollama")) {
            showAlert("Error", "API Key for " + selectedProvider + " is not set.");
            if (onComplete != null) onComplete.run();
            return;
        }

        Platform.runLater(() -> llmResponseArea.setText("Generating Company Review report using " + selectedProvider + "..."));

        new Thread(() -> {
            try {
                List<CompanyMetric> selectedMetrics = repoSelectionList.getItems().stream()
                        .filter(CompanyMetricSelection::isSelected)
                        .map(CompanyMetricSelection::getMetric)
                        .collect(Collectors.toList());

                StringBuilder companyMetrics = new StringBuilder();
                companyMetrics.append("COMPANY REVIEW METRICS (Selected Repositories)\n");
                companyMetrics.append("===========================================\n\n");
                
                for (CompanyMetric m : selectedMetrics) {
                    List<ContributorStats> stats = databaseService.getLatestMetrics(m.repoName());
                    if (stats.isEmpty()) {
                        // Fallback to what we have in the metric (e.g. from CSV)
                        companyMetrics.append("Repository: ").append(m.repoName()).append("\n");
                        companyMetrics.append("Contributors: ").append(m.totalContributors()).append("\n");
                        companyMetrics.append("Total Commits: ").append(m.totalCommits()).append("\n");
                        companyMetrics.append("Total Lines Added: ").append(m.totalLinesAdded()).append("\n\n");
                        continue;
                    }
                    companyMetrics.append("Repository: ").append(m.repoName()).append("\n");
                    companyMetrics.append("Contributors: ").append(stats.size()).append("\n");
                    int totalCommits = stats.stream().mapToInt(ContributorStats::commitCount).sum();
                    int totalAdded = stats.stream().mapToInt(ContributorStats::linesAdded).sum();
                    companyMetrics.append("Total Commits: ").append(totalCommits).append("\n");
                    companyMetrics.append("Total Lines Added: ").append(totalAdded).append("\n\n");
                }

                String response = llmService.callLlmApi(url, apiKey, model, systemPromptArea.getText(), 
                        userPromptArea.getText() + "\n\n" + companyMetrics.toString());
                
                response = response.replaceAll("```markdown", "").replaceAll("```", "").trim();
                final String finalResponse = response;
                Platform.runLater(() -> {
                    llmResponseArea.setText(finalResponse);
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

    private void generateRepoMds() {
        String path = mdFolderPathField.getText();
        if (path == null || path.isEmpty()) {
            String appDir = DatabaseService.getAppDir();
            path = appDir + File.separator + "repo_markdown";
            mdFolderPathField.setText(path);
        }
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        createDefaultMdFiles(folder, "repo_markdown");
        showAlert("Success", "Repo Markdown files created in: " + folder.getAbsolutePath());
    }

    private void generateCompanyReviewMds() {
        String path = companyReviewMdPathField.getText();
        if (path == null || path.isEmpty()) {
            String appDir = DatabaseService.getAppDir();
            path = appDir + File.separator + "company_review";
            companyReviewMdPathField.setText(path);
        }
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        createDefaultMdFiles(folder, "company_review");
        showAlert("Success", "Company Review Markdown files created in: " + folder.getAbsolutePath());
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
                createDefaultMdFiles(folder, "repo_markdown");
            } else {
                return;
            }
        }
        getHostServices().showDocument(folder.toURI().toString());
    }

    private void createDefaultMdFiles(File folder, String resourcePath) {
        try {
            boolean filesCopied = false;

            // Try to list resources from the JAR or classpath
            java.net.URL resourceUrl = getClass().getResource("/" + resourcePath);
            if (resourceUrl != null) {
                if (resourceUrl.getProtocol().equals("jar")) {
                    String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(java.net.URLDecoder.decode(jarPath, "UTF-8"))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(resourcePath + "/") && name.endsWith(".md")) {
                                String fileName = name.substring((resourcePath + "/").length());
                                if (!fileName.isEmpty()) {
                                    File targetFile = new File(folder, fileName);
                                    if (!targetFile.exists()) {
                                        copyResourceToFile(name, targetFile);
                                    }
                                    filesCopied = true;
                                }
                            }
                        }
                    }
                } else if (resourceUrl.getProtocol().equals("file")) {
                    File resourceFolder = new File(resourceUrl.toURI());
                    File[] files = resourceFolder.listFiles((dir, name) -> name.endsWith(".md"));
                    if (files != null) {
                        for (File file : files) {
                            File targetFile = new File(folder, file.getName());
                            if (!targetFile.exists()) {
                                java.nio.file.Files.copy(file.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            filesCopied = true;
                        }
                    }
                }
            }

            // Fallback to local folder if no files were copied from resources
            if (!filesCopied) {
                File templateFolder = new File(resourcePath);
                if (templateFolder.exists() && templateFolder.isDirectory()) {
                    File[] files = templateFolder.listFiles((dir, name) -> name.endsWith(".md"));
                    if (files != null) {
                        for (File file : files) {
                            File targetFile = new File(folder, file.getName());
                            if (!targetFile.exists()) {
                                java.nio.file.Files.copy(file.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            filesCopied = true;
                        }
                    }
                }
            }

            // Final fallback: if still no files and it's repo_markdown, use some hardcoded ones
            if (!filesCopied && resourcePath.equals("repo_markdown")) {
                String[] essentialFiles = {"01_Introduction.md", "02_Methodology.md", "04_Contributor_Deep_Dive.md", "05_Risk_and_Quality_Assessment.md", "10_Conclusion.md"};
                for (String fileName : essentialFiles) {
                    writeHardcodedDefault(fileName, new File(folder, fileName));
                }
            }

            // Also update the UI field to show the path if it was empty for repo_markdown
            if (resourcePath.equals("repo_markdown") && mdFolderPathField.getText().isEmpty()) {
                mdFolderPathField.setText(folder.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyResourceToFile(String resourcePath, File targetFile) throws java.io.IOException {
        try (java.io.InputStream is = getClass().getResourceAsStream("/" + resourcePath)) {
            if (is != null) {
                java.nio.file.Files.copy(is, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void writeHardcodedDefault(String fileName, File targetFile) throws java.io.IOException {
        if (fileName.equals("01_Introduction.md")) {
            java.nio.file.Files.writeString(targetFile.toPath(),
                "# Introduction\n" +
                "This report provides an exhaustive and technical analysis of the git repository's development history and contributor activity.\n" +
                "The focus is on identifying high-value contributions, assessing project stability, and evaluating technical risk across the codebase.\n\n" +
                "This is an **AI Assisted Review**, where AI assists by analyzing git metrics, commit messages, and project structure to provide insights into code quality, contributor impact, and potential risks.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Provide a high-level executive summary of the project's current state.\n" +
                "- Analyze the repository structure to identify core backend, frontend, and infrastructure components.\n" +
                "- HIGH-LEVEL CONTRIBUTOR RESPONSIBILITIES: Provide a high-level overview of the primary responsibilities and the unique value added by each major contributor based on their directory/file activity.\n" +
                "- **Key Man Risk Assessment**: Evaluate if any contributor is a Key Man (sole/primary owner of core project sections). High total lines indicate potential risk, but sole ownership of sections confirms Key Man status.\n" +
                "- **Ownership Analysis**: Distinguish between contributors who **created** critical files versus those who only **edited** them. File creation indicates deeper foundational knowledge.\n" +
                "- Reference specific directory patterns to explain the architectural distribution of work.\n" +
                "- SOFTWARE ARCHITECTURE & DESIGN: Analyze the directory structure and file distribution. \n" +
                "  Comment on the overall design patterns (e.g., MVC, Microservices, Layered), tech stack usage, \n" +
                "  and assess the long-term maintainability of these choices.\n" +
                "- **Documentation Review**: Review the provided 'Documentation Lines Added' metric for all contributors and explicitly identify who is NOT contributing to the project's documentation.");
        } else if (fileName.equals("02_Methodology.md")) {
            java.nio.file.Files.writeString(targetFile.toPath(),
                "# Analysis Methodology\n" +
                "The analysis utilizes JGit for precise metric extraction and AI-driven heuristics to interpret qualitative development patterns.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Explain the 'Lines Added per Commit' risk scoring system (1500+ VERY HIGH to <250 LOW).\n" +
                "- Describe how the presence of tests (files in 'test' directories) mitigates risk scores.\n" +
                "- Explicitly state that risk is based on average lines added per commit, not lines in a single file.\n" +
                "- Explain that merge commits are tracked but their lines of code are excluded from total counts to prevent metric skewing.\n" +
                "- Detail the 'Meaningful Change' score logic:\n" +
                "  - Repository-wide score: Weighted by Source Code (70%) and Tests (30%) insertions.\n" +
                "  - Contributor-level score: In this AI-assisted mode, the score is qualitatively assigned by the LLM (0-100) based on commit descriptive quality, iterative patterns, and functional impact.\n" +
                "  - Filters out boilerplate, generated artifacts, and documentation noise.");
        } else if (fileName.equals("04_Contributor_Deep_Dive.md")) {
            java.nio.file.Files.writeString(targetFile.toPath(),
                "# Contributor Impact Analysis\n" +
                "A detailed evaluation of individual contributions based on commit frequency, impact volume, and code quality.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- For EVERY major contributor listed in the METRICS, provide a dedicated technical subsection.\n" +
                "- Use the 'Gender' field for correct pronouns. Do NOT explicitly state the gender or pronouns in the document text (e.g., do not say \"He is a male contributor\"). Simply use the correct pronouns when referring to the contributor.\n" +
                "- Analyze their specific 'Impact Analysis' (Added vs Deleted lines) and the types of files they touched as shown in their metrics.\n" +
                "- Do NOT invent or assume names; attribute impact ONLY to the names provided in the metrics.\n" +
                "- **Key Man Identification**: Assess if this contributor is a Key Man for specific sections. High total lines committed indicate potential Key Man risk, but look at where all other contributors have committed. If other contributors did not touch a section this contributor owns, they are a Key Man for that section.\n" +
                "- MEANINGFUL COMMIT NAMES: Evaluate if the contributor's commit messages are descriptive and follow good practices (e.g., prefixing with type, clear intent) versus being vague (e.g., \"update\", \"fix\").\n" +
                "- FUNCTIONAL VS VISUAL/STYLING: Distinguish if their work was primarily functional logic or visual/styling (CSS, HTML, UI components in React/Vue). Be smart about detecting styling even in component files. **Weight backend logic as MORE VALUABLE than frontend styling.**\n" +
                "- **Documentation Contribution**: Analyze the 'Documentation Lines Added' metric and comment on the contributor's effort towards project documentation. Explicitly state if they are NOT contributing to documentation.\n" +
                "- MEANINGFUL SCORE: Provide a score 0-100 that takes into account commit messages, iterative patterns, and qualitative work. Include a MANDATORY tag: [MEANINGFUL_SCORE: Name=XX/100] at the end of each contributor section. **Heavily penalize this score if they pushed generated/build files.**\n" +
                "- Identify their 'Most Valuable Contributor' potential based on iterative development rather than just bulk LOC.\n" +
                "- **Directory Breakdown**: Review the 'Directory Breakdown' metric to see where they made their edits. This helps identify if they pushed generated files (e.g. in dist, build, or large amounts in a single folder) or did actual development across the project structure.");
        } else if (fileName.equals("05_Risk_and_Quality_Assessment.md")) {
            java.nio.file.Files.writeString(targetFile.toPath(),
                "# Risk & Quality Assessment\n" +
                "Evaluation of project stability and potential technical debt based on commit patterns.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Create a detailed Risk Table for all contributors.\n" +
                "- Columns: Contributor, Commits, Lines Added, Lines Added/Commit, Tests Touched, Risk Band, Justification.\n" +
                "- IGNORE ALL 'package-lock.json' mentions as a contributing factor for individuals.\n" +
                "- **RISK FACTORS**: Risk is primarily driven by Lines Added/Commit (Higher = Higher Risk), but also increases with high churn, low test coverage, and being a Key Man (sole owner of project sections).\n" +
                "- STRICT RULE: In the 'Tests Touched' column, use 'No' if there is no evidence of test changes in the provided commit details for this contributor. If the 'touchedTests' metric is false, they MUST NOT get a 'Yes' in this column.\n" +
                "- Explain the reasoning behind each risk level, explicitly addressing Key Man status (sole ownership of project sections).\n" +
                "- Identify patterns of 'Bulk Commits' vs 'Iterative Refinement'.\n" +
                "- Highlighting areas where test coverage is lacking relative to feature complexity.");
        } else if (fileName.equals("10_Conclusion.md")) {
            java.nio.file.Files.writeString(targetFile.toPath(),
                "# Conclusion & Recommendations\n" +
                "Final synthesis of findings and strategic recommendations for the project.\n\n" +
                "INSTRUCTIONS FOR AI:\n" +
                "- Identify the overall 'Most Valuable Contributor' with a detailed justification.\n" +
                "- Summarize the top 3 technical risks found in the repo.\n" +
                "- Provide 3 actionable recommendations for improving code quality or team velocity. " +
                "For each recommendation, include: Title, Severity, Effort, Why It Matters, and Action Plan.\n" +
                "- Do NOT list names or raw metrics in the recommendation titles.");
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
                try (java.io.InputStream is = getClass().getResourceAsStream("/coverpage.html")) {
                    if (is != null) {
                        java.nio.file.Files.copy(is, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // Fallback to local file if resource not found (dev mode)
                        File templateFile = new File("coverpage.html");
                        if (templateFile.exists()) {
                            java.nio.file.Files.copy(templateFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            // Last resort: hardcoded template
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
                        }
                    }
                }
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

        String repoId;
        try {
            repoId = repoDir.getCanonicalPath();
        } catch (Exception e) {
            repoId = repoDir.getAbsolutePath();
        }

        final String finalRepoId = repoId;

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
                String overridesStr = configManager.getSetting("emailOverrides", "");
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
                            s.filesEdited(), s.filesDeletedCount(), s.meaningfulChangeScore(), s.touchedTests(), s.generatedFilesPushed(), s.documentationLinesAdded(), s.directoryBreakdown());
                    }
                    return s;
                }).collect(Collectors.toList());

                currentMeaningfulAnalysis = gitService.performMeaningfulChangeAnalysis(repoDir, commitLimitSpinner.getValue(), ignoredFolders);
                String mainBranch = mainBranchField.getText();
                List<CommitInfo> recentCommits = gitService.getLastCommits(repoDir, commitLimitSpinner.getValue(), currentAliases, mainBranch);
                CommitInfo initial = gitService.getInitialCommit(repoDir, currentAliases);

                if (databaseService != null) {
                    try {
                        databaseService.saveMetrics(finalRepoId, currentStats);
                        databaseService.saveCommits(finalRepoId, recentCommits);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                Platform.runLater(() -> {
                    try {
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
                    } catch (Exception e) {
                        e.printStackTrace();
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
        int oGenerated = others.stream().mapToInt(ContributorStats::generatedFilesPushed).sum();
        int oDocLines = others.stream().mapToInt(ContributorStats::documentationLinesAdded).sum();
        double avgAi = others.stream().mapToDouble(ContributorStats::averageAiProbability).average().orElse(0.0);

        double avgMeaningful = others.stream().mapToDouble(ContributorStats::meaningfulChangeScore).average().orElse(0.0);

        Map<String, Integer> oLangs = new HashMap<>();
        others.forEach(s -> s.languageBreakdown().forEach((k, v) -> oLangs.merge(k, v, Integer::sum)));
        Map<String, Integer> oDirs = new HashMap<>();
        others.forEach(s -> s.directoryBreakdown().forEach((k, v) -> oDirs.merge(k, v, Integer::sum)));

        boolean oTouchedTests = others.stream().anyMatch(ContributorStats::touchedTests);

        top.add(new ContributorStats("Others", "others@example.com", "unknown", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted, avgMeaningful, oTouchedTests, oGenerated, oDocLines, oDirs));
        return top;
    }

    private void updateCharts(List<ContributorStats> stats, List<CommitInfo> recentCommits) {
        Platform.runLater(() -> {
            // Reset titles to Repo mode
            commitPieChart.setTitle("Commits by Contributor");
            languagePieChart.setTitle("Language Breakdown (Overall)");
            impactBarChart.setTitle("Impact (Lines Added/Deleted)");
            contribLanguageBarChart.setTitle("Languages by Contributor");
            devPieChart.setTitle("Code by Developer");
            projectLangPieChart.setTitle("Language of Projects");
            activityLineChart.setTitle("Recent Commit Activity");
            calendarActivityChart.setTitle("Daily Activity (Total Impact)");
            contributorActivityChart.setTitle("Daily Activity per Contributor (Lines Added)");
            commitsPerDayLineChart.setTitle("Commits per Day & per Contributor");

            if (companyBreakdownTab.getTabPane() != null) {
                statsTabPane.getTabs().remove(companyBreakdownTab);
            }

            chartManager.updateCharts(commitPieChart, languagePieChart, contribLanguageBarChart, commitsPerDayLineChart, impactBarChart, activityLineChart, calendarActivityChart, 
                                     contributorActivityChart, devPieChart, projectLangPieChart, stats, recentCommits);
            
            commitPieChart.applyCss();
            commitPieChart.layout();
            languagePieChart.applyCss();
            languagePieChart.layout();
            contribLanguageBarChart.applyCss();
            contribLanguageBarChart.layout();
            devPieChart.applyCss();
            devPieChart.layout();
            projectLangPieChart.applyCss();
            projectLangPieChart.layout();
            impactBarChart.applyCss();
            impactBarChart.layout();
            activityLineChart.applyCss();
            activityLineChart.layout();
            calendarActivityChart.applyCss();
            calendarActivityChart.layout();
            contributorActivityChart.applyCss();
            contributorActivityChart.layout();
            commitsPerDayLineChart.applyCss();
            commitsPerDayLineChart.layout();

            // Additional force layout for shared charts visibility
            commitPieChart.setVisible(true);
            commitPieChart.setManaged(true);
            languagePieChart.setVisible(true);
            languagePieChart.setManaged(true);
            contribLanguageBarChart.setVisible(true);
            contribLanguageBarChart.setManaged(true);
            impactBarChart.setVisible(true);
            impactBarChart.setManaged(true);
            activityLineChart.setVisible(true);
            activityLineChart.setManaged(true);
            calendarActivityChart.setVisible(true);
            calendarActivityChart.setManaged(true);
            contributorActivityChart.setVisible(true);
            contributorActivityChart.setManaged(true);
            commitsPerDayLineChart.setVisible(true);
            commitsPerDayLineChart.setManaged(true);
            devPieChart.setVisible(true);
            devPieChart.setManaged(true);
            projectLangPieChart.setVisible(true);
            projectLangPieChart.setManaged(true);
        });
    }


    private void exportToCsv(Stage stage) {
        if (statsTable.getItems().isEmpty()) {
            showAlert("No Data", "Please analyze a repository first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Quantitative Metrics CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("git_metrics.csv");
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                // Header
                writer.println("Name,Email,Gender,Commits,Merges,LinesAdded,LinesDeleted,FilesAdded,FilesEdited,FilesDeleted,MeaningfulScore,AIProbability,Languages");
                
                for (ContributorStats stat : statsTable.getItems()) {
                    writer.printf("\"%s\",\"%s\",\"%s\",%d,%d,%d,%d,%d,%d,%d,%.2f,%.4f,\"%s\"%n",
                        stat.name().replace("\"", "\"\""),
                        stat.email().replace("\"", "\"\""),
                        stat.gender(),
                        stat.commitCount(),
                        stat.mergeCount(),
                        stat.linesAdded(),
                        stat.linesDeleted(),
                        stat.filesAdded(),
                        stat.filesEdited(),
                        stat.filesDeletedCount(),
                        stat.meaningfulChangeScore(),
                        stat.averageAiProbability(),
                        formatLanguages(stat.languageBreakdown()).replace("\"", "\"\"")
                    );
                }
                showAlert("Success", "CSV exported successfully to " + file.getAbsolutePath());
            } catch (Exception e) {
                showAlert("Error", "Could not export CSV: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void loadCompanyCsvs(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Metrics CSVs");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null) {
            List<CompanyMetricSelection> selections = new ArrayList<>();
            for (File f : files) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f))) {
                    String header = reader.readLine();
                    String line;
                    int contribCount = 0;
                    int totalCommits = 0;
                    int totalAdded = 0;
                    int totalDeleted = 0;
                    double totalScore = 0;
                    Map<String, Integer> langTotals = new HashMap<>();

                    while ((line = reader.readLine()) != null) {
                        // Very simple CSV parsing (assuming the format we exported)
                        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        if (parts.length >= 13) {
                            contribCount++;
                            totalCommits += Integer.parseInt(parts[3]);
                            totalAdded += Integer.parseInt(parts[5]);
                            totalDeleted += Integer.parseInt(parts[6]);
                            totalScore += Double.parseDouble(parts[10]);
                            
                            String langPart = parts[12].replace("\"", "");
                            String[] langs = langPart.split(";");
                            for (String lp : langs) {
                                if (lp.contains(":")) {
                                    String[] lkv = lp.split(":");
                                    if (lkv.length == 2) {
                                        langTotals.merge(lkv[0].trim(), Integer.parseInt(lkv[1].trim()), Integer::sum);
                                    }
                                }
                            }
                        }
                    }
                    String primaryLang = langTotals.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("N/A");
                    
                    CompanyMetric metric = new CompanyMetric(
                        f.getName(),
                        contribCount,
                        totalCommits,
                        totalAdded,
                        totalDeleted,
                        contribCount > 0 ? totalScore / contribCount : 0,
                        primaryLang,
                        langTotals
                    );
                    selections.add(new CompanyMetricSelection(metric));
                } catch (Exception e) {
                    System.err.println("Error parsing CSV " + f.getName() + ": " + e.getMessage());
                }
            }
            repoSelectionList.getItems().addAll(selections);
        }
    }

    private void refreshCompanyReviewData(boolean updateLowerTabs) {
        if (databaseService == null) return;
        try {
            List<String> repoIds = databaseService.getAllRepoIds();
            List<CompanyMetricSelection> currentSelections = new ArrayList<>(repoSelectionList.getItems());
            List<CompanyMetricSelection> newSelections = new ArrayList<>();

            for (String repoId : repoIds) {
                // Check if already in list
                Optional<CompanyMetricSelection> existing = currentSelections.stream()
                        .filter(s -> s.getMetric().repoName().equals(repoId))
                        .findFirst();
                
                if (existing.isPresent()) {
                    newSelections.add(existing.get());
                    continue;
                }

                List<ContributorStats> stats = databaseService.getLatestMetrics(repoId);
                if (stats.isEmpty()) continue;

                int totalCommits = stats.stream().mapToInt(ContributorStats::commitCount).sum();
                int totalAdded = stats.stream().mapToInt(ContributorStats::linesAdded).sum();
                int totalDeleted = stats.stream().mapToInt(ContributorStats::linesDeleted).sum();
                double avgScore = stats.stream().mapToDouble(ContributorStats::meaningfulChangeScore).average().orElse(0.0);
                
                Map<String, Integer> langTotals = new HashMap<>();
                for (ContributorStats s : stats) {
                    if (s.languageBreakdown() != null) {
                        s.languageBreakdown().forEach((l, c) -> langTotals.merge(l, c, Integer::sum));
                    }
                }
                String primaryLang = langTotals.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A");

                CompanyMetric metric = new CompanyMetric(
                    repoId,
                    stats.size(),
                    totalCommits,
                    totalAdded,
                    totalDeleted,
                    avgScore,
                    primaryLang,
                    langTotals
                );
                newSelections.add(new CompanyMetricSelection(metric));
            }
            
            // Add any CSV loaded ones that are not in DB
            for (CompanyMetricSelection s : currentSelections) {
                if (repoIds.stream().noneMatch(id -> id.equals(s.getMetric().repoName()))) {
                    newSelections.add(s);
                }
            }

            repoSelectionList.setItems(FXCollections.observableArrayList(newSelections));
            
            if (updateLowerTabs) {
                List<CompanyMetric> selectedMetrics = repoSelectionList.getItems().stream()
                        .filter(CompanyMetricSelection::isSelected)
                        .map(CompanyMetricSelection::getMetric)
                        .collect(Collectors.toList());

                List<ContributorStats> allContributors = new ArrayList<>();
                List<CommitInfo> allCommits = new ArrayList<>();

                for (CompanyMetric m : selectedMetrics) {
                    try {
                        // Try to load from DB if it exists
                        List<ContributorStats> stats = databaseService.getLatestMetrics(m.repoName());
                        List<CommitInfo> commits = databaseService.getLatestCommits(m.repoName());
                        if (!stats.isEmpty()) {
                            allContributors.addAll(stats);
                            allCommits.addAll(commits);
                        } else {
                            // If not in DB, it might be from CSV. 
                            // We don't have full contributor stats or commit info from CSV,
                            // but we can create a dummy contributor to represent the repo's totals
                            // so that at least some charts can show something.
                            allContributors.add(new ContributorStats(
                                "Repo: " + new File(m.repoName()).getName(),
                                "repo@example.com",
                                "unknown",
                                m.totalCommits(),
                                0,
                                m.totalLinesAdded(),
                                m.totalLinesDeleted(),
                                m.languageBreakdown(),
                                0.0,
                                0,
                                0,
                                0,
                                m.averageMeaningfulScore(),
                                false,
                                0,
                                0,
                                new HashMap<>()
                            ));
                        }
                    } catch (Exception e) {
                        System.err.println("Error loading data for repo " + m.repoName() + ": " + e.getMessage());
                    }
                }

                if (allContributors.isEmpty()) {
                    showAlert("Warning", "No contributor data found for selected repositories. Some may only have summary metrics from CSV.");
                }

            // Overwrite lower tabs with company-wide contributor stats
            Platform.runLater(() -> {
                List<ContributorStats> aggregatedStats = aggregateContributors(allContributors);
                statsTable.setItems(FXCollections.observableArrayList(groupOthers(aggregatedStats, tableLimitSpinner.getValue())));
                
                // Switch to Statistics tab to ensure UI is updated
                if (statsTabPane != null && statsTab != null) {
                    statsTabPane.getSelectionModel().select(statsTab);
                }

                // Set titles for company mode
                commitPieChart.setTitle("Commits by Repository");
                languagePieChart.setTitle("Language Breakdown (Company)");
                impactBarChart.setTitle("Impact by Repository");
                contribLanguageBarChart.setTitle("Languages by Contributor (Company)");
                activityLineChart.setTitle("Recent Activity (Company)");
                calendarActivityChart.setTitle("Daily Activity (Company)");
                contributorActivityChart.setTitle("Daily Activity per Contributor (Company)");
                commitsPerDayLineChart.setTitle("Commits per Day & per Contributor (Company)");
                devPieChart.setTitle("Code by Developer (Company)");
                projectLangPieChart.setTitle("Language of Projects (Company)");

                // Update shared charts with company-specific visuals where appropriate
                if (!statsTabPane.getTabs().contains(companyBreakdownTab)) {
                    statsTabPane.getTabs().add(1, companyBreakdownTab);
                }

                chartManager.updateCompanyCharts(commitPieChart, languagePieChart, impactBarChart, devPieChart, 
                                                 projectLangPieChart, contribLanguageBarChart,
                                                 activityLineChart, calendarActivityChart, contributorActivityChart, commitsPerDayLineChart,
                                                 companyBreakdownList,
                                                 selectedMetrics, aggregatedStats, allCommits);
            
            // Force layout for company charts
            commitPieChart.applyCss();
            commitPieChart.layout();
            languagePieChart.applyCss();
            languagePieChart.layout();
            impactBarChart.applyCss();
            impactBarChart.layout();
            devPieChart.applyCss();
            devPieChart.layout();
            projectLangPieChart.applyCss();
            projectLangPieChart.layout();
            contribLanguageBarChart.applyCss();
            contribLanguageBarChart.layout();
            activityLineChart.applyCss();
            activityLineChart.layout();
            calendarActivityChart.applyCss();
            calendarActivityChart.layout();
            contributorActivityChart.applyCss();
            contributorActivityChart.layout();
            commitsPerDayLineChart.applyCss();
            commitsPerDayLineChart.layout();

            // Additional force layout for shared charts visibility
            commitPieChart.setVisible(true);
            commitPieChart.setManaged(true);
            languagePieChart.setVisible(true);
            languagePieChart.setManaged(true);
            contribLanguageBarChart.setVisible(true);
            contribLanguageBarChart.setManaged(true);
            impactBarChart.setVisible(true);
            impactBarChart.setManaged(true);
            activityLineChart.setVisible(true);
            activityLineChart.setManaged(true);
            calendarActivityChart.setVisible(true);
            calendarActivityChart.setManaged(true);
            contributorActivityChart.setVisible(true);
            contributorActivityChart.setManaged(true);
            commitsPerDayLineChart.setVisible(true);
            commitsPerDayLineChart.setManaged(true);
            devPieChart.setVisible(true);
            devPieChart.setManaged(true);
            projectLangPieChart.setVisible(true);
            projectLangPieChart.setManaged(true);

                // Final attempt to force tab rendering
                if (statsTabPane != null && visualsTab != null) {
                    statsTabPane.getSelectionModel().select(statsTab);
                    Platform.runLater(() -> statsTabPane.getSelectionModel().select(visualsTab));
                }
            });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ContributorStats> aggregateContributors(List<ContributorStats> all) {
        Map<String, ContributorStats> merged = new HashMap<>();
        for (ContributorStats s : all) {
            merged.merge(s.name(), s, (o, n) -> {
                Map<String, Integer> mLangs = new HashMap<>(o.languageBreakdown());
                n.languageBreakdown().forEach((k, v) -> mLangs.merge(k, v, Integer::sum));
                Map<String, Integer> mDirs = new HashMap<>(o.directoryBreakdown());
                n.directoryBreakdown().forEach((k, v) -> mDirs.merge(k, v, Integer::sum));
                
                return new ContributorStats(
                    o.name(),
                    o.email(),
                    o.gender(),
                    o.commitCount() + n.commitCount(),
                    o.mergeCount() + n.mergeCount(),
                    o.linesAdded() + n.linesAdded(),
                    o.linesDeleted() + n.linesDeleted(),
                    mLangs,
                    (o.averageAiProbability() + n.averageAiProbability()) / 2.0,
                    o.filesAdded() + n.filesAdded(),
                    o.filesEdited() + n.filesEdited(),
                    o.filesDeletedCount() + n.filesDeletedCount(),
                    (o.meaningfulChangeScore() + n.meaningfulChangeScore()) / 2.0,
                    o.touchedTests() || n.touchedTests(),
                    o.generatedFilesPushed() + n.generatedFilesPushed(),
                    o.documentationLinesAdded() + n.documentationLinesAdded(),
                    mDirs
                );
            });
        }
        return merged.values().stream()
                .sorted((s1, s2) -> Integer.compare(s2.commitCount(), s1.commitCount()))
                .collect(Collectors.toList());
    }


    private void exportCompanyToPdf(Stage stage) {
        List<CompanyMetric> selectedMetrics = repoSelectionList.getItems().stream()
                .filter(CompanyMetricSelection::isSelected)
                .map(CompanyMetricSelection::getMetric)
                .collect(Collectors.toList());

        if (selectedMetrics.isEmpty()) {
            showAlert("Warning", "No repositories selected for export. Load repositories or CSVs first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            performCompanyPdfExport(file);
        }
    }

    private void performCompanyPdfExport(File file) {
        try {
            List<CompanyMetric> selectedMetrics = repoSelectionList.getItems().stream()
                    .filter(CompanyMetricSelection::isSelected)
                    .map(CompanyMetricSelection::getMetric)
                    .collect(Collectors.toList());

            if (selectedMetrics.isEmpty()) {
                showAlert("Warning", "No repositories selected for PDF export.");
                return;
            }

            File exportDir = file.getParentFile();
            File pieFile = new File(exportDir, "company_commit_pie.png");
            File langPieFile = new File(exportDir, "company_lang_pie.png");
            File barFile = new File(exportDir, "company_impact_bar.png");
            File devPieFile = new File(exportDir, "company_dev_pie.png");
            File projLangPieFile = new File(exportDir, "company_proj_lang_pie.png");
            File contribLangBarFile = new File(exportDir, "company_contrib_lang_bar.png");
            File lineFile = new File(exportDir, "company_activity_line.png");
            File calFile = new File(exportDir, "company_calendar_activity.png");
            File conFile = new File(exportDir, "company_contributor_activity.png");
            File cpdFile = new File(exportDir, "company_cpd.png");
            File cpdPieFile = new File(exportDir, "company_cpd_pie.png");

            saveNodeSnapshot(commitPieChart, pieFile);
            saveNodeSnapshot(languagePieChart, langPieFile);
            saveNodeSnapshot(impactBarChart, barFile);
            saveNodeSnapshot(devPieChart, devPieFile); 
            saveNodeSnapshot(projectLangPieChart, projLangPieFile); 
            saveNodeSnapshot(contribLanguageBarChart, contribLangBarFile);
            saveNodeSnapshot(activityLineChart, lineFile);
            saveNodeSnapshot(calendarActivityChart, calFile);
            saveNodeSnapshot(contributorActivityChart, conFile);
            saveNodeSnapshot(commitsPerDayLineChart, cpdFile);
            // No pie fallback for line chart
            saveNodeSnapshot(commitsPerDayLineChart, cpdPieFile); 

            Map<String, String> mdSections = new HashMap<>();
            String mdPath = companyReviewMdPathField.getText();
            if (mdPath != null && !mdPath.isEmpty()) {
                File folder = new File(mdPath);
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles((dir, name) -> name.endsWith(".md"));
                    if (files != null) {
                        Arrays.sort(files);
                        for (File f : files) {
                            mdSections.put(f.getName(), java.nio.file.Files.readString(f.toPath()));
                        }
                    }
                }
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("Report Type", "Company Review");
            metadata.put("Repositories Selected", String.valueOf(selectedMetrics.size()));
            metadata.put("Generated On", java.time.LocalDateTime.now().toString());
            metadata.put("User", System.getProperty("user.name"));

            String coverHtml = null;
            String coverBasePath = null;
            String coverPath = coverPagePathField.getText();
            if (coverPath != null && !coverPath.isEmpty()) {
                File cf = new File(coverPath);
                if (cf.exists()) {
                    coverHtml = java.nio.file.Files.readString(cf.toPath());
                    coverHtml = coverHtml.replace("{{project}}", "Company Portfolio Review")
                                         .replace("{{user}}", System.getProperty("user.name"))
                                         .replace("{{generated_on}}", java.time.LocalDate.now().toString());
                    coverBasePath = cf.getParent();
                }
            }

            exportService.exportCompanyToPdf(
                    selectedMetrics,
                    file.getAbsolutePath(),
                    pieFile.getAbsolutePath(),
                    langPieFile.getAbsolutePath(),
                    barFile.getAbsolutePath(),
                    devPieFile.getAbsolutePath(),
                    projLangPieFile.getAbsolutePath(),
                    contribLangBarFile.getAbsolutePath(),
                    lineFile.getAbsolutePath(),
                    calFile.getAbsolutePath(),
                    conFile.getAbsolutePath(),
                    cpdFile.getAbsolutePath(),
                    cpdPieFile.getAbsolutePath(),
                    mdSections,
                    coverHtml,
                    coverBasePath,
                    metadata,
                    null // History not tracked for company yet
            );

            showAlert("Success", "Company report exported to " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not export PDF: " + e.getMessage());
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
                generateLlmReport(() -> {
                    if (databaseService != null) {
                        try {
                            String path = repoPathField.getText();
                            String repoId;
                            try {
                                repoId = new File(path).getCanonicalPath();
                            } catch (Exception e) {
                                repoId = new File(path).getAbsolutePath();
                            }
                            databaseService.saveMetrics(repoId, currentStats);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    performPdfExport(file);
                });
            } else {
                performPdfExport(file);
            }
        }
    }

    private void performPdfExport(File file) {
        try {
            // Take snapshots of charts in the same directory as the PDF
            File exportDir = file.getParentFile();
            File pieFile = new File(exportDir, "pie_chart.png");
            File langPieFile = new File(exportDir, "lang_pie_chart.png");
            File contribLangBarFile = new File(exportDir, "contrib_lang_bar_chart.png");
            File barFile = new File(exportDir, "bar_chart.png");
            File devFile = new File(exportDir, "dev_pie_chart.png");
            File projFile = new File(exportDir, "proj_lang_pie_chart.png");
            File lineFile = new File(exportDir, "line_chart.png");
            File calendarFile = new File(exportDir, "calendar_chart.png");
            File contribFile = new File(exportDir, "contrib_activity.png");
            File cpdFile = new File(exportDir, "commits_per_day.png");
            File cpdPieFile = new File(exportDir, "cpd_pie.png");
            
            saveNodeSnapshot(commitPieChart, pieFile);
            saveNodeSnapshot(languagePieChart, langPieFile);
            saveNodeSnapshot(contribLanguageBarChart, contribLangBarFile);
            saveNodeSnapshot(commitsPerDayLineChart, cpdPieFile);
            saveNodeSnapshot(impactBarChart, barFile);
            saveNodeSnapshot(devPieChart, devFile);
            saveNodeSnapshot(projectLangPieChart, projFile);
            saveNodeSnapshot(activityLineChart, lineFile);
            saveNodeSnapshot(calendarActivityChart, calendarFile);
            saveNodeSnapshot(contributorActivityChart, contribFile);
            saveNodeSnapshot(commitsPerDayLineChart, cpdFile);
            
            String aiReport = null;
            if (aiReviewCheckBox.isSelected() && !llmResponseArea.getText().isEmpty() && !llmResponseArea.getText().startsWith("Generating report")) {
                aiReport = llmResponseArea.getText();
                updateStatsWithAiScores(aiReport);
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

            Map<String, String> metadata = new LinkedHashMap<>();
            File repoDir = new File(repoPathField.getText());
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir)) {
                org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
                String commitHash = head != null ? head.getName().substring(0, 7) : "Unknown";
                
                String latestMessage = "";
                if (head != null) {
                    try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                        org.eclipse.jgit.revwalk.RevCommit commit = walk.parseCommit(head);
                        latestMessage = commit.getShortMessage();
                    }
                }

                // Get tags for the current commit
                String tags = "None";
                try {
                    List<org.eclipse.jgit.lib.Ref> tagRefs = git.tagList().call();
                    List<String> commitTags = new ArrayList<>();
                    for (org.eclipse.jgit.lib.Ref ref : tagRefs) {
                        org.eclipse.jgit.lib.ObjectId peeled = git.getRepository().getRefDatabase().peel(ref).getPeeledObjectId();
                        if (peeled == null) peeled = ref.getObjectId();
                        if (peeled.equals(head)) {
                            commitTags.add(ref.getName().replace("refs/tags/", ""));
                        }
                    }
                    if (!commitTags.isEmpty()) {
                        tags = String.join(", ", commitTags);
                    }
                } catch (Exception e) {
                    // Ignore tag errors
                }
                
                String gitInfo = commitHash;
                if (!tags.equals("None")) {
                    gitInfo += " (" + tags + ")";
                }
                if (!latestMessage.isEmpty()) {
                    gitInfo += " - " + latestMessage;
                }
                metadata.put("Git Info", gitInfo);
                
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL).call();
                String branchList = branches.stream()
                        .map(org.eclipse.jgit.lib.Ref::getName)
                        .map(git.getRepository()::shortenRemoteBranchName)
                        .filter(name -> name != null && !name.equals("HEAD"))
                        .distinct()
                        .collect(Collectors.joining(", "));
                metadata.put("Repo list + branches", repoDir.getName() + " [" + branchList + "]");
            } catch (Exception e) {
                metadata.put("Git Info", "Error retrieving git metadata: " + e.getMessage());
            }

            if (currentMeaningfulAnalysis != null) {
                metadata.put("Time range", currentMeaningfulAnalysis.dateRange());
            }
            
            String appVersion = envConfig.getOrDefault("APP_VERSION", "1.0.0");
            String configVersion = envConfig.getOrDefault("APP_CONFIG_VERSION", "1.0");
            metadata.put("Generator version", "Tool: v" + appVersion + " / Config: v" + configVersion);
            
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
            metadata.put("Generated timestamp (UTC)", now.withZoneSameInstant(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")));
            metadata.put("Generated timestamp (Local)", now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")));
            
            String chartConfig = envConfig.getOrDefault("APP_CHART_CONFIG", "Scaling: 3.0x (UI), 90% (PDF) | Thresholds: 1500+ VERY HIGH, 1000-1500 HIGH, 750-1000 MED-HIGH, 500-750 MED, 250-500 LOW-MED, <250 LOW");
            metadata.put("Chart config / thresholds", chartConfig);
            
            if (envConfig.containsKey("APP_DESCRIPTION")) {
                metadata.put("Description", envConfig.get("APP_DESCRIPTION"));
            }
            
            metadata.put("Parameters", "Commit Limit: " + commitLimitSpinner.getValue() + ", Table Limit: " + tableLimitSpinner.getValue());

            // Handle Report History
            List<ReportHistory> historyList = new ArrayList<>();
            if (databaseService != null) {
                try {
                    String path = repoPathField.getText();
                    String repoId;
                    try {
                        repoId = new File(path).getCanonicalPath();
                    } catch (Exception e) {
                        repoId = new File(path).getAbsolutePath();
                    }

                    String earliestCommit = "unknown";
                    if (currentMeaningfulAnalysis != null && currentMeaningfulAnalysis.commitRange() != null) {
                        String range = currentMeaningfulAnalysis.commitRange();
                        if (range.contains("..")) {
                            earliestCommit = range.split("\\.\\.")[0];
                        } else {
                            earliestCommit = range;
                        }
                    }

                    ReportHistory latest = databaseService.getLatestReportForCommit(repoId, earliestCommit);
                    String newVersion = "1.0";
                    if (manualVersionField != null && !manualVersionField.getText().trim().isEmpty()) {
                        newVersion = manualVersionField.getText().trim();
                    } else if (latest != null) {
                        try {
                            double v = Double.parseDouble(latest.version());
                            newVersion = String.format("%.1f", v + 1.0);
                        } catch (NumberFormatException e) {
                            newVersion = latest.version() + ".1";
                        }
                    }
                    
                    String description = "generation of report version " + newVersion;
                    if (manualDescriptionArea != null && !manualDescriptionArea.getText().trim().isEmpty()) {
                        description = manualDescriptionArea.getText().trim();
                    }

                    ReportHistory newEntry = new ReportHistory(
                        0, 
                        repoId,
                        newVersion, 
                        java.time.LocalDate.now(), 
                        System.getProperty("user.name"), 
                        description, 
                        earliestCommit
                    );
                    databaseService.saveReportHistory(newEntry);
                    historyList = databaseService.getLatestReportHistory(repoId, 5);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            exportService.exportToPdf(currentStats, gitService.getLastCommits(new File(repoPathField.getText()), commitLimitSpinner.getValue(), aliasesMap(), mainBranchField.getText()), currentMeaningfulAnalysis, file.getAbsolutePath(), 
                pieFile.getAbsolutePath(), langPieFile.getAbsolutePath(), contribLangBarFile.getAbsolutePath(), cpdPieFile.getAbsolutePath(),
                barFile.getAbsolutePath(), lineFile.getAbsolutePath(), 
                calendarFile.getAbsolutePath(), contribFile.getAbsolutePath(), cpdFile.getAbsolutePath(), 
                aiReport, mdSections, coverHtml, coverBasePath, tableLimitSpinner.getValue(), metadata, historyList);
            
            Platform.runLater(() -> showAlert("Success", "Report exported to " + file.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showAlert("Error", "Export failed: " + e.getMessage()));
        }
    }

    private void updateStatsWithAiScores(String aiReport) {
        if (aiReport == null || aiReport.isEmpty() || currentStats == null) return;

        // Pattern: [MEANINGFUL_SCORE: Name=XX/100]
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[MEANINGFUL_SCORE:\\s*([^=]+)=\\s*(\\d+)(?:/100)?\\]");
        java.util.regex.Matcher matcher = pattern.matcher(aiReport);

        Map<String, Double> aiScores = new HashMap<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            try {
                double score = Double.parseDouble(matcher.group(2));
                aiScores.put(name.toLowerCase(), score);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        if (aiScores.isEmpty()) return;

        currentStats = currentStats.stream().map(s -> {
            String cleanName = s.name();
            if (cleanName.contains("<") && cleanName.contains(">")) {
                cleanName = cleanName.substring(0, cleanName.indexOf("<")).trim();
            }
            
            Double aiScore = aiScores.get(cleanName.toLowerCase());
            if (aiScore != null) {
                return new ContributorStats(s.name(), s.email(), s.gender(), 
                    s.commitCount(), s.mergeCount(), s.linesAdded(), s.linesDeleted(), 
                    s.languageBreakdown(), s.averageAiProbability(), s.filesAdded(), 
                    s.filesEdited(), s.filesDeletedCount(), aiScore, s.touchedTests(), s.generatedFilesPushed(), s.documentationLinesAdded(), s.directoryBreakdown());
            }
            return s;
        }).collect(Collectors.toList());

        if (databaseService != null) {
            try {
                String path = repoPathField.getText();
                String repoId;
                try {
                    repoId = new File(path).getCanonicalPath();
                } catch (Exception e) {
                    repoId = new File(path).getAbsolutePath();
                }
                databaseService.saveMetrics(repoId, currentStats);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Platform.runLater(() -> {
            List<ContributorStats> tableStats = groupOthers(currentStats, tableLimitSpinner.getValue());
            statsTable.setItems(FXCollections.observableArrayList(tableStats));
        });
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
        params.setTransform(javafx.scene.transform.Transform.scale(3, 3)); 
        // Ensure the node is laid out before taking a snapshot
        if (node instanceof javafx.scene.Parent parent) {
            parent.layout();
        }
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

    private void loadDotenv() {
        // Try to load from local file first (development/override)
        File envFile = new File(".env");
        if (envFile.exists()) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(envFile.toPath());
                parseEnvLines(lines);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback: load from classpath (bundled)
        try (java.io.InputStream is = getClass().getResourceAsStream("/.env")) {
            if (is != null) {
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String content = s.hasNext() ? s.next() : "";
                
                // Bundled .env should be obfuscated
                String deobfuscated = ConfigObfuscator.deobfuscate(content);
                parseEnvLines(java.util.Arrays.asList(deobfuscated.split("\n")));
            }
        } catch (Exception e) {
            System.err.println("Could not load bundled .env: " + e.getMessage());
        }
    }

    private void parseEnvLines(java.util.List<String> lines) {
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                envConfig.put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    private void showAppInfoDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("App Info");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 20, 20));

        grid.add(new Label("Developer:"), 0, 0);
        grid.add(new Label(envConfig.getOrDefault("APP_DEVELOPER", "Graham Hill")), 1, 0);
        
        grid.add(new Label("Version:"), 0, 1);
        grid.add(new Label(envConfig.getOrDefault("APP_VERSION", "1.0-SNAPSHOT")), 1, 1);
        
        grid.add(new Label("Year:"), 0, 2);
        grid.add(new Label(envConfig.getOrDefault("APP_YEAR", "2026")), 1, 2);
        
        grid.add(new Label("Source Code:"), 0, 3);
        Hyperlink sourceLink = new Hyperlink(envConfig.getOrDefault("APP_SOURCE_CODE_URL", "https://github.com/grahamhill/contrib_metric"));
        sourceLink.setOnAction(e -> getHostServices().showDocument(sourceLink.getText()));
        grid.add(sourceLink, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
