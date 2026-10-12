package extension.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aleman que TU escribes y el traductor pasa a aleman (equivalente de NivelB2 para el ingles).
 * Google traduce con tono de libro de texto (y a veces con Sie/Ihnen/Ihr aunque hables de tu); aqui se pasa a aleman de chat:
 *   - tutear: Sie/Ihnen/Ihr -> du/dir/dein, SOLO en los casos sin duda (ver entutear);
 *   - tabla "forma de libro = forma de chat" de Memoria/aleman_chat.txt (se crea con POR_DEFECTO);
 *   - estilo_chat: sin punto final y primera letra en minuscula si la frase empieza por una palabra de funcion
 *     (los sustantivos y nombres propios conservan su mayuscula);
 *   - repara precios que Google rompe: "30 \u00b0C" -> "30c", "50 Cent" -> "50c", "3,5.000" -> "3.5k".
 * Todo es determinista (sin azar) e idempotente: aplicar(aplicar(x)) == aplicar(x). Nunca lanza: ante un error devuelve el texto tal cual.
 */
final class EstiloDe {
    private static final String POR_DEFECTO = "# Aleman de chat para lo que TU escribes y el traductor pasa a aleman. Guardalo en UTF-8.\n" +
        "# activo = si/no        enciende o apaga todo\n" +
        "# estilo_chat = si/no   sin punto final y primera letra en minuscula (solo si empieza por una palabra de funcion: ich, du, wie...)\n" +
        "# tutear = si/no        Google a veces traduce tu \"tu\" como Sie/Ihnen/Ihr; esto lo pasa a du/dir/dein (solo cuando no hay duda)\n" +
        "# Debajo: forma de libro = forma de chat   (una por linea, puedes agregar las tuyas)\n" +
        "# No importan mayusculas/minusculas; gana la frase mas larga. Se respeta tal cual lo que escribas a la derecha\n" +
        "# (los sustantivos con mayuscula: Zimmer) y al empezar frase se pone la primera letra en mayuscula sola.\n" +
        "# Si la linea empieza por ^ solo vale al EMPEZAR frase (^ich bin = bin: \"Ich bin hier\" -> \"Bin hier\", pero \"wer ich bin\" no se toca).\n" +
        "# Aparte, siempre: no se tocan URLs, @nombres ni :comandos; \"30 \u00b0C\" y \"50 Cent\" (Google lee 30c / 50c asi) vuelven a \"30c\" / \"50c\".\n" +
        "# version = 1\n" +
        "activo = si\n" +
        "estilo_chat = si\n" +
        "tutear = si\n" +
        "\n" +
        "# --- saludos, gracias, perdon ---\n" +
        "^guten tag = hallo\n" +
        "auf wiedersehen = tsch\u00fcss\n" +
        "vielen herzlichen dank = danke\n" +
        "vielen dank = danke\n" +
        "herzlichen dank = danke\n" +
        "besten dank = danke\n" +
        "danke sch\u00f6n = danke\n" +
        "dankesch\u00f6n = danke\n" +
        "danke sehr = danke\n" +
        "ich danke dir = danke dir\n" +
        "ich danke euch = danke euch\n" +
        "herzlichen gl\u00fcckwunsch = Gl\u00fcckwunsch\n" +
        "es tut mir so leid = tut mir so leid\n" +
        "es tut mir sehr leid = tut mir echt leid\n" +
        "es tut mir leid = tut mir leid\n" +
        "entschuldigung = sorry\n" +
        "verzeihung = sorry\n" +
        "gern geschehen = gerne\n" +
        "bitte sch\u00f6n = bitte\n" +
        "bittesch\u00f6n = bitte\n" +
        "\n" +
        "# --- contracciones de chat ---\n" +
        "gibt es = gibts\n" +
        "gibt's = gibts\n" +
        "geht's = gehts\n" +
        "geht es = gehts\n" +
        "wie w\u00e4re es = wie w\u00e4rs\n" +
        "war es = wars\n" +
        "wird es = wirds\n" +
        "hat es = hats\n" +
        "macht es = machts\n" +
        "kann es = kanns\n" +
        "ich habe = hab\n" +
        "ich werde = ich werd\n" +
        "ich w\u00fcrde gerne = ich w\u00fcrd gern\n" +
        "ich w\u00fcrde = ich w\u00fcrd\n" +
        "ich m\u00f6chte gerne = ich will\n" +
        "ich m\u00f6chte = ich will\n" +
        "m\u00f6chtest du = willst du\n" +
        "m\u00f6chtet ihr = wollt ihr\n" +
        "^ich bin = bin\n" +
        "^ich wei\u00df nicht = wei\u00df nicht\n" +
        "\n" +
        "# --- vocabulario de libro -> de cada dia ---\n" +
        "sehr gut = super\n" +
        "ausgezeichnet = super\n" +
        "hervorragend = super\n" +
        "wirklich = echt\n" +
        "tats\u00e4chlich = echt\n" +
        "eventuell = vielleicht\n" +
        "m\u00f6glicherweise = vielleicht\n" +
        "selbstverst\u00e4ndlich = klar\n" +
        "^nat\u00fcrlich = klar\n" +
        "ja nat\u00fcrlich = ja klar\n" +
        "ja, nat\u00fcrlich = ja klar\n" +
        "in ordnung = okay\n" +
        "ein wenig = ein bisschen\n" +
        "\n" +
        "# --- Habbo: \"sala\" es el Raum/Zimmer, no el salon de una casa ---\n" +
        "wohnzimmer = Zimmer\n";

    private static File archivoPrueba;

    private static final class Cfg {
        long leidoEn;
        boolean activo = true;
        boolean estiloChat = true;
        boolean tutear = true;
        Map<String, String> vocabulario = new LinkedHashMap<String, String>();
        Set<String> soloInicio = new HashSet<String>();
        Pattern patron;
    }

    private static volatile Cfg cfg;

    // ------------------------------------------------------------------ datos

    /** Palabras que empiezan frase y no son sustantivos ni nombres: solo estas pasan a minuscula al empezar. */
    private static final Set<String> INICIOS_MINUSCULA = set(
        "ich", "du", "wir", "er", "es", "man", "mir", "mich", "dir", "dich", "uns", "euch", "ihm", "ihn", "ja", "nein", "doch", "jo", "jap", "n\u00f6", "nee", "hey", "hi", "hallo", "huhu", "moin", "servus", "tsch\u00fcss", "tsch\u00fcs", "bye",
        "danke", "bitte", "sorry", "ok", "okay", "klar", "genau", "gerne", "gern", "super", "mega", "cool", "krass", "echt", "wow", "oh", "ach", "ah", "hmm", "haha", "lol", "juhu", "yay", "na", "naja", "tja",
        "wie", "was", "wo", "wer", "wen", "wem", "wessen", "wann", "warum", "wieso", "weshalb", "wozu", "wohin", "woher", "welche", "welcher", "welches", "welchen", "wieviel", "wieviele", "womit", "wof\u00fcr",
        "bin", "bist", "ist", "sind", "seid", "war", "waren", "warst", "hab", "habe", "hast", "hat", "haben", "habt", "hatte", "hatten", "kann", "kannst", "k\u00f6nnen", "k\u00f6nnt", "konnte", "muss", "musst", "m\u00fcssen", "m\u00fcsst",
        "will", "willst", "wollen", "wollt", "soll", "sollst", "sollen", "sollt", "darf", "darfst", "d\u00fcrfen", "mag", "magst", "m\u00f6gen", "m\u00f6chte", "m\u00f6chtest", "m\u00f6chten", "wird", "wirst", "werden", "werdet",
        "w\u00fcrde", "w\u00fcrdest", "w\u00fcrden", "h\u00e4tte", "h\u00e4ttest", "w\u00e4re", "w\u00e4rst", "w\u00e4ren", "gibt", "gibts", "geht", "gehts", "kommt", "kommst", "tut", "wars", "wirds", "hats", "machts", "kanns",
        "komm", "geh", "gehst", "gib", "mach", "machst", "macht", "schau", "sag", "sagst", "sieh", "siehst", "h\u00f6r", "hilf", "lass", "bleib", "warte", "wart", "denk", "glaub", "bleibt", "kommt", "seht", "schaut",
        "der", "die", "das", "ein", "eine", "einen", "einem", "einer", "eines", "dem", "den", "des", "dieser", "diese", "dieses", "diesen", "jeder", "jede", "jedes", "kein", "keine", "keinen", "keinem", "keiner",
        "mein", "meine", "meinen", "meinem", "meiner", "dein", "deine", "deinen", "deinem", "deiner", "unser", "unsere", "unseren", "euer", "eure",
        "und", "oder", "aber", "denn", "sondern", "weil", "wenn", "dass", "ob", "als", "da", "damit", "obwohl", "falls", "bevor", "nachdem", "w\u00e4hrend", "sobald",
        "in", "im", "auf", "aus", "an", "am", "zu", "zum", "zur", "mit", "von", "vom", "f\u00fcr", "bei", "beim", "nach", "vor", "\u00fcber", "unter", "um", "durch", "gegen", "ohne", "bis", "seit",
        "hier", "dort", "dann", "jetzt", "heute", "morgen", "gestern", "bald", "gleich", "schon", "noch", "nie", "nur", "auch", "also", "nun", "mal", "eben", "halt", "leider", "hoffentlich",
        "vielleicht", "bestimmt", "sicher", "wirklich", "sehr", "so", "ganz", "fast", "immer", "manchmal", "oft", "selten", "nicht", "nichts", "nix", "alles", "alle", "jemand", "niemand", "etwas", "viel", "viele", "wenig",
        "gut", "gute", "guten", "gutes", "guter", "sch\u00f6n", "sch\u00f6ne", "sch\u00f6nen", "sch\u00f6nes", "tolle", "tollen", "tolles", "toll", "frohe", "frohen", "liebe", "lieben", "lieber", "entschuldige", "schade", "zum", "meine", "ganz");

    private static final Set<String> ARTICULOS = set("ein", "eine", "einen", "einem", "einer", "der", "die", "das", "den", "dem", "mein", "meine", "dein", "deine", "kein", "keine");
    private static final Set<String> MULETILLAS = set("ja", "nein", "ok", "okay", "also", "oh", "ach", "hey", "hallo", "hi", "nun", "na", "und", "aber", "doch", "danke", "bitte", "sorry", "tja", "naja");

    /** Guardas por clave del vocabulario: si la palabra SIGUIENTE (o la ANTERIOR) casa con el patron, no se cambia. */
    private static final Map<String, Pattern> NO_ANTES_DE = new HashMap<String, Pattern>();
    private static final Map<String, Pattern> NO_TRAS = new HashMap<String, Pattern>();
    /** Si la ventana de texto alrededor (30 letras a cada lado) casa con el patron, no se cambia ("in Ordnung bringen"). */
    private static final Map<String, Pattern> NO_CONTEXTO = new HashMap<String, Pattern>();
    /** Si la palabra siguiente va con mayuscula (nombre o sustantivo) o es un articulo, no se cambia ("ich bin Pablo" no pierde el ich). */
    private static final Set<String> NO_SI_SIGUE_NOMBRE = set("ich bin");

    private static final Pattern PROTEGIDO = Pattern.compile(
        "(?i)(?:https?://|ftp://|www\\.)\\S+"
        + "|[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"
        + "|(?<![\\p{L}\\p{N}_])@\\w[\\w.-]*"
        + "|(?<![\\p{L}\\p{N}_]):\\p{L}[\\p{L}\\p{N}_]+"
        + "|(?<![\\p{L}\\p{N}_@/.-])[\\w-]+(?:\\.[\\w-]+)*\\.(?:com|net|org|es|de|co|io|me|tv|gg|uk|fr|it|nl|tr|fi|pt|ly|xyz|info|app|dev|ru)\\b(?:/\\S*)?"
        + "|(?<![\\p{L}\\p{N}_])#\\p{L}[\\p{L}\\p{N}_]*");
    private static final Pattern PUNTO_ABREVIATURA = Pattern.compile("(?i)(?<![\\p{L}])(?:usw|etc|bzw|ca|evtl|ggf|inkl|vgl|nr|dr|str|hr|fr|prof|z\\.\\s?b|d\\.\\s?h|u\\.\\s?a|u\\.\\s?\u00e4|o\\.\\s?\u00e4|s\\.\\s?o|s\\.\\s?u|i\\.\\s?d\\.\\s?r)\\.$");
    private static final Pattern RISA = Pattern.compile("(?i)(?<![\\p{L}])(ha)(?:[\\s-]+ha)+(?![\\p{L}])");
    private static final Pattern GRADOS = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s?\u00b0\\s?C(?![\\p{L}])");
    private static final Pattern CENT = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s?[Cc]ents?(?![\\p{L}])");
    private static final Pattern MILES_ROTOS = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d+),(\\d{1,2})\\.000(?![\\p{L}\\p{N}])");
    private static final Pattern CLIMA = Pattern.compile("(?i)(grad|celsius|temperatur|wetter|hei\u00df|heiss|kalt|k\u00e4lte|warm|k\u00fchl|sonne|regen|schnee|fieber|hitze)");
    private static final Pattern ZU_FUSIONADO = Pattern.compile("(?:ab|an|auf|aus|bei|ein|mit|nach|vor|weg|zur\u00fcck|fest|los|weiter|wieder|hin|her|teil|kennen|spazieren|kennen|rein|raus)zu\\p{L}{3,}n");
    private static final Pattern PALABRA = Pattern.compile("(?<![\\p{L}\\p{N}])\\p{L}+(?![\\p{N}])");

    // --- tutear

    private static final Map<String, String> POSESIVOS = new HashMap<String, String>();
    private static final Set<String> PREPOSICIONES = set("f\u00fcr", "ohne", "durch", "gegen", "um", "wider", "an", "auf", "\u00fcber", "in", "vor", "hinter", "neben", "unter", "zwischen");
    private static final Set<String> SUJETOS = set("ich", "wir", "er", "es", "man", "sie");
    /** Verbos que piden infinitivo detras: "ich kann Sie fragen", "wir werden Sie anrufen" -> Sie es el objeto del infinitivo. */
    private static final Set<String> AUX_INF = set("kann", "kannst", "k\u00f6nnen", "k\u00f6nnt", "will", "willst", "wollen", "muss", "musst", "m\u00fcssen", "soll", "sollst", "sollen", "darf", "darfst", "d\u00fcrfen", "mag", "magst",
        "m\u00f6gen", "m\u00f6chte", "m\u00f6chtest", "m\u00f6chten", "werde", "wirst", "wird", "werden", "w\u00fcrde", "w\u00fcrdest", "w\u00fcrden", "k\u00f6nnte", "k\u00f6nntest", "sollte", "solltest", "m\u00fcsste", "d\u00fcrfte", "wollte", "wolltest",
        "konnte", "konntest", "musste", "musstest", "lasse", "l\u00e4sst", "lassen", "helfe", "hilft", "h\u00f6re", "sehe");
    private static final Set<String> SUJETOS_2 = set("ich", "wir", "er", "man", "sie");
    private static final Set<String> SUBORDINANTES = set("wenn", "falls", "dass", "ob", "weil", "da", "damit", "sobald", "solange", "bevor", "nachdem", "w\u00e4hrend", "obwohl", "als", "wie", "wo", "wohin", "woher", "warum", "wann", "was", "wer", "sodass", "seit", "seitdem", "sooft", "wieso", "weshalb");
    private static final Set<String> COORDINANTES = set("und", "aber", "oder", "denn", "bitte", "doch", "mal");
    private static final Set<String> PREGUNTA = set("wie", "was", "wo", "wann", "warum", "wieso", "weshalb", "wer", "wen", "wem", "wohin", "woher", "welche", "welcher", "welches", "welchen", "wieviel", "wieviele", "lange", "viel", "viele", "oft", "schnell", "womit", "wof\u00fcr", "wozu", "weswegen");
    private static final Set<String> ADVERBIOS = set("dann", "jetzt", "vielleicht", "heute", "morgen", "gestern", "hier", "dort", "da", "gleich", "bald", "sp\u00e4ter", "sicher", "bestimmt", "wirklich", "leider", "hoffentlich", "wahrscheinlich", "eigentlich", "nat\u00fcrlich", "ja", "nein", "oh", "ach", "na", "also", "nun", "noch", "schon", "auch", "nur", "immer", "nie", "dabei", "deshalb", "daher", "trotzdem", "sonst");
    private static final String[] PREFIJOS = {"zusammen", "zur\u00fcck", "vorbei", "weiter", "wieder", "heraus", "herein", "hinein", "hinaus", "herunter", "runter", "dabei", "dazu", "hinzu", "statt", "teil", "fest", "los", "weg", "rein", "raus", "hoch", "rauf", "ab", "an", "auf", "aus", "bei", "ein", "her", "hin", "mit", "nach", "um", "vor", "zu"};
    /** infinitivo (= forma Sie):presente de du:imperativo de du ("-" = no existe en ese uso). */
    private static final String[] IRREGULARES = {
        "sind:bist:-", "seien:-:sei", "haben:hast:-", "werden:wirst:-",
        "k\u00f6nnen:kannst:-", "m\u00fcssen:musst:-", "wollen:willst:-", "sollen:sollst:-", "d\u00fcrfen:darfst:-", "m\u00f6gen:magst:-", "m\u00f6chten:m\u00f6chtest:-",
        "w\u00fcrden:w\u00fcrdest:-", "k\u00f6nnten:k\u00f6nntest:-", "h\u00e4tten:h\u00e4ttest:-", "w\u00e4ren:w\u00e4rst:-", "sollten:solltest:-", "m\u00fcssten:m\u00fcsstest:-", "d\u00fcrften:d\u00fcrftest:-",
        "wollten:wolltest:-", "konnten:konntest:-", "mussten:musstest:-", "hatten:hattest:-", "waren:warst:-", "wurden:wurdest:-",
        "geben:gibst:gib", "nehmen:nimmst:nimm", "sehen:siehst:sieh", "sprechen:sprichst:sprich", "helfen:hilfst:hilf", "treffen:triffst:triff", "lesen:liest:lies",
        "essen:isst:iss", "fahren:f\u00e4hrst:fahr", "laufen:l\u00e4ufst:lauf", "schlafen:schl\u00e4fst:schlaf", "halten:h\u00e4ltst:halt", "lassen:l\u00e4sst:lass",
        "wissen:wei\u00dft:-", "tragen:tr\u00e4gst:trag", "waschen:w\u00e4schst:wasch", "fallen:f\u00e4llst:fall", "fangen:f\u00e4ngst:fang", "raten:r\u00e4tst:rate",
        "brechen:brichst:brich", "vergessen:vergisst:vergiss", "messen:misst:miss", "werfen:wirfst:wirf", "treten:trittst:tritt", "empfehlen:empfiehlst:empfiehl",
        "stehlen:stiehlst:stiehl", "sterben:stirbst:stirb", "schlagen:schl\u00e4gst:schlag", "gelten:giltst:gilt", "tun:tust:tu", "verlassen:verl\u00e4sst:verlass",
        "erhalten:erh\u00e4ltst:erhalte", "behalten:beh\u00e4ltst:behalte", "laden:l\u00e4dst:lad",
        "versprechen:versprichst:versprich", "erfahren:erf\u00e4hrst:erfahre"};
    private static final String REGULARES =
        "spielen machen kaufen verkaufen tauschen suchen finden zeigen bringen denken glauben hoffen w\u00fcnschen brauchen besuchen warten kommen gehen bleiben stehen liegen wohnen leben arbeiten "
        + "lernen lieben fragen antworten sagen erkl\u00e4ren erz\u00e4hlen h\u00f6ren schauen gucken probieren versuchen benutzen nutzen \u00f6ffnen schlie\u00dfen starten beginnen beenden "
        + "aufh\u00f6ren folgen akzeptieren ablehnen bezahlen zahlen holen schicken senden schreiben rufen anrufen st\u00f6ren entschuldigen verzeihen erinnern verstehen kennen lachen weinen "
        + "gewinnen verlieren trinken f\u00fchlen setzen sitzen legen stellen aufpassen aufr\u00e4umen bauen verbinden entscheiden w\u00e4hlen bitten danken gratulieren \u00e4ndern l\u00f6schen melden "
        + "best\u00e4tigen \u00fcberpr\u00fcfen pr\u00fcfen kontrollieren bieten anbieten bekommen passen geh\u00f6ren hei\u00dfen reden interessieren z\u00e4hlen teilen tippen klicken dr\u00fccken ziehen "
        + "fliegen singen tanzen reisen mitkommen mitspielen mitmachen vorbeikommen zuschauen reinkommen zur\u00fcckkommen ankommen abholen mitbringen weitermachen aufstehen einsteigen "
        + "einladen anfangen lassen warten sparen sammeln wechseln verraten beantworten ben\u00f6tigen vermissen genie\u00dfen "
        + "verlangen nennen meinen merken schenken schaffen sch\u00e4tzen sch\u00fctzen spenden stimmen \u00fcberlegen unterst\u00fctzen verdienen vergleichen vertrauen verwenden wiederholen "
        + "zerst\u00f6ren erreichen erleben bestellen bearbeiten dekorieren richten f\u00fcgen l\u00e4cheln tr\u00e4umen leihen borgen mieten vermieten verpassen gestalten "
        + "einrichten anmelden abmelden hinzuf\u00fcgen ausleihen zuh\u00f6ren zustimmen zumachen aufmachen mitteilen";
    private static final Map<String, String[]> VERBOS = new HashMap<String, String[]>();

    private EstiloDe() {
    }

    static File archivo() {
        if (archivoPrueba != null) {
            return archivoPrueba;
        }
        File file = Diagnostics.directory().getParentFile();
        File file2 = file == null ? new File("Memoria") : new File(file, "Memoria");
        file2.mkdirs();
        return new File(file2, "aleman_chat.txt");
    }

    private static Set<String> set(String... palabras) {
        return new HashSet<String>(Arrays.asList(palabras));
    }

    private static String clave(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean si(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        return t.startsWith("s") || t.equals("1") || t.equals("on") || t.startsWith("y") || t.equals("true") || t.startsWith("j");
    }

    private static synchronized Cfg cargar() {
        Cfg actual = cfg;
        String texto = POR_DEFECTO;
        long modificado = 0L;
        try {
            File f = archivo();
            if (!f.isFile()) {
                Files.write(f.toPath(), POR_DEFECTO.getBytes(StandardCharsets.UTF_8));
            }
            modificado = f.lastModified();
            if (actual != null && actual.leidoEn == modificado && actual.patron != null) {
                return actual;
            }
            texto = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (texto.startsWith("\ufeff")) {
                texto = texto.substring(1);
            }
        } catch (Throwable t) {
            if (actual != null) {
                return actual;
            }
            texto = POR_DEFECTO;
        }
        Cfg nuevo = new Cfg();
        for (String linea : texto.split("\\r?\\n")) {
            linea = linea.trim();
            int n = linea.indexOf('=');
            if (linea.isEmpty() || linea.startsWith("#") || n <= 0) {
                continue;
            }
            String k = clave(linea.substring(0, n));
            String v = linea.substring(n + 1).trim();
            if (k.equals("activo")) {
                nuevo.activo = si(v);
                continue;
            }
            if (k.equals("estilo_chat")) {
                nuevo.estiloChat = si(v);
                continue;
            }
            if (k.equals("tutear")) {
                nuevo.tutear = si(v);
                continue;
            }
            boolean inicio = k.startsWith("^");
            if (inicio) {
                k = k.substring(1).trim();
            }
            if (k.isEmpty() || v.isEmpty() || k.equalsIgnoreCase(v)) {
                continue;
            }
            nuevo.vocabulario.put(k, v);
            if (inicio) {
                nuevo.soloInicio.add(k);
            } else {
                nuevo.soloInicio.remove(k);
            }
        }
        List<String> claves = new ArrayList<String>(nuevo.vocabulario.keySet());
        Collections.sort(claves, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        StringBuilder sb = new StringBuilder();
        for (String k : claves) {
            sb.append(sb.length() > 0 ? "|" : "").append(Pattern.quote(k).replace(" ", "\\E\\s+\\Q"));
        }
        nuevo.patron = sb.length() == 0 ? null : Pattern.compile("(?<![\\p{L}'])(" + sb + ")(?![\\p{L}'])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        nuevo.leidoEn = modificado;
        cfg = nuevo;
        return nuevo;
    }

    // ------------------------------------------------------------------ utilidades de texto

    private static boolean esMayusculas(String s) {
        int letras = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                letras++;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return letras >= 2;
    }

    private static boolean esLimite(char c) {
        return ".!?;:,\n\"\u201e\u201c\u201d()".indexOf(c) >= 0;
    }

    /** true si en pos empieza una frase de verdad (inicio, tras . ! ? : o salto de linea). */
    private static boolean inicioEstricto(String s, int pos) {
        int i = pos - 1;
        while (i >= 0 && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i--;
        }
        if (i < 0) {
            return true;
        }
        char c = s.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == ':' || c == '\n' || c == '\u2026';
    }

    /** igual, pero tambien tras una muletilla ("Hallo, ich bin..." -> "ich bin" abre frase). */
    private static boolean inicioDeFrase(String s, int pos) {
        if (inicioEstricto(s, pos)) {
            return true;
        }
        int i = pos - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        if (i >= 0 && s.charAt(i) == ',') {
            i--;
        }
        int fin = i + 1;
        while (i >= 0 && Character.isLetter(s.charAt(i))) {
            i--;
        }
        String w = s.substring(i + 1, fin).toLowerCase(Locale.ROOT);
        if (w.isEmpty() || !MULETILLAS.contains(w)) {
            return false;
        }
        return inicioEstricto(s, i + 1);
    }

    private static String palabraAnterior(String s, int pos) {
        int i = pos - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        int fin = i + 1;
        while (i >= 0 && Character.isLetter(s.charAt(i))) {
            i--;
        }
        return s.substring(i + 1, fin).toLowerCase(Locale.ROOT);
    }

    private static String palabraSiguiente(String s, int fin) {
        Matcher m = Pattern.compile("^\\s+(\\p{L}+)").matcher(s.substring(fin));
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
    }

    // ------------------------------------------------------------------ proteccion

    private static String proteger(String s, List<String> guardados) {
        Matcher m = PROTEGIDO.matcher(s);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            String g = m.group();
            int fin = g.length();
            while (fin > 0 && ".,;:!?)]}\"'".indexOf(g.charAt(fin - 1)) >= 0) {
                fin--;
            }
            if (fin == 0) {
                continue;
            }
            sb.append(s, ultimo, m.start());
            sb.append('\ue000').append(guardados.size()).append('\ue001');
            guardados.add(g.substring(0, fin));
            ultimo = m.start() + fin;
        }
        sb.append(s, ultimo, s.length());
        return sb.toString();
    }

    private static String restaurar(String s, List<String> guardados) {
        if (guardados.isEmpty()) {
            return s;
        }
        Matcher m = Pattern.compile("\ue000(\\d+)\ue001").matcher(s);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            sb.append(s, ultimo, m.start());
            int i = Integer.parseInt(m.group(1));
            sb.append(i < guardados.size() ? guardados.get(i) : "");
            ultimo = m.end();
        }
        sb.append(s, ultimo, s.length());
        return sb.toString();
    }

    // ------------------------------------------------------------------ reparar precios

    private static String reparar(String s) {
        if (!CLIMA.matcher(s).find()) {
            s = GRADOS.matcher(s).replaceAll("$1c");
        }
        s = CENT.matcher(s).replaceAll("$1c");
        return MILES_ROTOS.matcher(s).replaceAll("$1.$2k");
    }

    // ------------------------------------------------------------------ vocabulario

    private static String reemplazarVocabulario(Cfg c, String s, Set<String> propios) {
        if (c.patron == null) {
            return s;
        }
        Matcher m = c.patron.matcher(s);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            String orig = m.group(1);
            String k = clave(orig);
            String rep = c.vocabulario.get(k);
            sb.append(s, ultimo, m.start());
            ultimo = m.end();
            boolean inicio = inicioDeFrase(s, m.start());
            if (rep == null || esMayusculas(orig) || (c.soloInicio.contains(k) && !inicio) || !permitido(s, m.start(), m.end(), k)) {
                sb.append(orig);
                continue;
            }
            if (inicio && Character.isUpperCase(orig.charAt(0))) {
                if (Character.isLowerCase(rep.charAt(0))) {
                    propios.add(rep.split("[\\s,]+")[0].toLowerCase(Locale.ROOT)); // lo escribiste en minuscula: no es sustantivo
                }
                rep = Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
            }
            sb.append(rep);
        }
        sb.append(s, ultimo, s.length());
        return sb.toString();
    }

    private static boolean permitido(String s, int ini, int fin, String k) {
        Pattern no = NO_ANTES_DE.get(k);
        if (no != null && no.matcher(palabraSiguiente(s, fin)).matches()) {
            return false;
        }
        Pattern tras = NO_TRAS.get(k);
        if (tras != null && tras.matcher(palabraAnterior(s, ini)).matches()) {
            return false;
        }
        Pattern ctx = NO_CONTEXTO.get(k);
        if (ctx != null && ctx.matcher(s.substring(Math.max(0, ini - 30), Math.min(s.length(), fin + 30))).find()) {
            return false;
        }
        if (NO_SI_SIGUE_NOMBRE.contains(k)) {
            Matcher m = Pattern.compile("^\\s+(\\p{L}+)").matcher(s.substring(fin));
            if (m.find() && (Character.isUpperCase(m.group(1).charAt(0)) || ARTICULOS.contains(m.group(1).toLowerCase(Locale.ROOT)))) {
                return false;
            }
        }
        return true;
    }

    private static String reir(String s) {
        Matcher m = RISA.matcher(s);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            int veces = m.group().toLowerCase(Locale.ROOT).split("[\\s-]+").length;
            StringBuilder risa = new StringBuilder();
            for (int i = 0; i < veces; i++) {
                risa.append(i == 0 && Character.isUpperCase(m.group().charAt(0)) ? "Ha" : "ha");
            }
            sb.append(s, ultimo, m.start()).append(risa);
            ultimo = m.end();
        }
        sb.append(s, ultimo, s.length());
        return sb.toString();
    }

    // ------------------------------------------------------------------ estilo de chat

    private static String quitarPuntoFinal(String t) {
        if (!t.endsWith(".") || t.endsWith("..") || PUNTO_ABREVIATURA.matcher(t).find()) {
            return t;
        }
        String sin = t.substring(0, t.length() - 1).trim();
        if (sin.isEmpty() || Character.isDigit(sin.charAt(sin.length() - 1))) {
            return t; // "am 5." : el punto es del ordinal
        }
        return sin;
    }

    private static String minusculaInicial(String t, Set<String> propios) {
        Matcher m = Pattern.compile("^(\\p{L}+)").matcher(t);
        if (!m.find()) {
            return t;
        }
        String w = m.group(1);
        if (w.length() < 2 || !Character.isUpperCase(w.charAt(0)) || Character.isUpperCase(w.charAt(1))) {
            return t;
        }
        String l = w.toLowerCase(Locale.ROOT);
        if ((INICIOS_MINUSCULA.contains(l) || propios.contains(l) || l.matches("(?:ha){2,}")) && !l.equals("ihr") && !l.equals("sie")) {
            return Character.toLowerCase(t.charAt(0)) + t.substring(1);
        }
        return t;
    }

    private static String chat(String s, Set<String> propios) {
        return minusculaInicial(quitarPuntoFinal(s.trim()), propios);
    }

    // ------------------------------------------------------------------ tutear: Sie -> du, solo sin duda

    private static final class Tok {
        final int ini;
        final int fin;
        final String t;

        Tok(int ini, int fin, String t) {
            this.ini = ini;
            this.fin = fin;
            this.t = t;
        }
    }

    private static final class Edit {
        final int ini;
        final int fin;
        final String nuevo;

        Edit(int ini, int fin, String nuevo) {
            this.ini = ini;
            this.fin = fin;
            this.nuevo = nuevo;
        }
    }

    private static boolean soloEspacios(String s, int a, int b) {
        for (int i = a; i < b; i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static boolean hayLimite(String s, int a, int b) {
        for (int i = a; i < b; i++) {
            if (esLimite(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean necesitaE(String stem) {
        char l = stem.charAt(stem.length() - 1);
        if (l == 't' || l == 'd') {
            return true;
        }
        if ((l == 'm' || l == 'n') && stem.length() >= 2) {
            char p = stem.charAt(stem.length() - 2);
            return "fgdtbpk".indexOf(p) >= 0 || stem.endsWith("chn") || stem.endsWith("chm");
        }
        return false;
    }

    private static String raiz(String inf) {
        if (inf.endsWith("eln") || inf.endsWith("ern")) {
            return inf.substring(0, inf.length() - 1);
        }
        return inf.endsWith("en") ? inf.substring(0, inf.length() - 2) : inf.substring(0, inf.length() - 1);
    }

    private static String[] regular(String inf) {
        String stem = raiz(inf);
        char l = stem.charAt(stem.length() - 1);
        String du = "s\u00dfxz".indexOf(l) >= 0 ? stem + "t" : necesitaE(stem) ? stem + "est" : stem + "st";
        String imp = ((necesitaE(stem) || stem.endsWith("ig")) && !inf.endsWith("eln") && !inf.endsWith("ern")) ? stem + "e" : stem;
        return new String[]{du, imp};
    }

    /** {presente de du, imperativo de du} de una forma Sie/infinitivo, o null si el verbo no esta en la lista. */
    private static String[] verbo(String forma) {
        String f = forma.toLowerCase(Locale.ROOT);
        String[] v = VERBOS.get(f);
        if (v != null) {
            return v;
        }
        for (String p : PREFIJOS) {
            if (f.startsWith(p) && f.length() > p.length() + 2) {
                String[] b = VERBOS.get(f.substring(p.length()));
                if (b != null && !b[0].equals("-")) {
                    return new String[]{p + b[0], "-"};
                }
            }
        }
        return null;
    }

    private static String conMayuscula(String modelo, String forma) {
        return Character.isUpperCase(modelo.charAt(0)) ? Character.toUpperCase(forma.charAt(0)) + forma.substring(1) : forma;
    }

    private static boolean esPregunta(String s, int desde) {
        for (int i = desde; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '?') {
                return true;
            }
            if (c == '.' || c == '!') {
                return false;
            }
        }
        return false;
    }

    private static String entutear(String s, Set<String> propios) {
        if (!s.contains("Sie") && !s.contains("Ihr") && !s.contains("Ihn")) {
            return s;
        }
        List<Tok> tk = new ArrayList<Tok>();
        Matcher mp = PALABRA.matcher(s);
        while (mp.find()) {
            tk.add(new Tok(mp.start(), mp.end(), mp.group()));
        }
        List<Edit> ed = new ArrayList<Edit>();
        boolean[] usado = new boolean[tk.size()];
        for (int i = 0; i < tk.size(); i++) {
            Tok t = tk.get(i);
            String r = POSESIVOS.get(t.t);
            if (r != null && !inicioEstricto(s, t.ini)) {
                ed.add(new Edit(t.ini, t.fin, r));
                usado[i] = true;
            }
        }
        for (int i = 0; i < tk.size(); i++) {
            Tok t = tk.get(i);
            if (!t.t.equals("Sie") || usado[i]) {
                continue;
            }
            // limites de la oracion (hasta , . ! ? ; :) en la que esta este Sie
            int cIni = t.ini;
            while (cIni > 0 && !esLimite(s.charAt(cIni - 1))) {
                cIni--;
            }
            int cFin = t.fin;
            while (cFin < s.length() && !esLimite(s.charAt(cFin))) {
                cFin++;
            }
            if (Pattern.compile("(?<![\\p{L}])[Ss]ich(?![\\p{L}])").matcher(s.substring(cIni, cFin)).find()) {
                continue; // "sich" pediria dich/dir segun el verbo: no se arriesga
            }
            Tok prev = i > 0 ? tk.get(i - 1) : null;
            boolean pegadoPrev = prev != null && soloEspacios(s, prev.fin, t.ini);
            Tok next = i + 1 < tk.size() ? tk.get(i + 1) : null;
            boolean pegadoNext = next != null && soloEspacios(s, t.fin, next.ini);
            String pl = pegadoPrev ? prev.t.toLowerCase(Locale.ROOT) : "";
            boolean inicio = inicioEstricto(s, t.ini);

            // R1: "f\u00fcr Sie", "auf Sie"... una preposicion nunca lleva nominativo: es acusativo
            if (pegadoPrev && PREPOSICIONES.contains(pl) && !usado[i - 1]) {
                ed.add(new Edit(t.ini, t.fin, "dich"));
                usado[i] = true;
                continue;
            }
            // R2: "Sie zu sehen" (Sch\u00f6n, Sie zu sehen): objeto del infinitivo
            if (pegadoNext && next.t.equals("zu") && i + 2 < tk.size() && soloEspacios(s, next.fin, tk.get(i + 2).ini) && verbo(tk.get(i + 2).t) != null) {
                ed.add(new Edit(t.ini, t.fin, "dich"));
                usado[i] = true;
                continue;
            }
            // R2b: "Sie kennenzulernen" (zu fusionado con el infinitivo): tambien objeto
            if (pegadoNext && ZU_FUSIONADO.matcher(next.t).matches()) {
                ed.add(new Edit(t.ini, t.fin, "dich"));
                usado[i] = true;
                continue;
            }
            // R3: "Ich sehe Sie", "Kann ich Sie fragen", "Wir werden Sie anrufen": despues de un sujeto es objeto
            if (pegadoPrev && SUJETOS.contains(pl)) {
                ed.add(new Edit(t.ini, t.fin, "dich"));
                usado[i] = true;
                continue;
            }
            boolean sigueVerbo = pegadoNext && verbo(next.t) != null;
            if (pegadoPrev && i >= 2 && soloEspacios(s, tk.get(i - 2).fin, prev.ini) && SUJETOS_2.contains(tk.get(i - 2).t.toLowerCase(Locale.ROOT))
                && (!sigueVerbo || verbo(prev.t) != null || AUX_INF.contains(pl))) { // "Ich glaube Sie haben recht" (sin coma): Sie es sujeto, no se toca
                ed.add(new Edit(t.ini, t.fin, "dich"));
                usado[i] = true;
                continue;
            }
            // R4: verbo + Sie al principio de oracion: "K\u00f6nnen Sie", "St\u00f6ren Sie mich nicht", "Wie geht... Haben Sie"
            String[] v = pegadoPrev && !usado[i - 1] ? verbo(prev.t) : null;
            if (v != null) {
                Tok p2 = i >= 2 ? tk.get(i - 2) : null;
                boolean inicioClausula = p2 == null || hayLimite(s, p2.fin, prev.ini);
                String p2l = p2 == null ? "" : p2.t.toLowerCase(Locale.ROOT);
                String forma = null;
                boolean imperativo = false;
                boolean preg = esPregunta(s, t.fin);
                boolean tieneImp = !v[1].equals("-");
                boolean tienePres = !v[0].equals("-");
                Tok p3 = i >= 3 ? tk.get(i - 3) : null;
                boolean wieAdj = p2 != null && p3 != null && !inicioClausula && p3.t.equalsIgnoreCase("wie") && soloEspacios(s, p3.fin, p2.ini);
                if (!inicioClausula && (PREGUNTA.contains(p2l) || wieAdj)) {
                    forma = tienePres ? v[0] : null;
                } else if (inicioClausula || COORDINANTES.contains(p2l)) {
                    imperativo = !preg && tieneImp;
                    forma = imperativo ? v[1] : (tienePres ? v[0] : null);
                } else if (ADVERBIOS.contains(p2l)) {
                    forma = tienePres ? v[0] : null;
                }
                if (forma != null && !forma.equals("-")) {
                    String f = conMayuscula(prev.t, forma);
                    ed.add(new Edit(prev.ini, t.fin, imperativo ? f : f + " du"));
                    propios.add(f.toLowerCase(Locale.ROOT));
                    usado[i] = true;
                    usado[i - 1] = true;
                }
                continue;
            }
            // R5: "und Sie?" -> "und du?"
            int b = t.fin;
            while (b < s.length() && (s.charAt(b) == ' ' || s.charAt(b) == '\t')) {
                b++;
            }
            if (pegadoPrev && (pl.equals("und") || pl.equals("aber") || pl.equals("oder")) && (b >= s.length() || esLimite(s.charAt(b)))) {
                ed.add(new Edit(t.ini, t.fin, "du"));
                usado[i] = true;
                continue;
            }
            if (inicio) {
                continue; // "Sie sind..." al principio puede ser ellos/ella: no se toca
            }
            // R6: oracion principal tras coma o y/aber: "..., Sie haben recht"
            int a = t.ini - 1;
            while (a >= 0 && (s.charAt(a) == ' ' || s.charAt(a) == '\t')) {
                a--;
            }
            boolean trasComa = a >= 0 && s.charAt(a) == ',';
            if (next != null && pegadoNext && (trasComa || COORDINANTES.contains(pl)) && !SUBORDINANTES.contains(pl)) {
                String[] vn = verbo(next.t);
                if (vn != null && !vn[0].equals("-") && !usado[i + 1]) {
                    String f = vn[0];
                    ed.add(new Edit(t.ini, next.fin, "du" + s.substring(t.fin, next.ini) + f));
                    usado[i] = true;
                    usado[i + 1] = true;
                    continue;
                }
            }
            // R7: subordinada con el verbo al final: "wenn Sie k\u00f6nnen", "dass Sie gekommen sind", "wann Sie ankommen"
            if (pegadoPrev && SUBORDINANTES.contains(pl)) {
                int ultimo = -1;
                boolean coordina = false;
                for (int j = i + 1; j < tk.size() && tk.get(j).ini < cFin; j++) {
                    ultimo = j;
                    String w = tk.get(j).t.toLowerCase(Locale.ROOT);
                    if (w.equals("und") || w.equals("oder") || w.equals("aber") || w.equals("sondern") || w.equals("sowie")) {
                        coordina = true;
                    }
                }
                if (ultimo < 0 || coordina || usado[ultimo] || !s.substring(tk.get(ultimo).fin, cFin).trim().isEmpty()) {
                    continue;
                }
                String[] vf = verbo(tk.get(ultimo).t);
                boolean zuInf = ultimo - 1 > i && tk.get(ultimo - 1).t.equals("zu");
                if (vf == null || vf[0].equals("-") || zuInf) {
                    continue;
                }
                ed.add(new Edit(t.ini, t.fin, "du"));
                ed.add(new Edit(tk.get(ultimo).ini, tk.get(ultimo).fin, vf[0]));
                usado[i] = true;
                usado[ultimo] = true;
            }
        }
        if (ed.isEmpty()) {
            return s;
        }
        Collections.sort(ed, new Comparator<Edit>() {
            @Override
            public int compare(Edit a, Edit b) {
                return a.ini - b.ini;
            }
        });
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        for (Edit e : ed) {
            if (e.ini < ultimo) {
                continue; // solapado: se descarta
            }
            sb.append(s, ultimo, e.ini).append(e.nuevo);
            ultimo = e.fin;
        }
        sb.append(s, ultimo, s.length());
        return sb.toString();
    }

    // ------------------------------------------------------------------ API

    /** reparar + tutear + tabla + risa, repetido hasta que no cambie (lo que una pasada produce puede abrir contexto a la siguiente:
     *  "Vielen Dank, nat\u00fcrlich" -> "danke, nat\u00fcrlich" -> "danke, klar"); asi aplicar(aplicar(x)) == aplicar(x). */
    private static String nucleo(Cfg c, String s, Set<String> propios) {
        for (int ronda = 0; ronda < 4; ronda++) {
            String antes = s;
            s = reparar(s);
            if (c.tutear) {
                s = entutear(s, propios);
            }
            s = reemplazarVocabulario(c, s, propios);
            s = reir(s);
            if (s.equals(antes)) {
                break;
            }
        }
        return s;
    }

    static String aplicar(String string) {
        if (string == null || string.trim().isEmpty()) {
            return string;
        }
        try {
            Cfg c = cargar();
            if (!c.activo) {
                return string;
            }
            List<String> guardados = new ArrayList<String>();
            String s = proteger(string.replace('\u2019', '\'').replace('\u2018', '\''), guardados);
            Set<String> propios = new HashSet<String>();
            s = nucleo(c, s, propios);
            if (c.estiloChat) {
                s = chat(s, propios);
            }
            s = restaurar(s, guardados);
            return s.trim().isEmpty() ? string : s;
        } catch (Throwable t) {
            return string;
        }
    }

    public static void main(String[] args) {
        archivoPrueba = new File(args[0]);
        for (int i = 1; i < args.length; ++i) {
            System.out.println(args[i] + "\n   => " + aplicar(args[i]));
        }
    }

    static {
        POSESIVOS.put("Ihnen", "dir");
        POSESIVOS.put("Ihrer", "deiner");
        POSESIVOS.put("Ihrem", "deinem");
        POSESIVOS.put("Ihren", "deinen");
        POSESIVOS.put("Ihres", "deines");
        POSESIVOS.put("Ihre", "deine");
        POSESIVOS.put("Ihr", "dein");
        for (String linea : IRREGULARES) {
            String[] p = linea.split(":");
            VERBOS.put(p[0], new String[]{p[1], p[2]});
        }
        for (String inf : REGULARES.split(" ")) {
            if (!VERBOS.containsKey(inf)) {
                VERBOS.put(inf, regular(inf));
            }
        }
        NO_ANTES_DE.put("nat\u00fcrlich", Pattern.compile("nicht|kein|keine|keinen|keiner|nie|niemals"));
        NO_TRAS.put("wirklich", Pattern.compile("nicht|kein|keine|nie"));
        NO_TRAS.put("tats\u00e4chlich", Pattern.compile("nicht|kein|keine|nie"));
        NO_CONTEXTO.put("in ordnung", Pattern.compile("(?i)(?<![\\p{L}])(?:bring|gebracht|brachte|halt|geh\\u00e4lt|gehalten|h\\u00e4lt|mach|gemacht|stell)\\p{L}*"));
        NO_TRAS.put("ausgezeichnet", Pattern.compile("sich"));
    }
}
