import gearth.ui.themes.Theme;
import gearth.ui.themes.ThemeFactory;
import gearth.ui.titlebar.DefaultTitleBarConfig;
import gearth.ui.titlebar.TitleBarController;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Renderiza G-Presets como lo ve el usuario de verdad: hoja del tema de G-Earth + styles.css + barra de titulo
 * de G-Earth + PremiumLook (iconos, logo, insignia) + HideTabs (quita "Pixel art" y "Sala llena").
 * Propiedades: ui, fxml, out, tab, theme, premium, hide, w, h, report
 */
public class RenderReal extends Application {
    static String p(String k, String d) { return System.getProperty(k, d); }

    @Override
    public void start(Stage st) throws Exception {
        Path ui = Paths.get(p("ui", null)).toAbsolutePath();
        String fxml = p("fxml", "gpresets.fxml");
        File out = new File(p("out", "/tmp/out.png"));
        int tab = Integer.parseInt(p("tab", "-1"));
        boolean premium = Boolean.parseBoolean(p("premium", "true"));
        boolean hide = Boolean.parseBoolean(p("hide", "true"));
        boolean titlebar = Boolean.parseBoolean(p("titlebar", "true"));
        boolean report = Boolean.parseBoolean(p("report", "false"));
        double w = Double.parseDouble(p("w", "-1")), h = Double.parseDouble(p("h", "-1"));
        double opacity = Double.parseDouble(p("opacity", "1.0"));
        Path tmp0 = Files.createTempDirectory("gprender"); Path tmp = tmp0.resolve("extension/ui"); Files.createDirectories(tmp);
        try (java.util.stream.Stream<Path> s = Files.list(ui)) {
            for (Path pp : s.collect(Collectors.toList())) {
                String n = pp.getFileName().toString();
                if (n.endsWith(".fxml")) {
                    String t = new String(Files.readAllBytes(pp), StandardCharsets.UTF_8);
                    t = t.replaceAll("\\sfx:controller=\"[^\"]*\"", "").replaceAll("\\son[A-Za-z]+=\"#[^\"]*\"", "");
                    Files.write(tmp.resolve(n), t.getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(pp, tmp.resolve(n), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Parent root = FXMLLoader.load(tmp.resolve(fxml).toUri().toURL());
        Scene sc = new Scene(root);
        st.setScene(sc);
        sc.getStylesheets().add(tmp.resolve("styles.css").toUri().toString());
        Theme theme = ThemeFactory.themeForTitle(p("theme", "G-Earth Dark"));
        if (titlebar) {
            st.setTitle("G-Presets - Building & Wired Presets - 1.2.11-premium30");
            DefaultTitleBarConfig cfg = new DefaultTitleBarConfig(st, theme) {
                @Override public boolean displayThemePicker() { return false; }
            };
            TitleBarController.create(st, cfg);
            Platform.runLater(() -> {
                sc.getRoot().getStyleClass().add(theme.title().replace(" ", "-").toLowerCase());
                sc.getRoot().getStyleClass().add(theme.isDark() ? "g-dark" : "g-light");
            });
        } else {
            root.getStyleClass().add(theme.isDark() ? "g-dark" : "g-light");
        }
        st.setResizable(false);
        final boolean early = Boolean.parseBoolean(p("early", "true"));
        Runnable post = () -> {
            try {
                Parent shown = sc.getRoot();
                if (premium) {
                    Class<?> pl = Class.forName("extension.tools.PremiumLook");
                    Method ap = pl.getMethod("apply", Parent.class);
                    ap.invoke(null, shown);
                } else {
                    try (java.util.stream.Stream<Path> s2 = Files.list(tmp)) {
                        for (Path pp : s2.collect(Collectors.toList())) {
                            if (pp.toString().endsWith(".ttf")) Font.loadFont(pp.toUri().toString(), 12);
                        }
                    }
                }
                if (hide) strip(shown);
                if (!p("demo", "").isEmpty()) demo(shown, p("demo", ""));
                for (String spec : p("forcepc", "").split(",")) {
                    if (spec.isEmpty()) continue;
                    String[] kv = spec.split(":");
                    Node fn = shown.lookup("#" + kv[1]);
                    if (fn != null) { for (String ps : kv[0].split("\\+")) fn.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(ps), true); }
                }
                for (String id : p("forcefocus", "").split(",")) {
                    if (id.isEmpty()) continue;
                    Node fn = shown.lookup("#" + id);
                    if (fn != null) fn.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                }
                if (tab >= 0) {
                    TabPane tp = (TabPane) shown.lookup(".tab-pane");
                    if (tp != null) tp.getSelectionModel().select(tab);
                }
            } catch (Throwable ex) { ex.printStackTrace(); }
        };
        if (early) {
            // el tema de G-Earth se anade con runLater: simularlo antes de aplicar el aspecto
            if (titlebar) {
                Platform.runLater(() -> {
                    Platform.runLater(post);
                    PauseTransition shw = new PauseTransition(Duration.millis(300));
                    shw.setOnFinished(ev -> {
                        if (w > 0) { st.setWidth(w); st.setHeight(h); } else { st.sizeToScene(); }
                        st.setOpacity(opacity);
                        st.show();
                    });
                    shw.play();
                });
            } else { post.run(); }
        }
        if (!early || !titlebar) {
            if (w > 0) { st.setWidth(w); st.setHeight(h); } else { st.sizeToScene(); }
            st.setOpacity(opacity);
            st.show();
        }
        if (!early) {
            PauseTransition pt0 = new PauseTransition(Duration.millis(600));
            pt0.setOnFinished(e0 -> post.run());
            pt0.play();
        }

        final String dlg = p("dialog", "");
        if (!dlg.isEmpty() || !p("preview", "").isEmpty()) {
            PauseTransition pd = new PauseTransition(Duration.millis(900));
            pd.setOnFinished(ed -> {
                try {
                    if (dlg.equals("paste")) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Pegar \u00ablista_compra_individual_8\u00bb");
                        alert.setHeaderText("Te faltan 14 furnis de 3 tipos");
                        javafx.scene.layout.VBox vb = new javafx.scene.layout.VBox(8.0);
                        CheckBox cb = new CheckBox("Usar el furni m\u00e1s parecido que s\u00ed tengo en lugar de los que faltan");
                        Label lb = new Label("No est\u00e1n en tu inventario ni en el Club del Arquitecto. A la derecha de cada uno, lo m\u00e1s parecido que s\u00ed tienes:");
                        lb.setWrapText(true);
                        TextArea ta = new TextArea("bc_block_1 x8  ->  bc_block_2\nwf_trg_say_something x4  ->  (nada parecido)\nclub_sofa x2  ->  habbo_sofa");
                        ta.setEditable(false); ta.setWrapText(false); ta.setPrefSize(700.0, 210.0);
                        vb.getChildren().addAll(lb, ta, cb);
                        alert.getDialogPane().setContent(vb);
                        alert.initOwner(st);
                        if (p("dialogfix", "").equals("1")) { alert.setGraphic(null); alert.getDialogPane().getStyleClass().add("gp-root"); }
                        alert.show();
                        PauseTransition pp2 = new PauseTransition(Duration.millis(700));
                        pp2.setOnFinished(e2 -> snap(alert.getDialogPane().getScene(), new File(out.getPath().replace(".png", "_dialog.png"))));
                        pp2.play();
                    } else if (dlg.equals("noowner")) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Pegar con piso");
                        alert.setHeaderText("Ya hay furnis donde va la copia");
                        javafx.scene.control.ButtonType b1 = new javafx.scene.control.ButtonType("Pegar de todos modos");
                        javafx.scene.control.ButtonType b2 = new javafx.scene.control.ButtonType("Recoger esos 12 y pegar");
                        alert.setContentText("\u2022 12 furnis de tipo bc_block_1 en 0,0\n\u2022 Recoger: vuelven a tu inventario y la copia se pega limpia.\n\u2022 Pegar de todos modos: se quedan y lo nuevo se apila encima.");
                        alert.getButtonTypes().setAll(b2, b1, javafx.scene.control.ButtonType.CANCEL);
                        alert.show();
                        PauseTransition pp2 = new PauseTransition(Duration.millis(700));
                        pp2.setOnFinished(e2 -> snap(alert.getDialogPane().getScene(), new File(out.getPath().replace(".png", "_dialog.png"))));
                        pp2.play();
                    } else if (dlg.equals("undo")) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Quitar la \u00faltima pegada");
                        alert.setHeaderText("Se recoger\u00e1n 2244 furnis");
                        String longTxt = "Son solo los que coloc\u00f3 la \u00faltima pegada en esta sala (piso y pared). Lo que ya estaba antes no se toca. Los de tu inventario vuelven al inventario; los del Club del Arquitecto vuelven al almac\u00e9n.";
                        if (p("dialogfix", "").equals("2")) {
                            Label cl = new Label(longTxt); cl.setWrapText(true); cl.setPrefWidth(440); cl.setMaxWidth(440); cl.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                            alert.getDialogPane().setContent(cl); alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                        } else alert.setContentText(longTxt);
                        alert.initOwner(st);
                        if (p("dialogfix", "").equals("1") || p("dialogfix", "").equals("2")) { alert.setGraphic(null); alert.getDialogPane().getStyleClass().add("gp-root"); }
                        alert.show();
                        PauseTransition pp2 = new PauseTransition(Duration.millis(700));
                        pp2.setOnFinished(e2 -> snap(alert.getDialogPane().getScene(), new File(out.getPath().replace(".png", "_dialog.png"))));
                        pp2.play();
                    }
                    if (!p("preview", "").isEmpty()) {
                        Class<?> vv = Class.forName("extension.tools.vista.VistaPreviaVentana");
                        Method ab = vv.getMethod("abrir", Scene.class, String.class);
                        ab.invoke(null, sc, null);
                        PauseTransition pp3 = new PauseTransition(Duration.millis(Long.parseLong(p("previewdelay","900"))));
                        if (p("previewfix", "").equals("1")) { PauseTransition pp4 = new PauseTransition(Duration.millis(300)); pp4.setOnFinished(e4 -> { for (javafx.stage.Window wd : javafx.stage.Window.getWindows()) if (wd != st && wd.getScene() != null && wd.isShowing()) wd.getScene().getRoot().getStyleClass().add("gp-root"); }); pp4.play(); }
                        pp3.setOnFinished(e3 -> {
                            for (javafx.stage.Window wd : new ArrayList<>(javafx.stage.Window.getWindows())) {
                                if (wd != st && wd.getScene() != null && wd.isShowing()) {
                                    snap(wd.getScene(), new File(out.getPath().replace(".png", "_preview.png")));
                                }
                            }
                        });
                        pp3.play();
                    }
                } catch (Throwable ex) { ex.printStackTrace(); }
            });
            pd.play();
        }
        if (p("bench", "").equals("1")) {
            PauseTransition pb = new PauseTransition(Duration.millis(1500));
            pb.setOnFinished(eb -> {
                Node bar = sc.getRoot().lookup("#progressBar");
                Node st2 = sc.getRoot().lookup("#homeStatusLbl");
                for (int ww = 0; ww < 10; ww++) { ((ProgressBar) bar).setProgress(ww / 100.0); sc.snapshot(null); }
                long t0 = System.nanoTime(); int N = 150;
                for (int i = 0; i < N; i++) {
                    ((ProgressBar) bar).setProgress(i / (double) N);
                    ((Labeled) st2).setText("Pegando " + i + " de " + N);
                    sc.snapshot(null);
                }
                System.out.printf("BENCH %.1f ms por actualizacion (%d)%n", (System.nanoTime() - t0) / 1e6 / N, N);
            });
            pb.play();
        }
        PauseTransition pt = new PauseTransition(Duration.millis(Long.parseLong(p("wait", "2200"))));
        pt.setOnFinished(e -> {
            try {
                if (report) report(sc.getRoot());
                WritableImage img = sc.snapshot(null);
                BufferedImage bi = SwingFXUtils.fromFXImage(img, null);
                BufferedImage rgb = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(new Color(0x7a7a7a));
                g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
                g.drawImage(bi, 0, 0, null);
                g.dispose();
                ImageIO.write(rgb, "png", out);
                System.out.println("OK " + out + " " + (int) img.getWidth() + "x" + (int) img.getHeight());
            } catch (Exception ex) { ex.printStackTrace(); }
            Platform.exit();
        });
        pt.play();
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    static void demo(Parent root, String mode) {
        try {
            boolean first = mode.contains("first");
            String[] names = first ? new String[0] : new String[]{"lista_compra_individual_8_demo", "lista_compra_individual_8", "Colombo", "sala_12345678", "pato", "casa_de_playa_con_piscina_y_jardin_v2"};
            Node n;
            if (first) {
                for (String id : new String[]{"copyBtn", "pasteBtn", "importBtn", "availabilityBtn", "homeAbortBtn", "undoFloorBtn", "undoPasteBtn", "currentPresetBtn"}) {
                    Node bn = root.lookup("#" + id); if (bn != null) bn.setDisable(true);
                }
            }
            if ((n = root.lookup("#presetListView")) instanceof ListView && !first) { ((ListView) n).getItems().setAll(names); ((ListView) n).getSelectionModel().select(1); }
            if (!first && (n = root.lookup("#homePresetCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll(names); ((ComboBox) n).setValue(names[0]); }
            if (!first) setText(root, "#homePresetInfoLbl", "2244 furnis + 12 de pared  \u00b7  130 wireds  \u00b7  con piso (1234 baldosas)");
            if (mode.contains("room")) {
                setText(root, "#homeRoomLbl", "Est\u00e1s en una sala"); cls(root, "#homeRoomLbl", "lblgreen");
            } else {
                cls(root, "#homeRoomLbl", "lblred");
            }
            if (mode.contains("falta")) { setText(root, "#resumenLbl", "Falta: Conectado a Habbo, Sala detectada y 4 m\u00e1s"); cls(root, "#resumenLbl", "lblred"); }
            else if (mode.contains("listo")) { setText(root, "#resumenLbl", "\u2714  Todo listo para pegar"); cls(root, "#resumenLbl", "lblgreen"); }
            else if (first) { setText(root, "#resumenLbl", "Esperando a Habbo\u2026"); cls(root, "#resumenLbl", "lblgrey"); }
            else { setText(root, "#resumenLbl", "Entra a una sala"); cls(root, "#resumenLbl", "lblgrey"); }
            if (!first && (n = root.lookup("#undoPasteBtn")) instanceof Button) { ((Button) n).setText("Quitar lo \u00faltimo que pegu\u00e9 (2244)"); ((Button) n).setDisable(false); }
            if (mode.contains("status")) setText(root, "#homeStatusLbl", "No pude cargar la copia \u00ablista_compra_individual_8_demo\u00bb: java.io.EOFException: Unexpected end of file from server while reading the JSON");
            else if (first) setText(root, "#homeStatusLbl", "Listo. Entra a una sala para empezar.");
            else setText(root, "#homeStatusLbl", "Pegando 1520 de 2244\u2026");
            if ((n = root.lookup("#logsBorderPane")) instanceof javafx.scene.layout.BorderPane) {
                extension.logger.Logger lg = new extension.logger.Logger();
                lg.initialize((javafx.scene.layout.BorderPane) n);
                lg.log("Bienvenido a G-Presets (version mejorada)", "purple");
                lg.log("Comandos en el chat de Habbo:", "purple");
                lg.log("* :exportpreset [all] / :ep [all]", "purple");
                lg.log("* :importpreset [x,y] / :ip [x,y]", "purple");
                lg.log("* :abort / :a", "purple");
                lg.log("* :crop [N|ver|copiar|deshacer] - recorta el plano a las baldosas con furnis", "purple");
                lg.log("* :ep all guarda tambien el plano recortado; :plano lo aplica al preset elegido (:plano deshacer)", "purple");
                lg.log("Doble clic en un preset para elegirlo", "purple");
                lg.log("Si Habbo rechaza un furni (limite o no permitido) se omite y se sigue.", "purple");
                lg.log("Preset elegido: \"lista_compra_individual_8_demo\"", "green");
                lg.log("Leaving room..", "blue");
                lg.log("Couldn't load \"x\": y", "red");
                if (p("wrap", "").equals("1")) { Node ta = n.lookup(".styled-text-area"); if (ta instanceof org.fxmisc.richtext.StyleClassedTextArea) ((org.fxmisc.richtext.StyleClassedTextArea) ta).setWrapText(true); }
            }
            if (mode.contains("longprog")) {
                setText(root, "#progressLbl", "Pegando 80% (120/150) - faltan bc_block_1, wf_trg_say_something");
                Node pb = root.lookup("#progressBar"); if (pb instanceof ProgressBar) ((ProgressBar) pb).setProgress(0.8);
                setText(root, "#progressPctLbl", "80%");
                setText(root, "#homeStatusLbl", "Colocando furnis y wireds\u2026");
            }
            if (mode.contains("errprog")) {
                setText(root, "#progressLbl", "Cancelado: faltan furni");
                setText(root, "#homeStatusLbl", "Faltaban 14 furnis que no est\u00e1n ni en tu inventario ni en el cat\u00e1logo BC.");
            }
            if (mode.contains("speed")) {
                Node sl = root.lookup("#ratelimiter");
                if (sl != null && sl.getParent() instanceof javafx.scene.layout.Pane) {
                    javafx.scene.layout.Pane pane = (javafx.scene.layout.Pane) sl.getParent();
                    ComboBox<String> box = new ComboBox<>();
                    box.getItems().addAll("Segura (lenta)", "Normal (recomendada)", "Rapida (puede faltar alguno)");
                    box.setMaxWidth(Double.MAX_VALUE);
                    box.getSelectionModel().select(1);
                    int idx = pane.getChildren().indexOf(sl);
                    sl.setVisible(false); sl.setManaged(false);
                    if (idx + 1 < pane.getChildren().size() && pane.getChildren().get(idx + 1) instanceof Label) { pane.getChildren().get(idx + 1).setVisible(false); pane.getChildren().get(idx + 1).setManaged(false); }
                    pane.getChildren().add(idx, box);
                    javafx.scene.layout.HBox.setHgrow(box, javafx.scene.layout.Priority.ALWAYS);
                }
            }
            if (mode.contains("s2")) {
                for (String id : new String[]{"selfDonateBtn", "donateLabel", "donateAll", "donateMissing"}) {
                    Node dn = root.lookup("#" + id); if (dn != null) { dn.setVisible(true); dn.setManaged(true); }
                }
            }
            // Extras
            if ((n = root.lookup("#usersList")) instanceof ListView) ((ListView) n).getItems().setAll("Emmanuel1860", "Carolina_X", "SrDon", "Pepe", "Luna", "Zorro_Rojo_2020", "ana", "mike", "juan");
            if ((n = root.lookup("#myLangCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Espa\u00f1ol", "Ingl\u00e9s"); ((ComboBox) n).getSelectionModel().select(0); }
            if ((n = root.lookup("#roomLangCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Espa\u00f1ol", "Ingl\u00e9s"); ((ComboBox) n).getSelectionModel().select(1); }
            if ((n = root.lookup("#colorCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Sin color", "Rojo"); ((ComboBox) n).getSelectionModel().select(0); }
            if ((n = root.lookup("#ruleDirCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Lo que env\u00edo a Habbo"); ((ComboBox) n).getSelectionModel().select(0); }
            if ((n = root.lookup("#ruleTypeCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Bloquear el paquete"); ((ComboBox) n).getSelectionModel().select(0); }
            if ((n = root.lookup("#temaCombo")) instanceof ComboBox) { ((ComboBox) n).getItems().setAll("Pirata", "Drag queen"); ((ComboBox) n).getSelectionModel().select(0); }
            if ((n = root.lookup("#rulesList")) instanceof ListView) ((ListView) n).getItems().setAll("Lo que envio a Habbo  \u00b7  Chat  \u00b7  \"hola\" -> \"hello\"", "Bloquear Shout");
            setText(root, "#transLbl", "Apagado");
        } catch (Throwable t) { t.printStackTrace(); }
    }
    static void setText(Parent root, String sel, String t) { Node n = root.lookup(sel); if (n instanceof Labeled) ((Labeled) n).setText(t); }
    static void cls(Parent root, String sel, String c) {
        Node n = root.lookup(sel);
        if (n != null) { n.getStyleClass().removeAll("lblgreen", "lblred", "lblorange", "lblgrey"); n.getStyleClass().add(c); }
    }


    static void snap(Scene scene, File f) {
        try {
            WritableImage img = scene.snapshot(null);
            BufferedImage bi = SwingFXUtils.fromFXImage(img, null);
            BufferedImage rgb = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(new Color(0x7a7a7a)); g.fillRect(0, 0, bi.getWidth(), bi.getHeight()); g.drawImage(bi, 0, 0, null); g.dispose();
            ImageIO.write(rgb, "png", f);
            System.out.println("OK " + f + " " + bi.getWidth() + "x" + bi.getHeight());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    static void strip(Node n) {
        if (n instanceof TabPane) {
            TabPane tp = (TabPane) n;
            tp.getTabs().removeIf(t -> {
                String s = t.getText() == null ? "" : t.getText().trim().toLowerCase();
                return s.equals("pixel art") || s.equals("sala llena");
            });
        }
        if (n instanceof Parent) for (Node c : new ArrayList<>(((Parent) n).getChildrenUnmodifiable())) strip(c);
    }

    static void report(Parent root) {
        System.out.println("ROOT size " + root.getLayoutBounds().getWidth() + "x" + root.getLayoutBounds().getHeight()
                + " minH=" + root.minHeight(-1) + " minW=" + root.minWidth(-1) + " prefH=" + root.prefHeight(-1) + " prefW=" + root.prefWidth(-1));
        TabPane tp = (TabPane) root.lookup(".tab-pane");
        if (tp != null) {
            for (Tab t : tp.getTabs()) {
                Node c = t.getContent();
                if (c != null) System.out.println("TAB '" + t.getText() + "' content min=" + c.minWidth(-1) + "x" + c.minHeight(-1) + " pref=" + c.prefWidth(-1) + "x" + c.prefHeight(-1));
            }
        }
        // etiquetas recortadas
        for (Node n : root.lookupAll(".labeled")) { /* none */ }
        List<Node> all = new ArrayList<>();
        collect(root, all);
        for (Node n : all) {
            if (n instanceof Labeled && ((Labeled) n).getText() != null && !((Labeled) n).getText().isEmpty() && n.isVisible()) {
                Labeled l = (Labeled) n;
                Node tx = null;
                for (Node ch : l.getChildrenUnmodifiable()) if (ch instanceof javafx.scene.text.Text) tx = ch;
                if (tx instanceof javafx.scene.text.Text) {
                    String shown = ((javafx.scene.text.Text) tx).getText();
                    if (shown != null && !shown.equals(l.getText().replace("\n", "")) && !l.getText().contains("\n")) {
                        System.out.println("TRUNC '" + l.getText() + "' -> '" + shown + "' " + l.getClass().getSimpleName() + " w=" + l.getWidth());
                    }
                }
            }
        }
        // fuera de la escena
        double sw = root.getScene().getWidth(), sh = root.getScene().getHeight();
        for (Node n : all) {
            if (!n.isVisible() || !n.isManaged()) continue;
            javafx.geometry.Bounds b = n.localToScene(n.getBoundsInLocal());
            if (b != null && (b.getMaxX() > sw + 0.5 || b.getMaxY() > sh + 0.5) && (n instanceof Control)) {
                System.out.println("OFF " + n.getClass().getSimpleName() + " " + (n instanceof Labeled ? ((Labeled) n).getText() : "") + " maxX=" + b.getMaxX() + " maxY=" + b.getMaxY() + " scene=" + sw + "x" + sh);
            }
        }
    }

    static void collect(Node n, List<Node> out) {
        out.add(n);
        if (n instanceof TabPane) {
            for (Tab t : ((TabPane) n).getTabs()) if (t.getContent() != null) collect(t.getContent(), out);
        }
        if (n instanceof Parent) for (Node c : ((Parent) n).getChildrenUnmodifiable()) collect(c, out);
    }

    public static void main(String[] args) { launch(args); }
}
