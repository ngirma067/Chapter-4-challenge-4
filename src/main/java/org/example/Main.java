package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class Main extends Application {

    private DiaryManager diaryManager;
    private ObservableList<DiaryEntry> entryList;
    private ListView<DiaryEntry> entryListView;
    private TextField titleField;
    private HTMLEditor contentEditor;
    private WebView entryPreview;
    private TextField searchField;
    private BorderPane root;
    private boolean isDarkMode = false;
    private DiaryEntry currentEntry = null;
    private Scene scene;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    private Timeline autoSaveTimeline;
    private String lastSavedContent = "";
    private String lastSavedTitle = "";

    @Override
    public void start(Stage primaryStage) {
        diaryManager = new DiaryManager();
        entryList = FXCollections.observableArrayList();

        root = new BorderPane();
        root.setPadding(new Insets(10));

        // Left Pane: Navigation and Search
        VBox leftPane = new VBox(10);
        leftPane.setPrefWidth(250);
        
        searchField = new TextField();
        searchField.setPromptText("Search entries...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterEntries(newValue));

        entryListView = new ListView<>(entryList);
        entryListView.setCellFactory(param -> new ListCell<DiaryEntry>() {
            @Override
            protected void updateItem(DiaryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });
        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadEntryForReading(newVal);
            }
        });

        Button newEntryButton = new Button("New Entry");
        newEntryButton.setMaxWidth(Double.MAX_VALUE);
        newEntryButton.setOnAction(e -> prepareNewEntry());

        Button deleteButton = new Button("Delete Entry");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(e -> deleteSelectedEntry());

        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.setMaxWidth(Double.MAX_VALUE);
        themeToggle.setOnAction(e -> toggleTheme(themeToggle.isSelected()));

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(30, 30);
        progressIndicator.setVisible(false);
        
        HBox progressBox = new HBox(progressIndicator);
        progressBox.setAlignment(Pos.CENTER);

        leftPane.getChildren().addAll(searchField, entryListView, newEntryButton, deleteButton, themeToggle, progressBox);
        root.setLeft(leftPane);

        // Center Pane: Editor or Preview
        VBox centerPane = new VBox(10);
        centerPane.setPadding(new Insets(0, 0, 0, 10));
        
        titleField = new TextField();
        titleField.setPromptText("Entry Title");
        
        contentEditor = new HTMLEditor();
        contentEditor.setPrefHeight(400);
        
        entryPreview = new WebView();
        entryPreview.setPrefHeight(400);
        entryPreview.setVisible(false);

        HBox buttonBox = new HBox(10);
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveEntry(true));
        
        Button editButton = new Button("Edit");
        editButton.setOnAction(e -> {
            if (currentEntry != null) {
                loadEntryForEditing(currentEntry);
            }
        });

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: gray;");

        buttonBox.getChildren().addAll(saveButton, editButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        HBox bottomBar = new HBox(10, buttonBox, new Region(), statusLabel);
        HBox.setHgrow(new Region(), Priority.ALWAYS);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        centerPane.getChildren().addAll(titleField, contentEditor, entryPreview, bottomBar);
        root.setCenter(centerPane);

        // Initial Load
        refreshEntryList();
        prepareNewEntry();

        // Auto-save setup (every 30 seconds)
        autoSaveTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> performAutoSave()));
        autoSaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autoSaveTimeline.play();

        scene = new Scene(root, 950, 650);
        
        URL cssResource = getClass().getResource("/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("Warning: styles.css not found");
        }
        
        primaryStage.setTitle("Personal Diary Manager");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisible(show);
    }

    private void refreshEntryList() {
        showProgress(true);
        CompletableFuture.supplyAsync(() -> diaryManager.getAllEntries())
                .thenAccept(entries -> Platform.runLater(() -> {
                    entryList.setAll(entries);
                    showProgress(false);
                }));
    }

    private void filterEntries(String query) {
        showProgress(true);
        if (query == null || query.isEmpty()) {
            refreshEntryList();
        } else {
            CompletableFuture.supplyAsync(() -> diaryManager.searchEntries(query))
                    .thenAccept(entries -> Platform.runLater(() -> {
                        entryList.setAll(entries);
                        showProgress(false);
                    }));
        }
    }

    private void prepareNewEntry() {
        currentEntry = null;
        titleField.clear();
        contentEditor.setHtmlText("");
        titleField.setEditable(true);
        contentEditor.setVisible(true);
        entryPreview.setVisible(false);
        entryListView.getSelectionModel().clearSelection();
        lastSavedContent = "";
        lastSavedTitle = "";
        statusLabel.setText("New Entry");
    }

    private void loadEntryForReading(DiaryEntry entry) {
        currentEntry = entry;
        titleField.setText(entry.getTitle());
        titleField.setEditable(false);
        
        contentEditor.setVisible(false);
        entryPreview.setVisible(true);
        entryPreview.getEngine().loadContent(entry.getContent());
        
        lastSavedContent = entry.getContent();
        lastSavedTitle = entry.getTitle();
        statusLabel.setText("Reading: " + entry.getTitle());
    }

    private void loadEntryForEditing(DiaryEntry entry) {
        currentEntry = entry;
        titleField.setText(entry.getTitle());
        titleField.setEditable(true);
        contentEditor.setHtmlText(entry.getContent());
        
        contentEditor.setVisible(true);
        entryPreview.setVisible(false);
        
        lastSavedContent = entry.getContent();
        lastSavedTitle = entry.getTitle();
        statusLabel.setText("Editing: " + entry.getTitle());
    }

    private void performAutoSave() {
        // Only auto-save if we are in edit mode (content editor is visible)
        if (contentEditor.isVisible()) {
            String currentContent = contentEditor.getHtmlText();
            String currentTitle = titleField.getText();

            // Check if there are changes
            if (!currentContent.equals(lastSavedContent) || !currentTitle.equals(lastSavedTitle)) {
                if (!currentTitle.isEmpty()) {
                    statusLabel.setText("Auto-saving...");
                    saveEntry(false);
                }
            }
        }
    }

    private void saveEntry(boolean showSuccessAlert) {
        String title = titleField.getText();
        String content = contentEditor.getHtmlText();

        if (title.isEmpty()) {
            if (showSuccessAlert) showAlert("Error", "Title cannot be empty.");
            return;
        }

        showProgress(true);
        CompletableFuture.runAsync(() -> {
            try {
                if (currentEntry == null) {
                    DiaryEntry newEntry = new DiaryEntry(title, content);
                    diaryManager.saveEntry(newEntry);
                    currentEntry = newEntry; // Update current entry so subsequent saves update this one
                } else {
                    DiaryEntry updatedEntry = new DiaryEntry(title, content, currentEntry.getTimestamp());
                    // Preserve filename if it exists to ensure correct deletion/update
                    updatedEntry.setFilename(currentEntry.getFilename());
                    diaryManager.updateEntry(currentEntry, updatedEntry);
                    currentEntry = updatedEntry;
                }
                
                // Update tracking variables
                lastSavedTitle = title;
                lastSavedContent = content;

                Platform.runLater(() -> {
                    refreshEntryList();
                    statusLabel.setText("Saved: " + title);
                    showProgress(false);
                    if (showSuccessAlert) showAlert("Success", "Entry saved successfully.");
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    showProgress(false);
                    showAlert("Error", "Failed to save entry: " + e.getMessage());
                });
            }
        });
    }

    private void deleteSelectedEntry() {
        DiaryEntry selected = entryListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showProgress(true);
            CompletableFuture.runAsync(() -> {
                try {
                    diaryManager.deleteEntry(selected);
                    Platform.runLater(() -> {
                        refreshEntryList();
                        prepareNewEntry();
                        showProgress(false);
                        statusLabel.setText("Entry deleted");
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showProgress(false);
                        showAlert("Error", "Failed to delete entry: " + e.getMessage());
                    });
                }
            });
        }
    }

    private void toggleTheme(boolean darkMode) {
        isDarkMode = darkMode;
        if (isDarkMode) {
            root.getStyleClass().add("dark-mode");
        } else {
            root.getStyleClass().remove("dark-mode");
        }
        applyDarkThemeToEditor();
    }

    private void applyDarkThemeToEditor() {
        WebView webView = (WebView) contentEditor.lookup(".web-view");
        if (webView != null) {
            if (isDarkMode) {
                String css = getClass().getResource("/dark-editor.css").toExternalForm();
                webView.getEngine().setUserStyleSheetLocation(css);
            } else {
                webView.getEngine().setUserStyleSheetLocation(null);
            }
        }
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