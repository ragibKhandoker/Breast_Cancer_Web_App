package com.cancerweb.backend.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.cancerweb.backend.dto.ChatMessage;
import com.cancerweb.backend.knowledge.KnowledgeContent.Item;
import com.cancerweb.backend.knowledge.KnowledgeContent.Section;
import com.cancerweb.backend.knowledge.KnowledgeModule;
import com.cancerweb.backend.knowledge.KnowledgeService;
import com.cancerweb.backend.knowledge.KnowledgeService.Faq;
import com.cancerweb.backend.knowledge.KnowledgeService.GlossaryTerm;
import com.cancerweb.backend.service.BreastHealthAssistant.Answer;

/**
 * The chatbot that needs no API key: it answers from BreastCare's own Knowledge Hub (the referenced lessons, the FAQ
 * and the glossary), so every answer is text the site already publishes. It only answers questions about breast
 * cancer, breast health and using BreastCare; anything else is marked off topic and gets ChatService's fixed reply.
 * It is used when no AI model is set up, or when the AI service cannot answer.
 */
@Component
public class KnowledgeChatbot {

    static final String NOT_FOUND = "I couldn't find that in BreastCare's lessons. Try asking about breast cancer "
            + "symptoms, self-checks, screening and mammograms, BI-RADS, risk factors, types, stages or treatment.";
    static final String GREETING = "Hello! I can answer questions about breast cancer and breast health, for example "
            + "warning signs, how to check your breasts, mammograms, risk factors or treatment. What would you like to know?";
    static final String THANKS = "You're welcome. Ask me anything else about breast health whenever you like.";
    static final String SEE_DOCTOR = "If you notice a new or changing breast symptom, please see a doctor soon. "
            + "Most changes are not cancer, but only a doctor can check.";
    static final String URGENT = "If you have severe pain, a high fever with a red, hot breast, or trouble breathing, "
            + "please get urgent medical care now.";
    static final String ENGLISH_NOTE = "(BreastCare's lessons are in English, so this answer is in English.)";
    static final int MAX_CHARS = 700;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");
    private static final Pattern BENGALI = Pattern.compile("\\p{IsBengali}");

    /** Words that make a question about breasts or breast cancer on their own. */
    private static final Set<String> BREAST_TERMS = Set.of("breast", "mammogram", "mammography", "nipple", "mastectomy",
            "lumpectomy", "birads", "her2", "brca", "brca1", "brca2", "dcis", "lcis", "idc", "ilc", "tnbc", "tamoxifen",
            "areola", "fibroadenoma", "lobular", "ductal", "tomosynthesis", "lymphedema", "paget", "selfexam", "breastcare",
            "implant", "augmentation", "alcl",
            // Using the site itself.
            "heatmap", "upload", "prediction", "appointment", "surgeon");
    /** General cancer or health words: on topic only next to a breast word, or in a breast conversation. */
    private static final Set<String> HEALTH_TERMS = Set.of("cancer", "tumor", "tumour", "lump", "cyst", "biopsy",
            "screening", "symptom", "sign", "risk", "stage", "grade", "treatment", "chemotherapy", "radiation",
            "radiotherapy", "hormone", "surgery", "reconstruction", "recovery", "survivor", "survivorship", "metastasis",
            "metastatic", "lymph", "node", "oncologist", "ultrasound", "mri", "diagnosis", "prevention", "immunotherapy",
            "receptor", "estrogen", "progesterone", "gene", "genetic", "discharge", "dimpling", "pain", "swelling",
            "type", "benign", "malignant", "check", "exam", "examination", "mammo", "heatmap", "report", "result", "upload");
    /** Other organs and diseases: a question about these is off topic even if it says "cancer". */
    private static final Set<String> OTHER_TOPICS = Set.of("lung", "prostate", "colon", "colorectal", "skin", "liver",
            "cervical", "cervix", "ovarian", "ovary", "pancreatic", "stomach", "kidney", "bladder", "brain", "blood",
            "leukemia", "lymphoma", "thyroid", "diabetes", "covid", "malaria", "dengue", "heart", "stroke", "tb",
            "tuberculosis", "hiv", "flu", "fever", "migraine");
    /** Requests for something other than information, which stay off topic whatever they mention. */
    private static final Set<String> TASKS = Set.of("code", "python", "java", "javascript", "programming", "essay", "poem",
            "story", "joke", "song", "translate", "translation", "homework", "assignment", "recipe", "ignore", "pretend",
            "roleplay", "jailbreak");
    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "salam", "assalamualaikum", "assalamu",
            "hola", "namaskar", "adab");
    private static final Set<String> THANK_WORDS = Set.of("thanks", "thank", "thx", "dhonnobad", "dhonnobaad", "shukriya");
    private static final Set<String> WARNING_WORDS = Set.of("lump", "discharge", "dimpling", "swelling", "pain",
            "bleeding", "blood", "redness", "inverted", "change");
    private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "is", "are", "was", "were", "be", "to", "of",
            "in", "on", "for", "and", "or", "i", "my", "me", "you", "your", "it", "this", "that", "what", "which", "who",
            "how", "do", "does", "did", "can", "could", "should", "would", "will", "about", "with", "have", "has", "if",
            "at", "by", "from", "as", "there", "any", "some", "tell", "know", "please", "explain", "mean", "means", "get",
            "much", "many", "when", "why", "where", "kno", "ki", "keno", "kivabe", "ar", "er", "ta", "te", "e", "na", "ache",
            "hoy", "hole", "kore", "korbo", "korte", "amar", "ami", "bolo", "bolen", "jante", "chai", "kemon", "koto");
    /** Bangla and Banglish words mapped to the English words the lessons use. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("স্তন", "breast"), Map.entry("stan", "breast"), Map.entry("stoner", "breast"), Map.entry("brest", "breast"),
            Map.entry("ক্যান্সার", "cancer"), Map.entry("ক্যানসার", "cancer"), Map.entry("cancar", "cancer"), Map.entry("kanser", "cancer"),
            Map.entry("চাকা", "lump"), Map.entry("chaka", "lump"), Map.entry("গুটি", "lump"), Map.entry("guti", "lump"), Map.entry("gota", "lump"),
            Map.entry("ব্যথা", "pain"), Map.entry("betha", "pain"), Map.entry("byatha", "pain"), Map.entry("batha", "pain"),
            Map.entry("লক্ষণ", "symptom"), Map.entry("lokkhon", "symptom"), Map.entry("lakkhan", "symptom"), Map.entry("symptoms", "symptom"),
            Map.entry("চিকিৎসা", "treatment"), Map.entry("chikitsa", "treatment"), Map.entry("treatments", "treatment"),
            Map.entry("কারণ", "risk"), Map.entry("karon", "risk"), Map.entry("causes", "risk"), Map.entry("cause", "risk"),
            Map.entry("পরীক্ষা", "check"), Map.entry("porikkha", "check"), Map.entry("test", "check"), Map.entry("tests", "check"),
            Map.entry("ম্যামোগ্রাম", "mammogram"), Map.entry("mammograms", "mammogram"), Map.entry("mamogram", "mammogram"),
            Map.entry("bi", "birads"), Map.entry("rads", "birads"), Map.entry("selfcheck", "selfexam"),
            Map.entry("stages", "stage"), Map.entry("types", "type"), Map.entry("signs", "sign"), Map.entry("lumps", "lump"),
            Map.entry("nipples", "nipple"), Map.entry("breasts", "breast"), Map.entry("chemo", "chemotherapy"),
            Map.entry("tumors", "tumor"), Map.entry("tumours", "tumour"), Map.entry("risks", "risk"), Map.entry("genes", "gene"));

    /** How to use BreastCare: question words, answer, where it is on the site. */
    private static final String[][] SITE_HELP = {
        { "how upload mammogram image analysis prediction scan breastcare",
          "To analyse a mammogram, sign in as a patient and open Prediction. Choose a PNG or JPG mammogram, tick the consent box and press Upload Image. In a few seconds you get an estimated malignancy percentage with a heatmap. It is a research estimate, not a diagnosis.",
          "/prediction" },
        { "heatmap result percentage report meaning breastcare",
          "The result shows the model's estimated chance that the mammogram shows signs of malignancy, and a heatmap of the areas that pushed the estimate up. It is a research estimate, not a diagnosis: a doctor, imaging and if needed a biopsy decide.",
          "/history" },
        { "pdf report download share doctor breastcare",
          "Open a report from your history to download it as a PDF, share it with a verified doctor, or ask your doctor (after a confirmed appointment) to review it and write you a note.",
          "/history" },
        { "book appointment surgeon doctor breastcare",
          "Use Find a Surgeon to see verified Breast Surgeons, their fees and free slots, and request an appointment. The surgeon confirms it, and you can then ask them to review your reports.",
          "/breast-surgeons" },
    };

    /** One answerable piece of text and where it comes from. */
    record Passage(String text, String source, String link, Set<String> words, Set<String> titleWords, double weight,
            List<String> points, String title) {
    }

    /** "Stage I: Early, small. ..." without the "Stage I: " label. */
    private static String stripLabel(String point) {
        int colon = point.indexOf(": ");
        return colon > 0 && colon < 20 ? point.substring(colon + 2) : point;
    }

    private final KnowledgeService knowledge;
    private volatile List<Passage> passages = List.of();
    private volatile Map<String, Double> idf = Map.of();
    /** Which lessons the index was built from (count and latest update), so an edit shows up at once. */
    private volatile String builtFrom = "";

    public KnowledgeChatbot(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    /** The answer to the last user message, from the Knowledge Hub. */
    public Answer ask(List<ChatMessage> conversation) {
        String question = conversation.getLast().content();
        List<String> words = words(question);
        Set<String> set = new HashSet<>(words);
        if (words.isEmpty()) {
            return new Answer(false, "");
        }
        if (set.stream().anyMatch(TASKS::contains)) {
            return new Answer(false, "");
        }
        boolean breast = set.stream().anyMatch(BREAST_TERMS::contains);
        boolean health = set.stream().anyMatch(HEALTH_TERMS::contains);
        boolean other = set.stream().anyMatch(OTHER_TOPICS::contains);
        if (!breast && words.size() <= 4 && set.stream().anyMatch(GREETINGS::contains)) {
            return new Answer(true, GREETING);
        }
        if (!breast && set.stream().anyMatch(THANK_WORDS::contains)) {
            return new Answer(true, THANKS);
        }
        // A follow-up ("and the treatment?") counts when the conversation so far was about breast health.
        boolean breastConversation = conversation.stream().filter(m -> "user".equals(m.role()))
                .anyMatch(m -> words(m.content()).stream().anyMatch(BREAST_TERMS::contains));
        boolean onTopic = breast || (health && !other && breastConversation);
        if (!onTopic) {
            return new Answer(false, "");
        }

        ensureIndex();
        List<Passage> best = search(words);
        StringBuilder reply = new StringBuilder();
        if (best.isEmpty()) {
            reply.append(NOT_FOUND);
        } else {
            Passage top = best.getFirst();
            boolean onePoint = top.points().stream().anyMatch(point -> point.endsWith(stripLabel(top.text()))
                    || top.text().startsWith(stripLabel(point)));
            if (onePoint) {
                // The best match is one point of a list (a stage, a sign, a step): give the whole list in order.
                reply.append(top.title()).append(":");
            } else {
                reply.append(shorten(top.text()));
            }
            for (String point : top.points()) {
                if (!onePoint && (top.text().contains(stripLabel(point)) || stripLabel(point).contains(top.text()))) {
                    continue;
                }
                if (reply.length() + point.length() > MAX_CHARS + 400) {
                    break;
                }
                reply.append("\n- ").append(point);
            }
            reply.append("\n\nRead more: ").append(top.source()).append(" in the Knowledge Hub (").append(top.link()).append(").");
        }
        if (set.stream().anyMatch(w -> Set.of("severe", "fever", "breathe", "breathing", "emergency").contains(w))) {
            reply.append("\n\n").append(URGENT);
        } else if (set.stream().anyMatch(WARNING_WORDS::contains)) {
            reply.append("\n\n").append(SEE_DOCTOR);
        }
        reply.append("\n\nThis is general information, not a diagnosis.");
        if (BENGALI.matcher(question).find()) {
            reply.append(" ").append(ENGLISH_NOTE);
        }
        return new Answer(true, reply.toString());
    }

    /**
     * The best answer for these words. Passages are scored BM25-style (title words count extra); then the section with
     * the strongest matches wins, so a question about a whole topic gets that topic's section rather than one stray
     * sentence. Returns the winning section's passages, best first, or nothing when nothing matches well.
     */
    List<Passage> search(List<String> query) {
        Set<String> terms = new LinkedHashSet<>(query);
        terms.removeAll(STOPWORDS);
        record Scored(Passage passage, double score) {
        }
        Map<String, List<Scored>> bySection = new HashMap<>();
        for (Passage p : passages) {
            double score = 0;
            for (String t : terms) {
                double weight = idf.getOrDefault(t, 0.0);
                if (p.words().contains(t)) {
                    score += weight;
                }
                if (p.titleWords().contains(t)) {
                    score += weight * 0.8;
                }
            }
            // Prefer focused passages over long ones that happen to mention everything.
            score = score * p.weight() / (1 + Math.log1p(p.words().size()) * 0.15);
            if (score > 0) {
                bySection.computeIfAbsent(p.link() + "|" + p.source(), k -> new ArrayList<>()).add(new Scored(p, score));
            }
        }
        List<Scored> bestSection = null;
        double bestScore = 0;
        for (List<Scored> section : bySection.values()) {
            section.sort(Comparator.comparingDouble(Scored::score).reversed());
            double total = section.getFirst().score();
            for (int i = 1; i < Math.min(section.size(), 4); i++) {
                total += section.get(i).score() * 0.35;
            }
            if (total > bestScore) {
                bestScore = total;
                bestSection = section;
            }
        }
        if (bestSection == null || bestSection.getFirst().score() < 1.2) {
            return List.of();
        }
        return bestSection.stream().map(Scored::passage).toList();
    }

    private synchronized void ensureIndex() {
        List<KnowledgeModule> modules = knowledge.listPublished();
        String from = modules.size() + "|" + modules.stream().map(KnowledgeModule::updatedAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).map(Instant::toString).orElse("");
        if (from.equals(builtFrom)) {
            return;
        }
        List<Passage> out = new ArrayList<>();
        for (KnowledgeModule m : modules) {
            for (Section s : m.sections()) {
                String link = "/knowledge/" + m.slug() + "#" + s.id();
                String source = "\"" + s.title() + "\" (" + m.title() + ")";
                Set<String> titleWords = new HashSet<>(words(m.title() + " " + s.title()));
                boolean labelled = s.display() != null && Set.of("stages", "timeline", "birads").contains(s.display());
                List<String> points = s.items() == null ? List.of()
                        : s.items().stream().map(i -> labelled && i.label() != null
                                ? (s.display().equals("birads") ? "BI-RADS " : "") + i.label() + ": " + join(i.title(), i.text())
                                : join(i.title(), i.text()))
                                .filter(t -> !t.isBlank()).limit(7).toList();
                if (s.body() != null) {
                    List<String> paras = paragraphs(s.body());
                    for (String paragraph : paras) {
                        // Each paragraph carries its section's list of steps or points (added while the reply stays short).
                        out.add(new Passage(paragraph, source, link, new HashSet<>(words(paragraph)), titleWords, 1.0, points, s.title()));
                    }
                }
                if (s.items() != null) {
                    for (Item i : s.items()) {
                        String text = join(i.title(), i.text(), i.note());
                        Set<String> itemTitle = new HashSet<>(titleWords);
                        itemTitle.addAll(words(join(i.label(), i.title())));
                        out.add(new Passage(join(i.title(), i.text()), source, link, new HashSet<>(words(text)), itemTitle, 1.0, points, s.title()));
                    }
                }
            }
        }
        for (String[] u : SITE_HELP) {
            out.add(passage(u[1], "BreastCare help", u[2], new HashSet<>(words(u[0]))));
        }
        for (Faq f : knowledge.faqs()) {
            out.add(new Passage(f.question() + "\n" + f.answer(), "the FAQ", "/knowledge#faq-title",
                    new HashSet<>(words(f.answer())), new HashSet<>(words(f.question())), 1.0, List.of(), "FAQ"));
        }
        for (GlossaryTerm g : knowledge.glossary()) {
            out.add(new Passage(g.term() + ": " + g.definition(), "the glossary", "/knowledge#glossary-title",
                    new HashSet<>(words(g.term() + " " + g.definition())), new HashSet<>(words(g.term())), 0.75, List.of(), "Glossary"));
        }
        Map<String, Integer> df = new HashMap<>();
        out.forEach(p -> p.words().forEach(w -> df.merge(w, 1, Integer::sum)));
        Map<String, Double> weights = new HashMap<>();
        df.forEach((w, n) -> weights.put(w, Math.log(1 + (out.size() - n + 0.5) / (n + 0.5))));
        idf = weights;
        passages = List.copyOf(out);
        builtFrom = from;
    }

    private static Passage passage(String text, String source, String link, Set<String> titleWords) {
        return new Passage(text, source, link, new HashSet<>(words(text)), titleWords, 1.0, List.of(), "BreastCare help");
    }

    /** Lesson text: paragraphs, with a bullet list kept together with the sentence before it. */
    private static List<String> paragraphs(String body) {
        List<String> out = new ArrayList<>();
        for (String block : body.split("\\n\\s*\\n")) {
            String clean = block.lines().map(String::strip).filter(l -> !l.isEmpty())
                    .map(l -> l.startsWith("> ") ? l.substring(2) : l)
                    .reduce((a, b) -> b.startsWith("- ") ? a + "\n" + b : a + " " + b).orElse("");
            if (!clean.isBlank()) {
                out.add(clean);
            }
        }
        return out;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(sb.toString().endsWith(".") ? " " : ". ");
                }
                sb.append(p.strip());
            }
        }
        return sb.toString();
    }

    /** Cuts long text at a sentence end so the reply stays short. */
    static String shorten(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        int cut = text.lastIndexOf(". ", MAX_CHARS);
        return cut > 200 ? text.substring(0, cut + 1) : text.substring(0, MAX_CHARS).strip() + "…";
    }

    /** Lower-case words, with Bangla/Banglish words and plurals mapped to the lessons' English words. */
    static List<String> words(String text) {
        String t = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("bi-rads", "birads").replace("self-examination", "selfexam").replace("self examination", "selfexam")
                .replace("self-exam", "selfexam").replace("self exam", "selfexam").replace("self check", "selfcheck").replace("self-check", "selfcheck");
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(t);
        while (m.find()) {
            String w = m.group();
            w = SYNONYMS.getOrDefault(w, w);
            if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("is") && !w.endsWith("us")
                    && !BREAST_TERMS.contains(w) && !HEALTH_TERMS.contains(w)) {
                w = SYNONYMS.getOrDefault(w.substring(0, w.length() - 1), w.substring(0, w.length() - 1));
            }
            out.add(w);
        }
        return out;
    }
}
