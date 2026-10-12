package extension.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Port a Java de "wiredtool validar". Lo usa GPresets.revisarPreset al elegir un preset: no tiene comandos
 * ni ventanas; solo devuelve la lista de problemas y GPresets la escribe en el registro si hay algo.
 */
public class PresetValidator {
    private static final String[][] SECCIONES = {
        {"triggers", "wf_trg_"}, {"selectors", "wf_slc_"}, {"conditions", "wf_cnd_"},
        {"effects", "wf_act_"}, {"addons", "wf_xtra_"}, {"variables", "wf_var_"}};
    private static final String[] SIN_ENTRADA = {"wf_antenna", "wf_contract_", "wf_storage_", "wf_wire",
        "wf_floor_switch", "wf_button", "wf_firegate", "wf_glowball", "wf_colortile", "wf_teamcolortile",
        "wf_upcounter", "wf_numberscreen", "wf_teammeter", "wf_arrowplate", "wf_room_linker", "wf_blob", "wf_conf_"};

    // ------------------------------------------------------------------------------------------
    public static List<String[]> validar(JSONObject d) {
        List<String[]> res = new ArrayList<String[]>();
        JSONArray furniArr = d.optJSONArray("furni") == null ? new JSONArray() : d.getJSONArray("furni");
        JSONObject wired = d.optJSONObject("wired") == null ? new JSONObject() : d.getJSONObject("wired");
        Map<Integer, JSONObject> fid = new HashMap<Integer, JSONObject>();
        Map<Integer, Integer> contador = new HashMap<Integer, Integer>();
        for (int i = 0; i < furniArr.length(); i++) {
            JSONObject f = furniArr.getJSONObject(i);
            int id = f.getInt("id");
            fid.put(id, f);
            Integer n = contador.get(id);
            contador.put(id, n == null ? 1 : n + 1);
        }
        for (Map.Entry<Integer, Integer> e : contador.entrySet()) {
            if (e.getValue() > 1) res.add(new String[]{"error", "id de furni repetido: " + e.getKey()});
        }
        Map<Integer, String> seccionDe = new HashMap<Integer, String>();
        Map<Integer, JSONObject> wiredDe = new HashMap<Integer, JSONObject>();
        Set<String> nombresVar = new HashSet<String>();
        Set<String> idsDefinidos = new HashSet<String>();
        Map<String, Integer> vecesVar = new HashMap<String, Integer>();
        for (String[] sec : SECCIONES) {
            JSONArray arr = wired.optJSONArray(sec[0]);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                int wid = x.optInt("wiredId", -1);
                JSONObject f = fid.get(wid);
                if (f == null) {
                    res.add(new String[]{"error", sec[0] + ": wiredId " + wid + " no apunta a ningun furni"});
                    continue;
                }
                String cls = f.getString("className");
                seccionDe.put(wid, sec[0]);
                wiredDe.put(wid, x);
                if (!cls.startsWith(sec[1])) {
                    res.add(new String[]{"error", sec[0] + ": wiredId " + wid + " es " + cls + ", se esperaba " + sec[1] + "*"});
                }
                if (sec[0].equals("conditions") && !x.has("quantifier")) res.add(new String[]{"aviso", "condicion " + wid + " (" + cls + ") sin quantifier"});
                if (sec[0].equals("effects") && !x.has("delay")) res.add(new String[]{"aviso", "efecto " + wid + " (" + cls + ") sin delay"});
                for (String k : new String[]{"items", "secondItems"}) {
                    JSONArray its = x.optJSONArray(k);
                    if (its == null) continue;
                    for (int j = 0; j < its.length(); j++) {
                        if (!fid.containsKey(its.getInt(j))) res.add(new String[]{"error", cls + " " + wid + ": item " + its.getInt(j) + " no existe en furni"});
                    }
                }
                if (sec[0].equals("variables")) {
                    String nombre = x.optString("config", "");
                    Integer n = vecesVar.get(nombre);
                    vecesVar.put(nombre, n == null ? 1 : n + 1);
                    String vid = x.optString("variableId", "");
                    if (!vid.isEmpty()) idsDefinidos.add(vid);
                }
            }
        }
        for (Map.Entry<String, Integer> e : vecesVar.entrySet()) {
            if (e.getValue() > 1) res.add(new String[]{"error", "variable \"" + e.getKey() + "\" definida " + e.getValue() + " veces en el preset"});
        }
        for (int i = 0; i < furniArr.length(); i++) {
            JSONObject f = furniArr.getJSONObject(i);
            String cls = f.getString("className");
            if (cls.startsWith("wf_") && !seccionDe.containsKey(f.getInt("id")) && !empiezaPorAlguno(cls, SIN_ENTRADA)) {
                res.add(new String[]{"aviso", cls + " (id " + f.getInt("id") + ") esta en furni pero no en wired: hay que configurarlo a mano"});
            }
        }
        // variables usadas sin definir
        Set<String> conocidos = new HashSet<String>(idsDefinidos);
        JSONObject vmap = wired.optJSONObject("variables_map");
        if (vmap != null) for (String k : vmap.keySet()) conocidos.add(String.valueOf(vmap.get(k)));
        Map<String, Integer> sinDef = new TreeMap<String, Integer>();
        for (String[] sec : SECCIONES) {
            if (sec[0].equals("variables")) continue;
            JSONArray arr = wired.optJSONArray(sec[0]);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONArray vs = arr.getJSONObject(i).optJSONArray("variableIds");
                if (vs == null) continue;
                for (int j = 0; j < vs.length(); j++) {
                    String v = vs.getString(j);
                    if (v.isEmpty() || v.equals("0") || v.equals("n") || v.matches("-\\d+") || v.startsWith("~")) continue;
                    if (v.matches("s\\d+-.*")) continue;
                    if (!conocidos.contains(v)) {
                        Integer n = sinDef.get(v);
                        sinDef.put(v, n == null ? 1 : n + 1);
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> e : sinDef.entrySet()) {
            res.add(new String[]{"aviso", "variable " + e.getKey() + " usada en " + e.getValue() + " wired pero no definida en este preset (ya existe en la sala destino?)"});
        }
        // antenas
        Map<Integer, Integer> envia = new HashMap<Integer, Integer>(), recibe = new HashMap<Integer, Integer>();
        for (String sec : new String[]{"effects", "triggers"}) {
            JSONArray arr = wired.optJSONArray(sec);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                JSONObject f = fid.get(x.optInt("wiredId", -1));
                if (f == null) continue;
                String cls = f.getString("className");
                JSONArray its = x.optJSONArray("items");
                if (its == null) continue;
                boolean env = cls.equals("wf_act_send_signal") || cls.equals("wf_act_neg_send_signal");
                boolean rec = cls.equals("wf_trg_recv_signal");
                for (int j = 0; j < its.length() && (env || rec); j++) {
                    (env ? envia : recibe).put(its.getInt(j), 1);
                }
            }
        }
        Set<Integer> antenas = new HashSet<Integer>(envia.keySet());
        antenas.addAll(recibe.keySet());
        for (Integer a : antenas) {
            if (recibe.containsKey(a) && !envia.containsKey(a)) res.add(new String[]{"aviso", "antena " + a + ": tiene receptor pero ningun emisor en este preset (lo envia tu juego?)"});
            if (envia.containsKey(a) && !recibe.containsKey(a)) res.add(new String[]{"aviso", "antena " + a + ": se envia senal pero nadie la recibe"});
        }
        // pilas por baldosa
        Map<String, List<double[]>> pilas = new LinkedHashMap<String, List<double[]>>();
        for (int i = 0; i < furniArr.length(); i++) {
            JSONObject f = furniArr.getJSONObject(i);
            int id = f.getInt("id");
            if (!seccionDe.containsKey(id)) continue;
            JSONObject l = f.getJSONObject("location");
            String pos = l.getInt("x") + "," + l.getInt("y");
            List<double[]> lst = pilas.get(pos);
            if (lst == null) { lst = new ArrayList<double[]>(); pilas.put(pos, lst); }
            lst.add(new double[]{l.getDouble("z"), id});
        }
        for (Map.Entry<String, List<double[]>> e : pilas.entrySet()) {
            List<double[]> lst = e.getValue();
            lst.sort((a, b) -> Double.compare(a[0], b[0]));
            boolean soloVars = true;
            for (double[] z : lst) {
                String s = seccionDe.get((int) z[1]);
                String c = fid.get((int) z[1]).getString("className");
                if (!(s.equals("variables") || (s.equals("addons") && c.equals("wf_xtra_var_time_util")))) soloVars = false;
            }
            if (soloVars) continue;
            Map<Long, Integer> alturas = new HashMap<Long, Integer>();
            for (double[] z : lst) {
                long k = Math.round(z[0] * 1000);
                Integer n = alturas.get(k);
                alturas.put(k, n == null ? 1 : n + 1);
            }
            for (Map.Entry<Long, Integer> a : alturas.entrySet()) {
                if (a.getValue() > 1) res.add(new String[]{"error", "pila (" + e.getKey() + "): dos wireds a la misma altura z=" + (a.getKey() / 1000.0)});
            }
            boolean hayTrigger = false;
            for (double[] z : lst) if (seccionDe.get((int) z[1]).equals("triggers")) hayTrigger = true;
            if (!hayTrigger) res.add(new String[]{"aviso", "pila (" + e.getKey() + "): no tiene trigger"});
            else if (!seccionDe.get((int) lst.get(0)[1]).equals("triggers")) res.add(new String[]{"aviso", "pila (" + e.getKey() + "): el trigger no esta abajo del todo"});
        }
        // textos con acento sin doble codificacion
        for (String sec : new String[]{"effects", "addons", "triggers"}) {
            JSONArray arr = wired.optJSONArray(sec);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                String t = x.optString("config", "");
                boolean noAscii = false;
                for (int k = 0; k < t.length(); k++) if (t.charAt(k) > 127) { noAscii = true; break; }
                if (noAscii && !t.contains("Ã") && !t.contains("Â")) {
                    res.add(new String[]{"aviso", "texto con acento sin doble codificacion (wiredId " + x.optInt("wiredId") + ")"});
                }
            }
        }
        // sin confirmar
        Map<String, Integer> sc = new LinkedHashMap<String, Integer>();
        for (String[] sec : SECCIONES) {
            JSONArray arr = wired.optJSONArray(sec[0]);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                JSONObject f = fid.get(x.optInt("wiredId", -1));
                if (f == null) continue;
                String cls = f.getString("className");
                JSONArray op = x.optJSONArray("options");
                JSONArray vs = x.optJSONArray("variableIds");
                if (vs != null) for (int j = 0; j < vs.length(); j++) {
                    String v = vs.getString(j);
                    if (v.equals("-401")) cuenta(sc, "variable -401 como cantidad de usuarios seleccionados");
                    if (v.equals("~110") || v.equals("~111") || v.equals("~112") || v.equals("~113")) cuenta(sc, "subvariables de ~area_hide (~110 a ~113): orden asumido");
                }
                if (cls.equals("wf_cnd_actor_in_group")) cuenta(sc, "wf_cnd_actor_in_group (formato de opciones)");
                if (cls.equals("wf_trg_clock_counter") && op != null && op.length() == 3 && op.getInt(0) == 30 && op.getInt(1) == 1 && op.getInt(2) == 1) cuenta(sc, "reloj con opciones [30,1,1]");
                if (cls.equals("wf_trg_click_user")) cuenta(sc, "wf_trg_click_user: significado de las opciones");
                if (cls.equals("wf_act_change_var_val") && op != null && op.length() > 1 && op.getInt(1) == 3) cuenta(sc, "operacion 3 de change_var_val (multiplicacion)");
                if (cls.equals("wf_cnd_var_age_match") && op != null && op.length() > 5 && (op.getInt(5) >= 2 && op.getInt(5) <= 4)) cuenta(sc, "unidad de edad 2/3/4 en wf_cnd_var_age_match (solo 1=segundos y 5=semanas vistas)");
            }
        }
        for (Map.Entry<String, Integer> e : sc.entrySet()) {
            res.add(new String[]{"info", "sin confirmar: " + e.getKey() + " (en " + e.getValue() + " wired)"});
        }
        return res;
    }

    private static void cuenta(Map<String, Integer> m, String k) {
        Integer n = m.get(k);
        m.put(k, n == null ? 1 : n + 1);
    }

    private static boolean empiezaPorAlguno(String s, String[] prefijos) {
        for (String p : prefijos) if (s.startsWith(p)) return true;
        return false;
    }
}
