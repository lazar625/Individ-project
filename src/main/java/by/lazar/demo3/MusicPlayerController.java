package by.lazar.demo3;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerController {
    
    @FXML
    private ListView<String> playlistView;
    
    @FXML
    private Label songTitleLabel;
    
    @FXML
    private Label artistLabel;
    
    @FXML
    private Label albumArtLabel;
    
    @FXML
    private Label currentTimeLabel;
    
    @FXML
    private Label totalTimeLabel;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Label fileCountLabel;
    
    @FXML
    private Label volumeLabel;
    
    @FXML
    private Button playPauseButton;
    
    @FXML
    private Slider progressSlider;
    
    @FXML
    private Slider volumeSlider;
    
    private ObservableList<String> playlist;
    private List<File> musicFiles;
    private MediaPlayer mediaPlayer;
    private Media currentMedia;
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private boolean isDragging = false;
    
    @FXML
    public void initialize() {
        playlist = FXCollections.observableArrayList();
        musicFiles = new ArrayList<>();
        playlistView.setItems(playlist);
        
        playlistView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    int index = playlist.indexOf(newValue);
                    playTrack(index);
                }
            }
        );
        
        volumeSlider.setValue(50);
        volumeLabel.setText("50%");
        
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            onVolumeChanged();
        });
        
        progressSlider.setOnMousePressed(event -> {
            isDragging = true;
        });
        
        progressSlider.setOnMouseDragged(event -> {
            if (mediaPlayer != null && currentMedia != null) {
                double duration = currentMedia.getDuration().toSeconds();
                double newTime = (progressSlider.getValue() / 100.0) * duration;
                mediaPlayer.seek(Duration.seconds(newTime));
            }
        });
        
        progressSlider.setOnMouseReleased(event -> {
            isDragging = false;
        });
        
        updateFileCount();
    }
    
    @FXML
    protected void openFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите музыкальные файлы");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a", "*.aac", "*.flac", "*.ogg")
        );
        
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            for (File file : selectedFiles) {
                if (!musicFiles.contains(file)) {
                    musicFiles.add(file);
                    playlist.add(file.getName());
                }
            }
            updateFileCount();
            statusLabel.setText("Загружено " + selectedFiles.size() + " файл(ов)");
        }
    }
    
    @FXML
    protected void addToPlaylist() {
        openFiles();
    }
    
    @FXML
    protected void removeFromPlaylist() {
        int selectedIndex = playlistView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            if (selectedIndex == currentTrackIndex) {
                stopCurrentTrack();
            }
            playlist.remove(selectedIndex);
            musicFiles.remove(selectedIndex);
            if (currentTrackIndex > selectedIndex) {
                currentTrackIndex--;
            }
            updateFileCount();
        }
    }
    
    @FXML
    protected void playPause() {
        if (currentTrackIndex < 0 && !playlist.isEmpty()) {
            playTrack(0);
            return;
        }
        
        if (mediaPlayer != null) {
            if (isPlaying) {
                pauseTrack();
            } else {
                resumeTrack();
            }
        } else if (!playlist.isEmpty()) {
            playTrack(0);
        }
    }
    
    @FXML
    protected void nextTrack() {
        if (playlist.isEmpty()) return;
        
        int nextIndex = (currentTrackIndex + 1) % playlist.size();
        playTrack(nextIndex);
    }
    
    @FXML
    protected void previousTrack() {
        if (playlist.isEmpty()) return;
        
        int prevIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        playTrack(prevIndex);
    }
    
    private void onVolumeChanged() {
        double volume = volumeSlider.getValue() / 100.0;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume);
        }
        volumeLabel.setText((int)volumeSlider.getValue() + "%");
    }
    
    private void playTrack(int index) {
        if (index < 0 || index >= musicFiles.size()) return;
        
        stopCurrentTrack();
        
        currentTrackIndex = index;
        File file = musicFiles.get(index);
        
        try {
            currentMedia = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(currentMedia);
            
            // Set volume
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            
            // Update UI when media is ready
            mediaPlayer.setOnReady(() -> {
                Duration duration = currentMedia.getDuration();
                totalTimeLabel.setText(formatTime(duration));
                progressSlider.setMax(100);
            });
            
            mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                if (!isDragging && newValue != null) {
                    Duration duration = currentMedia.getDuration();
                    if (duration != null && duration.toSeconds() > 0) {
                        double progress = (newValue.toSeconds() / duration.toSeconds()) * 100;
                        progressSlider.setValue(progress);
                        currentTimeLabel.setText(formatTime(newValue));
                    }
                }
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                nextTrack();
            });
            
            mediaPlayer.setOnError(() -> {
                statusLabel.setText("Ошибка воспроизведения: " + mediaPlayer.getError().getMessage());
            });
            
            String fileName = file.getName();
            songTitleLabel.setText(fileName.substring(0, fileName.lastIndexOf('.')));
            artistLabel.setText("Неизвестный исполнитель");
            albumArtLabel.setText("🎵");
            
            playlistView.getSelectionModel().select(index);
            
            mediaPlayer.play();
            isPlaying = true;
            playPauseButton.setText("⏸");
            statusLabel.setText("Воспроизведение: " + fileName);
            
        } catch (Exception e) {
            statusLabel.setText("Ошибка загрузки файла: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void pauseTrack() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            isPlaying = false;
            playPauseButton.setText("▶");
            statusLabel.setText("Пауза");
        }
    }
    
    private void resumeTrack() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
            isPlaying = true;
            playPauseButton.setText("⏸");
            statusLabel.setText("Воспроизведение");
        }
    }
    
    private void stopCurrentTrack() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
            currentMedia = null;
            isPlaying = false;
            playPauseButton.setText("▶");
            progressSlider.setValue(0);
            currentTimeLabel.setText("0:00");
            totalTimeLabel.setText("0:00");
        }
    }
    
    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "0:00";
        }
        int minutes = (int) duration.toMinutes();
        int seconds = (int) (duration.toSeconds() % 60);
        return String.format("%d:%02d", minutes, seconds);
    }
    
    private void updateFileCount() {
        fileCountLabel.setText("Треков: " + playlist.size());
    }
}

