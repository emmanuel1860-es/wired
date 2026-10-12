package extension.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ritmo de escritura humano para lo que TU escribes: despues de traducir, el mensaje se retiene un poco
 * (segun su largo, descontando lo que ya tardo la traduccion) para que no salga al instante.
 * Archivo editable Memoria/escritura.txt (se crea solo y se relee al cambiar). Sin ventanas ni comandos.
 */
final class Escritura {
    private static final String POR_DEFECTO = "# Ritmo de escritura de lo que TU escribes con el traductor activado.\r\n"
        + "# Despues de traducir, el mensaje espera un poco (como si lo estuvieras tecleando) antes de salir.\r\n"
        + "# activo = si/no                   apaga o enciende la espera\r\n"
        + "# caracteres_por_segundo = 15      velocidad de tecleo (3 a 40). Menos = mas lento\r\n"
        + "# pausa_ms = 350                   pausa inicial antes de empezar a teclear (0 a 3000)\r\n"
        + "# maximo_ms = 3500                 espera maxima por mensaje (500 a 15000)\r\n"
        + "# variacion = 25                   cuanto varia la espera al azar, en % (0 a 60)\r\n"
        + "activo = si\r\n"
        + "caracteres_por_segundo = 15\r\n"
        + "pausa_ms = 350\r\n"
        + "maximo_ms = 3500\r\n"
        + "variacion = 25\r\n";

    private static File archivoPrueba;
    private static long leidoEn = -1L;
    private static boolean activo = true;
    private static double cps = 15.0;
    private static long pausaMs = 350L;
    private static long maximoMs = 3500L;
    private static double variacion = 0.25;

    private Escritura() {
    }

    static File archivo() {
        if (archivoPrueba != null) {
            return archivoPrueba;
        }
        File file = Diagnostics.directory().getParentFile();
        File file2 = file == null ? new File("Memoria") : new File(file, "Memoria");
        file2.mkdirs();
        return new File(file2, "escritura.txt");
    }

    private static boolean si(String s) {
        String t = s.trim().toLowerCase();
        return t.startsWith("s") || t.equals("1") || t.equals("on") || t.startsWith("y") || t.equals("true");
    }

    private static double numero(String s, double min, double max, double def) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    private static synchronized void cargar() {
        try {
            File f = archivo();
            if (!f.isFile()) {
                Files.write(f.toPath(), POR_DEFECTO.getBytes(StandardCharsets.UTF_8));
            }
            long m = f.lastModified();
            if (m == leidoEn) {
                return;
            }
            boolean a = true;
            double c = 15.0;
            long p = 350L;
            long mx = 3500L;
            double v = 0.25;
            for (String linea : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\\r?\\n")) {
                String l = linea.trim();
                int e = l.indexOf('=');
                if (l.isEmpty() || l.startsWith("#") || e <= 0) continue;
                String k = l.substring(0, e).trim().toLowerCase();
                String val = l.substring(e + 1);
                if (k.equals("activo")) {
                    a = si(val);
                } else if (k.equals("caracteres_por_segundo")) {
                    c = numero(val, 3.0, 40.0, 15.0);
                } else if (k.equals("pausa_ms")) {
                    p = (long) numero(val, 0.0, 3000.0, 350.0);
                } else if (k.equals("maximo_ms")) {
                    mx = (long) numero(val, 500.0, 15000.0, 3500.0);
                } else if (k.equals("variacion")) {
                    v = numero(val, 0.0, 60.0, 25.0) / 100.0;
                }
            }
            activo = a;
            cps = c;
            pausaMs = p;
            maximoMs = mx;
            variacion = v;
            leidoEn = m;
        } catch (Throwable t) {
            // si el archivo no se puede leer se usan los valores de antes (o los de fabrica)
        }
    }

    /** Espera total (ms) que tardaria en teclearse el texto; 0 si es vacio. Pura, para poder probarla. */
    static long calcular(String texto, Random azar) {
        if (texto == null) {
            return 0L;
        }
        String t = texto.trim();
        if (t.isEmpty()) {
            return 0L;
        }
        double base = pausaMs + t.codePointCount(0, t.length()) * 1000.0 / cps;
        double salto = variacion <= 0.0 ? 0.0 : (azar.nextDouble() * 2.0 - 1.0) * variacion;
        long ms = Math.round(base * (1.0 + salto));
        return Math.max(0L, Math.min(ms, maximoMs));
    }

    /** Retiene el hilo lo que falte para completar el tiempo de tecleo (yaPasadoMs = lo que ya tardo la traduccion). */
    static void esperar(String texto, long yaPasadoMs) {
        try {
            cargar();
            if (!activo) {
                return;
            }
            long falta = calcular(texto, ThreadLocalRandom.current()) - Math.max(0L, yaPasadoMs);
            if (falta > 0L) {
                Thread.sleep(falta);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            // la espera es opcional: ante cualquier error el mensaje sale sin esperar
        }
    }

    static void probar(File archivo) {
        archivoPrueba = archivo;
        leidoEn = -1L;
    }

    static boolean activoAhora() {
        cargar();
        return activo;
    }
}
