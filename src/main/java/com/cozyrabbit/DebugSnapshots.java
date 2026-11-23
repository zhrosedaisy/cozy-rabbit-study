package com.cozyrabbit;

import com.cozyrabbit.ui.ReportsWindow;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Supplier;

/**
 * Developer helper: run with -Dcozyrabbit.snapshot=/some/dir to save screenshots of the
 * main window and the report pages, then quit. Not used in normal runs.
 */
final class DebugSnapshots {

    private DebugSnapshots() {
    }

    static void run(String dir, Stage stage, Runnable openReports, Supplier<ReportsWindow> reports, Runnable openFish) {
        PauseTransition first = new PauseTransition(Duration.seconds(4));
        first.setOnFinished(e -> {
            save(stage.getScene(), new File(dir, "app.png"));
            openReports.run();
            PauseTransition second = new PauseTransition(Duration.seconds(2));
            second.setOnFinished(e2 -> {
                for (Window w : new java.util.ArrayList<>(Window.getWindows())) {
                    if (w != stage && w.getScene() != null
                            && w.getScene().getRoot() instanceof javafx.scene.control.TabPane) {
                        save(w.getScene(), new File(dir, "report-daily.png"));
                        javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) w.getScene().getRoot();
                        tabs.getSelectionModel().select(1);
                        PauseTransition third = new PauseTransition(Duration.seconds(1.5));
                        third.setOnFinished(e3 -> {
                            save(w.getScene(), new File(dir, "report-weekly.png"));
                            w.hide();
                            PauseTransition fourth = new PauseTransition(Duration.seconds(1.5));
                            fourth.setOnFinished(e4 -> {
                                save(stage.getScene(), new File(dir, "fish-bubble.png"));
                                stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                                javafx.application.Platform.exit();
                            });
                            fourth.play();
                            javafx.application.Platform.runLater(openFish);
                        });
                        third.play();
                        return;
                    }
                }
            });
            second.play();
        });
        first.play();
    }

    static void save(Scene scene, File file) {
        WritableImage img = scene.snapshot(null);
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, r.getArgb(x, y));
            }
        }
        try {
            ImageIO.write(out, "png", file);
        } catch (Exception ex) {
            System.err.println("Snapshot failed: " + ex.getMessage());
        }
    }
}
