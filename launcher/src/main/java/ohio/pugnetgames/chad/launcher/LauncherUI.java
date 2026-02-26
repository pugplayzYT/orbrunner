package ohio.pugnetgames.chad.launcher;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LauncherUI {

    private final Stage stage;
    private final GameManager gameManager;
    private final StackPane root;
    private final SettingsManager settings = new SettingsManager();

    // Main UI
    private Button actionButton;
    private Label statusLabel;
    private Label versionInfoLabel;
    private ComboBox<String> versionCombo;

    // Progress overlay
    private VBox progressOverlay;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label downloadSizeLabel;

    // Settings overlay
    private VBox settingsOverlay;

    // Changelog overlay
    private VBox changelogOverlay;
    private Label changelogTitleLabel;
    private VBox changelogContentBox;
    private Slider sensitivitySlider;
    private Label sensitivityValueLabel;
    private Slider fovSlider;
    private Label fovValueLabel;
    private Slider fogSlider;
    private Label fogValueLabel;
    private Slider volumeSlider;
    private Label volumeValueLabel;
    private ToggleButton invertYToggle;

    // Suppresses re-saving when we're the ones loading from file
    private boolean updatingFromFile = false;

    public LauncherUI(Stage stage) {
        this.stage = stage;
        this.gameManager = new GameManager();
        this.root = new StackPane();
        root.getStyleClass().add("root-pane");

        buildUI();

        settings.load();
        settings.watchForExternalChanges(s -> Platform.runLater(this::syncSlidersFromSettings));

        refreshState();
    }

    public StackPane getRoot() {
        return root;
    }

    // ──────────────────────────────────────────────
    // UI Construction
    // ──────────────────────────────────────────────

    private void buildUI() {
        VBox mainContent = new VBox(20);
        mainContent.setAlignment(Pos.CENTER);
        mainContent.setPadding(new Insets(40, 50, 40, 50));

        VBox titleSection = buildTitleSection();
        VBox actionSection = buildActionSection();
        HBox bottomBar = buildBottomBar();

        Region topSpacer = new Region();
        VBox.setVgrow(topSpacer, Priority.SOMETIMES);
        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.SOMETIMES);

        mainContent.getChildren().addAll(titleSection, topSpacer, actionSection, bottomSpacer, bottomBar);

        progressOverlay = buildProgressOverlay();
        progressOverlay.setVisible(false);
        progressOverlay.setOpacity(0);

        settingsOverlay = buildSettingsOverlay();
        settingsOverlay.setVisible(false);
        settingsOverlay.setOpacity(0);

        changelogOverlay = buildChangelogOverlay();
        changelogOverlay.setVisible(false);
        changelogOverlay.setOpacity(0);

        root.getChildren().addAll(mainContent, progressOverlay, settingsOverlay, changelogOverlay);
    }

    private VBox buildTitleSection() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);

        Label title = new Label("ORBRUNNER");
        title.getStyleClass().add("title-label");

        Label subtitle = new Label("L A U N C H E R");
        subtitle.getStyleClass().add("subtitle-label");

        versionInfoLabel = new Label("");
        versionInfoLabel.getStyleClass().add("version-info-label");

        box.getChildren().addAll(title, subtitle, versionInfoLabel);
        return box;
    }

    private VBox buildActionSection() {
        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);

        actionButton = new Button("PLAY");
        actionButton.getStyleClass().add("action-button");
        actionButton.setPrefWidth(260);
        actionButton.setPrefHeight(60);
        actionButton.setOnAction(e -> onActionButtonClicked());

        statusLabel = new Label("Checking for updates...");
        statusLabel.getStyleClass().add("status-label");

        box.getChildren().addAll(actionButton, statusLabel);
        return box;
    }

    private HBox buildBottomBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label versionLabel = new Label("Version:");
        versionLabel.getStyleClass().add("combo-label");

        versionCombo = new ComboBox<>();
        versionCombo.getStyleClass().add("version-combo");
        versionCombo.setPrefWidth(160);
        versionCombo.setPromptText("Latest");
        versionCombo.setOnAction(e -> onVersionSelected());

        Button refreshBtn = new Button("⟳");
        refreshBtn.getStyleClass().add("refresh-button");
        refreshBtn.setOnAction(e -> refreshState());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label serverLabel = new Label("●");
        serverLabel.getStyleClass().add("server-indicator");
        serverLabel.setId("serverIndicator");

        Label serverText = new Label("Server");
        serverText.getStyleClass().add("server-text");

        Button changelogBtn = new Button("Patch Notes");
        changelogBtn.getStyleClass().add("changelog-button");
        changelogBtn.setOnAction(e -> {
            String v = versionCombo.getValue();
            showChangelog(v != null ? v : "");
        });

        Button settingsBtn = new Button("⚙");
        settingsBtn.getStyleClass().add("settings-gear-button");
        settingsBtn.setOnAction(e -> showSettings());

        bar.getChildren().addAll(versionLabel, versionCombo, refreshBtn, spacer, serverLabel, serverText, changelogBtn, settingsBtn);
        return bar;
    }

    private VBox buildProgressOverlay() {
        VBox overlay = new VBox(20);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("progress-overlay");

        Label downloadTitle = new Label("DOWNLOADING");
        downloadTitle.getStyleClass().add("download-title");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("download-progress");
        progressBar.setPrefWidth(500);
        progressBar.setPrefHeight(24);

        progressLabel = new Label("Connecting...");
        progressLabel.getStyleClass().add("progress-text");

        downloadSizeLabel = new Label("");
        downloadSizeLabel.getStyleClass().add("download-size-text");

        Button cancelBtn = new Button("CANCEL");
        cancelBtn.getStyleClass().add("cancel-button");
        cancelBtn.setOnAction(e -> cancelDownload());

        overlay.getChildren().addAll(downloadTitle, progressBar, progressLabel, downloadSizeLabel, cancelBtn);
        return overlay;
    }

    private VBox buildSettingsOverlay() {
        VBox overlay = new VBox(0);
        overlay.setAlignment(Pos.TOP_LEFT);
        overlay.getStyleClass().add("settings-overlay");
        overlay.setFillWidth(true);

        // ── Header ──
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("settings-header");

        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("✕");
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> hideSettings());

        header.getChildren().addAll(title, spacer, backBtn);

        // ── Rows ──
        VBox rows = new VBox(6);
        rows.getStyleClass().add("settings-rows");

        // Sensitivity
        sensitivityValueLabel = new Label();
        sensitivitySlider = buildSlider(0.01, 0.5, settings.sensitivity, sensitivityValueLabel,
                v -> String.format("%.2f", v), v -> {
                    settings.sensitivity = v.floatValue();
                    settings.save();
                });
        rows.getChildren().add(buildSettingsRow("Sensitivity", sensitivitySlider, sensitivityValueLabel));

        // Field of View
        fovValueLabel = new Label();
        fovSlider = buildSlider(30, 120, settings.fieldOfView, fovValueLabel,
                v -> String.format("%.0f°", v), v -> {
                    settings.fieldOfView = v.floatValue();
                    settings.save();
                });
        rows.getChildren().add(buildSettingsRow("Field of View", fovSlider, fovValueLabel));

        // Fog Density
        fogValueLabel = new Label();
        fogSlider = buildSlider(0.0, 0.2, settings.fogDensity, fogValueLabel,
                v -> String.format("%.3f", v), v -> {
                    settings.fogDensity = v.floatValue();
                    settings.save();
                });
        rows.getChildren().add(buildSettingsRow("Fog Density", fogSlider, fogValueLabel));

        // Volume
        volumeValueLabel = new Label();
        volumeSlider = buildSlider(0.0, 1.0, settings.masterVolume, volumeValueLabel,
                v -> String.format("%.0f%%", v * 100), v -> {
                    settings.masterVolume = v.floatValue();
                    settings.save();
                });
        rows.getChildren().add(buildSettingsRow("Volume", volumeSlider, volumeValueLabel));

        // Invert Y
        invertYToggle = new ToggleButton(settings.invertY ? "On" : "Off");
        invertYToggle.setSelected(settings.invertY);
        invertYToggle.getStyleClass().add("invert-toggle");
        invertYToggle.selectedProperty().addListener((obs, old, selected) -> {
            if (updatingFromFile) return;
            invertYToggle.setText(selected ? "On" : "Off");
            settings.invertY = selected;
            settings.save();
        });
        rows.getChildren().add(buildSettingsRowToggle("Invert Y", invertYToggle));

        // Hint
        Label hint = new Label("Changes apply immediately when the game is running.");
        hint.getStyleClass().add("settings-hint");

        overlay.getChildren().addAll(header, rows, hint);
        return overlay;
    }

    private Slider buildSlider(double min, double max, double initial,
            Label valueLabel, java.util.function.Function<Double, String> formatter,
            java.util.function.Consumer<Double> onChange) {
        Slider slider = new Slider(min, max, initial);
        slider.getStyleClass().add("settings-slider");
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        valueLabel.setText(formatter.apply(initial));
        valueLabel.getStyleClass().add("settings-value-label");
        slider.valueProperty().addListener((obs, old, val) -> {
            valueLabel.setText(formatter.apply(val.doubleValue()));
            if (!updatingFromFile) {
                onChange.accept(val.doubleValue());
            }
        });
        return slider;
    }

    private HBox buildSettingsRow(String labelText, Slider slider, Label valueLabel) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");

        Label label = new Label(labelText);
        label.getStyleClass().add("settings-row-label");
        label.setMinWidth(110);

        valueLabel.setMinWidth(46);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox.setHgrow(slider, Priority.ALWAYS);
        row.getChildren().addAll(label, slider, valueLabel);
        return row;
    }

    private HBox buildSettingsRowToggle(String labelText, ToggleButton toggle) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");

        Label label = new Label(labelText);
        label.getStyleClass().add("settings-row-label");
        label.setMinWidth(110);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(label, spacer, toggle);
        return row;
    }

    // ──────────────────────────────────────────────
    // Changelog Overlay
    // ──────────────────────────────────────────────

    private VBox buildChangelogOverlay() {
        VBox overlay = new VBox();
        overlay.getStyleClass().add("changelog-overlay");
        overlay.setFillWidth(true);

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("settings-header");

        changelogTitleLabel = new Label();
        changelogTitleLabel.getStyleClass().add("settings-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("✕");
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> hideChangelog());

        header.getChildren().addAll(changelogTitleLabel, spacer, backBtn);

        // Scrollable content
        changelogContentBox = new VBox(4);
        changelogContentBox.getStyleClass().add("changelog-content");
        changelogContentBox.setPadding(new Insets(4, 0, 20, 0));

        ScrollPane scroll = new ScrollPane(changelogContentBox);
        scroll.getStyleClass().add("changelog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        overlay.getChildren().addAll(header, scroll);
        return overlay;
    }

    private void showChangelog(String version) {
        if (version == null || version.isEmpty()) {
            version = versionCombo.getItems().isEmpty() ? "" : versionCombo.getItems().get(0);
        }
        final String finalVersion = version;

        changelogTitleLabel.setText(finalVersion);
        changelogContentBox.getChildren().clear();
        Label loading = new Label("Loading...");
        loading.getStyleClass().add("md-text");
        changelogContentBox.getChildren().add(loading);

        changelogOverlay.setVisible(true);
        FadeTransition fade = new FadeTransition(Duration.millis(200), changelogOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        new Thread(() -> {
            String content = gameManager.fetchChangelog(finalVersion);
            Platform.runLater(() -> {
                changelogContentBox.getChildren().clear();
                if (content != null) {
                    // Extract H1 title for the header, then render the rest
                    String[] lines = content.split("\n");
                    StringBuilder body = new StringBuilder();
                    boolean titleFound = false;
                    for (String line : lines) {
                        if (!titleFound && line.trim().startsWith("# ")) {
                            changelogTitleLabel.setText(line.trim().substring(2).replace("**", "").trim());
                            titleFound = true;
                            continue; // don't render the H1 again in the body
                        }
                        body.append(line).append("\n");
                    }
                    changelogContentBox.getChildren().addAll(MarkdownRenderer.render(body.toString()));
                } else {
                    Label err = new Label("No changelog available for " + finalVersion + ".");
                    err.getStyleClass().add("md-text");
                    changelogContentBox.getChildren().add(err);
                }
            });
        }).start();
    }

    private void hideChangelog() {
        FadeTransition fade = new FadeTransition(Duration.millis(200), changelogOverlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> changelogOverlay.setVisible(false));
        fade.play();
    }

    // ──────────────────────────────────────────────
    // Settings sync
    // ──────────────────────────────────────────────

    /** Called when the file watcher detects the game updated settings. */
    private void syncSlidersFromSettings() {
        updatingFromFile = true;
        sensitivitySlider.setValue(settings.sensitivity);
        fovSlider.setValue(settings.fieldOfView);
        fogSlider.setValue(settings.fogDensity);
        volumeSlider.setValue(settings.masterVolume);
        invertYToggle.setSelected(settings.invertY);
        invertYToggle.setText(settings.invertY ? "On" : "Off");
        updatingFromFile = false;
    }

    private void showSettings() {
        syncSlidersFromSettings();
        settingsOverlay.setVisible(true);
        FadeTransition fade = new FadeTransition(Duration.millis(200), settingsOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void hideSettings() {
        FadeTransition fade = new FadeTransition(Duration.millis(200), settingsOverlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> settingsOverlay.setVisible(false));
        fade.play();
    }

    // ──────────────────────────────────────────────
    // State Management
    // ──────────────────────────────────────────────

    private DownloadTask currentDownload;

    private void refreshState() {
        statusLabel.setText("Checking server...");
        actionButton.setDisable(true);

        // Preserve the current selection across the refresh
        String previousSelection = versionCombo.getValue();

        new Thread(() -> {
            GameManager.VersionInfo latest = gameManager.getLatestVersion();
            List<GameManager.VersionInfo> versions = gameManager.getAvailableVersions();
            boolean hasAny = gameManager.hasAnyInstall();

            Platform.runLater(() -> {
                versionCombo.getItems().clear();
                if (!versions.isEmpty()) {
                    List<String> versionStrings = versions.stream()
                            .map(v -> v.version)
                            .collect(Collectors.toList());
                    // Latest first
                    Collections.reverse(versionStrings);
                    versionCombo.getItems().addAll(versionStrings);
                    setServerOnline(true);
                } else {
                    setServerOnline(false);
                }

                // Restore previous selection if it's still in the list
                if (previousSelection != null && versionCombo.getItems().contains(previousSelection)) {
                    versionCombo.setValue(previousSelection);
                }

                if (latest != null) {
                    versionInfoLabel.setText("Latest: " + latest.version);
                    String selectedVersion = getSelectedVersion(latest.version);

                    if (!hasAny) {
                        actionButton.setText("INSTALL");
                        statusLabel.setText("Ready to install " + selectedVersion);
                    } else if (!gameManager.isInstalled(selectedVersion)) {
                        actionButton.setText("DOWNLOAD");
                        statusLabel.setText(selectedVersion + " needs to be downloaded");
                    } else if (gameManager.needsUpdate() && selectedVersion.equals(latest.version)) {
                        actionButton.setText("UPDATE");
                        statusLabel.setText("Update available: " + latest.version);
                    } else {
                        actionButton.setText("PLAY");
                        statusLabel.setText("Ready to launch " + selectedVersion);
                    }
                } else {
                    versionInfoLabel.setText("Server offline");
                    if (hasAny) {
                        actionButton.setText("PLAY");
                        statusLabel.setText("Playing offline — cached version");
                    } else {
                        actionButton.setText("INSTALL");
                        actionButton.setDisable(true);
                        statusLabel.setText("Cannot connect to server");
                        return;
                    }
                }

                actionButton.setDisable(false);
            });
        }).start();
    }

    private String getSelectedVersion(String fallbackLatest) {
        String selected = versionCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            String configSelected = gameManager.getConfig().selectedVersion;
            if (configSelected != null) return configSelected;
            return fallbackLatest;
        }
        return selected;
    }

    private void setServerOnline(boolean online) {
        Label indicator = (Label) root.lookup("#serverIndicator");
        if (indicator != null) {
            indicator.setStyle(online ? "-fx-text-fill: #2f9e44;" : "-fx-text-fill: #e03131;");
        }
    }

    // ──────────────────────────────────────────────
    // Actions
    // ──────────────────────────────────────────────

    private void onActionButtonClicked() {
        String buttonText = actionButton.getText();
        GameManager.VersionInfo latest = gameManager.getLatestVersion();
        String version = getSelectedVersion(latest != null ? latest.version : null);

        if (version == null) {
            statusLabel.setText("No version available");
            return;
        }

        switch (buttonText) {
            case "INSTALL":
            case "DOWNLOAD":
            case "UPDATE":
                startDownload(version);
                break;
            case "PLAY":
                launchGame(version);
                break;
        }
    }

    private void startDownload(String version) {
        String url = gameManager.getDownloadUrl(version);
        Path destination = gameManager.jarPath(version);

        currentDownload = new DownloadTask(url, destination);

        progressBar.progressProperty().bind(currentDownload.progressProperty());
        progressLabel.textProperty().bind(currentDownload.messageProperty());

        currentDownload.setOnSucceeded(e -> {
            hideProgressOverlay();
            statusLabel.setText("Downloaded " + version + " successfully!");
            gameManager.setSelectedVersion(version);
            refreshState();
        });
        currentDownload.setOnFailed(e -> {
            hideProgressOverlay();
            Throwable ex = currentDownload.getException();
            statusLabel.setText("Download failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });
        currentDownload.setOnCancelled(e -> {
            hideProgressOverlay();
            statusLabel.setText("Download cancelled");
        });

        showProgressOverlay();
        Thread downloadThread = new Thread(currentDownload);
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void cancelDownload() {
        if (currentDownload != null && currentDownload.isRunning()) {
            currentDownload.cancel();
        }
    }

    private void launchGame(String version) {
        statusLabel.setText("Launching " + version + "...");
        actionButton.setDisable(true);

        new Thread(() -> {
            try {
                gameManager.launchGame(version);
                Platform.runLater(() -> {
                    statusLabel.setText("Game is running...");
                    PauseTransition pause = new PauseTransition(Duration.seconds(3));
                    pause.setOnFinished(e -> {
                        actionButton.setDisable(false);
                        statusLabel.setText("Ready");
                    });
                    pause.play();
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to launch: " + ex.getMessage());
                    actionButton.setDisable(false);
                });
            }
        }).start();
    }

    private void onVersionSelected() {
        String selected = versionCombo.getValue();
        if (selected != null) {
            gameManager.setSelectedVersion(selected);
            if (gameManager.isInstalled(selected)) {
                actionButton.setText("PLAY");
                statusLabel.setText("Ready to launch " + selected);
            } else {
                actionButton.setText("DOWNLOAD");
                statusLabel.setText(selected + " needs to be downloaded");
            }
        }
    }

    // ──────────────────────────────────────────────
    // Progress Overlay
    // ──────────────────────────────────────────────

    private void showProgressOverlay() {
        progressOverlay.setVisible(true);
        FadeTransition fade = new FadeTransition(Duration.millis(300), progressOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        actionButton.setDisable(true);
        versionCombo.setDisable(true);
    }

    private void hideProgressOverlay() {
        FadeTransition fade = new FadeTransition(Duration.millis(300), progressOverlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> progressOverlay.setVisible(false));
        fade.play();

        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressBar.setProgress(0);
        progressLabel.setText("");

        actionButton.setDisable(false);
        versionCombo.setDisable(false);
    }
}
