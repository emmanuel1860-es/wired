import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renderiza una interfaz FXML de G-Presets a PNG, sin G-Earth (solo para revisar el aspecto).
 * Uso:  xvfb-run -a java ... Render.java <carpeta_ui> <archivo.fxml> <salida.png> [ancho alto] [pestana]
 * Quita fx:controller y los manejadores on*="#..." (no hay programa detras), carga styles.css y las fuentes de la carpeta.
 */
public class Render extends Application {
    @Override
    public void start(Stage st) throws Exception {
        List<String> a = getParameters().getRaw();
        Path ui = Paths.get(a.get(0)).toAbsolutePath();
        String fxml = a.get(1);
        File out = new File(a.get(2));
        double w = a.size() > 4 ? Double.parseDouble(a.get(3)) : -1, h = a.size() > 4 ? Double.parseDouble(a.get(4)) : -1;
        int tab = a.size() > 5 ? Integer.parseInt(a.get(5)) : -1;
        Path tmp = Files.createTempDirectory("gprender");
        try (java.util.stream.Stream<Path> s = Files.list(ui)) {
            for (Path p : s.collect(Collectors.toList())) {
                String n = p.getFileName().toString();
                if (n.endsWith(".fxml")) {
                    String t = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    t = t.replaceAll("\\sfx:controller=\"[^\"]*\"", "").replaceAll("\\son[A-Za-z]+=\"#[^\"]*\"", "");
                    Files.write(tmp.resolve(n), t.getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(p, tmp.resolve(n), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        try (java.util.stream.Stream<Path> s = Files.list(tmp)) {
            for (Path p : s.collect(Collectors.toList())) {
                if (p.toString().endsWith(".ttf")) Font.loadFont(p.toUri().toString(), 12);
            }
        }
        Parent root = FXMLLoader.load(tmp.resolve(fxml).toUri().toURL());
        Scene sc = (w > 0) ? new Scene(root, w, h) : new Scene(root);
        sc.getStylesheets().add(tmp.resolve("styles.css").toUri().toString());
        st.setScene(sc);
        st.show();
        if (tab >= 0) {
            TabPane tp = (TabPane) root.lookup(".tab-pane");
            if (tp != null) tp.getSelectionModel().select(tab);
        }
        PauseTransition pt = new PauseTransition(Duration.millis(900));
        pt.setOnFinished(e -> {
            try {
                WritableImage img = sc.snapshot(null);
                ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
                System.out.println("OK " + out + " " + (int) img.getWidth() + "x" + (int) img.getHeight());
            } catch (Exception ex) { ex.printStackTrace(); }
            Platform.exit();
        });
        pt.play();
    }
    public static void main(String[] args) { launch(args); }
}
