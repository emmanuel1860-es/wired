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
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ingles "C2 de chat": lo que TU escribes (ya traducido por Google) se reescribe como lo diria un
 * hablante nativo, fluido y articulado, charlando en el juego (frases hechas, phrasal verbs,
 * muletillas que encajan, contracciones siempre). No cambia el significado: si una regla no encaja
 * con seguridad, el texto queda como esta.
 *
 * Las reglas viven en Memoria/ingles_c2.txt (se crea sola con POR_DEFECTO y se recarga al guardarla).
 * Nunca lanza excepciones (ante cualquier error devuelve el texto original), es segura entre hilos
 * y es idempotente: aplicar(aplicar(x)) es igual a aplicar(x).
 */
final class NivelC2 {
    private static final String TABLA_0 = "# Ingles nivel C2 (chat) para lo que TU escribes y el traductor pasa a ingles.\n"
        + "# Resultado: como lo diria un nativo fluido charlando en el juego: frases hechas, phrasal verbs, contracciones\n"
        + "# siempre (I'm, don't, it's...) y muletillas solo donde encajan. Sin palabras pomposas ni arcaicas.\n"
        + "# Nunca cambia el significado: si una regla no te convence, borra la linea.\n"
        + "#\n"
        + "# AJUSTES\n"
        + "#   activo = si/no        enciende o apaga todo\n"
        + "#   estilo_chat = si/no   quita el punto final (solo uno; no toca \"..\", \"...\", ?, ! ni comas)\n"
        + "#   picante = si/no       activa la seccion [picante] del final (bloody, damn: suaves). Apagado por defecto\n"
        + "#\n"
        + "# REGLAS    frase de Google = alternativa1|alternativa2|...     (una por linea; la alternativa se elige segun el mensaje:\n"
        + "#           el mismo mensaje da siempre lo mismo, mensajes distintos no repiten la misma muletilla)\n"
        + "#   - Da igual mayusculas/minusculas (se conserva la inicial). Escribe las frases completas (\"i do not know\"):\n"
        + "#     tambien vale \"i don't know\". Solo actua en palabras enteras: nunca dentro de palabras, enlaces, numeros\n"
        + "#     (5c, 3.5k), emoticonos, :comandos, @nombres ni citas entre comillas.\n"
        + "#   - Gana la frase mas larga. Una alternativa que otra regla volveria a reescribir (sola o detras de so/very/a/the...) se descarta sola.\n"
        + "#   - ^frase          solo al empezar frase o tras una coma:  ^thank you = thanks   (^^frase: solo al empezar frase)\n"
        + "#   - frase$          solo si despues viene el final o puntuacion:  thank you very much$ = much appreciated\n"
        + "#   - frase {a|b}     solo si la palabra siguiente es a o b ({$} final o puntuacion, {.} final de frase, {,} coma,\n"
        + "#                     {?} signo de pregunta, {-a|-b} si NO es a ni b):  i think {it|that} = i reckon\n"
        + "#   - <a|b> frase     solo si la palabra anterior es a o b (<-a|-b> si NO lo es). Atajos dentro de <...>:\n"
        + "#                     neg = hay una negacion en la misma clausula (not, n't, never, no, doubt...): <-neg> = no la hay\n"
        + "#                     q = la frase es una pregunta (acaba en ?)     ^ = inicio de frase o clausula\n"
        + "#                     disc = but, and, so, well, ok, yes...   det = a, the, my...   be = is, are, I'm...   vb = I, you, to, will...\n"
        + "#                     <^|disc> ve = \"al empezar la clausula o tras but/and/ok\": para fragmentos sin sujeto\n"
        + "#   - *               comodin de 1 a 3 palabras; en la derecha se usa $1, $2, $3:  how much does * cost = how much is $1\n"
        + "#   - #               comodin SOLO para importes (30c, 3.5k, 1,000, 5 credits): i will give you # for = i'll pay you $1 for\n"
        + "#   - ^alternativa    (dentro de la derecha) solo se usa si la frase empieza una clausula: fragmentos sin sujeto\n"
        + "#                     (^no idea, ^my bad). Si no, se elige otra alternativa; sin ninguna valida la regla no actua\n"
        + "#   - [picante]       las lineas de debajo solo valen con picante = si; [base] vuelve a las normales\n"
        + "# Largo: el chat de Habbo corta a 100 caracteres. Si el original cabia y el resultado no, se usa la alternativa mas corta de cada\n"
        + "# regla y, si aun asi no cabe, solo contracciones y estilo_chat (o el original).\n"
        + "# Nota Habbo: \"living room\" = room porque en Habbo \"sala\" es room.\n"
        + "activo = si\n"
        + "estilo_chat = si\n"
        + "picante = no\n"
        + "\n"
        + "# ---- saludos y despedidas\n"
        + "^hello there = hey there|hi there|hey\n"
        + "^hello = hey|hi|hey\n"
        + "hello everyone = hey everyone|hi all|hi everyone\n"
        + "hello everybody = hey everybody|hi all|hi everybody\n"
        + "hello friends = hey guys|hi guys|hey folks\n"
        + "how are you {$|?} = how's it going|how are you doing\n"
        + "how are things going {$|?|with|there|here|today|lately} = how's it going|how's everything going|how are you doing\n"
        + "how are things {$|?|with|there|here|today|lately} = how's it going|how's everything|how are you doing\n"
        + "how are you today {$|?} = how's your day going|how are you doing today\n"
        + "<^|disc> see you later {$} = catch you later|talk to you later|see you around\n"
        + "<^|disc> see you tomorrow {$|then|at} = catch you tomorrow|talk to you tomorrow\n"
        + "<^|disc> see you soon {$} = catch you soon|talk soon|speak soon\n"
        + "^goodbye {$} = bye for now|catch you later|take care|see ya\n"
        + "<^|vb> have a good rest {-of} = get some rest|sleep well|rest up\n"
        + "<^|vb> rest well = sleep well|get some rest|rest up\n"
        + "<^|disc|you> have a nice day {$|too|then} = have a good one|have a great one|enjoy the rest of your day\n"
        + "<^|disc|you> have a good day {$|too|then} = have a good one|have a great day|enjoy the rest of your day\n"
        + "<^|disc|you> have a good night {$|too|then} = sleep well|sleep tight|have a good one\n"
        + "<^|disc|you> have a good evening {$|too|then} = have a good one|enjoy your evening\n"
        + "<^|disc|you> have a good weekend {$|too|then} = have a good one|enjoy the weekend\n"
        + "<-do|-does|-did|-should|-would|-could|-must|-will|-can|-may|-might|-shall|-neg> i have to go {$|now|soon|already|but|because|since|and|so|too|then} = i've got to go|i gotta go|i need to head out|i'd better get going\n"
        + "<-do|-does|-did|-should|-would|-could|-must|-will|-can|-may|-might|-shall|-neg> i must go {$|now|soon|already|but|because|since|and|so|too|then} = i've got to go|i need to head out|i'd better get going\n"
        + "<-do|-does|-did|-should|-would|-could|-must|-will|-can|-may|-might|-shall|-neg> i have to leave {$|now|soon|already|but|because|since|and|so|too|then} = i've got to head out|i need to head out|i've got to run\n"
        + "<-do|-does|-did|-should|-would|-could|-must|-will|-can|-may|-might|-shall|-neg> i need to go {$|now|soon|already|but|because|since|and|so|too|then} = i've got to run|i need to head out|i should get going\n"
        + "i am leaving {$|now|already|soon} = i'm off|i'm heading out|i'm heading off\n"
        + "i am going to sleep {$|now|soon|early|already|too|then|because|so} = i'm off to bed|i'm heading to bed|i'm going to bed\n"
        + "i am going to eat {$|now|soon|first|then|too|and|because|so} = i'm off to eat|i'm going to grab a bite|i'm going to grab something to eat\n"
        + "i will come back soon {$|and|but|so|because|okay|ok} = i'll be back in a bit|i'll be back before long|i'll be back shortly\n"
        + "i will be back soon {$|and|but|so|because|okay|ok} = i'll be back in a bit|i'll be back before long|i'll be back shortly\n"
        + "i will be back in a moment = i'll be back in a sec|i'll be back in a bit|i'll be right back\n"
        + "i will come back in a moment = i'll be back in a sec|i'll be back in a bit|i'll be right back\n"
        + "i will be back in a while = i'll be back in a little while|i'll be back later\n"
        + "i am coming back {$|soon|later|in|shortly} = i'll be back\n"
        + "i come back right away = i'll be right back\n"
        + "<^|disc|please|just> wait a moment {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hang on a sec|hold on a sec|hang on a moment|hold up a sec\n"
        + "<^|disc|please|just> wait a minute {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hold on a minute|hang on a minute|hold on a second\n"
        + "<^|disc|please|just> wait a second {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hang on a second|hold on a sec|hang on a sec\n"
        + "<^|disc|please|just> wait a sec {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hang on a sec|hold on a sec\n"
        + "<^|disc|please> wait just a moment {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hang on a sec|hold on a sec\n"
        + "<^|disc|please> wait one moment {$|while|until|and|so|then|ok|okay|because|but|that|this|what|i|you|it|there|let|before|please} = hang on a sec|hold on a sec|one sec\n"
        + "give me a moment {$|please|while|so|because|until|and|but} = give me a sec|hang on a sec|bear with me a sec|hold on a sec\n"
        + "give me a minute {$|please|while|so|because|until|and|but} = bear with me a minute|hang on a minute|hold on a minute\n"
        + "give me a second {$|please|while|so|because|until|and|but} = give me a sec|hang on a sec|bear with me a sec\n"
        + "hold on a moment = hold on a sec|hang on a sec\n"
        + "<^|disc> one moment {$|please} = one sec|hang on|hold on\n"
        + "<^|disc> just a moment {$|please} = just a sec|hang on|hold on\n"
        + "\n"
        + "# ---- gracias y de nada\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you very much for = thanks a ton for|thanks so much for|cheers for|many thanks for\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you so much for = thanks a ton for|thanks so much for|cheers for|many thanks for\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you a lot for = thanks a ton for|thanks so much for|cheers for\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you very much$ = thanks a ton|thanks so much|much appreciated|cheers|many thanks\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you so much$ = thanks a ton|thanks so much|much appreciated|cheers|many thanks\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you a lot$ = thanks a ton|thanks so much|much appreciated\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you very much = thanks a ton|thanks so much|cheers|many thanks\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you so much = thanks a ton|thanks so much|cheers|many thanks\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you for = thanks for|thanks for|cheers for\n"
        + "^thank you = thanks|thanks|cheers\n"
        + "<-to|-i|-we|-would|-will|-can|-could|-must|-should|-may|-might|-shall|-let|-me|-us|-you|-they|-he|-she|-always|-just|-want|-wish|-like|-who|-i'd|-i'll|-we'd|-we'll|-you'll|-you'd|-they'll|-he'll|-she'll|-i've|-we've|-they've> thank you in advance = thanks in advance|many thanks in advance\n"
        + "i appreciate it very much = i really appreciate it|^much appreciated|^thanks a ton\n"
        + "i appreciate it a lot = i really appreciate it|^much appreciated|^thanks a ton\n"
        + "i appreciate it so much = i really appreciate it|^much appreciated|^thanks a ton\n"
        + "i appreciate your help a lot = i really appreciate the help|^thanks a ton for the help|^many thanks for the help\n"
        + "i appreciate your help very much = i really appreciate the help|^thanks a ton for the help|^many thanks for the help\n"
        + "i appreciate your help so much = i really appreciate the help|^thanks a ton for the help|^many thanks for the help\n"
        + "i appreciate your help {$|with|today|and|but|because|yesterday|earlier|again} = ^thanks for the help|i really appreciate the help|^much appreciated\n"
        + "^you are welcome {$} = no worries|no problem|anytime|my pleasure|don't mention it\n"
        + "you are very kind {$} = that's really kind of you|that's very kind of you|you're too kind\n"
        + "you are so kind {$} = that's really kind of you|you're too kind\n"
        + "you are really kind {$} = that's really kind of you|you're too kind\n"
        + "\n"
        + "# ---- disculpas y condolencias\n"
        + "i am very sorry = i'm so sorry|i'm really sorry|i'm truly sorry|i'm terribly sorry\n"
        + "i am extremely sorry = i'm so sorry|i'm truly sorry|i'm terribly sorry\n"
        + "i am sorry for the {delay|mistake|confusion|trouble|inconvenience|noise|spam|wait|misunderstanding|lag|mess|bother|disturbance|interruption|error|rudeness|bug|problem|problems} = ^apologies for the|^sorry for the|^sorry about the|i'm truly sorry for the\n"
        + "i am sorry for = ^sorry for|^so sorry for|i'm so sorry for|i'm really sorry for\n"
        + "i am sorry about = ^sorry about|^so sorry about|i'm so sorry about|i'm really sorry about\n"
        + "i am sorry to hear = ^sorry to hear|^so sorry to hear|i'm so sorry to hear|i'm really sorry to hear\n"
        + "i am sorry that = ^sorry that|^so sorry that|i'm so sorry that|i'm really sorry that\n"
        + "i am sorry, but = ^sorry, but|^apologies, but|^my apologies, but|i'm so sorry, but\n"
        + "i am sorry {.} = ^so sorry|^really sorry|i'm so sorry|i'm really sorry\n"
        + "i am sorry {,} = ^so sorry|^really sorry|i'm so sorry|i'm really sorry\n"
        + "^sorry, i did not mean to = my bad, i didn't mean to|apologies, i didn't mean to|my apologies, i didn't mean to\n"
        + "i apologize for the = ^apologies for the|^sorry about the|^sorry for the|i'm truly sorry about the\n"
        + "i apologize for = ^apologies for|^sorry for|^my apologies for|i'm truly sorry for\n"
        + "i apologize {.} = ^my apologies|^apologies|^sorry about that|i'm truly sorry\n"
        + "i apologize, = ^apologies,|^my apologies,|^sorry,|i'm truly sorry,\n"
        + "i apologise for = ^apologies for|^sorry for|^my apologies for|i'm truly sorry for\n"
        + "it was my mistake {$} = that one's on me|^my mistake|that was my bad|that's on me\n"
        + "it was my fault {$} = that was on me|^my bad|^my fault|that one's on me\n"
        + "it was my error {$} = ^my mistake|that was my bad|that's on me\n"
        + "it is my fault {$} = that's on me|^my bad|^my fault\n"
        + "it is my mistake {$} = that's on me|^my mistake|^my bad\n"
        + "i had not seen you {$|there|here|coming|come|and|but|because|so} = i hadn't noticed you|i didn't spot you|i hadn't spotted you\n"
        + "i did not see you {-for|-since|-lately|-yet|-in|-around} = i didn't spot you|i didn't notice you\n"
        + "i did not know {what|how|where|when|why|who|which|if|whether} = i had no idea|i had no clue\n"
        + "i did not know {that|you were|you are|he was|she was|they were|it was|there was|we were|i was} = i had no idea|i had no clue|i didn't realize\n"
        + "\n"
        + "# ---- acuerdo, desacuerdo y opinion\n"
        + "i completely agree with you {-that|-in} = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed|^100% agree\n"
        + "i completely agree with that {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i completely agree with this {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i completely agree with what you said {$|and|but|because|so|too} = i couldn't agree more|i'm with you there|^agreed\n"
        + "i completely agree {$|and|but|because|so|too|about|on} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i agree with you completely = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed\n"
        + "i totally agree with you {-that|-in} = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed|^100% agree\n"
        + "i totally agree with that {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i totally agree with this {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i totally agree with what you said {$|and|but|because|so|too} = i couldn't agree more|i'm with you there|^agreed\n"
        + "i totally agree {$|and|but|because|so|too|about|on} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i agree with you totally = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed\n"
        + "i fully agree with you {-that|-in} = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed|^100% agree\n"
        + "i fully agree with that {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i fully agree with this {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i fully agree with what you said {$|and|but|because|so|too} = i couldn't agree more|i'm with you there|^agreed\n"
        + "i fully agree {$|and|but|because|so|too|about|on} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i agree with you fully = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed\n"
        + "i absolutely agree with you {-that|-in} = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed|^100% agree\n"
        + "i absolutely agree with that {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i absolutely agree with this {$|and|but|because|so|too} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i absolutely agree with what you said {$|and|but|because|so|too} = i couldn't agree more|i'm with you there|^agreed\n"
        + "i absolutely agree {$|and|but|because|so|too|about|on} = i couldn't agree more|i'm with you all the way|^agreed|^100% agree\n"
        + "i agree with you absolutely = i couldn't agree more|i'm with you all the way|i'm totally with you|^agreed\n"
        + "i agree with you {$|and|but|because|so|too} = i couldn't agree more|i'm with you on that|i'm with you there|^agreed\n"
        + "i agree with that {$|and|but|because|so|too} = i couldn't agree more|i'm with you on that|i'm on board with that|^agreed\n"
        + "i agree with this {$|and|but|because|so|too} = i couldn't agree more|i'm with you on that|i'm on board with this|^agreed\n"
        + "i agree with what you said {$|and|but|because|so|too} = i couldn't agree more|i'm with you there|^agreed\n"
        + "<^|disc> i agree {$} = agreed|i couldn't agree more|i'm with you|i second that\n"
        + "<-neg> you are absolutely right = you're spot on|you've hit the nail on the head|you're dead right\n"
        + "<-neg> you are totally right = you're spot on|you're dead right\n"
        + "<-neg> you are completely right = you're spot on|you're dead right\n"
        + "<-neg> you are quite right = you're spot on|you're dead right\n"
        + "<-neg> you are right {$} = ^fair point|you've got a point|you're not wrong|^good point|^fair enough\n"
        + "<-neg> you are right about = you've got a point about|you're not wrong about\n"
        + "<-neg> you are wrong {$} = you've got that wrong|that's not right|you've got it wrong\n"
        + "<-neg> you are wrong about = you're mistaken about|you've got it wrong about\n"
        + "<-neg> you are mistaken {$} = you've got it wrong|that's not right|you're off the mark\n"
        + "i think you are wrong = i think you've got it wrong|i reckon you've got it wrong|i'd say you've got that wrong|i think you've got that wrong\n"
        + "i think you are right = i reckon you're right|i'd say you're right|i think you've got a point\n"
        + "i do not agree with you {$|and|but|because|so|too} = i see it differently|i'd have to disagree|i don't see it that way|i can't really agree\n"
        + "i do not agree with that {$|and|but|because|so|too} = i don't see it that way|i'd have to disagree|i can't really agree\n"
        + "i do not agree {$} = i see it differently|i'd have to disagree|i beg to differ|i don't see it that way\n"
        + "i disagree with you {$|and|but|because|so|too} = i see it differently|i'd have to disagree|i don't see it that way\n"
        + "i disagree {$} = i'd have to disagree|i don't see it that way|i beg to differ|i see it differently\n"
        + "<^|disc> that is true {$} = fair point|fair enough|no argument there\n"
        + "i had not thought about it {$} = it hadn't crossed my mind|i hadn't thought of that|that hadn't occurred to me\n"
        + "i did not think about it {$} = it hadn't crossed my mind|i hadn't thought of that|that hadn't occurred to me\n"
        + "i think so {$} = i reckon so|i'd say so\n"
        + "i believe so {$} = i reckon so|i'd say so\n"
        + "i think it over = i'll think it over|i'm thinking it over\n"
        + "i think it through = i'll think it through|i'm thinking it through\n"
        + "i think this over = i'll think this over|i'm thinking this over\n"
        + "i think this through = i'll think this through|i'm thinking this through\n"
        + "i think that over = i'll think that over|i'm thinking that over\n"
        + "i think that through = i'll think that through|i'm thinking that through\n"
        + "i think {it|that|this|so|there|we|they|he|she} = i reckon|i'd say|i figure\n"
        + "i believe it {$} = i do believe it|i truly believe it\n"
        + "i believe that {$} = i do believe that|i truly believe that\n"
        + "i believe this {$} = i do believe this|i truly believe this\n"
        + "i believe {it|that|this|so|there|we|they|he|she} = i reckon|i'd say|i figure\n"
        + "^in my opinion, = if you ask me,|personally,|for me,|i'd say\n"
        + "in my opinion, = if you ask me,|personally,|for me,\n"
        + "in my opinion {$} = if you ask me|in my book|for what it's worth\n"
        + "in my opinion = personally,|if you ask me,|for me,\n"
        + "it seems to me that = i get the impression that|it looks to me like|i reckon\n"
        + "it seems like a very good idea to me = ^sounds like a great idea to me|i'm all for it|that sounds like a brilliant idea|^sounds like a plan to me\n"
        + "it seems like a good idea to me = ^sounds like a good idea to me|i'm all for it|^sounds like a plan to me\n"
        + "it seems like a very good idea to = it sounds like a great idea to|it seems like a brilliant idea to\n"
        + "it seems like a good idea to = it sounds like a good idea to|it seems like a great idea to\n"
        + "it seems like a very good idea {$|and|but|because|so|too} = ^sounds like a great idea|that sounds like a brilliant idea\n"
        + "it seems like a good idea {$|and|but|because|so|too} = ^sounds like a good idea|^sounds like a plan|that sounds like a good idea\n"
        + "<-neg> that is a very good idea = that's a brilliant idea|^brilliant idea|^great idea|^good thinking\n"
        + "<-neg> that is a good idea {$} = that's a good call|^good call|^nice one|^sounds like a plan\n"
        + "i do not think it is a good idea = i doubt that's the best idea|i'm not convinced that's the best idea|i wouldn't say that's a great idea|i doubt it's a good idea\n"
        + "i do not think that is a good idea = i doubt that's the best idea|i'm not convinced that's the best idea|i wouldn't say that's a great idea\n"
        + "i do not think so {$} = i doubt it|^don't think so|i reckon not|i'd say not|i wouldn't say so\n"
        + "i think not = i doubt it|i reckon not\n"
        + "it is not a good idea to = it's not a great idea to|it's not wise to|it's not the best idea to\n"
        + "it is not a good idea for = it's not a great idea for|it's not wise for|it's not the best idea for\n"
        + "it is not a good idea {$|and|but|because|so|too|to me} = that's not a great idea|that's not the best idea|that's not wise\n"
        + "\n"
        + "# ---- saber, dudar y entender\n"
        + "i am not sure if = ^not sure if|i'm not entirely sure if|i'm not totally sure if\n"
        + "i am not sure whether = ^not sure whether|i'm not totally sure whether|i'm not entirely sure whether\n"
        + "i am not sure = i'm not entirely sure|i'm not totally sure|i'm not completely sure\n"
        + "i am not certain = i'm not entirely sure|i'm not totally sure\n"
        + "i am unsure = i'm not entirely sure|i'm a bit unsure\n"
        + "i do not know if = ^no idea if|^not sure if|i couldn't say if\n"
        + "i do not know whether = ^no idea whether|^not sure whether|i couldn't say whether\n"
        + "i do not know what to do = ^no idea what to do|i'm at a loss|i've got no clue what to do|i haven't a clue what to do\n"
        + "i do not know about = ^no idea about|i couldn't tell you about|i'm in the dark about\n"
        + "i do not know {$} = ^no idea|^beats me|i've got no idea|i haven't got a clue|i couldn't tell you\n"
        + "i do not know {what|how|where|when|why|who|which} = ^no idea|i've got no idea|i haven't got a clue\n"
        + "i have no idea about = ^no idea about|i'm clueless about|^haven't a clue about\n"
        + "i have no idea {$} = ^no idea|^beats me|i haven't got a clue|i've got no clue|^not a clue\n"
        + "i have no idea {what|how|where|when|why|who|which|if|whether} = ^no idea|i haven't got a clue|i've got no clue\n"
        + "i have no clue {$} = ^no clue|^no idea|^beats me|i haven't got a clue\n"
        + "i have no clue {what|how|where|when|why|who|which|if|whether} = ^no clue|^no idea|i haven't got a clue|i've got no idea\n"
        + "i do not have a clue {$} = ^no clue|^no idea|^beats me|i've got no idea\n"
        + "i do not have a clue {what|how|where|when|why|who|which|if|whether} = ^no clue|^no idea|i've got no idea\n";
    private static final String TABLA_1 = "i do not have any idea {$} = ^no idea|^no clue|^beats me|i haven't got a clue\n"
        + "i do not have any idea {what|how|where|when|why|who|which|if|whether} = ^no idea|^no clue|i haven't got a clue\n"
        + "i do not understand anything = i'm totally lost|i don't get any of it|none of this makes sense to me|i can't make head or tail of any of it\n"
        + "i do not understand {$|but} = i don't get it|i'm lost|i'm not following|i don't follow\n"
        + "i do not understand {why|how|what|when|where|who|which} = i don't get|i can't work out|i can't see\n"
        + "i do not understand {this|that|it} = i don't get|i'm not getting|i can't make sense of\n"
        + "do you know {where|how|when|why|who|if|whether|which} = ^any idea|do you happen to know|do you have any idea\n"
        + "do you know what {-$|-?} = ^any idea what|do you happen to know what|do you have any idea what\n"
        + "i would like to know {what|where|how|when|why|who|if|whether|which} = i was wondering|i'm curious|i'd love to know\n"
        + "i want to know {what|where|how|when|why|who|if|whether|which} = i'm curious|i was wondering|i'd love to know\n"
        + "\n"
        + "# ---- preguntas y peticiones\n"
        + "what do you want {$|?} = what are you after|what is it you're after\n"
        + "what are you doing {$|?|now|right now} = what are you up to|what are you up to|what are you getting up to\n"
        + "why do not you come to = why not come to|how about coming to|why not swing by\n"
        + "why do not you come over = why not come over|how about coming over\n"
        + "why do not you visit {-him|-her|-them|-us|-me|-it} = why not drop by|how about visiting|why not swing by\n"
        + "can you help me {$|?|please|with} = can you give me a hand|could you give me a hand|can you lend me a hand|would you mind giving me a hand\n"
        + "could you help me {$|?|please|with} = could you give me a hand|would you mind giving me a hand|could you lend me a hand\n"
        + "would you help me {$|?|please|with} = would you give me a hand|would you mind giving me a hand\n"
        + "can i help you {$|?|with} = ^need a hand|can i give you a hand|can i lend a hand\n"
        + "i will help you {$|with|if|when|now|later|today|tomorrow|soon|in} = i'll give you a hand|i'll lend you a hand\n"
        + "i need help = i could use some help|i could use a hand|i need a hand|i'd appreciate some help\n"
        + "i need some help = i could use some help|i could use a hand|i'd appreciate some help\n"
        + "i need your help = i could use your help|i'd appreciate your help|i'd love your help\n"
        + "can you explain to me how = can you walk me through how|could you talk me through how|can you break down how\n"
        + "can you explain me how = can you walk me through how|could you talk me through how\n"
        + "can you explain how = can you walk me through how|could you talk me through how|can you break down how\n"
        + "could you explain how = could you walk me through how|could you talk me through how\n"
        + "let me know if = give me a shout if|ping me if|just say if\n"
        + "let me know when = give me a shout when|ping me when|just say when\n"
        + "let me know whenever = give me a shout whenever|ping me whenever\n"
        + "let me know as soon as = give me a shout as soon as|ping me as soon as\n"
        + "<^|disc|please|just|oh> leave me alone {$|please|now|for} = leave me be|give me some space|let me be\n"
        + "please do not bother me = please don't bug me|please don't hassle me|please leave me be\n"
        + "<^|disc|please|just|oh> do not bother me = don't bug me|don't hassle me|quit bugging me\n"
        + "<^|disc|please|just|oh> do not disturb me = don't bug me|don't hassle me|leave me be\n"
        + "stop bothering me = quit bugging me|stop hassling me|quit pestering me\n"
        + "i am busy {$|now|right now|at the moment|today|tonight|with} = i'm tied up|i've got my hands full|i'm swamped|i'm snowed under\n"
        + "<-neg> i am very busy = i'm swamped|i've got my hands full|i'm tied up\n"
        + "<^|disc|please|just|oh> do not worry about it = don't sweat it|^no worries|^it's all good|forget about it\n"
        + "<^|disc|please|just|oh> do not worry about = don't stress about|^no worries about\n"
        + "<^|disc|please|just|oh> do not worry {$} = don't sweat it|^no worries|^it's all good|^no stress\n"
        + "^be careful {$} = watch out|careful|look out\n"
        + "beware of = watch out for|steer clear of\n"
        + "do what you want {$} = do whatever you like|do as you please|do whatever works for you\n"
        + "do what you like {$} = do whatever you want|do as you please\n"
        + "i do not care {$} = it's all the same to me|it makes no difference to me|i'm not fussed\n"
        + "i do not care if = i don't mind if|i'm not bothered if\n"
        + "i do not have enough money to = i can't afford to|i'm too short on cash to|i haven't got enough cash to\n"
        + "i do not have enough money {$|right now|now|at the moment|today|but|and|so} = i'm short on cash|i can't afford it|i'm a bit short|i'm short of cash\n"
        + "\n"
        + "# ---- compraventa y trueques de Habbo\n"
        + "<-neg> i want to {buy|sell|trade|swap|exchange|get|find|pick|offer|grab} = i'm looking to|i'm looking to|i'm hoping to|i'm keen to\n"
        + "<-neg> i would like to {buy|sell|trade|swap|exchange|get|find|pick|offer|grab} = i'm looking to|i'm looking to|i'm hoping to|i'm keen to\n"
        + "i am looking for {a|an|some|any|another|someone|somebody|anyone|anybody|something|rare|one|two|three|more} = i'm after|i'm on the hunt for|i'm hunting for|i'm trying to track down\n"
        + "i am searching for {a|an|some|any|another|someone|somebody|anyone|anybody|something|rare|one|two|three|more} = i'm on the hunt for|i'm hunting for|i'm trying to track down\n"
        + "does anyone have = anyone got|anyone got|anyone have|does anyone happen to have\n"
        + "does anybody have = anybody got|anybody got|anybody have|does anybody happen to have\n"
        + "does anyone want to = anyone want to|anyone wanna|does anybody want to\n"
        + "would anyone like to = anyone care to|anyone want to\n"
        + "who wants to play {$|?|with|again|now|too|together|here|more|tonight|today} = who's up for a game|who's up for a game|who's up for playing|who's keen for a game\n"
        + "i trade {-you|-in} = i'm trading|i'll trade|i'm swapping|i'll swap\n"
        + "well played everyone = well played all|well played, all|nicely done, all\n"
        + "well played to everyone = well played, all|well played all\n"
        + "well done to everyone = nice one, all|well done, all\n"
        + "good game everyone = good game all|good game, all|nice one, all\n"
        + "i exchange {-you|-in} = i'm swapping|i'll swap|i'm trading|i'll trade\n"
        + "i sell = i'm selling\n"
        + "i buy = i'm buying\n"
        + "i offer = i'm offering|i'll offer|i can offer\n"
        + "i give away = i'm giving away|i'm giving away|i'll give away\n"
        + "i give * to whoever = i'm giving $1 away to whoever|i'm handing $1 over to whoever\n"
        + "i give * to anyone who = i'm giving $1 away to anyone who|i'm handing $1 over to anyone who\n"
        + "i will give you # for = i'll pay you $1 for|i'll offer you $1 for\n"
        + "i give you # for = i'll offer you $1 for|i'll pay you $1 for|i'm offering you $1 for\n"
        + "to exchange = to swap|to trade\n"
        + "exchange it for = swap it for|trade it for\n"
        + "exchange my = swap my|trade my\n"
        + "exchange your = swap your|trade your\n"
        + "<-in|-the|-an|-a|-currency|-this|-that|-my|-your> exchange for = swap for|trade for\n"
        + "in exchange for = in return for|in trade for|for\n"
        + "the exchange {-rate|-rates|-student|-students|-program|-programme|-office|-of} = the swap|the trade\n"
        + "an exchange {-rate|-rates|-student|-students|-program|-programme|-office|-of} = a swap|a trade\n"
        + "do you accept # for = would you take $1 for|would you do $1 for|how about $1 for\n"
        + "will you accept # for = would you take $1 for|would you do $1 for|how about $1 for\n"
        + "do you accept * for = would you take $1 for|how about $1 for\n"
        + "will you accept * for = would you take $1 for|how about $1 for\n"
        + "do you accept {credits|coins|diamonds|duckets|payments|payment|cash|bc|hc|offers|trades|swaps|deals|my offer|this offer|the offer|that offer|an offer|my trade|this trade|the trade|that trade|my deal|this deal|the deal|that deal} = would you take|would you go for\n"
        + "how much does * cost {?|$|today|now|here|there|and|please} = how much is $1|what's $1 going for|how much for $1|what are you asking for $1\n"
        + "how much do * cost {?|$|today|now|here|there|and|please} = how much are $1|what are $1 going for|how much for $1\n"
        + "how much do you want for = what are you asking for|how much are you asking for|what would you want for\n"
        + "how much is * worth {?|$} = what's $1 worth|what would $1 go for\n"
        + "what is the price {$|?} = how much is it|how much are you asking|what's the damage\n"
        + "can you give me a discount {?|$|please|for|if} = can you knock a bit off the price|any chance of a discount|can you do me a deal\n"
        + "could you give me a discount {?|$|please|for|if} = could you knock a bit off the price|any chance of a discount|could you do me a deal\n"
        + "will you give me a discount {?|$|please|for|if} = will you knock a bit off the price|any chance of a discount|will you do me a deal\n"
        + "can you lower the price {?|$|please|if} = can you come down a bit|would you take a bit less|any room on the price\n"
        + "could you lower the price {?|$|please|if} = could you come down a bit|would you take a bit less|any room on the price\n"
        + "<-much> too expensive {$|for|to|and|but|so|?} = too pricey|too steep|too much\n"
        + "^negotiable price = price negotiable|price is negotiable|open to offers\n"
        + "the price is negotiable = the price is flexible|i'm open to offers|^open to offers\n"
        + "i pay well = i'll pay well|i pay good money\n"
        + "furniture = furni\n"
        + "living room = room\n"
        + "* looks like a scam {$|to me|and|but|because|so|too|honestly} = $1 smells like a scam|$1 reeks of a scam|$1 has scam written all over it\n"
        + "* seems like a scam {$|to me|and|but|because|so|too|honestly} = $1 smells like a scam|$1 sounds like a scam|$1 reeks of a scam\n"
        + "* seems to be a scam {$|to me|and|but|because|so|too|honestly} = $1 smells like a scam|$1 has scam written all over it\n"
        + "to whoever wants it = to anyone who wants it|to anyone interested|to whoever's interested\n"
        + "\n"
        + "# ---- juegos\n"
        + "it is your turn {$|now|then} = you're up|^your move|^your go|^over to you\n"
        + "it is my turn {$|now} = i'm up|^my move|^my go\n"
        + "congratulations on = congrats on\n"
        + "congratulations {$} = congrats|congrats|^well done|^nice one|^nicely done\n"
        + "<^|disc> bad luck {$} = unlucky|hard luck|tough break\n"
        + "^next time it will be {$} = you'll get it next time|next time for sure|better luck next time|next time's yours\n"
        + "i almost lost {$} = i nearly lost|it was a close call|i came close to losing\n"
        + "i almost won {$} = i nearly won|i came close to winning|^so close\n"
        + "i almost lost = i nearly lost\n"
        + "i almost won = i nearly won\n"
        + "<^|disc> how fast you are {$} = you're so quick|you're lightning fast|you're seriously fast\n"
        + "you beat me by a little bit {$|and|but|so} = you beat me by a hair|you narrowly beat me|you pipped me at the post\n"
        + "you beat me by a little {$|and|but|so} = you beat me by a hair|you narrowly beat me|you pipped me at the post\n"
        + "you beat me by a narrow margin = you narrowly beat me|you beat me by a hair|you just edged me out\n"
        + "you won by a little bit {$|and|but|so} = you won by a hair|you won narrowly\n"
        + "you won by a little {$|and|but|so} = you won by a hair|you won narrowly\n"
        + "the game starts in = the game kicks off in|we kick off in|the game gets going in\n"
        + "the match starts in = the match kicks off in|we kick off in\n"
        + "the game begins in = the game kicks off in|we kick off in\n"
        + "the game is about to start = the game's about to kick off|we're about to kick off\n"
        + "<-please|-just|-to|-if|-you|-they|-will|-would|-can|-could|-never|-not|-don't|-do|-who|-he|-she|-it|-i|-we> let us play again = let's go again|let's run it back|let's play another round|^one more round\n"
        + "<-please|-just|-to|-if|-you|-they|-will|-would|-can|-could|-never|-not|-don't|-do|-who|-he|-she|-it|-i|-we> let us start {$|now|then|already} = let's get started|let's kick things off|let's get going\n"
        + "<-please|-just|-to|-if|-you|-they|-will|-would|-can|-could|-never|-not|-don't|-do|-who|-he|-she|-it|-i|-we> let us begin {$|now|then|already} = let's get started|let's kick things off|let's get going\n"
        + "<-please|-just|-to|-if|-you|-they|-will|-would|-can|-could|-never|-not|-don't|-do|-who|-he|-she|-it|-i|-we> let us continue with = let's carry on with|let's get on with\n"
        + "<-please|-just|-to|-if|-you|-they|-will|-would|-can|-could|-never|-not|-don't|-do|-who|-he|-she|-it|-i|-we> let us continue {$|later|now|then|tomorrow} = let's carry on|let's pick this up|let's keep going\n"
        + "i want a rematch = ^let's have a rematch|^give me a rematch|i'd love a rematch\n"
        + "you are the winner {$|and|but|so|now|today} = you're the champ|you came out on top|you took the win\n"
        + "it is not fair = that's so unfair|that's just not fair|that's completely unfair\n"
        + "this is not fair = that's so unfair|that's just not fair\n"
        + "that is not fair = that's so unfair|that's just not fair\n"
        + "i wish you luck {$|in|with|for|on|and|today|tomorrow|tonight|there} = good luck|best of luck|all the best\n"
        + "i wish you good luck {$|in|with|for|on|and|today|tomorrow|tonight|there} = good luck|best of luck|all the best\n"
        + "i wish you the best of luck = best of luck|all the best|good luck\n"
        + "i wish you the best {$|in|with|for|on|and|today|tomorrow|there} = all the best|best of luck\n"
        + "\n"
        + "# ---- emociones y gustos\n"
        + "<-neg> i am very happy = i'm over the moon|i'm thrilled|i'm delighted|i'm so happy\n"
        + "<-neg> i am extremely happy = i'm over the moon|i'm thrilled|i'm delighted\n"
        + "<-neg> i am very angry with = i'm furious with|i'm livid with|i'm fuming at\n"
        + "<-neg> i am very bored {-of|-with|-by} = i'm bored stiff|i'm bored out of my mind|i'm bored to tears\n"
        + "<-neg> i am so bored {-of|-with|-by|-that} = i'm bored stiff|i'm bored out of my mind|i'm bored to tears\n"
        + "<-neg> i am very worried = i'm worried sick|i'm genuinely worried\n"
        + "<-neg> i am very nervous {-to} = i'm on edge|i'm a bundle of nerves\n"
        + "<-neg|-q> very tired of = sick and tired of|fed up with\n"
        + "<-neg> so tired of = sick and tired of|fed up with\n"
        + "<-neg|-q> really tired of = sick and tired of|fed up with\n"
        + "<-neg> extremely tired of = sick and tired of|fed up with\n"
        + "i like it a lot {-more|-less|-better|-than|-too} = i'm really into it|i'm a big fan of it|i'm loving it\n"
        + "i like it very much {-more|-less|-better|-than|-too} = i'm really into it|i'm a big fan of it|i'm loving it\n"
        + "i like this a lot {-more|-less|-better|-than|-too} = i'm really into this|i'm a big fan of this|i'm loving this\n"
        + "i like that a lot {-more|-less|-better|-than|-too} = i'm really into that|i'm a big fan of that|i'm loving that\n"
        + "i like this very much {-more|-less|-better|-than|-too} = i'm really into this|i'm a big fan of this\n"
        + "i like this * a lot {-more|-less|-better|-than|-too} = i'm really into this $1|i'm a big fan of this $1|i'm loving this $1\n"
        + "i really like it {$|and|but|so|too} = i'm really into it|i'm a big fan of it\n"
        + "i really like this = i'm really into this|i'm a big fan of this|i'm loving this\n"
        + "i really like that {-you|-we|-they|-he|-she|-it|-i|-there|-people|-everyone|-everybody|-someone|-nobody|-no|-the|-my|-your|-his|-her|-our|-their|-this|-those|-these} = i'm really into that|i'm a big fan of that\n"
        + "i really like your = i'm a big fan of your|i'm loving your\n"
        + "i love your = i adore your|i'm a big fan of your|i'm loving your\n"
        + "i love this = i'm loving this|i adore this\n"
        + "i love it very much = i absolutely love it|i really love it|i just love it\n"
        + "i love it so much = i absolutely love it|i really love it|i just love it\n"
        + "i do not like it {$|at all} = i'm not a fan|i'm not feeling it|it's not for me\n"
        + "i do not like {this|these|those|the|your|his|their|our|my|ghosts|spiders|snakes|monsters|clowns|zombies|bugs|heights} = i'm not a fan of|i'm not into\n"
        + "i do not like {him|her|them} = i'm not a fan of\n"
        + "i do not like that {-you|-we|-they|-he|-she|-it|-i|-there|-people|-everyone|-everybody|-someone|-nobody|-no|-the|-my|-your|-his|-her|-our|-their|-this|-those|-these} = i'm not a fan of that|i'm not into that\n"
        + "what a pity that = it's a shame that|it's such a shame that|^shame that\n"
        + "what a shame that = it's such a shame that|^shame that\n"
        + "what a pity = ^such a shame|that's a shame|^what a bummer\n"
        + "what a shame = ^such a shame|that's a shame|^what a bummer\n"
        + "it is a pity = it's a shame|it's such a shame\n"
        + "how scary {$} = that's terrifying|how creepy\n"
        + "\n"
        + "# ---- tiempo\n"
        + "a long time ago = ages ago|a good while back|ages back\n"
        + "for a long time = for ages|for a good while|for a long while\n"
        + "in a long time = in ages|in a good while|in a long while\n"
        + "it has been a long time since = it's been ages since|it's been a good while since\n"
        + "it has been a long time = it's been ages|it's been a while|it's been a long while\n"
        + "i have not seen you in a long time = i haven't seen you in ages|it's been ages|^long time no see|it's been a while\n"
        + "immediately {$|please|and|then} = right away|straight away|right now\n"
        + "as soon as possible = ASAP|as quickly as possible\n"
        + "in a moment {$|and|but|then|so|ok|okay|please|again} = in a sec|in a bit|shortly\n"
        + "in a little while = in a bit|in a while\n"
        + "in a few moments = in a bit|in a minute|shortly\n"
        + "at this moment {-in} = right now|at the moment\n"
        + "at the present time = right now|at the moment\n"
        + "at present = right now|at the moment\n"
        + "in the near future = soon|before long\n"
        + "nowadays = these days\n"
        + "<^|vb> return {$|here|there|soon|later|tomorrow|tonight|now|again|home|to} = come back|get back\n"
        + "<subj> returned {$|here|there|soon|later|tomorrow|tonight|now|again|home|to} = came back|got back\n"
        + "<he|she|it|who|that|which|everyone|everybody|nobody|someone|somebody> returns {$|here|there|soon|later|tomorrow|tonight|now|again|home|to} = comes back|gets back\n"
        + "<be> returning {$|here|there|soon|later|tomorrow|tonight|now|again|home|to} = coming back|getting back\n"
        + "increase {the|its|my|your|our|their|this|that} = raise|bump up\n"
        + "decrease {the|its|my|your|our|their|this|that} = lower|bring down|cut\n"
        + "reduce {the|its|my|your|our|their|this|that} = lower|bring down|cut\n"
        + "remain {$|here|there|calm|quiet|silent|in|at|with|for|until|inside|outside|home|still} = stay\n"
        + "<he|she|it|who|that|which|everything|everyone|everybody|nobody|this> remains {$|here|there|calm|quiet|silent|in|at|with|for|until|inside|outside|home|still} = stays\n"
        + "<subj> remained {$|here|there|calm|quiet|silent|in|at|with|for|until|inside|outside|home|still} = stayed\n"
        + "<be|a|an> rather {-than|-a|-an|-the|-more|-less|-like|-be} = pretty|fairly\n"
        + "<be> somewhat {-of|-than|-more|-less|-the|-a|-an} = a bit|kind of|fairly|pretty\n"
        + "incidentally, = by the way,|on a side note,\n"
        + "\n"
        + "# ---- phrasal verbs y colocaciones\n"
        + "continue with = carry on with|get on with\n"
        + "continue the = carry on with the|get on with the\n"
        + "continue this = carry on with this|get on with this\n"
        + "continue that = carry on with that|get on with that\n"
        + "continue our = carry on with our|get on with our\n"
        + "continue your = carry on with your|get on with your\n"
        + "continue my = carry on with my|get on with my\n"
        + "continue his = carry on with his\n"
        + "continue her = carry on with her\n"
        + "continue their = carry on with their|get on with their\n"
        + "continue {$|later|now|then|tomorrow|again|here|there|after|afterwards} = carry on|keep going\n"
        + "<subj> continued {$|later|now|then|tomorrow|again|here|there|after|afterwards} = carried on|kept going\n"
        + "arrive {$|here|there|later|late|soon|early|first|on time|tomorrow|today|tonight|again|now|too} = turn up|show up\n"
        + "arrives {$|here|there|later|late|soon|early|first|on time|tomorrow|today|tonight|again|now|too} = turns up|shows up\n"
        + "<subj> arrived {$|here|there|later|late|soon|early|first|on time|tomorrow|today|tonight|again|now|too} = turned up|showed up\n"
        + "<has|have|had|i've|you've|we've|they've|he's|she's|it's|who's|that's|been> arrived {$|here|there|later|late|soon|early|first|on time|tomorrow|today|tonight|again|now|too} = turned up|shown up\n"
        + "arriving {$|here|there|later|late|soon|early|first|on time|tomorrow|today|tonight|again|now|too} = turning up|showing up\n"
        + "invent {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = come up with|come up with|make up|think up\n"
        + "<subj> invented {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = came up with|came up with|made up|thought up\n"
        + "inventing {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = coming up with|making up|thinking up\n"
        + "can not tolerate = can't stand|can't put up with|can't take|can't bear\n"
        + "will not tolerate = won't put up with|won't stand for|won't take\n"
        + "tolerate = put up with\n"
        + "tolerated = put up with\n"
        + "tolerates = puts up with\n"
        + "tolerating = putting up with\n"
        + "postpone {the|this|that|our|my|your|their|until|till|for|a|an} = put off|push back\n"
        + "postponed {the|this|that|our|my|your|their|until|till|for|a|an} = put off|pushed back\n"
        + "discover that = find out that|learn that\n"
        + "discovered that = found out that|learned that\n"
        + "discover {a|an|some} = come across|stumble across|stumble upon\n"
        + "<subj> discovered {a|an|some} = came across|stumbled across|stumbled upon\n"
        + "obtain {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = get|get hold of|come by\n"
        + "<subj> obtained {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = got|got hold of|came by\n"
        + "purchase {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = buy|pick up|grab\n"
        + "purchased {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = bought|picked up|grabbed\n"
        + "purchasing {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = buying|picking up|grabbing\n"
        + "acquire {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = get hold of|pick up\n"
        + "acquired {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = got hold of|picked up\n"
        + "require = need\n"
        + "requires = needs\n"
        + "required {-to} = needed\n"
        + "requiring = needing\n"
        + "commence = kick off|start\n"
        + "commenced = kicked off|started\n"
        + "commences = kicks off|starts\n"
        + "commencing = kicking off|starting\n"
        + "inform me = let me know\n"
        + "inform you = let you know\n"
        + "inform him = let him know\n"
        + "inform her = let her know\n"
        + "inform us = let us know\n"
        + "inform them = let them know\n"
        + "informed me = told me\n"
        + "informed you = told you\n"
        + "<-det> attempt to = try to\n"
        + "attempted to = tried to\n"
        + "attempting to = trying to\n"
        + "demonstrate that = show that|prove that\n"
        + "demonstrated that = showed that|proved that\n"
        + "indicate that = show that|suggest that\n"
        + "indicates that = shows that|suggests that\n"
        + "utilize {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = use\n"
        + "utilise {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = use\n"
        + "utilized {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = used\n"
        + "select {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = pick|choose|go for\n"
        + "<subj> selected {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = picked|chose|went for\n"
        + "locate {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = find|track down\n"
        + "modify {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = change|tweak\n"
        + "modified {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = changed|tweaked\n"
        + "construct {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = build|put together\n"
        + "constructed {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = built|put together\n"
        + "examine {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = check out|take a look at\n"
        + "<subj> examined {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = checked out|took a look at\n"
        + "verify that = check that|double-check that\n"
        + "verify {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = check|double-check\n"
        + "verified {a|an|the|this|that|these|those|it|some|my|your|his|her|their|our|any|one|another|several|many|more|two|three} = checked|double-checked\n"
        + "provide you with = give you|get you|set you up with\n"
        + "assist = help\n"
        + "assistance = help\n";
    private static final String TABLA_2 = "enter the {room|house|building|arena|lobby|maze|shop|store|club|cafe|park|hotel|castle|cave|portal|bar|pub|library} = go into the|head into the|step into the\n"
        + "enter my {room|house|building|arena|lobby|maze|shop|store|club|cafe|park|hotel|castle|cave|portal|bar|pub|library} = go into my|head into my|step into my\n"
        + "enter this {room|house|building|arena|lobby|maze|shop|store|club|cafe|park|hotel|castle|cave|portal|bar|pub|library} = go into this|head into this|step into this\n"
        + "\n"
        + "# ---- continue + gerundio\n"
        + "continue playing = carry on playing|keep playing\n"
        + "continue talking = carry on talking|keep talking\n"
        + "continue working = carry on working|keep working\n"
        + "continue trying = carry on trying|keep trying\n"
        + "continue waiting = carry on waiting|keep waiting\n"
        + "continue doing = carry on doing|keep doing\n"
        + "continue going = carry on going|keep going\n"
        + "continue walking = carry on walking|keep walking\n"
        + "continue building = carry on building|keep building\n"
        + "continue fighting = carry on fighting|keep fighting\n"
        + "continue looking = carry on looking|keep looking\n"
        + "continue searching = carry on searching|keep searching\n"
        + "continue chatting = carry on chatting|keep chatting\n"
        + "continue learning = carry on learning|keep learning\n"
        + "continue practicing = carry on practicing|keep practicing\n"
        + "continue practising = carry on practising|keep practising\n"
        + "continue training = carry on training|keep training\n"
        + "<subj> continued playing = carried on playing|kept playing\n"
        + "<subj> continued talking = carried on talking|kept talking\n"
        + "<subj> continued working = carried on working|kept working\n"
        + "<subj> continued trying = carried on trying|kept trying\n"
        + "<subj> continued waiting = carried on waiting|kept waiting\n"
        + "<subj> continued doing = carried on doing|kept doing\n"
        + "<subj> continued going = carried on going|kept going\n"
        + "<subj> continued walking = carried on walking|kept walking\n"
        + "<subj> continued building = carried on building|kept building\n"
        + "<subj> continued fighting = carried on fighting|kept fighting\n"
        + "<subj> continued looking = carried on looking|kept looking\n"
        + "<subj> continued searching = carried on searching|kept searching\n"
        + "<subj> continued chatting = carried on chatting|kept chatting\n"
        + "<subj> continued learning = carried on learning|kept learning\n"
        + "<subj> continued practicing = carried on practicing|kept practicing\n"
        + "<subj> continued practising = carried on practising|kept practising\n"
        + "<subj> continued training = carried on training|kept training\n"
        + "\n"
        + "# ---- cancelar\n"
        + "cancel the {game|match|trade|swap|deal|event|party|meeting|plan|plans|race|round|contest|tournament} = call off the\n"
        + "cancel this {game|match|trade|swap|deal|event|party|meeting|plan|plans|race|round|contest|tournament} = call off this\n"
        + "cancel our {game|match|trade|swap|deal|event|party|meeting|plan|plans|race|round|contest|tournament} = call off our\n"
        + "cancel my {game|match|trade|swap|deal|event|party|meeting|plan|plans|race|round|contest|tournament} = call off my\n"
        + "cancel your {game|match|trade|swap|deal|event|party|meeting|plan|plans|race|round|contest|tournament} = call off your\n"
        + "\n"
        + "# ---- conectores y marcadores del discurso\n"
        + "in order to = to\n"
        + "prior to = before\n"
        + "due to the fact that = because|since|seeing as\n"
        + "in spite of {-the fact} = despite\n"
        + "with regard to = about|^as for|^when it comes to\n"
        + "regarding {the|this|that|your|my|his|her|their|our|these|those|a|an} = about|^as for|^when it comes to\n"
        + "concerning {the|this|that|your|my|his|her|their|our|these|those|a|an} = about|^as for\n"
        + "in any case, = anyway,|anyhow,\n"
        + "in any case {-of} = anyway|anyhow\n"
        + "at any rate, = anyway,|anyhow,\n"
        + "^however, = that said,|even so,|still,\n"
        + ", however, = , but|, that said,|, even so,\n"
        + "^nevertheless, = even so,|still,|all the same,\n"
        + "^nonetheless, = even so,|still,|all the same,\n"
        + "^nevertheless = even so|still|all the same\n"
        + "^nonetheless = even so|still|all the same\n"
        + "^therefore, = so|that's why|which is why\n"
        + ", therefore, = , so|, which is why\n"
        + "^therefore = so|that's why|which is why\n"
        + "^consequently, = so|that's why\n"
        + "^consequently = so|that's why|which is why\n"
        + "^thus, = so|that's why\n"
        + "^thus {-far} = so\n"
        + "^furthermore, = what's more,|on top of that,|besides,\n"
        + "^moreover, = what's more,|on top of that,|besides,\n"
        + "^in addition, = on top of that,|plus,|besides,\n"
        + "^additionally, = on top of that,|plus,|also,\n"
        + "^for this reason, = that's why|which is why\n"
        + "^for that reason, = that's why|which is why\n"
        + "^fortunately, = luckily,|thankfully,|as luck would have it,\n"
        + "fortunately = luckily|thankfully\n"
        + "<^|disc> to be honest, = honestly,|if i'm honest,|truth be told,\n"
        + "<^|disc> to be honest {-with} = honestly|if i'm honest|truth be told\n"
        + "<^|disc> to tell the truth, = truth be told,|honestly,\n"
        + "<^|disc> to tell the truth {-about|-to} = truth be told|honestly\n"
        + "<^|disc> the truth is {,|that|i|it|you|we|they|he|she|there|this} = truth is|the thing is|the fact is\n"
        + "<^|disc> the problem is {,|that|i|it|you|we|they|he|she|there|this} = the thing is|the catch is|the trouble is\n"
        + "<^|disc> the only problem is {,|that|i|it|you|we|they|he|she|there|this} = the only catch is|the only snag is\n"
        + "approximately = roughly|around|about\n"
        + "additional = extra\n"
        + "<-det> sufficient = enough\n"
        + "<-det> numerous {-$} = loads of|plenty of|countless\n"
        + "a large number of = loads of|plenty of|a ton of\n"
        + "a great number of = loads of|plenty of|a ton of\n"
        + "a great deal of = a lot of|loads of|a ton of\n"
        + "a number of = several|a few|a bunch of\n"
        + "of course not = absolutely not|definitely not|certainly not\n"
        + "^yes, of course {$} = absolutely|yes, absolutely|for sure|yeah, for sure|definitely|sure thing\n"
        + "^of course you can {$} = go for it|be my guest|sure thing\n"
        + "^^of course {$} = absolutely|for sure|definitely|sure thing\n"
        + "^certainly {$} = absolutely|definitely|sure|sure thing\n"
        + "^by all means {$} = be my guest|please do|go right ahead\n"
        + "without a doubt = no doubt|definitely|beyond a doubt\n"
        + "without doubt = no doubt|definitely\n"
        + "\n"
        + "# ---- a little + adjetivo\n"
        + "a little confused {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit confused|a tad confused|a touch confused|slightly confused\n"
        + "a little nervous {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit nervous|a tad nervous|a touch nervous|slightly nervous\n"
        + "a little tired {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit tired|a tad tired|a touch tired|slightly tired\n"
        + "a little worried {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit worried|a tad worried|a touch worried|slightly worried\n"
        + "a little late {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit late|a tad late|a touch late|slightly late\n"
        + "a little early {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit early|a tad early|a touch early|slightly early\n"
        + "a little slow {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit slow|a tad slow|a touch slow|slightly slow\n"
        + "a little scared {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit scared|a tad scared|a touch scared|slightly scared\n"
        + "a little sad {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit sad|a tad sad|a touch sad|slightly sad\n"
        + "a little upset {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit upset|a tad upset|a touch upset|slightly upset\n"
        + "a little lost {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit lost|a tad lost|a touch lost|slightly lost\n"
        + "a little bored {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit bored|a tad bored|a touch bored|slightly bored\n"
        + "a little busy {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit busy|a tad busy|a touch busy|slightly busy\n"
        + "a little annoyed {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit annoyed|a tad annoyed|a touch annoyed|slightly annoyed\n"
        + "a little shy {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit shy|a tad shy|a touch shy|slightly shy\n"
        + "a little expensive {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit expensive|a tad expensive|a touch expensive|slightly expensive\n"
        + "a little strange {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit strange|a tad strange|a touch strange|slightly strange\n"
        + "a little weird {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit weird|a tad weird|a touch weird|slightly weird\n"
        + "a little odd {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit odd|a tad odd|a touch odd|slightly odd\n"
        + "a little different {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit different|a tad different|a touch different|slightly different\n"
        + "a little difficult {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit difficult|a tad difficult|a touch difficult|slightly difficult\n"
        + "a little complicated {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit complicated|a tad complicated|a touch complicated|slightly complicated\n"
        + "a little anxious {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit anxious|a tad anxious|a touch anxious|slightly anxious\n"
        + "a little sleepy {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit sleepy|a tad sleepy|a touch sleepy|slightly sleepy\n"
        + "a little hungry {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit hungry|a tad hungry|a touch hungry|slightly hungry\n"
        + "a little cold {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit cold|a tad cold|a touch cold|slightly cold\n"
        + "a little hot {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit hot|a tad hot|a touch hot|slightly hot\n"
        + "a little dizzy {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit dizzy|a tad dizzy|a touch dizzy|slightly dizzy\n"
        + "a little jealous {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit jealous|a tad jealous|a touch jealous|slightly jealous\n"
        + "a little disappointed {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit disappointed|a tad disappointed|a touch disappointed|slightly disappointed\n"
        + "a little embarrassed {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit embarrassed|a tad embarrassed|a touch embarrassed|slightly embarrassed\n"
        + "a little afraid {$|and|but|for|to|today|tonight|now|here|there|too|about|at|in|on|of|with|from|so|because|when|if|after|before|since|than|compared|right|lately} = a bit afraid|a tad afraid|a touch afraid|slightly afraid\n"
        + "a little more = a bit more|a touch more|a tad more\n"
        + "a little less = a bit less|a touch less|a tad less\n"
        + "a little earlier = a bit earlier|a touch earlier|a tad earlier\n"
        + "a little later = a bit later|a touch later|a tad later\n"
        + "a little faster = a bit faster|a touch faster|a tad faster\n"
        + "a little slower = a bit slower|a touch slower|a tad slower\n"
        + "a little bigger = a bit bigger|a touch bigger|a tad bigger\n"
        + "a little smaller = a bit smaller|a touch smaller|a tad smaller\n"
        + "a little harder = a bit harder|a touch harder|a tad harder\n"
        + "a little easier = a bit easier|a touch easier|a tad easier\n"
        + "a little higher = a bit higher|a touch higher|a tad higher\n"
        + "a little lower = a bit lower|a touch lower|a tad lower\n"
        + "a little cheaper = a bit cheaper|a touch cheaper|a tad cheaper\n"
        + "a little closer = a bit closer|a touch closer|a tad closer\n"
        + "a little louder = a bit louder|a touch louder|a tad louder\n"
        + "a little quieter = a bit quieter|a touch quieter|a tad quieter\n"
        + "a little better = a bit better|a touch better|a tad better\n"
        + "a little worse = a bit worse|a touch worse|a tad worse\n"
        + "a little bit of = a bit of\n"
        + "a little bit {$} = a bit\n"
        + "a little bit {-of|-$} = a bit|a touch|a tad\n"
        + "\n"
        + "# ---- very + adjetivo\n"
        + "<-neg> very good {-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = brilliant|great|excellent\n"
        + "<-neg|-q> really good {-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = brilliant|great|excellent\n"
        + "<-neg|-^|-disc> so good {-that|-a|-an|-the|-one|-ones|-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = brilliant|great|excellent\n"
        + "<-neg> extremely good {-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = brilliant|great|excellent\n"
        + "<-neg> very bad = awful|terrible|dreadful\n"
        + "<-neg|-q> really bad = awful|terrible|dreadful\n"
        + "<-neg|-^|-disc> so bad {-that|-a|-an|-the|-one|-ones} = awful|terrible|dreadful\n"
        + "<-neg> extremely bad = awful|terrible|dreadful\n"
        + "<-neg> very big = huge|massive|enormous\n"
        + "<-neg|-q> really big = huge|massive|enormous\n"
        + "<-neg|-^|-disc> so big {-that|-a|-an|-the|-one|-ones} = huge|massive|enormous\n"
        + "<-neg> extremely big = huge|massive|enormous\n"
        + "<-neg> very large = huge|massive\n"
        + "<-neg|-q> really large = huge|massive\n"
        + "<-neg|-^|-disc> so large {-that|-a|-an|-the|-one|-ones} = huge|massive\n"
        + "<-neg> extremely large = huge|massive\n"
        + "<-neg> very small = tiny|minuscule\n"
        + "<-neg|-q> really small = tiny|minuscule\n"
        + "<-neg|-^|-disc> so small {-that|-a|-an|-the|-one|-ones} = tiny|minuscule\n"
        + "<-neg> extremely small = tiny|minuscule\n"
        + "<-neg> very expensive = really pricey|seriously expensive|eye-wateringly expensive\n"
        + "<-neg|-q> really expensive = really pricey|seriously expensive|eye-wateringly expensive\n"
        + "<-neg|-^|-disc> so expensive {-that|-a|-an|-the|-one|-ones} = really pricey|seriously expensive|eye-wateringly expensive\n"
        + "<-neg> extremely expensive = really pricey|seriously expensive|eye-wateringly expensive\n"
        + "<-neg> very cheap = dirt cheap\n"
        + "<-neg|-q> really cheap = dirt cheap\n"
        + "<-neg|-^|-disc> so cheap {-that|-a|-an|-the|-one|-ones} = dirt cheap\n"
        + "<-neg> extremely cheap = dirt cheap\n"
        + "<-neg> very easy = dead easy|super easy|ridiculously easy\n"
        + "<-neg|-q> really easy = dead easy|super easy|ridiculously easy\n"
        + "<-neg|-^|-disc> so easy {-that|-a|-an|-the|-one|-ones} = dead easy|super easy|ridiculously easy\n"
        + "<-neg> extremely easy = dead easy|super easy|ridiculously easy\n"
        + "<-neg> very difficult {-to} = really tough|seriously hard|brutal\n"
        + "<-neg|-q> really difficult {-to} = really tough|seriously hard|brutal\n"
        + "<-neg|-^|-disc> so difficult {-that|-a|-an|-the|-one|-ones|-to} = really tough|seriously hard|brutal\n"
        + "<-neg> extremely difficult {-to} = really tough|seriously hard|brutal\n"
        + "<-neg> very difficult to = really tough to|seriously hard to|really hard to\n"
        + "<-neg|-q> really difficult to = really tough to|seriously hard to|really hard to\n"
        + "<-neg|-^|-disc> so difficult to = really tough to|seriously hard to|really hard to\n"
        + "<-neg> extremely difficult to = really tough to|seriously hard to|really hard to\n"
        + "<-neg|-det> very tired {-of} = exhausted|worn out|wiped out|shattered\n"
        + "<-neg|-q|-det> really tired {-of} = exhausted|worn out|wiped out|shattered\n"
        + "<-neg|-^|-disc|-det> so tired {-that|-a|-an|-the|-one|-ones|-of} = exhausted|worn out|wiped out|shattered\n"
        + "<-neg|-det> extremely tired {-of} = exhausted|worn out|wiped out|shattered\n"
        + "<-neg> very angry = furious|livid|fuming\n"
        + "<-neg|-q> really angry = furious|livid|fuming\n"
        + "<-neg|-^|-disc> so angry {-that|-a|-an|-the|-one|-ones} = furious|livid|fuming\n"
        + "<-neg> extremely angry = furious|livid|fuming\n"
        + "<-neg> very scared = terrified|petrified\n"
        + "<-neg|-q> really scared = terrified|petrified\n"
        + "<-neg|-^|-disc> so scared {-that|-a|-an|-the|-one|-ones} = terrified|petrified\n"
        + "<-neg> extremely scared = terrified|petrified\n"
        + "<-neg> very afraid = terrified|petrified\n"
        + "<-neg|-q> really afraid = terrified|petrified\n"
        + "<-neg|-^|-disc> so afraid {-that|-a|-an|-the|-one|-ones} = terrified|petrified\n"
        + "<-neg> extremely afraid = terrified|petrified\n"
        + "<-neg> very scary = terrifying|properly scary|spine-chilling\n"
        + "<-neg|-q> really scary = terrifying|properly scary|spine-chilling\n"
        + "<-neg|-^|-disc> so scary {-that|-a|-an|-the|-one|-ones} = terrifying|properly scary|spine-chilling\n"
        + "<-neg> extremely scary = terrifying|properly scary|spine-chilling\n"
        + "<-neg> very funny = hilarious|hysterical\n"
        + "<-neg|-q> really funny = hilarious|hysterical\n"
        + "<-neg|-^|-disc> so funny {-that|-a|-an|-the|-one|-ones} = hilarious|hysterical\n"
        + "<-neg> extremely funny = hilarious|hysterical\n"
        + "<-neg> very interesting = fascinating|intriguing\n"
        + "<-neg|-q> really interesting = fascinating|intriguing\n"
        + "<-neg|-^|-disc> so interesting {-that|-a|-an|-the|-one|-ones} = fascinating|intriguing\n"
        + "<-neg> extremely interesting = fascinating|intriguing\n"
        + "<-neg> very important = crucial|vital\n"
        + "<-neg|-q> really important = crucial|vital\n"
        + "<-neg|-^|-disc> so important {-that|-a|-an|-the|-one|-ones} = crucial|vital\n"
        + "<-neg> extremely important = crucial|vital\n"
        + "<-neg> very beautiful = gorgeous|stunning|breathtaking\n"
        + "<-neg|-q> really beautiful = gorgeous|stunning|breathtaking\n"
        + "<-neg|-^|-disc> so beautiful {-that|-a|-an|-the|-one|-ones} = gorgeous|stunning|breathtaking\n"
        + "<-neg> extremely beautiful = gorgeous|stunning|breathtaking\n"
        + "<-neg> very pretty {$|and|but|for|to|at|in|on|with} = gorgeous|lovely|stunning\n"
        + "<-neg|-q> really pretty {$|and|but|for|to|at|in|on|with} = gorgeous|lovely|stunning\n"
        + "<-neg|-^|-disc> so pretty {$|and|but|for|to|at|in|on|with} = gorgeous|lovely|stunning\n"
        + "<-neg> extremely pretty {$|and|but|for|to|at|in|on|with} = gorgeous|lovely|stunning\n"
        + "<-neg> very nice = lovely|wonderful|great\n"
        + "<-neg|-q> really nice = lovely|wonderful|great\n"
        + "<-neg|-^|-disc> so nice {-that|-a|-an|-the|-one|-ones} = lovely|wonderful|great\n"
        + "<-neg> extremely nice = lovely|wonderful|great\n"
        + "<-neg> very cool = awesome|brilliant|super cool\n"
        + "<-neg|-q> really cool = awesome|brilliant|super cool\n"
        + "<-neg|-^|-disc> so cool {-that|-a|-an|-the|-one|-ones} = awesome|brilliant|super cool\n"
        + "<-neg> extremely cool = awesome|brilliant|super cool\n"
        + "<-neg> very fast = super fast|blazing fast|lightning fast\n"
        + "<-neg|-q> really fast = super fast|blazing fast|lightning fast\n"
        + "<-neg|-^|-disc> so fast {-that|-a|-an|-the|-one|-ones} = super fast|blazing fast|lightning fast\n"
        + "<-neg> extremely fast = super fast|blazing fast|lightning fast\n"
        + "<-neg> very hungry = starving|famished|ravenous\n"
        + "<-neg|-q> really hungry = starving|famished|ravenous\n"
        + "<-neg|-^|-disc> so hungry {-that|-a|-an|-the|-one|-ones} = starving|famished|ravenous\n"
        + "<-neg> extremely hungry = starving|famished|ravenous\n"
        + "<-neg> very cold = freezing|bitterly cold\n"
        + "<-neg|-q> really cold = freezing|bitterly cold\n"
        + "<-neg|-^|-disc> so cold {-that|-a|-an|-the|-one|-ones} = freezing|bitterly cold\n"
        + "<-neg> extremely cold = freezing|bitterly cold\n"
        + "<-neg> very hot {$|today|here|there|now|outside|inside|in|out|and|but|tonight|so|too|for} = boiling|scorching\n"
        + "<-neg|-q> really hot {$|today|here|there|now|outside|inside|in|out|and|but|tonight|so|too|for} = boiling|scorching\n"
        + "<-neg|-^|-disc> so hot {$|today|here|there|now|outside|inside|in|out|and|but|tonight|so|too|for} = boiling|scorching\n"
        + "<-neg> extremely hot {$|today|here|there|now|outside|inside|in|out|and|but|tonight|so|too|for} = boiling|scorching\n"
        + "<-neg> very dirty = filthy\n"
        + "<-neg|-q> really dirty = filthy\n"
        + "<-neg|-^|-disc> so dirty {-that|-a|-an|-the|-one|-ones} = filthy\n"
        + "<-neg> extremely dirty = filthy\n"
        + "<-neg> very clean = spotless\n"
        + "<-neg|-q> really clean = spotless\n"
        + "<-neg|-^|-disc> so clean {-that|-a|-an|-the|-one|-ones} = spotless\n"
        + "<-neg> extremely clean = spotless\n"
        + "<-neg|-det> very wet = soaked\n"
        + "<-neg|-q|-det> really wet = soaked\n"
        + "<-neg|-^|-disc|-det> so wet {-that|-a|-an|-the|-one|-ones} = soaked\n"
        + "<-neg|-det> extremely wet = soaked\n"
        + "<-neg|-det> very surprised = stunned|shocked|taken aback\n"
        + "<-neg|-q|-det> really surprised = stunned|shocked|taken aback\n"
        + "<-neg|-^|-disc|-det> so surprised {-that|-a|-an|-the|-one|-ones} = stunned|shocked|taken aback\n"
        + "<-neg|-det> extremely surprised = stunned|shocked|taken aback\n"
        + "<-neg> very confused = baffled|bewildered\n"
        + "<-neg|-q> really confused = baffled|bewildered\n"
        + "<-neg|-^|-disc> so confused {-that|-a|-an|-the|-one|-ones} = baffled|bewildered\n"
        + "<-neg> extremely confused = baffled|bewildered\n"
        + "<-neg|-det> very worried = worried sick\n"
        + "<-neg|-q|-det> really worried = worried sick\n"
        + "<-neg|-^|-disc|-det> so worried {-that|-a|-an|-the|-one|-ones} = worried sick\n"
        + "<-neg|-det> extremely worried = worried sick\n"
        + "<-neg> very excited = thrilled|buzzing|pumped\n"
        + "<-neg|-q> really excited = thrilled|buzzing|pumped\n"
        + "<-neg|-^|-disc> so excited {-that|-a|-an|-the|-one|-ones} = thrilled|buzzing|pumped\n"
        + "<-neg> extremely excited = thrilled|buzzing|pumped\n"
        + "\n"
        + "# ---- seccion picante (solo vale con picante = si)\n"
        + "[picante]\n"
        + "<-neg> very good {-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = bloody good|damn good|bloody brilliant\n"
        + "<-neg|-q> really good {-luck|-friend|-friends|-morning|-afternoon|-evening|-night|-bye|-for} = bloody good|damn good\n"
        + "<-neg> very bad = bloody awful|damn awful|bloody terrible\n"
        + "<-neg> very difficult {-to} = bloody difficult|damn hard\n"
        + "<-neg> very cold = bloody freezing\n"
        + "<-neg> very hot {$|today|here|there|now|outside|inside|in|out|and|but|tonight|so|too|for} = bloody boiling\n"
        + "<-neg|-det> very tired {-of} = bloody knackered|damn tired\n"
        + "<-neg> very funny = bloody hilarious\n"
        + "<-neg> very expensive = bloody expensive|damn pricey\n"
        + "i do not care {$} = i don't give a damn|i couldn't care less\n"
        + "what the heck = what the hell|what in the world\n";
    private static final String POR_DEFECTO = juntar(TABLA_0, TABLA_1, TABLA_2);

    /** Para probar: si no es null, se usa este archivo en vez de Memoria/ingles_c2.txt. */
    private static volatile File archivoPrueba;

    private static final int MAX_TEXTO = 3000;
    private static final int MAX_REGLAS = 3000;
    private static final int MAX_MASCARAS = 200;
    private static final int PASADAS = 5;
    /** Limite de caracteres de un mensaje de chat de Habbo: el servidor descarta o recorta lo que pasa de aqui. */
    private static final int MAX_CHAT = 100;

    /** Limite de caracteres del chat de Habbo (100), para que el integrador reutilice la constante. */
    static int maxChat() {
        return MAX_CHAT;
    }

    private static final int MODO_NORMAL = 0;
    private static final int MODO_CORTO = 1;
    private static final int MODO_SIN_REGLAS = 2;
    /** Primer caracter privado de las mascaras de importes (30c, 3.5k, 1,000): el comodin # solo casa con estos. */
    private static final int BASE_IMPORTE = 0xE100;
    private static final String IMPORTE_RE = "[\\uE100-\\uE1FF](?:\\s+(?:credits?|coins?|diamonds?|duckets?|bc|hc|cr|c|k|dollars?|euros?|pounds?|usd|gold)"
            + "(?![\\p{L}\\p{N}_'\u2019\\-]))?";
    private static final Pattern IMPORTE = Pattern.compile("[$\u20ac\u00a3]?\\p{N}+(?:[.,]\\p{N}+)*(?:[kKmMcC]|[bB][cC]|[hH][cC]|[cC][rR])?");
    /** Comando del chat: ":ip", ":wave", "!help", "/w". Un emoticono (":D", ":P", ":v", ":)") NO es un comando. */
    private static final Pattern COMANDO = Pattern.compile("^(?:[:!/]\\p{L}{2,}|[!/]\\p{L})(?![\\p{L}\\p{N}])");
    private static final Pattern EMOTICONO_LETRAS = Pattern.compile("^:([DdPpOoSsVvCcXx3])\\1*(?![\\p{L}\\p{N}])");

    private static final String LETRA = "\\p{L}\\p{N}_'\u2019\\-";
    private static final String PALABRA_COMODIN = "[\\p{L}\\p{N}'\u2019\\-\\uE000-\\uF8FF]+";
    private static final String FIN_CLAUSULA = "(?:\\s*$|\\s*[.,!;:\u2026\u2014\u2013)\\]\\uF000-\\uF0FF])";
    private static final String NEGACION = "not|never|no|without|hardly|barely|scarcely|nobody|nothing|none|neither|nor|nowhere|cannot|"
            + "doubt|doubts|doubtful|unlikely|rarely|seldom|ain't|isn't|aren't|wasn't|weren't|don't|doesn't|didn't|"
            + "can't|won't|wouldn't|couldn't|shouldn't|haven't|hasn't|hadn't|mustn't|mightn't|needn't";
    /** Palabras que cierran la clausula al mirar hacia atras buscando una negacion. */
    private static final String FRONTERA_NEGACION = "but|because|although|though|while|whereas|however|since";
    private static final int ALCANCE_NEGACION = 10;
    /** Atajos para los guards <...>: marcadores del discurso, determinantes, copulas, lo que precede a un verbo, sujetos. */
    private static final String MACRO_DISC = "but|and|so|well|oh|ah|aw|wow|hey|yes|yeah|yep|no|nope|ok|okay|then|now|also|honestly|actually|"
            + "sadly|hmm|um|uh|anyway|plus|sorry|oops";
    private static final String MACRO_DET = "a|an|the|my|your|his|her|its|our|their|this|that|these|those|some|any|no|every|each|another|"
            + "such|what|which|whose|more|less|both|all|many|few|several|other|one";
    private static final String MACRO_BE = "am|is|are|was|were|be|been|being|i'm|you're|he's|she's|it's|we're|they're|that's|there's|here's|"
            + "who's|what's|isn't|aren't|wasn't|weren't|feel|feels|felt|feeling|look|looks|looked|looking|seem|seems|seemed|get|gets|got|"
            + "getting|become|becomes|became|stay|stays|stayed|sound|sounds|sounded";
    private static final String MACRO_VB = "i|you|we|they|he|she|it|who|that|which|to|will|would|can|could|should|must|may|might|shall|"
            + "please|just|then|also|always|never|not|soon|and|but|so|let's|don't|doesn't|didn't|won't|can't|couldn't|wouldn't|shouldn't|"
            + "i'll|you'll|we'll|they'll|he'll|she'll|it'll|i'd|you'd|we'd|they'd|he'd|she'd|everyone|everybody|nobody|someone|somebody|people";
    private static final String MACRO_SUBJ = "i|you|we|they|he|she|it|who|that|which|everyone|everybody|nobody|someone|somebody|people|and|"
            + "but|then|just|finally|also|so";
    /** "let us" solo se contrae a "let's" cuando es una sugerencia (let's play), no un permiso (let us pass). */
    private static final Set<String> LETUS_SUGERENCIA = new HashSet<String>(Arrays.asList(("play|start|begin|go|try|do|get|make|meet|talk|"
            + "move|continue|stop|celebrate|party|run|race|dance|build|trade|swap|win|check|look|take|find|hurry|rest|eat|order|buy|"
            + "sell|vote|decide|fight|duel|battle|hide|seek|compete|restart|reset|pick|choose|forget|focus|keep|enjoy|celebrate|"
            + "chat|hang|relax|head|call|ask|set|plan|practice|practise|train|invite|give|show|put|use").split("\\|")));
    private static final Set<String> LETUS_BLOQUEA = new HashSet<String>(Arrays.asList(("please|just|to|if|you|they|will|would|can|could|"
            + "never|not|don't|do|who|he|she|it|i|we|nobody|someone|somebody|everyone|and not").split("\\|")));
    /** Primeras palabras demasiado comunes para detectar muletillas repetidas. */
    private static final Set<String> PALABRAS_COMUNES = new HashSet<String>(Arrays.asList(("that's|you're|you've|i'll|i've|we're|we'll|"
            + "they're|there's|here's|what's|who's|let's|don't|can't|won't|i'm|it's|he's|she's|how's|doesn't|didn't|isn't|wasn't|"
            + "couldn't|wouldn't|shouldn't|haven't|hasn't|hadn't|aren't|weren't|with|what|when|where|which|that|this|have|just|really|"
            + "very|much|some|about|from|your|more|than|then|would|could|should|there|their|they|been|were|will|into|also|only").split("\\|")));

    /** {forma completa, forma contraida (minuscula)} */
    private static final String[][] CONTRAIBLES = {
        {"i am", "i'm"}, {"you are", "you're"}, {"we are", "we're"}, {"they are", "they're"},
        {"he is", "he's"}, {"she is", "she's"}, {"it is", "it's"}, {"that is", "that's"}, {"what is", "what's"},
        {"there is", "there's"}, {"here is", "here's"}, {"who is", "who's"}, {"where is", "where's"}, {"how is", "how's"},
        {"let us", "let's"}, {"i will", "i'll"}, {"you will", "you'll"}, {"we will", "we'll"}, {"they will", "they'll"},
        {"he will", "he'll"}, {"she will", "she'll"}, {"it will", "it'll"}, {"i would", "i'd"}, {"you would", "you'd"},
        {"we would", "we'd"}, {"they would", "they'd"}, {"he would", "he'd"}, {"she would", "she'd"},
        {"i have", "i've"}, {"you have", "you've"}, {"we have", "we've"}, {"they have", "they've"},
        {"do not", "don't"}, {"does not", "doesn't"}, {"did not", "didn't"}, {"is not", "isn't"}, {"are not", "aren't"},
        {"was not", "wasn't"}, {"were not", "weren't"}, {"would not", "wouldn't"}, {"should not", "shouldn't"},
        {"could not", "couldn't"}, {"have not", "haven't"}, {"has not", "hasn't"}, {"had not", "hadn't"},
        {"will not", "won't"}, {"can not", "can't"}, {"can not", "cannot"}
    };
    /** Contraccion -> expansion, para normalizar las reglas escritas ya contraidas. */
    private static final String[][] EXPANSIONES = {
        {"won't", "will not"}, {"can't", "can not"}, {"cannot", "can not"}, {"don't", "do not"}, {"doesn't", "does not"},
        {"didn't", "did not"}, {"isn't", "is not"}, {"aren't", "are not"}, {"wasn't", "was not"}, {"weren't", "were not"},
        {"wouldn't", "would not"}, {"shouldn't", "should not"}, {"couldn't", "could not"}, {"haven't", "have not"},
        {"hasn't", "has not"}, {"hadn't", "had not"}, {"i'm", "i am"}, {"you're", "you are"}, {"we're", "we are"},
        {"they're", "they are"}, {"he's", "he is"}, {"she's", "she is"}, {"it's", "it is"}, {"that's", "that is"},
        {"what's", "what is"}, {"there's", "there is"}, {"here's", "here is"}, {"who's", "who is"}, {"where's", "where is"},
        {"how's", "how is"}, {"let's", "let us"}, {"i'll", "i will"}, {"you'll", "you will"}, {"we'll", "we will"},
        {"they'll", "they will"}, {"he'll", "he will"}, {"she'll", "she will"}, {"it'll", "it will"}, {"i'd", "i would"},
        {"you'd", "you would"}, {"we'd", "we would"}, {"they'd", "they would"}, {"he'd", "he would"}, {"she'd", "she would"},
        {"i've", "i have"}, {"you've", "you have"}, {"we've", "we have"}, {"they've", "they have"}
    };
    private static final Map<String, String[]> POR_BIGRAMA = new HashMap<String, String[]>();
    private static final Map<String, String> POR_CONTRAIDA = new HashMap<String, String>();

    /** Contracciones que se aplican al final a todo el texto (la forma completa suena a libro). */
    private static final Pattern CONTRAER_SIEMPRE = Pattern.compile(
        "(?<![\\p{L}\\p{N}_'\u2019\\-])(do\\s+not|does\\s+not|did\\s+not|is\\s+not|are\\s+not|was\\s+not|were\\s+not|"
        + "would\\s+not|should\\s+not|could\\s+not|have\\s+not|has\\s+not|had\\s+not|will\\s+not|can\\s+not|cannot)"
        + "(?![\\p{L}\\p{N}_'\u2019\\-])", Pattern.CASE_INSENSITIVE);
    /** Contracciones que solo valen si despues viene otra palabra ("that is." no se contrae). */
    private static final Pattern CONTRAER_CON_SIGUIENTE = Pattern.compile(
        "(?<![\\p{L}\\p{N}_'\u2019\\-])(i\\s+am|you\\s+are|we\\s+are|they\\s+are|he\\s+is|she\\s+is|it\\s+is|that\\s+is|"
        + "what\\s+is|there\\s+is|here\\s+is|who\\s+is|where\\s+is|how\\s+is|i\\s+will|you\\s+will|we\\s+will|they\\s+will|"
        + "he\\s+will|she\\s+will|it\\s+will|i\\s+would|you\\s+would|we\\s+would|they\\s+would|he\\s+would|she\\s+would|"
        + "(?:it|he|she|that|there|what|who)\\s+has(?=\\s+been\\b)|"
        + "let\\s+us)"
        + "(?![\\p{L}\\p{N}_'\u2019\\-])(?=\\s+[\\p{L}\\p{N}\\uE000-\\uE7FF])", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRAER_HAVE = Pattern.compile(
        "(?<![\\p{L}\\p{N}_'\u2019\\-])((?:i|you|we|they)\\s+have)(?![\\p{L}\\p{N}_'\u2019\\-])"
        + "(?=\\s+(?:been|got|never|just|already|always|ever|seen|done|heard|tried|played|won|lost|finished|decided|found|"
        + "forgotten|made|had|become|gotten|not)\\b)", Pattern.CASE_INSENSITIVE);
    /** Lo que nunca se toca: se sustituye por un caracter privado y se devuelve igual al final. */
    private static final Pattern PROTEGIDO = Pattern.compile(
        "(?<url>(?:https?://|ftp://|www\\.)[^\\s<>\"]*[^\\s<>\"'.,;:!?)\\]]"
        + "|(?<![\\p{L}\\p{N}_@.-])[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*\\.(?:com|net|org|es|io|gg|me|tv|ly|co|xyz|info|app|dev|gl|us|uk|ar|mx|cl|pe|de|fr|ru|br)(?![\\p{L}\\p{N}])(?:/[^\\s<>\"]*[^\\s<>\"'.,;:!?)\\]])?)"
        + "|(?<mail>[\\p{L}\\p{N}_.+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+)"
        + "|(?<nombre>(?<![\\p{L}\\p{N}_])[@#][\\p{L}\\p{N}_](?:[\\p{L}\\p{N}_.\\-]*[\\p{L}\\p{N}_])?)"
        + "|(?<cita>\"[^\"\\n]+\"|\u201c[^\u201d\\n]+\u201d|\u00ab[^\u00bb\\n]+\u00bb|(?<![\\p{L}\\p{N}])'[^'\\n]+'(?![\\p{L}\\p{N}]))"
        + "|(?<cara>(?<![\\p{L}\\p{N}])(?:[:;=8xX][\\-^o']?[()DdPpOo3/\\\\|*$@sSvVcC>]+|<3+|[xX][dD]+|[\\^\\-oOTt][_.][\\^\\-oOTt]|\\(:)(?![\\p{L}\\p{N}]))"
        + "|(?<cmd>(?:^|(?<=[\\s(\\[]))[:!/][\\p{L}][\\p{L}\\p{N}_]*)"
        + "|(?<num>(?<![\\p{L}\\p{N}_])[$\u20ac\u00a3]?[\\p{L}\\p{N}_]*[\\p{N}_][\\p{L}\\p{N}_]*(?:[.,:/][\\p{N}][\\p{L}\\p{N}_]*)*)");

    private static final Object CANDADO = new Object();
    private static volatile Estado estado;
    private static volatile String estadoRuta;
    private static volatile long estadoFecha = -2L;
    private static volatile long estadoLargo = -2L;
    private static volatile long ultimoIntentoEscribir;
    private static volatile Estado estadoPorDefecto;
    private static volatile List<String> avisos = Collections.emptyList();

    static {
        for (String[] c : CONTRAIBLES) {
            String[] anterior = POR_BIGRAMA.get(c[0]);
            if (anterior == null) {
                POR_BIGRAMA.put(c[0], new String[] {c[1]});
            } else {
                String[] nuevo = Arrays.copyOf(anterior, anterior.length + 1);
                nuevo[anterior.length] = c[1];
                POR_BIGRAMA.put(c[0], nuevo);
            }
        }
        for (String[] e : EXPANSIONES) {
            POR_CONTRAIDA.put(e[0], e[1]);
        }
    }

    private NivelC2() {
    }

    // ------------------------------------------------------------------ archivo

    static File archivo() {
        File prueba = archivoPrueba;
        if (prueba != null) {
            return prueba;
        }
        File base = Diagnostics.directory().getParentFile();
        File memoria = base == null ? new File("Memoria") : new File(base, "Memoria");
        memoria.mkdirs();
        return new File(memoria, "ingles_c2.txt");
    }

    /** Avisos de la ultima carga (reglas descartadas); solo para pruebas. */
    static List<String> avisos() {
        return avisos;
    }

    // ------------------------------------------------------------------ reglas

    /** Una alternativa de la parte derecha; "soloInicio" (prefijo ^) = solo vale si la frase empieza una clausula (fragmentos sin sujeto). */
    private static final class Alt {
        final String texto;
        final boolean soloInicio;

        Alt(String texto, boolean soloInicio) {
            this.texto = texto;
            this.soloInicio = soloInicio;
        }
    }

    private static final class Regla {
        String clave;
        String nucleo;
        boolean inicio;
        boolean estricto;
        String previo;
        String siguiente;
        int comodines;
        List<Alt> alternativas = new ArrayList<Alt>();
        int orden;
        Pattern propio;
        // guard de la palabra anterior <...> (se evalua en Java, no con regex)
        boolean previoNegativo;
        boolean previoCaret;
        Set<String> previoSet = new HashSet<String>();
        boolean sinNeg;
        boolean conNeg;
        boolean sinPregunta;
        boolean conPregunta;
        boolean libres() {
            for (Alt a : alternativas) {
                if (!a.soloInicio) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Estado {
        boolean activo = true;
        boolean estiloChat = true;
        boolean picante;
        Regla[] reglas = new Regla[0];
        Map<String, Regla[]> indice = new HashMap<String, Regla[]>();
        Regla[] comodin = new Regla[0];
    }

    private static boolean si(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("s") || v.equals("1") || v.equals("on") || v.startsWith("y") || v.equals("true");
    }

    private static Estado estadoPorDefecto() {
        Estado e = estadoPorDefecto;
        if (e == null) {
            synchronized (CANDADO) {
                e = estadoPorDefecto;
                if (e == null) {
                    List<String> av = new ArrayList<String>();
                    e = analizar(POR_DEFECTO, av);
                    estadoPorDefecto = e;
                    if (estado == null) {
                        avisos = av;
                    }
                }
            }
        }
        return e;
    }

    /** Devuelve el estado vigente, recargando el archivo si cambio (o creandolo si falta). */
    private static Estado cargar() {
        File f;
        try {
            f = archivo();
        } catch (Throwable t) {
            return estado != null ? estado : estadoPorDefecto();
        }
        String ruta = f.getAbsolutePath();
        long fecha = f.lastModified();
        long largo = f.length();
        Estado actual = estado;
        if (actual != null && fecha == estadoFecha && largo == estadoLargo && ruta.equals(estadoRuta)) {
            return actual;
        }
        synchronized (CANDADO) {
            actual = estado;
            if (actual != null && fecha == estadoFecha && largo == estadoLargo && ruta.equals(estadoRuta)) {
                return actual;
            }
            try {
                if (!f.isFile()) {
                    long ahora = System.currentTimeMillis();
                    if (ahora - ultimoIntentoEscribir > 30000L) {
                        ultimoIntentoEscribir = ahora;
                        try {
                            File padre = f.getAbsoluteFile().getParentFile();
                            if (padre != null) {
                                padre.mkdirs();
                            }
                            Files.write(f.toPath(), POR_DEFECTO.getBytes(StandardCharsets.UTF_8));
                        } catch (Throwable t) {
                            // carpeta de solo lectura: se sigue con las reglas por defecto
                        }
                    }
                }
                if (!f.isFile()) {
                    estado = null;
                    estadoFecha = -2L;
                    estadoLargo = -2L;
                    estadoRuta = ruta;
                    return estadoPorDefecto();
                }
                fecha = f.lastModified();
                largo = f.length();
                String texto = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                List<String> av = new ArrayList<String>();
                Estado nuevo = analizar(texto, av);
                avisos = av;
                estado = nuevo;
                estadoFecha = fecha;
                estadoLargo = largo;
                estadoRuta = ruta;
                return nuevo;
            } catch (Throwable t) {
                // archivo ilegible: se conserva lo ultimo que funcionaba (o las reglas por defecto)
                return actual != null ? actual : estadoPorDefecto();
            }
        }
    }

    private static Estado analizar(String texto, List<String> av) {
        Estado e = new Estado();
        Map<String, Regla> base = new LinkedHashMap<String, Regla>();
        Map<String, Regla> picantes = new LinkedHashMap<String, Regla>();
        boolean enPicante = false;
        boolean primera = true;
        for (String linea : texto.split("\\r?\\n")) {
            if (primera) {
                primera = false;
                if (linea.startsWith("\uFEFF")) {
                    linea = linea.substring(1);
                }
            }
            linea = linea.trim();
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }
            if (linea.startsWith("[") && linea.endsWith("]")) {
                enPicante = linea.substring(1, linea.length() - 1).trim().equalsIgnoreCase("picante");
                continue;
            }
            int igual = linea.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            String izq = linea.substring(0, igual).trim();
            String der = linea.substring(igual + 1).trim();
            String ajuste = izq.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
            if (!enPicante && (ajuste.equals("activo") || ajuste.equals("estilo_chat") || ajuste.equals("picante"))) {
                boolean v = si(der);
                if (ajuste.equals("activo")) {
                    e.activo = v;
                } else if (ajuste.equals("estilo_chat")) {
                    e.estiloChat = v;
                } else {
                    e.picante = v;
                }
                continue;
            }
            Regla r = leerRegla(izq, der, av);
            if (r != null) {
                (enPicante ? picantes : base).put(r.clave, r);
            }
        }
        Map<String, Regla> todas = new LinkedHashMap<String, Regla>(base);
        if (e.picante) {
            todas.putAll(picantes);
        }
        List<Regla> lista = new ArrayList<Regla>(todas.values());
        if (lista.size() > MAX_REGLAS) {
            lista = new ArrayList<Regla>(lista.subList(0, MAX_REGLAS));
            av.add("demasiadas reglas: se usan solo las " + MAX_REGLAS + " primeras");
        }
        final Map<Regla, Integer> orden = new HashMap<Regla, Integer>();
        for (int i = 0; i < lista.size(); i++) {
            orden.put(lista.get(i), i);
        }
        Collections.sort(lista, new Comparator<Regla>() {
            @Override
            public int compare(Regla a, Regla b) {
                if (a.nucleo.length() != b.nucleo.length()) {
                    return b.nucleo.length() - a.nucleo.length();
                }
                int ea = (a.inicio ? 1 : 0) + (a.previo != null ? 1 : 0) + (a.siguiente != null ? 1 : 0);
                int eb = (b.inicio ? 1 : 0) + (b.previo != null ? 1 : 0) + (b.siguiente != null ? 1 : 0);
                if (ea != eb) {
                    return eb - ea;
                }
                return orden.get(a) - orden.get(b);
            }
        });
        // cada regla se compila sola; las que no compilan se descartan sin afectar al resto
        List<Regla> validas = new ArrayList<Regla>();
        for (Regla r : lista) {
            try {
                r.propio = Pattern.compile(construir(r), Pattern.CASE_INSENSITIVE);
                validas.add(r);
            } catch (Throwable t) {
                av.add("regla descartada (no compila): " + r.clave);
            }
        }
        indexar(e, validas);
        if (e.reglas.length > 0) {
            descartarAutoencajes(e, av);
        }
        return e;
    }

    /** Agrupa las reglas por la primera palabra de cada forma (completa o contraida) para no probarlas todas en cada sitio. */
    private static void indexar(Estado e, List<Regla> reglas) {
        Map<String, List<Regla>> tmp = new HashMap<String, List<Regla>>();
        int orden = 0;
        for (Regla r : reglas) {
            r.orden = orden++;
            for (String forma : variantes(r.nucleo)) {
                String clave = claveInicial(forma);
                List<Regla> l = tmp.get(clave);
                if (l == null) {
                    l = new ArrayList<Regla>();
                    tmp.put(clave, l);
                }
                if (!l.contains(r)) {
                    l.add(r);
                }
            }
        }
        List<Regla> comodines = tmp.containsKey("*") ? tmp.get("*") : new ArrayList<Regla>();
        Map<String, Regla[]> indice = new HashMap<String, Regla[]>();
        for (Map.Entry<String, List<Regla>> en : tmp.entrySet()) {
            List<Regla> l = new ArrayList<Regla>(en.getValue());
            if (!en.getKey().equals("*") && Character.isLetter(en.getKey().charAt(0))) {
                for (Regla c : comodines) {
                    if (!l.contains(c)) {
                        l.add(c);
                    }
                }
            }
            Collections.sort(l, new Comparator<Regla>() {
                @Override
                public int compare(Regla a, Regla b) {
                    return a.orden - b.orden;
                }
            });
            indice.put(en.getKey(), l.toArray(new Regla[0]));
        }
        e.reglas = reglas.toArray(new Regla[0]);
        e.indice = indice;
        e.comodin = comodines.toArray(new Regla[0]);
    }

    /** Clave de busqueda de una forma: sus primeras letras en minuscula, "*" si empieza por comodin o el primer signo. */
    private static String claveInicial(String forma) {
        char c = forma.charAt(0);
        if (c == '*') {
            return "*";
        }
        if (!Character.isLetter(c)) {
            return String.valueOf(c);
        }
        int j = 0;
        while (j < forma.length() && Character.isLetter(forma.charAt(j))) {
            j++;
        }
        return forma.substring(0, j).toLowerCase(Locale.ROOT);
    }

    private static boolean pegadoAPalabra(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\'' || c == '\u2019' || c == '-';
    }

    /** Primera regla que encaja a partir de "desde" (la mas prioritaria en el primer sitio donde alguna encaje). */
    private static Regla buscar(Estado e, String s, int desde, Matcher[] salida) {
        int n = s.length();
        for (int i = desde; i < n; i++) {
            char c = s.charAt(i);
            Regla[] candidatas;
            if (Character.isLetter(c)) {
                if (i > 0 && pegadoAPalabra(s.charAt(i - 1))) {
                    continue;
                }
                int j = i;
                while (j < n && Character.isLetter(s.charAt(j))) {
                    j++;
                }
                candidatas = e.indice.get(s.substring(i, j).toLowerCase(Locale.ROOT));
                if (candidatas == null) {
                    candidatas = e.comodin;
                }
            } else if (Character.isDigit(c)) {
                continue;
            } else {
                candidatas = e.indice.get(String.valueOf(c));
            }
            if (candidatas == null) {
                continue;
            }
            for (Regla r : candidatas) {
                Matcher m = r.propio.matcher(s);
                m.region(i, n);
                m.useTransparentBounds(true);
                m.useAnchoringBounds(false);
                if (m.lookingAt() && guardsOk(r, s, i, m.end())) {
                    salida[0] = m;
                    return r;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ guards evaluados en Java

    private static boolean esBlanco(char c) {
        return Character.isWhitespace(c);
    }

    /** Palabra (minuscula, apostrofo recto) que va justo antes de "pos" separada por espacios; null si no hay. */
    private static String palabraPrevia(String s, int pos) {
        int j = pos;
        while (j > 0 && esBlanco(s.charAt(j - 1))) {
            j--;
        }
        if (j == pos || j == 0) {
            return null;
        }
        int k = j;
        while (k > 0 && pegadoAPalabra(s.charAt(k - 1))) {
            k--;
        }
        if (k == j) {
            return null;
        }
        return s.substring(k, j).toLowerCase(Locale.ROOT).replace('\u2019', '\'');
    }

    /** true si en "pos" empieza una frase o clausula (principio, tras . ! ? ; : , o salto de linea o un emoticono). */
    private static boolean esInicioClausula(String s, int pos) {
        int i = pos - 1;
        while (i >= 0 && esBlanco(s.charAt(i))) {
            if (s.charAt(i) == '\n') {
                return true;
            }
            i--;
        }
        if (i < 0) {
            return true;
        }
        char c = s.charAt(i);
        return ".!?;:,\u2026\u2014\u2013".indexOf(c) >= 0 || (c >= '\uF000' && c <= '\uF0FF');
    }

    /** Inicio de clausula, o justo despues de un marcador del discurso (but, and, so, well, ok...). */
    private static boolean esInicioAmplio(String s, int pos) {
        if (esInicioClausula(s, pos)) {
            return true;
        }
        String pw = palabraPrevia(s, pos);
        return pw != null && DISC.contains(pw);
    }

    private static final Set<String> DISC = new HashSet<String>(Arrays.asList(MACRO_DISC.split("\\|")));
    private static final Set<String> NEG_PALABRAS = new HashSet<String>(Arrays.asList(NEGACION.split("\\|")));
    private static final Set<String> FRONTERA = new HashSet<String>(Arrays.asList(FRONTERA_NEGACION.split("\\|")));

    private static boolean esNegacion(String w) {
        return NEG_PALABRAS.contains(w) || w.endsWith("n't") || w.equals("no");
    }

    /** Hay una negacion en la misma clausula antes de "pos" (hasta ALCANCE_NEGACION palabras, sin cruzar , . ; : ! ? ni but/because...). */
    private static boolean negadoEnClausula(String s, int pos) {
        int i = pos;
        int palabras = 0;
        while (i > 0 && palabras < ALCANCE_NEGACION) {
            int j = i;
            while (j > 0 && esBlanco(s.charAt(j - 1))) {
                if (s.charAt(j - 1) == '\n') {
                    return false;
                }
                j--;
            }
            if (j == 0) {
                return false;
            }
            char c = s.charAt(j - 1);
            if (".,;:!?\u2026\u2014\u2013".indexOf(c) >= 0 || (c >= '\uF000' && c <= '\uF0FF')) {
                return false;
            }
            int k = j;
            while (k > 0 && pegadoAPalabra(s.charAt(k - 1))) {
                k--;
            }
            if (k == j) {
                i = j - 1;
                continue;
            }
            String w = s.substring(k, j).toLowerCase(Locale.ROOT).replace('\u2019', '\'');
            if (esNegacion(w)) {
                return true;
            }
            if (FRONTERA.contains(w)) {
                return false;
            }
            palabras++;
            i = k;
        }
        return false;
    }

    /** La frase que contiene "pos" termina en "?". */
    private static boolean esPregunta(String s, int pos) {
        for (int i = pos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '?') {
                return true;
            }
            if (c == '.' || c == '!' || c == '\u2026' || c == '\n') {
                return false;
            }
        }
        return false;
    }

    private static boolean guardsOk(Regla r, String s, int ini, int fin) {
        if (r.previoCaret || !r.previoSet.isEmpty()) {
            String pw = palabraPrevia(s, ini);
            boolean acierta = (pw != null && r.previoSet.contains(pw)) || (r.previoCaret && esInicioClausula(s, ini));
            if (acierta == r.previoNegativo) {
                return false;
            }
        }
        if ((r.sinNeg || r.conNeg) && negadoEnClausula(s, ini) == r.sinNeg) {
            return false;
        }
        if ((r.sinPregunta || r.conPregunta) && esPregunta(s, fin) == r.sinPregunta) {
            return false;
        }
        if (!r.libres() && !esInicioAmplio(s, ini)) {
            return false;
        }
        return true;
    }

    private static boolean esLetraONumero(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static Regla leerRegla(String izq, String der, List<String> av) {
        String s = izq.trim();
        Regla r = new Regla();
        if (s.startsWith("^^")) {
            r.inicio = true;
            r.estricto = true;
            s = s.substring(2).trim();
        } else if (s.startsWith("^")) {
            r.inicio = true;
            s = s.substring(1).trim();
        }
        if (s.startsWith("<")) {
            int fin = s.indexOf('>');
            if (fin < 0) {
                av.add("regla mal escrita: " + izq);
                return null;
            }
            r.previo = s.substring(1, fin).trim();
            s = s.substring(fin + 1).trim();
            leerPrevio(r);
        }
        if (s.endsWith("}")) {
            int ini = s.lastIndexOf('{');
            if (ini < 0) {
                av.add("regla mal escrita: " + izq);
                return null;
            }
            r.siguiente = s.substring(ini + 1, s.length() - 1).trim();
            s = s.substring(0, ini).trim();
        } else if (s.endsWith("$")) {
            r.siguiente = "$";
            s = s.substring(0, s.length() - 1).trim();
        }
        s = s.toLowerCase(Locale.ROOT).replace('\u2019', '\'').replaceAll("\\s+", " ").trim();
        if (s.isEmpty() || s.length() > 80 || !s.matches("(?s).*\\p{L}.*")) {
            av.add("regla ignorada (parte izquierda vacia o muy larga): " + izq);
            return null;
        }
        s = expandir(s);
        r.nucleo = s;
        for (String t : s.split(" ")) {
            if (t.equals("*") || t.equals("#")) {
                r.comodines++;
            }
        }
        if (r.comodines > 3) {
            av.add("regla ignorada (mas de 3 comodines): " + izq);
            return null;
        }
        if (s.startsWith("# ") || s.equals("#")) {
            av.add("regla ignorada (no puede empezar por #): " + izq);
            return null;
        }
        for (String alt : der.split("\\|")) {
            alt = alt.trim().replaceAll("\\s+", " ");
            boolean soloInicio = false;
            if (alt.startsWith("^")) {
                soloInicio = true;
                alt = alt.substring(1).trim();
            }
            if (alt.isEmpty() || alt.length() > 200) {
                continue;
            }
            alt = alt.replaceAll("(?<![\\p{L}\\p{N}'\u2019])i(?=\\s|['\u2019]|[,.!?;:]|$)", "I");
            r.alternativas.add(new Alt(alt, soloInicio));
        }
        if (r.alternativas.isEmpty()) {
            return null;
        }
        r.clave = (r.inicio ? (r.estricto ? "^^" : "^") : "") + (r.previo != null ? "<" + r.previo + ">" : "") + s
            + (r.siguiente != null ? "{" + r.siguiente + "}" : "");
        return r;
    }

    /**
     * Guard <...>: palabras (la palabra anterior es una de ellas; con "-" delante, NO es ninguna), y atajos:
     * neg = hay una negacion en la misma clausula, q = la frase es una pregunta, ^ = inicio de clausula,
     * disc / det / be / vb / subj = listas de palabras (marcadores, determinantes, copulas, lo que precede a un verbo, sujetos).
     */
    private static void leerPrevio(Regla r) {
        String spec = r.previo;
        r.previoNegativo = spec.startsWith("-");
        for (String p : spec.split("[|\\s]+")) {
            p = p.trim().replaceFirst("^-", "").toLowerCase(Locale.ROOT).replace('\u2019', '\'');
            if (p.isEmpty()) {
                continue;
            }
            if (p.equals("neg")) {
                if (r.previoNegativo) {
                    r.sinNeg = true;
                } else {
                    r.conNeg = true;
                }
            } else if (p.equals("q")) {
                if (r.previoNegativo) {
                    r.sinPregunta = true;
                } else {
                    r.conPregunta = true;
                }
            } else if (p.equals("^")) {
                r.previoCaret = true;
            } else if (p.equals("disc")) {
                r.previoSet.addAll(Arrays.asList(MACRO_DISC.split("\\|")));
            } else if (p.equals("det")) {
                r.previoSet.addAll(Arrays.asList(MACRO_DET.split("\\|")));
            } else if (p.equals("be")) {
                r.previoSet.addAll(Arrays.asList(MACRO_BE.split("\\|")));
            } else if (p.equals("vb")) {
                r.previoSet.addAll(Arrays.asList(MACRO_VB.split("\\|")));
            } else if (p.equals("subj")) {
                r.previoSet.addAll(Arrays.asList(MACRO_SUBJ.split("\\|")));
            } else {
                r.previoSet.add(p);
            }
        }
    }

    /** "don't know" y "do not know" son la misma regla: se guarda la forma completa. */
    private static String expandir(String s) {
        StringBuilder sb = new StringBuilder();
        for (String t : s.split(" ")) {
            String e = POR_CONTRAIDA.get(t);
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(e != null ? e : t);
        }
        return sb.toString();
    }

    private static String quitarAcento(String palabra) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < palabra.length(); i++) {
            char c = palabra.charAt(i);
            if (c == '\'') {
                sb.append("['\u2019]");
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    /** Todas las formas (completa / contraida) en que puede aparecer el nucleo en el texto. */
    private static List<String> variantes(String nucleo) {
        String[] t = nucleo.split(" ");
        List<String> salida = new ArrayList<String>();
        generar(t, 0, new ArrayList<String>(), salida);
        return salida;
    }

    private static void generar(String[] t, int i, List<String> acum, List<String> salida) {
        if (salida.size() >= 16) {
            return;
        }
        if (i >= t.length) {
            salida.add(unir(acum));
            return;
        }
        acum.add(t[i]);
        generar(t, i + 1, acum, salida);
        acum.remove(acum.size() - 1);
        if (i + 1 < t.length) {
            String[] formas = POR_BIGRAMA.get(t[i] + " " + t[i + 1]);
            if (formas != null) {
                for (String forma : formas) {
                    acum.add(forma);
                    generar(t, i + 2, acum, salida);
                    acum.remove(acum.size() - 1);
                }
            }
        }
    }

    private static String unir(List<String> palabras) {
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private static String regexNucleo(String nucleo) {
        List<String> formas = variantes(nucleo);
        StringBuilder alt = new StringBuilder();
        for (String forma : formas) {
            StringBuilder sb = new StringBuilder();
            for (String p : forma.split(" ")) {
                if (sb.length() > 0) {
                    sb.append("\\s+");
                }
                if (p.equals("*")) {
                    sb.append("(").append("(?:" + PALABRA_COMODIN + "\\s+){0,2}?" + PALABRA_COMODIN + ")");
                } else if (p.equals("#")) {
                    sb.append("(").append(IMPORTE_RE).append(")");
                } else {
                    sb.append(quitarAcento(p));
                }
            }
            alt.append(alt.length() > 0 ? "|" : "").append(sb);
        }
        return "(?:" + alt + ")";
    }

    private static String listaPalabras(String spec) {
        List<String> palabras = new ArrayList<String>();
        boolean fin = false;
        boolean interrogante = false;
        boolean terminal = false;
        boolean coma = false;
        for (String p : spec.split("\\|")) {
            p = p.trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'');
            if (p.isEmpty()) {
                continue;
            }
            if (p.equals("$")) {
                fin = true;
            } else if (p.equals("?")) {
                interrogante = true;
            } else if (p.equals(".")) {
                terminal = true;
            } else if (p.equals(",")) {
                coma = true;
            } else if (p.equals("neg")) {
                palabras.addAll(Arrays.asList(NEGACION.split("\\|")));
            } else {
                palabras.add(p);
            }
        }
        List<String> trozos = new ArrayList<String>();
        if (fin) {
            trozos.add(FIN_CLAUSULA);
        }
        if (interrogante) {
            trozos.add("\\s*\\?");
        }
        if (terminal) {
            trozos.add("(?:\\s*$|\\s*[.!\u2026\\uF000-\\uF0FF])");
        }
        if (coma) {
            trozos.add("\\s*[,;:\u2014\u2013]");
        }
        if (!palabras.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String p : palabras) {
                StringBuilder w = new StringBuilder();
                for (String parte : p.split(" ")) {
                    if (w.length() > 0) {
                        w.append("\\s+");
                    }
                    w.append(quitarAcento(parte));
                }
                sb.append(sb.length() > 0 ? "|" : "").append(w);
            }
            trozos.add("\\s+(?:" + sb + ")(?![\\p{L}\\p{N}_\\-])");
        }
        StringBuilder u = new StringBuilder();
        for (String t : trozos) {
            u.append(u.length() > 0 ? "|" : "").append(t);
        }
        return "(?:" + u + ")";
    }

    private static String construir(Regla r) {
        StringBuilder sb = new StringBuilder();
        String nucleo = r.nucleo;
        boolean empiezaLetra = esLetraONumero(nucleo.charAt(0)) || nucleo.charAt(0) == '*';
        char ultimo = nucleo.charAt(nucleo.length() - 1);
        boolean terminaLetra = esLetraONumero(ultimo) || ultimo == '\'' || ultimo == '*' || ultimo == '#';
        if (empiezaLetra) {
            sb.append("(?<![" + LETRA + "])");
        }
        if (r.inicio) {
            sb.append(r.estricto ? "(?:^|(?<=[.!?\u2026]\\s{1,3})|(?<=\\n))"
                : "(?:^|(?<=[.!?\u2026;:,]\\s{1,3})|(?<=[\\uF000-\\uF0FF]\\s{1,3})|(?<=\\n))");
        }
        sb.append(regexNucleo(nucleo));
        if (terminaLetra) {
            sb.append("(?![" + LETRA + "])");
        }
        if (r.siguiente != null) {
            boolean negativo = r.siguiente.startsWith("-");
            String spec = negativo ? r.siguiente.substring(1) : r.siguiente;
            if (negativo) {
                spec = spec.replace("|-", "|");
            }
            sb.append(negativo ? "(?!" : "(?=").append(listaPalabras(spec)).append(")");
        }
        return sb.toString();
    }

    /** Contextos en los que se prueba cada alternativa: sola y detras de palabras que podrian formar otra frase ("so good", "very good"). */
    private static final String[] CONTEXTOS_PREVIOS = {"", "so ", "very ", "really ", "extremely ", "a ", "the ", "to ", "not "};

    /** true si alguna regla casa solapando el trozo [ini, fin) de "prueba". */
    private static boolean reescribible(Estado e, String prueba, int ini, int fin) {
        int desde = 0;
        Matcher[] sal = new Matcher[1];
        while (desde < prueba.length()) {
            Regla r = buscar(e, prueba, desde, sal);
            if (r == null) {
                return false;
            }
            Matcher m = sal[0];
            if (m.end() > ini && m.start() < fin) {
                return true;
            }
            desde = m.start() + 1;
        }
        return false;
    }

    /**
     * Quita las alternativas que otra regla volveria a reescribir (evita bucles y cascadas): se prueba la alternativa sola
     * y detras de so / very / really / a / the / to / not, que es donde dos reglas se pisan ("so" + "good luck").
     */
    private static void descartarAutoencajes(Estado e, List<String> av) {
        for (Regla r : e.reglas) {
            List<Alt> buenas = new ArrayList<Alt>();
            for (Alt alt : r.alternativas) {
                String prueba = alt.texto.replaceAll("\\$[1-3]", "thing");
                boolean encaja = false;
                try {
                    for (String pre : CONTEXTOS_PREVIOS) {
                        String t = pre + prueba;
                        if (reescribible(e, t, pre.length(), t.length())) {
                            encaja = true;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    encaja = true;
                }
                if (encaja) {
                    av.add("alternativa descartada porque otra regla la reescribiria: '" + alt.texto + "' (en '" + r.clave + "')");
                } else {
                    buenas.add(alt);
                }
            }
            r.alternativas = buenas;
        }
        List<Regla> utiles = new ArrayList<Regla>();
        for (Regla r : e.reglas) {
            if (!r.alternativas.isEmpty()) {
                utiles.add(r);
            }
        }
        if (utiles.size() != e.reglas.length) {
            indexar(e, utiles);
        }
    }

    // ------------------------------------------------------------------ aplicar

    private static String juntar(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            sb.append(p);
        }
        return sb.toString();
    }

    /** Un mensaje que empieza por un comando (":ip 0,0", "!help", "/w") no se toca; un emoticono (":D", ":P", ":v") si se procesa. */
    private static boolean esComando(String t) {
        if (t.length() < 2) {
            return false;
        }
        char c = t.charAt(0);
        if (c != ':' && c != '!' && c != '/') {
            return false;
        }
        if (c == ':' && EMOTICONO_LETRAS.matcher(t).find()) {
            return false;
        }
        return COMANDO.matcher(t).find();
    }

    static String aplicar(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return texto;
        }
        try {
            if (texto.length() > MAX_TEXTO) {
                return texto;
            }
            Estado e = cargar();
            if (e == null || !e.activo) {
                return texto;
            }
            String t = texto.trim();
            if (esComando(t)) {
                return texto; // comando del chat: no se toca
            }
            // el chat de Habbo corta a MAX_CHAT caracteres: si el original cabia, el resultado tiene que caber tambien
            int limite = Math.max(MAX_CHAT, t.length());
            String r = convergir(e, texto, MODO_NORMAL);
            if (r.length() > limite) {
                r = convergir(e, texto, MODO_CORTO);
            }
            if (r.length() > limite) {
                r = convergir(e, texto, MODO_SIN_REGLAS);
                if (r.length() > limite || r.length() > texto.length()) {
                    return texto;
                }
            }
            return r;
        } catch (Throwable t) {
            return texto;
        }
    }

    /** Repite pasadas hasta un punto fijo (devuelve el original si no converge o algo falla). */
    private static String convergir(Estado e, String texto, int modo) {
        String actual = texto;
        Random azar = new Random(texto.hashCode());
        for (int i = 0; i < PASADAS; i++) {
            String siguiente = pasada(e, actual, azar, modo);
            if (siguiente == null) {
                return texto;
            }
            if (siguiente.equals(actual)) {
                return actual;
            }
            actual = siguiente;
            azar = new Random(actual.hashCode());
        }
        return texto; // no converge: mejor no tocar nada
    }

    private static String pasada(Estado e, String texto, Random azar, int modo) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') {
                return null;
            }
        }
        List<String> guardados = new ArrayList<String>();
        String m = enmascarar(texto, guardados);
        if (m == null) {
            return null;
        }
        if (e.reglas.length > 0 && modo != MODO_SIN_REGLAS) {
            m = reglas(e, m, azar, modo == MODO_CORTO);
        }
        m = contraer(m);
        m = m.replaceAll(",(\\s*,)+", ",");
        if (e.estiloChat) {
            m = estiloChat(m);
        }
        String r = desenmascarar(m, guardados);
        return r.trim().isEmpty() ? null : r;
    }

    private static String enmascarar(String s, List<String> guardados) {
        Matcher m = PROTEGIDO.matcher(s);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (m.find()) {
            if (m.end() == m.start()) {
                continue;
            }
            boolean suave = m.start("cara") >= 0 || m.start("nombre") >= 0 || m.start("url") >= 0 || m.start("mail") >= 0;
            String grupo = m.group();
            if (m.start("num") >= 0 && !tieneDigitoOGuion(grupo)) {
                continue;
            }
            if (guardados.size() >= MAX_MASCARAS) {
                return null;
            }
            sb.append(s, pos, m.start());
            int n = guardados.size();
            int base = suave ? 0xF000 : (m.start("num") >= 0 && IMPORTE.matcher(grupo).matches() ? BASE_IMPORTE : 0xE000);
            sb.append((char) (base + n));
            guardados.add(grupo);
            pos = m.end();
        }
        sb.append(s, pos, s.length());
        return sb.toString();
    }

    private static boolean tieneDigitoOGuion(String g) {
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (Character.isDigit(c) || c == '_') {
                return true;
            }
        }
        return false;
    }

    private static String desenmascarar(String s, List<String> guardados) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xE000 && c < 0xE000 + MAX_MASCARAS) {
                sb.append(guardados.get(c - 0xE000));
            } else if (c >= BASE_IMPORTE && c < BASE_IMPORTE + MAX_MASCARAS) {
                sb.append(guardados.get(c - BASE_IMPORTE));
            } else if (c >= 0xF000 && c < 0xF000 + MAX_MASCARAS) {
                sb.append(guardados.get(c - 0xF000));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final Pattern ARTICULO_CAPTURA = Pattern.compile("(?<![\\p{L}\\p{N}'\u2019])(a|an)(\\s+)\\$([1-3])", Pattern.CASE_INSENSITIVE);

    /** Alternativa con $1..$3 sustituidos; "a $1" / "an $1" se corrigen segun la palabra capturada ("a orange lamp" -> "an orange lamp"). */
    private static String sustituir(String alt, Regla r, Matcher m) {
        if (r.comodines == 0) {
            return alt;
        }
        int variantes = m.groupCount() / r.comodines;
        String[] caps = new String[4];
        for (int k = 1; k <= r.comodines; k++) {
            String cap = null;
            for (int v = 0; v < variantes && cap == null; v++) {
                cap = m.group(v * r.comodines + k);
            }
            caps[k] = cap == null ? "" : cap;
        }
        Matcher am = ARTICULO_CAPTURA.matcher(alt);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (am.find()) {
            int k = Integer.parseInt(am.group(3));
            String cap = k <= r.comodines ? caps[k] : "";
            String primera = cap.split("[^\\p{L}']")[0];
            sb.append(alt, pos, am.start());
            String art = am.group(1);
            if (!primera.isEmpty() && Character.isLetter(cap.charAt(0))) {
                String nuevo = suenaAVocal(primera) ? "an" : "a";
                art = Character.isUpperCase(art.charAt(0)) ? Character.toUpperCase(nuevo.charAt(0)) + nuevo.substring(1) : nuevo;
            }
            sb.append(art).append(am.group(2)).append("$").append(k);
            pos = am.end();
        }
        sb.append(alt, pos, alt.length());
        alt = sb.toString();
        for (int k = 1; k <= r.comodines; k++) {
            alt = alt.replace("$" + k, caps[k]);
        }
        return alt;
    }

    /** Primeras dos palabras de una alternativa (minusculas): sirven para no repetir la misma muletilla en un mensaje. */
    private static String firma(String alt) {
        String[] p = alt.toLowerCase(Locale.ROOT).replace('\u2019', '\'').split("[^\\p{L}\\p{N}']+");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String w : p) {
            if (w.isEmpty()) {
                continue;
            }
            sb.append(sb.length() > 0 ? " " : "").append(w);
            if (++n == 2) {
                break;
            }
        }
        return sb.toString();
    }

    private static boolean contienePalabra(CharSequence texto, String palabra) {
        Matcher mm = Pattern.compile("(?<![\\p{L}\\p{N}_'\u2019\\-])" + Pattern.quote(palabra) + "(?![\\p{L}\\p{N}_'\u2019\\-])",
            Pattern.CASE_INSENSITIVE).matcher(texto);
        return mm.find();
    }

    /** La alternativa repite una muletilla que ya esta en el mensaje (la de otra regla o una palabra suelta del texto). */
    private static boolean repetida(String alt, Set<String> firmas, CharSequence antes, String despues) {
        String f = firma(alt);
        if (f.isEmpty()) {
            return false;
        }
        if (firmas.contains(f)) {
            return true;
        }
        String primera = f.split(" ")[0];
        if (primera.length() >= 4 && !PALABRAS_COMUNES.contains(primera)) {
            return contienePalabra(antes, primera) || contienePalabra(despues, primera);
        }
        return false;
    }

    private static String reglas(Estado e, String s, Random azar, boolean corto) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        Matcher[] salida = new Matcher[1];
        Set<String> firmas = new HashSet<String>();
        while (pos < s.length()) {
            Regla r = buscar(e, s, pos, salida);
            if (r == null) {
                break;
            }
            Matcher m = salida[0];
            boolean inicio = esInicioAmplio(s, m.start());
            List<String> posibles = new ArrayList<String>();
            for (Alt a : r.alternativas) {
                if (!a.soloInicio || inicio) {
                    posibles.add(sustituir(a.texto, r, m));
                }
            }
            sb.append(s, pos, m.start());
            if (posibles.isEmpty()) { // no deberia pasar (buscar ya lo comprueba): se deja el texto como esta
                sb.append(m.group());
                pos = m.end();
                continue;
            }
            int n = posibles.size();
            int k = azar.nextInt(n);
            Integer[] orden = new Integer[n];
            for (int i = 0; i < n; i++) {
                orden[i] = (k + i) % n;
            }
            if (corto) {
                final List<String> fp = posibles;
                Arrays.sort(orden, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer a, Integer b) {
                        int d = fp.get(a).length() - fp.get(b).length();
                        return d != 0 ? d : a - b;
                    }
                });
            }
            String despues = s.substring(m.end());
            String alt = posibles.get(orden[0]);
            for (int i = 0; i < n; i++) {
                String candidata = posibles.get(orden[i]);
                if (!repetida(candidata, firmas, sb, despues)) {
                    alt = candidata;
                    break;
                }
            }
            firmas.add(firma(alt));
            String hallado = m.group();
            alt = copiarMayusculas(hallado, alt, esInicio(s, m.start()));
            ajustarArticulo(sb, alt);
            sb.append(alt);
            pos = m.end();
        }
        sb.append(s, pos, s.length());
        return sb.toString();
    }

    private static boolean esInicio(String s, int pos) {
        int i = pos - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        if (i < 0) {
            return true;
        }
        char c = s.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\u2026';
    }

    private static String copiarMayusculas(String hallado, String alt, boolean inicio) {
        int letras = 0;
        int mayus = 0;
        for (int i = 0; i < hallado.length(); i++) {
            char c = hallado.charAt(i);
            if (Character.isLetter(c)) {
                letras++;
                if (Character.isUpperCase(c)) {
                    mayus++;
                }
            }
        }
        if (letras >= 3 && mayus == letras) {
            return alt.toUpperCase(Locale.ROOT);
        }
        char primero = hallado.charAt(0);
        if (Character.isUpperCase(primero)) {
            boolean yo = hallado.length() == 1 || hallado.charAt(1) == '\'' || hallado.charAt(1) == '\u2019' || hallado.charAt(1) == ' ';
            if (primero == 'I' && yo && !inicio) {
                return alt;
            }
            return Character.toUpperCase(alt.charAt(0)) + alt.substring(1);
        }
        return alt;
    }

    private static boolean suenaAVocal(String palabra) {
        String p = palabra.toLowerCase(Locale.ROOT);
        if (p.isEmpty() || !Character.isLetter(p.charAt(0))) {
            return false;
        }
        if (p.startsWith("hour") || p.startsWith("honest") || p.startsWith("honor") || p.startsWith("honour") || p.startsWith("heir")) {
            return true;
        }
        if (p.startsWith("uni") || p.startsWith("use") || p.startsWith("usu") || p.startsWith("uti") || p.startsWith("eu")
            || p.startsWith("one") || p.startsWith("once") || p.startsWith("ubiq")) {
            return false;
        }
        return "aeiou".indexOf(p.charAt(0)) >= 0;
    }

    /** "a awful" -> "an awful", "an big" -> "a big" cuando la regla cambia la palabra siguiente. */
    private static void ajustarArticulo(StringBuilder sb, String alt) {
        int fin = sb.length();
        int i = fin;
        while (i > 0 && sb.charAt(i - 1) == ' ') {
            i--;
        }
        if (i == fin || i == 0) {
            return;
        }
        int j = i;
        while (j > 0 && Character.isLetter(sb.charAt(j - 1))) {
            j--;
        }
        String palabra = sb.substring(j, i);
        if (!(palabra.equalsIgnoreCase("a") || palabra.equalsIgnoreCase("an"))) {
            return;
        }
        if (j > 0 && (Character.isLetterOrDigit(sb.charAt(j - 1)) || sb.charAt(j - 1) == '\'' || sb.charAt(j - 1) == '\u2019')) {
            return;
        }
        String primera = alt.split("[^\\p{L}']")[0];
        if (primera.isEmpty() || (!alt.isEmpty() && !Character.isLetter(alt.charAt(0)))) {
            return;
        }
        boolean an = suenaAVocal(primera);
        String nuevo = an ? "an" : "a";
        if (!nuevo.equalsIgnoreCase(palabra)) {
            if (Character.isUpperCase(palabra.charAt(0))) {
                nuevo = Character.toUpperCase(nuevo.charAt(0)) + nuevo.substring(1);
            }
            sb.replace(j, i, nuevo);
        }
    }

    // ------------------------------------------------------------------ contracciones y estilo

    private static String contraerCon(Pattern p, String s) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (m.find()) {
            String g = m.group(1) == null ? m.group() : m.group(1);
            String clave = g.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            String nueva = null;
            if (clave.equals("cannot")) {
                nueva = "can't";
            } else if (clave.endsWith(" has") && clave.matches("(it|he|she|that|there|what|who) has")) {
                nueva = clave.substring(0, clave.length() - 4) + "'s";
            } else {
                String[] formas = POR_BIGRAMA.get(clave);
                if (formas != null) {
                    nueva = formas[0];
                    if (clave.equals("can not")) {
                        nueva = "can't";
                    }
                }
            }
            if (nueva == null) {
                continue;
            }
            if (clave.equals("let us") && !letUsSugerencia(s, m.start(), m.end())) {
                continue;
            }
            // "what is it you want" suena mejor completo que "what's it you want"
            if (clave.matches("(what|where|who) is") && s.substring(m.end(1) >= 0 ? m.end(1) : m.end()).matches("(?is)^\\s+it\\b.*")) {
                continue;
            }
            if (nueva.equals("i'm") || nueva.startsWith("i'")) {
                nueva = "I" + nueva.substring(1);
            }
            sb.append(s, pos, m.start(1) >= 0 ? m.start(1) : m.start());
            sb.append(copiarMayusculasSimple(g, nueva));
            pos = m.start(1) >= 0 ? m.end(1) : m.end();
        }
        sb.append(s, pos, s.length());
        return sb.toString();
    }

    /** "let us play" = sugerencia (let's play); "let us pass / let us know / please let us in" = permiso, se queda como esta. */
    private static boolean letUsSugerencia(String s, int ini, int fin) {
        int j = fin;
        while (j < s.length() && esBlanco(s.charAt(j))) {
            j++;
        }
        int k = j;
        while (k < s.length() && Character.isLetter(s.charAt(k))) {
            k++;
        }
        if (k == j || !LETUS_SUGERENCIA.contains(s.substring(j, k).toLowerCase(Locale.ROOT))) {
            return false;
        }
        String pw = palabraPrevia(s, ini);
        return pw == null || !LETUS_BLOQUEA.contains(pw);
    }

    private static String copiarMayusculasSimple(String hallado, String nueva) {
        int letras = 0;
        int mayus = 0;
        for (int i = 0; i < hallado.length(); i++) {
            char c = hallado.charAt(i);
            if (Character.isLetter(c)) {
                letras++;
                if (Character.isUpperCase(c)) {
                    mayus++;
                }
            }
        }
        if (letras >= 4 && mayus == letras) {
            return nueva.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(hallado.charAt(0))) {
            return Character.toUpperCase(nueva.charAt(0)) + nueva.substring(1);
        }
        return nueva;
    }

    private static String contraer(String s) {
        s = contraerCon(CONTRAER_SIEMPRE, s);
        s = contraerCon(CONTRAER_CON_SIGUIENTE, s);
        s = contraerCon(CONTRAER_HAVE, s);
        return s;
    }

    private static final String[] ABREVIATURAS = {"etc", "vs", "mr", "mrs", "ms", "dr"};

    private static String estiloChat(String s) {
        String t = s.trim();
        if (t.endsWith(".") && !t.endsWith("..") && !t.endsWith("\u2026")) {
            String antes = t.substring(0, t.length() - 1);
            String ultima = antes.substring(antes.lastIndexOf(' ') + 1);
            boolean abreviatura = ultima.indexOf('.') >= 0;
            for (String a : ABREVIATURAS) {
                if (ultima.equalsIgnoreCase(a)) {
                    abreviatura = true;
                }
            }
            if (!abreviatura && !antes.trim().isEmpty()) {
                t = antes.trim();
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ pruebas

    public static void main(String[] args) {
        if (args.length > 0) {
            archivoPrueba = new File(args[0]);
        }
        Estado e = cargar();
        for (String a : avisos) {
            System.out.println("AVISO: " + a);
        }
        System.out.println("reglas: " + (e == null ? 0 : e.reglas.length));
        for (int i = 1; i < args.length; i++) {
            System.out.println(args[i] + "\n   => " + aplicar(args[i]));
        }
    }
}
