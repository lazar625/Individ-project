package by.lazar.demo3;

import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
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
    
    @FXML
    private Canvas visualizerCanvas;
    
    private ObservableList<String> playlist;
    private List<File> musicFiles;
    private MediaPlayer mediaPlayer;
    private Media currentMedia;
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private boolean isDragging = false;
    
    // Данные спектра для визуализатора (обновляются из AudioSpectrumListener)
    private volatile float[] spectrumMagnitudes = new float[0];
    
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
        
        // Фиксированный размер Canvas — без привязки к контейнеру, чтобы при Play не плыла разметка
        if (visualizerCanvas != null) {
            visualizerCanvas.setWidth(640);
            visualizerCanvas.setHeight(180);
        }
        
        // Запуск визуализатора спектра
        startVisualizerTimer();
    }
    
    private void startVisualizerTimer() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawVisualizer();
            }
        };
        timer.start();
    }
    
    private void drawVisualizer() {
        if (visualizerCanvas == null) return;
        GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
        double w = visualizerCanvas.getWidth();
        double h = visualizerCanvas.getHeight();
        gc.clearRect(0, 0, w, h);
        
        float[] mags = spectrumMagnitudes;
        if (mags.length == 0) {
            // Когда нет звука — рисуем тихую линию
            gc.setFill(Color.rgb(74, 144, 226, 0.3));
            gc.fillRect(0, h * 0.8, w, 2);
            return;
        }
        
        // Меньше полосок = толще полоски (мин. ширина полоски ~14 px)
        int bars = Math.min((int) (w / 14), mags.length);
        double barWidth = w / bars;
        // Диапазон dB примерно от -60 до 0, нормализуем в высоту
        double maxDb = 0;
        double minDb = -60;
        
        for (int i = 0; i < bars; i++) {
            int idx = (i * mags.length) / bars;
            float dB = mags[idx];
            double t = (dB - minDb) / (maxDb - minDb);
            t = Math.max(0, Math.min(1, t));
            double barHeight = (h / 2) * t;
            double x = i * barWidth + 1;
            double y = h / 2 - barHeight / 2;
            gc.setFill(Color.rgb(74, 144, 226, 0.5 + 0.5 * t));
            gc.fillRect(x, y, barWidth - 1, barHeight);
            // Зеркало внизу
            gc.setFill(Color.rgb(74, 144, 226, 0.2 + 0.3 * t));
            gc.fillRect(x, h / 2, barWidth - 1, barHeight);
        }
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
            
            // Визуализация: включаем аудиоспектр и слушатель
            mediaPlayer.setAudioSpectrumNumBands(64);
            mediaPlayer.setAudioSpectrumInterval(0.05);
            mediaPlayer.setAudioSpectrumThreshold(-60);
            mediaPlayer.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
                spectrumMagnitudes = magnitudes.clone();
            });
            
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
            spectrumMagnitudes = new float[0];
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

