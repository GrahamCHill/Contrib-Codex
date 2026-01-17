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

        // Center: Stats Table and Charts
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(0, 10, 0, 0));
        
        statsTable = new TableView<>();
        TableColumn<ContributorStats, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<ContributorStats, Integer> commitsCol = new TableColumn<>("Commits");
        commitsCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().commitCount()).asObject());
        TableColumn<ContributorStats, Integer> addedCol = new TableColumn<>("Added");
        addedCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().linesAdded()).asObject());
        TableColumn<ContributorStats, Integer> deletedCol = new TableColumn<>("Deleted");
        deletedCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().linesDeleted()).asObject());

        statsTable.getColumns().addAll(nameCol, commitsCol, addedCol, deletedCol);
        statsTable.setPrefHeight(200);

        HBox chartsBox = new HBox(10);
        commitPieChart = new PieChart();
        commitPieChart.setTitle("Commits by Contributor");
        commitPieChart.setPrefSize(400, 300);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        impactBarChart = new BarChart<>(xAxis, yAxis);
        impactBarChart.setTitle("Impact (Lines Added/Deleted)");
        impactBarChart.setPrefSize(400, 300);

        chartsBox.getChildren().addAll(commitPieChart, impactBarChart);

        centerBox.getChildren().addAll(new Label("Top 10 Contributors:"), statsTable, chartsBox);
        root.setCenter(centerBox);

        // Right: Git Tree and Initial Commit
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(0, 0, 0, 10));
        commitList = new ListView<>();
        commitList.setPrefWidth(300);
        initialCommitLabel = new Label("Initial Commit: N/A");
        rightBox.getChildren().addAll(new Label("Recent Commits:"), commitList, initialCommitLabel);
        root.setRight(rightBox);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
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
                    commitList.setItems(FXCollections.observableArrayList(
                            recentCommits.stream().map(c -> "[" + c.id() + "] " + c.authorName() + ": " + c.message()).toList()
                    ));
                    if (initial != null) {
                        initialCommitLabel.setText("Initial Commit: [" + initial.id() + "] by " + initial.authorName());
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
