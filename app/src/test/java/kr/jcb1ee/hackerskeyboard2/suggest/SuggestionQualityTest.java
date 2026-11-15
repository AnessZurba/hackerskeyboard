package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kr.jcb1ee.hackerskeyboard2.Keyboard;
import kr.jcb1ee.hackerskeyboard2.LocaleLayouts;

/**
 * Measures the decoder on synthetic typing errors made on our real 5-row layouts (English,
 * Hebrew, Arabic): takes common words from the language's word list, mistypes them, and
 * reports how often the intended word is the first suggestion (top-1) or among the first
 * three (top-3), and how long a query takes. The reports go to
 * app/build/reports/suggestion-quality[-LANG].txt and stdout.
 *
 * <p>Error kinds: none (taps at key centres), a neighbouring key instead of the right one,
 * a letter left out, two letters swapped, an extra neighbouring letter, "noisy" taps (each
 * tap displaced by Gaussian noise of a third of a key; the letter typed is the key under the
 * tap, as on a real keyboard), and for Hebrew and Arabic a letter variant typed as its base
 * letter (a final form as the ordinary one, أ إ آ as ا, ى as ي, ة as ه): "variant" where
 * that is not a word, "variant_word" where it is one too (then the typed word stays first
 * and only top-3 says something). Letters on a
 * shifted key (Arabic أ إ آ) are tapped on that key. Deterministic: fixed seed.
 *
 * <p>The word lists are those of the adaptive key targets (assets/wordlists): for English
 * and Hebrew the same AOSP lists the dictionaries come from, for Arabic a subtitle corpus
 * (OpenSubtitles), not the news and Wikipedia text of the dictionary.
 *
 * <p>The optional (downloadable) dictionaries are measured when tools/mkdicts.sh has built
 * them (build/dicts; skipped otherwise), on their language's layout, with words from their
 * own word list (build/dicts/src), 500 by default. There "variant" is a letter with a
 * diacritic typed as its base letter (é as e, ł as l, ё as е, ά as α: typing without
 * accents), "digraph" (German) an umlaut typed as ae/oe/ue. Letters only on a long press
 * are tapped on their base letter's key.
 *
 * <p>Run alone with {@code ./gradlew testDebugUnitTest --tests '*SuggestionQualityTest*'};
 * {@code -PsuggestWords=N} changes the sample size (default 1000).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class SuggestionQualityTest {
    private static final File REPORTS = new File("build/reports");

    private enum Error {
        NONE, NEIGHBOUR, OMISSION, TRANSPOSITION, EXTRA, NOISY_TAPS, VARIANT, VARIANT_WORD,
        DIGRAPH
    }

    /** One mistyped word: the letters and where they were tapped. */
    private static final class Typed {
        final String text;
        final int[] xs, ys;

        Typed(String text, int[] xs, int[] ys) {
            this.text = text;
            this.xs = xs;
            this.ys = ys;
        }
    }

    private static final class Score {
        int n, top1, top3;
        long nanos, worstNanos;
        final List<String> misses = new ArrayList<>();
    }

    /** Where each letter is typed: its key, unshifted or shifted. */
    private static final class Layout {
        final KeyGeometry keys;
        final Map<Integer, KeyGeometry.Key> taps = new HashMap<>();
        /** Letters with no key are tapped on their base letter's (a long press there). */
        final boolean longPressOnBase;

        Layout(String locale, boolean longPressOnBase) {
            this.longPressOnBase = longPressOnBase;
            Keyboard kb = LocaleLayouts.fullKeyboard(locale);
            keys = KeyGeometry.fromKeyboard(kb);
            for (KeyGeometry.Key k : keys.keys()) taps.put(k.code, k);
            for (Keyboard.Key k : kb.getKeys()) {
                if (k.modifier || k.shiftLabel == null || k.shiftLabel.length() != 1) continue;
                int c = k.shiftLabel.charAt(0);
                if (Character.isLetter(c) && !taps.containsKey(Character.toLowerCase(c))) {
                    taps.put(c, new KeyGeometry.Key(c, k.x, k.y, k.width, k.height));
                }
            }
        }

        KeyGeometry.Key tap(int c) {
            KeyGeometry.Key k = taps.get(c);
            if (k == null) k = taps.get(Character.toLowerCase(c));
            if (k == null && longPressOnBase) {
                // A letter on a long press only (Catalan à, Portuguese ã on the default
                // layout): tapped where its base letter's key is.
                int base = baseLetter(c);
                KeyGeometry.Key b = base != c ? taps.get(base) : null;
                if (b != null) k = new KeyGeometry.Key(c, b.x, b.y, b.width, b.height);
            }
            return k;
        }
    }

    /**
     * The letter a diacritic letter is typed as when typing without accents (é as e, ł as l,
     * Turkish ı as i, Greek ά as α, Russian ё as е), or {@code c} itself. Cyrillic й (a letter
     * of its own) and ß stay; these are the letters the decoder folds (CharUtils::BASE_CHARS).
     */
    static int baseLetter(int c) {
        switch (c) {
            case 'ł': return 'l';
            case 'đ': return 'd';
            case 'ı': return 'i';
            case 'ø': return 'o';
            case 'й': case 'ß': return c;
            default:
                String d = java.text.Normalizer.normalize(String.valueOf((char) c),
                        java.text.Normalizer.Form.NFD);
                return d.length() > 1 && Character.isLetter(d.charAt(0)) ? d.charAt(0) : c;
        }
    }

    /** {variant, typed instead} pairs: each letter of the language with a diacritic. */
    private static int[][] accentVariants(String letters) {
        List<int[]> out = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            int b = baseLetter(c);
            if (b != c) out.add(new int[] {c, b});
        }
        return out.toArray(new int[0][]);
    }

    @Test
    public void english() throws IOException {
        Map<Error, Score> s = measure("en_US", "en", "en.txt", "main_en.dict",
                new int[0][], "suggestion-quality.txt");
        // Loose floors, to catch a broken engine or layout mapping, not to tune against.
        assertTrue(rate(s.get(Error.NONE), true) > 0.90);
        assertTrue(rate(s.get(Error.NEIGHBOUR), false) > 0.60);
        assertTrue(rate(s.get(Error.TRANSPOSITION), false) > 0.60);
    }

    @Test
    public void hebrew() throws IOException {
        Map<Error, Score> s = measure("iw", "iw", "he.txt", "main_iw.dict", new int[][] {
                {'ך', 'כ'}, {'ם', 'מ'}, {'ן', 'נ'}, {'ף', 'פ'}, {'ץ', 'צ'}},
                "suggestion-quality-iw.txt");
        assertTrue(rate(s.get(Error.NONE), true) > 0.90);
        assertTrue(rate(s.get(Error.NEIGHBOUR), false) > 0.50);
        assertTrue(rate(s.get(Error.VARIANT), true) > 0.80);
    }

    @Test
    public void arabic() throws IOException {
        Map<Error, Score> s = measure("ar", "ar", "ar.txt", "main_ar.dict", new int[][] {
                {'أ', 'ا'}, {'إ', 'ا'}, {'آ', 'ا'}, {'ى', 'ي'}, {'ة', 'ه'}},
                "suggestion-quality-ar.txt");
        assertTrue(rate(s.get(Error.NONE), true) > 0.85);
        assertTrue(rate(s.get(Error.NEIGHBOUR), false) > 0.50);
        assertTrue(rate(s.get(Error.VARIANT), false) > 0.80);
    }

    // ---------------------------------------------------------------- optional dictionaries

    @Test public void german() throws IOException { optional("de", "de", "äöü"); }
    @Test public void french() throws IOException { optional("fr", "fr", "àâæçéèêëîïôœùûüÿ"); }
    @Test public void spanish() throws IOException { optional("es", "es", "áéíñóúü"); }
    @Test public void portuguese() throws IOException { optional("pt", "pt_PT", "áâãàçéêíóôõú"); }
    @Test public void italian() throws IOException { optional("it", "it", "àèéìíîòóùú"); }
    @Test public void polish() throws IOException { optional("pl", "pl", "ąćęłńóśźż"); }
    @Test public void czech() throws IOException { optional("cs", "cs", "áčďéěíňóřšťúůýž"); }
    @Test public void swedish() throws IOException { optional("sv", "sv", "åäö"); }
    @Test public void turkish() throws IOException { optional("tr", "tr", "çğıöşü"); }
    @Test public void romanian() throws IOException { optional("ro", "ro", "ăâîșțşţ"); }
    @Test public void russian() throws IOException { optional("ru", "ru", "ё"); }
    @Test public void ukrainian() throws IOException { optional("uk", "uk", "ї"); }
    @Test public void bulgarian() throws IOException { optional("bg", "bg", "ѝ"); }
    @Test public void greek() throws IOException { optional("el", "el", "άέήίόύώϊϋΐΰ"); }
    @Test public void hungarian() throws IOException { optional("hu", "hu", "áéíóöőúüű"); }
    @Test public void catalan() throws IOException { optional("ca", "ca", "àéèíïòóúüç"); }

    /**
     * An optional dictionary on {@code layoutLocale}'s layout, with {@code accents} (the
     * language's letters with diacritics) typed without them as the "variant" errors.
     */
    private void optional(String name, String layoutLocale, String accents) throws IOException {
        File dict = SuggestTestSupport.optionalDict(name);
        File wordlist = new File(SuggestTestSupport.OPTIONAL_DICT_DIR, "src/" + name + ".combined");
        Map<Error, Score> s = measure(dictLocale(name), layoutLocale, wordlist, dict.getName(),
                accentVariants(accents), "suggestion-quality-" + name + ".txt", true,
                Integer.getInteger("suggestWords", 500), name.equals("de"));
        assertTrue(rate(s.get(Error.NONE), true) > 0.85);
        assertTrue(rate(s.get(Error.NEIGHBOUR), false) > 0.50);
    }

    /** A locale that opens dictionary {@code name} ("pt_pt" is pt_PT). */
    private static String dictLocale(String name) {
        int sep = name.indexOf('_');
        return sep < 0 ? name : name.substring(0, sep) + "_" + name.substring(sep + 1).toUpperCase(Locale.ROOT);
    }

    private Map<Error, Score> measure(String locale, String layoutLocale, String wordlist,
            String dictName, int[][] variants, String reportName) throws IOException {
        return measure(locale, layoutLocale, new File("src/main/assets/wordlists/" + wordlist),
                dictName, variants, reportName, false, Integer.getInteger("suggestWords", 1000),
                false);
    }

    /**
     * Runs every error kind on {@code locale}'s layout; {@code variants} are the letter
     * variants as {variant, typed instead} pairs.
     */
    private Map<Error, Score> measure(String locale, String layoutLocale, File wordlist,
            String dictName, int[][] variants, String reportName, boolean longPressOnBase,
            int n, boolean digraphs) throws IOException {
        AospSuggestionEngine engine = SuggestTestSupport.engine(locale);
        Layout layout = new Layout(layoutLocale, longPressOnBase);
        KeyGeometry keys = layout.keys;
        List<String> words = sampleWords(engine, layout, wordlist, n);
        Random random = new Random(20261114);
        Map<Error, Score> scores = new LinkedHashMap<>();
        // Warm up (page in the dictionary, JIT) so the latencies are steady state.
        for (int i = 0; i < 200; i++) {
            engine.suggestions(words.get(i % words.size()), null, null, null, keys, 3);
        }
        for (Error error : Error.values()) {
            boolean variant = error == Error.VARIANT || error == Error.VARIANT_WORD;
            if (variant && variants.length == 0) continue;
            if (error == Error.DIGRAPH && !digraphs) continue;
            Score score = new Score();
            for (String word : words) {
                Typed typed = mistype(word, error, layout, variants, random);
                if (typed == null) continue;
                // A variant typed as its base letter is sometimes a word itself (Arabic
                // hamza-less spellings such as "اجل" for "أجل"): then it stays first.
                if (variant && engine.isValidWord(typed.text) != (error == Error.VARIANT_WORD)) {
                    continue;
                }
                long t0 = System.nanoTime();
                List<Suggestion> s = engine.suggestions(typed.text, typed.xs, typed.ys, null,
                        keys, 3);
                long dt = System.nanoTime() - t0;
                score.n++;
                score.nanos += dt;
                score.worstNanos = Math.max(score.worstNanos, dt);
                int rank = -1;
                for (int i = 0; i < s.size(); i++) {
                    if (s.get(i).word.equals(word)) {
                        rank = i;
                        break;
                    }
                }
                if (rank == 0) score.top1++;
                if (rank >= 0 && rank < 3) score.top3++;
                if (rank != 0 && score.misses.size() < 8) {
                    score.misses.add(typed.text + "->" + (s.isEmpty() ? "-" : s.get(0).word)
                            + " (" + word + ")");
                }
            }
            if (score.n > 0) scores.put(error, score);
        }
        engine.close();

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "AOSP decoder, %s, kbd_full (%s) at %dx%d px,"
                + " %d words (length 3-12, frequency rank < 20000)%n",
                dictName, layoutLocale, keys.width, keys.height, words.size()));
        report.append(String.format(Locale.ROOT, "%-14s %6s %7s %7s %9s %9s%n",
                "error", "n", "top-1", "top-3", "mean ms", "max ms"));
        for (Map.Entry<Error, Score> e : scores.entrySet()) {
            Score s = e.getValue();
            report.append(String.format(Locale.ROOT, "%-14s %6d %6.1f%% %6.1f%% %9.3f %9.3f%n",
                    e.getKey().name().toLowerCase(Locale.ROOT), s.n, 100.0 * s.top1 / s.n,
                    100.0 * s.top3 / s.n, s.nanos / 1e6 / s.n, s.worstNanos / 1e6));
        }
        report.append("Sample misses (typed->first suggestion (intended)):\n");
        for (Map.Entry<Error, Score> e : scores.entrySet()) {
            report.append("  ").append(e.getKey().name().toLowerCase(Locale.ROOT)).append(": ")
                    .append(String.join(", ", e.getValue().misses)).append('\n');
        }
        System.out.print(report);
        REPORTS.mkdirs();
        Files.write(new File(REPORTS, reportName).toPath(),
                report.toString().getBytes(StandardCharsets.UTF_8));
        return scores;
    }

    private static double rate(Score s, boolean top1) {
        return (double) (top1 ? s.top1 : s.top3) / s.n;
    }

    /**
     * Every k-th of the 20000 most frequent words of 3-12 letters that the dictionary has in
     * lower case and that the layout can type, n in all.
     */
    private static List<String> sampleWords(AospSuggestionEngine engine, Layout layout,
            File wordlist, int n) throws IOException {
        List<String[]> entries = new ArrayList<>();
        for (String line : Files.readAllLines(wordlist.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.startsWith(" word=")) {
                // An AOSP combined list: " word=W,f=N,flags=...".
                String[] fields = line.trim().split(",");
                if (line.contains("not_a_word=true") || line.contains("possibly_offensive")) {
                    continue;
                }
                entries.add(new String[] {fields[0].substring(5), fields[1].substring(2)});
                continue;
            }
            if (line.startsWith(" ")) continue; // shortcuts, bigrams; the header
            String[] parts = line.split("\t");
            if (parts.length >= 2) entries.add(parts);
        }
        entries.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        List<String> pool = new ArrayList<>();
        for (String[] e : entries) {
            if (pool.size() >= 20000) break;
            String w = e[0];
            if (w.length() < 3 || w.length() > 12 || !typeable(w, layout)) continue;
            if (!engine.isValidWord(w) || !w.equals(w.toLowerCase(Locale.ROOT))) continue;
            pool.add(w);
        }
        List<String> out = new ArrayList<>();
        int step = Math.max(1, pool.size() / n);
        for (int i = 0; i < pool.size() && out.size() < n; i += step) out.add(pool.get(i));
        return out;
    }

    private static boolean typeable(String w, Layout layout) {
        for (int i = 0; i < w.length(); i++) {
            if (!Character.isLetter(w.charAt(i)) || layout.tap(w.charAt(i)) == null) return false;
        }
        return true;
    }

    private static Typed mistype(String word, Error error, Layout layout, int[][] variants,
            Random random) {
        KeyGeometry keys = layout.keys;
        StringBuilder s = new StringBuilder(word);
        int len = word.length();
        switch (error) {
            case NONE:
                break;
            case NEIGHBOUR: {
                int i = random.nextInt(len);
                KeyGeometry.Key k = neighbour(keys, layout.tap(word.charAt(i)), random);
                s.setCharAt(i, (char) k.code);
                break;
            }
            case OMISSION:
                s.deleteCharAt(1 + random.nextInt(len - 1));
                break;
            case TRANSPOSITION: {
                int i = random.nextInt(len - 1);
                if (word.charAt(i) == word.charAt(i + 1)) return null;
                s.setCharAt(i, word.charAt(i + 1));
                s.setCharAt(i + 1, word.charAt(i));
                break;
            }
            case EXTRA: {
                int i = random.nextInt(len);
                KeyGeometry.Key k = neighbour(keys, layout.tap(word.charAt(i)), random);
                s.insert(i + random.nextInt(2), (char) k.code);
                break;
            }
            case NOISY_TAPS:
                return noisyTaps(word, layout, random);
            case VARIANT:
            case VARIANT_WORD: {
                // Words with a variant letter only; one of them (at random) typed as its base.
                List<int[]> at = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    for (int[] v : variants) {
                        if (word.charAt(i) == v[0]) at.add(new int[] {i, v[1]});
                    }
                }
                if (at.isEmpty()) return null;
                if (layout.longPressOnBase) {
                    // Diacritics (optional dictionaries): all of them left out.
                    for (int[] p : at) s.setCharAt(p[0], (char) p[1]);
                    break;
                }
                int[] pick = at.get(random.nextInt(at.size()));
                s.setCharAt(pick[0], (char) pick[1]);
                break;
            }
            case DIGRAPH: {
                // German: one umlaut typed as ae, oe or ue.
                List<Integer> at = new ArrayList<>();
                for (int i = 0; i < len; i++) if ("äöü".indexOf(word.charAt(i)) >= 0) at.add(i);
                if (at.isEmpty()) return null;
                int i = at.get(random.nextInt(at.size()));
                s.replace(i, i + 1, (char) baseLetter(word.charAt(i)) + "e");
                break;
            }
        }
        String text = s.toString();
        int[] xs = new int[text.length()], ys = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            KeyGeometry.Key k = layout.tap(text.charAt(i));
            xs[i] = k.centerX();
            ys[i] = k.centerY();
        }
        return new Typed(text, xs, ys);
    }

    /** A letter key adjacent to {@code k} (sharing an edge or corner, within a key's width). */
    private static KeyGeometry.Key neighbour(KeyGeometry keys, KeyGeometry.Key k, Random r) {
        List<KeyGeometry.Key> near = new ArrayList<>();
        for (KeyGeometry.Key o : keys.keys()) {
            if (o.code == k.code || !Character.isLetter(o.code)) continue;
            int dx = Math.abs(o.centerX() - k.centerX()), dy = Math.abs(o.centerY() - k.centerY());
            if (dx <= k.width * 1.1 && dy <= k.height * 1.1) near.add(o);
        }
        if (near.isEmpty()) {
            // A letter on its own (Arabic ذ left of the digits): the nearest letter key.
            KeyGeometry.Key best = null;
            long bestD = Long.MAX_VALUE;
            for (KeyGeometry.Key o : keys.keys()) {
                if (o.code == k.code || !Character.isLetter(o.code)) continue;
                long dx = o.centerX() - k.centerX(), dy = o.centerY() - k.centerY();
                if (dx * dx + dy * dy < bestD) {
                    bestD = dx * dx + dy * dy;
                    best = o;
                }
            }
            near.add(best);
        }
        return near.get(r.nextInt(near.size()));
    }

    private static Typed noisyTaps(String word, Layout layout, Random r) {
        StringBuilder text = new StringBuilder();
        int[] xs = new int[word.length()], ys = new int[word.length()];
        for (int i = 0; i < word.length(); i++) {
            KeyGeometry.Key k = layout.tap(word.charAt(i));
            int x = (int) Math.round(k.centerX() + r.nextGaussian() * k.width / 3.0);
            int y = (int) Math.round(k.centerY() + r.nextGaussian() * k.height / 3.0);
            KeyGeometry.Key hit = keyAt(layout.keys, x, y);
            // Off the letter keys, or the key the letter is on (a shifted letter): a hit.
            if (hit == null || (hit.x == k.x && hit.y == k.y)) hit = k;
            text.append((char) hit.code);
            xs[i] = x;
            ys[i] = y;
        }
        return new Typed(text.toString(), xs, ys);
    }

    private static KeyGeometry.Key keyAt(KeyGeometry keys, int x, int y) {
        for (KeyGeometry.Key k : keys.keys()) {
            if (x >= k.x && x < k.x + k.width && y >= k.y && y < k.y + k.height) return k;
        }
        return null;
    }
}
