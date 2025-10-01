package com.musicplayer;

import javafx.animation.KeyFrame;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.AudioEqualizer;
import javafx.scene.media.EqualizerBand;
import javafx.util.Duration;

import java.io.File;
import java.util.*;

public class App extends Application {

    private Map<String, List<File>> playlists = new HashMap<>();
    private Map<File, List<String>> tagsMap = new HashMap<>();
    private String currentPlaylist = null;
    private int current = -1;
    private MediaPlayer mediaPlayer;

    private ComboBox<String> playlistCombo = new ComboBox<>();
    private ListView<String> listView = new ListView<>();
    private ObservableList<String> songNames = FXCollections.observableArrayList();
    private TextField searchField = new TextField();

    // Metadata display
    private Label songTitleLabel = new Label("Title: ");
    private Label songArtistLabel = new Label("Artist: ");
    private Label songAlbumLabel = new Label("Album: ");
    private ImageView albumArtView = new ImageView();

    // Shuffle & Repeat
    private boolean shuffle = false;
    private String repeatMode = "None";

    // Volume & Crossfade
    private final int CROSSFADE_DURATION = 5;

    // Seek/Progress
    private Slider progressSlider = new Slider();
    private Label currentTimeLabel = new Label("00:00");
    private Label totalTimeLabel = new Label("00:00");

    @Override
    public void start(Stage primaryStage) {

        // --- Buttons for playback ---
        Button importBtn = new Button("Import Songs");
        Button playBtn = new Button("Play/Pause");
        Button resumeBtn = new Button("Resume");
        Button stopBtn = new Button("Stop");
        Button nextBtn = new Button("Next");
        Button prevBtn = new Button("Previous");

        Button shuffleBtn = new Button("Shuffle: OFF");
        Button repeatBtn = new Button("Repeat: None");

        Slider volumeSlider = new Slider(0, 1, 1);
        Label volumeLabel = new Label("Volume:");

        HBox controls = new HBox(10, importBtn, prevBtn, resumeBtn, playBtn, stopBtn, nextBtn,
                shuffleBtn, repeatBtn, volumeLabel, volumeSlider);

        // --- Playlist management ---
        TextField playlistNameField = new TextField();
        playlistNameField.setPromptText("Enter playlist name");

        Button createPlaylistBtn = new Button("Create Playlist");
        Button deletePlaylistBtn = new Button("Delete Playlist");
        Button addSongBtn = new Button("Add Song");
        Button removeSongBtn = new Button("Remove Song");

        VBox playlistControls = new VBox(10, playlistNameField, createPlaylistBtn, deletePlaylistBtn,
                addSongBtn, removeSongBtn, playlistCombo, listView);
        playlistControls.setPrefWidth(250);

        // --- Equalizer ---
        Label eqLabel = new Label("Equalizer:");
        Slider bassSlider = new Slider(-12, 12, 0);
        Slider midSlider = new Slider(-12, 12, 0);
        Slider trebleSlider = new Slider(-12, 12, 0);
        VBox eqBox = new VBox(5, eqLabel,
                new Label("Bass"), bassSlider,
                new Label("Mid"), midSlider,
                new Label("Treble"), trebleSlider);
        eqBox.setPrefWidth(200);

        ComboBox<String> presetCombo = new ComboBox<>();
        presetCombo.getItems().addAll("Flat", "Bass Boost", "Treble Boost", "Vocal Boost");
        presetCombo.setValue("Flat");

        // --- Metadata UI ---
        albumArtView.setFitHeight(150);
        albumArtView.setFitWidth(150);
        VBox metadataBox = new VBox(5, albumArtView, songTitleLabel, songArtistLabel, songAlbumLabel);
        metadataBox.setPrefWidth(200);

        // --- Search bar ---
        searchField.setPromptText("Search by song name or tag");

        // --- Root layout ---
        HBox rootControls = new HBox(10, playlistControls, eqBox, metadataBox, presetCombo);

        // --- Seek/Progress UI ---
        progressSlider.setMin(0);
        progressSlider.setMax(100);
        progressSlider.setValue(0);
        progressSlider.setPrefWidth(400);
        HBox progressBox = new HBox(10, currentTimeLabel, progressSlider, totalTimeLabel);

        VBox root = new VBox(10, rootControls, controls, progressBox, searchField);
        Scene scene = new Scene(root, 1000, 550);

        primaryStage.setTitle("Music Player");
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- Event Handlers ---
        createPlaylistBtn.setOnAction(e -> {
            String name = playlistNameField.getText().trim();
            if (!name.isEmpty() && !playlists.containsKey(name)) {
                playlists.put(name, new ArrayList<>());
                playlistCombo.getItems().setAll(playlists.keySet());
                playlistNameField.clear();
            }
        });

        deletePlaylistBtn.setOnAction(e -> {
            String selected = playlistCombo.getValue();
            if (selected != null) {
                playlists.remove(selected);
                playlistCombo.getItems().setAll(playlists.keySet());
                listView.getItems().clear();
                currentPlaylist = null;
                current = -1;
            }
        });

        addSongBtn.setOnAction(e -> {
            String selected = playlistCombo.getValue();
            if (selected == null) {
                showAlert("No Playlist Selected", "Please select a playlist first.");
                return;
            }
            importSongs(primaryStage, selected);
        });

        importBtn.setOnAction(e -> {
            String targetPlaylist = currentPlaylist;
            if (targetPlaylist == null) {
                targetPlaylist = "Default Playlist";
                playlists.putIfAbsent(targetPlaylist, new ArrayList<>());
                playlistCombo.getItems().setAll(playlists.keySet());
                currentPlaylist = targetPlaylist;
            }
            importSongs(primaryStage, targetPlaylist);
        });

        removeSongBtn.setOnAction(e -> {
            String selectedPlaylist = playlistCombo.getValue();
            String selectedSong = listView.getSelectionModel().getSelectedItem();
            if (selectedPlaylist != null && selectedSong != null) {
                List<File> songs = playlists.get(selectedPlaylist);
                songs.removeIf(f -> f.getName().equals(selectedSong));
                updateSongList(selectedPlaylist);
            }
        });

        resumeBtn.setOnAction(e -> {
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                mediaPlayer.play();
            }
        });

        playlistCombo.setOnAction(e -> {
            String selected = playlistCombo.getValue();
            if (selected != null) {
                currentPlaylist = selected;
                updateSongList(selected);
                current = 0;
            }
        });

        playBtn.setOnAction(e -> {
            if (currentPlaylist != null && !playlists.get(currentPlaylist).isEmpty()) {
                if (mediaPlayer == null && current >= 0) playTrack(currentPlaylist, current);
                else if (mediaPlayer != null) {
                    if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
                    else mediaPlayer.play();
                }
            }
        });

        stopBtn.setOnAction(e -> {
            if (mediaPlayer != null) mediaPlayer.stop();
        });

        nextBtn.setOnAction(e -> nextTrack());
        prevBtn.setOnAction(e -> prevTrack());

        shuffleBtn.setOnAction(e -> {
            shuffle = !shuffle;
            shuffleBtn.setText(shuffle ? "Shuffle: ON" : "Shuffle: OFF");
        });

        repeatBtn.setOnAction(e -> {
            if ("None".equals(repeatMode)) repeatMode = "One";
            else if ("One".equals(repeatMode)) repeatMode = "All";
            else repeatMode = "None";
            repeatBtn.setText("Repeat: " + repeatMode);
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(newVal.doubleValue());
        });

        listView.setOnMouseClicked(event -> {
            int selectedIndex = listView.getSelectionModel().getSelectedIndex();
            if (currentPlaylist != null && selectedIndex >= 0) {
                current = selectedIndex;
                playTrack(currentPlaylist, current);
            }
        });

        presetCombo.setOnAction(e -> applyPreset(presetCombo.getValue(), bassSlider, midSlider, trebleSlider));

        // --- Seek/Progress Slider listener ---
        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && mediaPlayer != null) {
                Duration total = mediaPlayer.getTotalDuration();
                if (total != null && !total.isUnknown()) {
                    double seekMillis = progressSlider.getValue() / 100 * total.toMillis();
                    mediaPlayer.seek(Duration.millis(seekMillis));
                }
            }
        });
    }

    private void importSongs(Stage stage, String playlistName) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.flac"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            playlists.get(playlistName).addAll(files);
            for (File f : files) tagsMap.putIfAbsent(f, new ArrayList<>());
            updateSongList(playlistName);
            if (current == -1) current = 0;
        }
    }

    private void updateSongList(String playlistName) {
        songNames.clear();
        List<File> songs = playlists.get(playlistName);
        if (songs != null) {
            for (File song : songs) songNames.add(song.getName());
        }
        listView.setItems(songNames);
    }

    private void playTrack(String playlistName, int index) {
        List<File> songs = playlists.get(playlistName);
        if (songs == null || songs.isEmpty() || index < 0 || index >= songs.size()) return;

        Media media = new Media(songs.get(index).toURI().toString());
        MediaPlayer newPlayer = new MediaPlayer(media);
        setupEqualizer(newPlayer);

        if (mediaPlayer != null) mediaPlayer.stop();
        mediaPlayer = newPlayer;

        // Metadata listener
        media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
            if (change.wasAdded()) {
                Object value = change.getValueAdded();
                String key = change.getKey();
                if ("title".equals(key) && value instanceof String) songTitleLabel.setText("Title: " + value);
                else if ("artist".equals(key) && value instanceof String) songArtistLabel.setText("Artist: " + value);
                else if ("album".equals(key) && value instanceof String) songAlbumLabel.setText("Album: " + value);
                else if ("image".equals(key) && value instanceof Image) albumArtView.setImage((Image) value);
            }
        });

        // Update progress slider during playback
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && !total.isUnknown() && !progressSlider.isValueChanging()) {
                progressSlider.setValue(newTime.toMillis() / total.toMillis() * 100);
                currentTimeLabel.setText(formatTime(newTime));
                totalTimeLabel.setText(formatTime(total));
            }
        });

        mediaPlayer.play();

        mediaPlayer.setOnEndOfMedia(() -> {
            if ("One".equals(repeatMode)) {
                playTrack(currentPlaylist, current);
            } else {
                nextTrack();
            }
        });
    }

    private void setupEqualizer(MediaPlayer mp) {
        AudioEqualizer eq = mp.getAudioEqualizer();
        eq.setEnabled(true);
        List<EqualizerBand> bands = eq.getBands();
        if (bands.size() >= 3) {
            bands.get(0).setGain(0);
            bands.get(1).setGain(0);
            bands.get(2).setGain(0);
        }
    }

    private void nextTrack() {
        if (currentPlaylist != null && !playlists.get(currentPlaylist).isEmpty()) {
            if (shuffle) {
                Random rand = new Random();
                current = rand.nextInt(playlists.get(currentPlaylist).size());
            } else {
                current = (current + 1) % playlists.get(currentPlaylist).size();
                if ("None".equals(repeatMode) && current == 0) {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    return;
                }
            }
            playTrack(currentPlaylist, current);
        }
    }

    private void prevTrack() {
        if (currentPlaylist != null && !playlists.get(currentPlaylist).isEmpty()) {
            if (shuffle) {
                Random rand = new Random();
                current = rand.nextInt(playlists.get(currentPlaylist).size());
            } else {
                current = (current - 1 + playlists.get(currentPlaylist).size()) % playlists.get(currentPlaylist).size();
            }
            playTrack(currentPlaylist, current);
        }
    }

    private void applyPreset(String preset, Slider bass, Slider mid, Slider treble) {
        if ("Flat".equals(preset)) { bass.setValue(0); mid.setValue(0); treble.setValue(0); }
        else if ("Bass Boost".equals(preset)) { bass.setValue(8); mid.setValue(0); treble.setValue(-2); }
        else if ("Treble Boost".equals(preset)) { bass.setValue(-2); mid.setValue(0); treble.setValue(8); }
        else if ("Vocal Boost".equals(preset)) { bass.setValue(-2); mid.setValue(6); treble.setValue(2); }

        if (mediaPlayer != null) setupEqualizer(mediaPlayer);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatTime(Duration duration) {
        int seconds = (int) duration.toSeconds();
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
