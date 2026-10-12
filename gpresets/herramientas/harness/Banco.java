package extension.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * Banco de pruebas del traductor de G-Presets (fuera de G-Earth).
 * Lee de stdin lineas "comando<TAB>arg1<TAB>arg2..." y escribe una linea JSON por cada una.
 *   det   texto          -> idioma detectado (estricto, como al recibir) y no estricto, con puntuaciones
 *   norm  texto          -> texto tras expandir abreviaturas del idioma detectado
 *   b2    texto          -> NivelB2.aplicar(texto)  (estilo del ingles que escribo)
 *   tr    idioma  texto  -> Traductor.traducir(texto, "auto", idioma)  (LLAMA A GOOGLE: sin abusar)
 *   trs   origen idioma texto -> igual pero con idioma de origen
 *   merece texto         -> Traductor.merece(texto)
 * Variables: BANCO_B2 = ruta del ingles_b2.txt de prueba (por defecto, uno vacio => valores por defecto).
 */
public class Banco {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        String b2 = System.getenv("BANCO_B2");
        File f = new File(b2 != null ? b2 : System.getProperty("java.io.tmpdir") + "/banco_ingles_b2_" + System.nanoTime() + ".txt");
        try {
            Field fld = NivelB2.class.getDeclaredField("archivoPrueba");
            fld.setAccessible(true);
            fld.set(null, f);
        } catch (Throwable t) { /* version sin archivoPrueba */ }
        Slang slang = Slang.forTest();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t");
            JSONObject o = new JSONObject();
            o.put("cmd", p[0]);
            try {
                switch (p[0]) {
                    case "det":
                        o.put("texto", p[1]);
                        o.put("estricto", String.valueOf(slang.detect(p[1], true)));
                        o.put("flojo", String.valueOf(slang.detect(p[1], false)));
                        break;
                    case "norm":
                        o.put("texto", p[1]);
                        o.put("salida", slang.normalize(p[1]));
                        break;
                    case "b2":
                        o.put("texto", p[1]);
                        o.put("salida", NivelB2.aplicar(p[1]));
                        break;
                    case "merece":
                        o.put("texto", p[1]);
                        o.put("merece", Traductor.merece(p[1]));
                        break;
                    case "tr":
                        o.put("a", p[1]);
                        o.put("texto", p[2]);
                        o.put("salida", Traductor.traducir(p[2], "auto", p[1], 6000));
                        o.put("servicio", Traductor.ultimoServicio());
                        break;
                    case "trs":
                        o.put("de", p[1]);
                        o.put("a", p[2]);
                        o.put("texto", p[3]);
                        o.put("salida", Traductor.traducir(p[3], p[1], p[2], 6000));
                        o.put("servicio", Traductor.ultimoServicio());
                        break;
                    default:
                        o.put("error", "comando desconocido");
                }
            } catch (Throwable t) {
                o.put("error", t.toString());
            }
            out.println(o.toString());
        }
    }
}
