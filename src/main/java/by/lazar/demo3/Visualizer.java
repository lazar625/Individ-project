package by.lazar.demo3;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Random;

public class Visualizer extends Application {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 400;

    private Random random = new Random();

    @Override
    public void  start(Stage primaryStage) {
        primaryStage.setTitle("Audio Visualizer");

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                draw(gc);
            }
        };

        timer.start();

        StackPane root = new StackPane();
        root.getChildren().add(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void draw(GraphicsContext gc) {
        // Очистка канваса
        gc.clearRect(0, 0, WIDTH, HEIGHT);

        // Генерация случайных "пиков" для визуализации
        int barWidth = 10;
        for (int i = 0; i < WIDTH / barWidth; i++) {
            int barHeight = random.nextInt(HEIGHT);
            gc.fillRect(i * barWidth, HEIGHT - barHeight, barWidth - 1, barHeight);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}