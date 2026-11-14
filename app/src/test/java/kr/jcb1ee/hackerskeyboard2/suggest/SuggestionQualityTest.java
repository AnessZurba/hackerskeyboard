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
 * <p>Run alone with {@code ./gradlew testDebugUnitTest --tests '*SuggestionQualityTest*'};
 * {@code -PsuggestWords=N} changes the sample size (default 1000).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class SuggestionQualityTest {
    private static final File REPORTS = new File("build/reports");

    private enum Error { NONE, NEIGHBOUR, OMISSION, TRANSPOSITION, EXTRA, NOISY_TAPS, VARIANT, VARIANT_WORD }

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

        Layout(String locale) {
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
            return k != null ? k : taps.get(Character.toLowerCase(c));
        }
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

    /**
     * Runs every error kind on {@code locale}'s layout; {@code variants} are the letter
     * variants as {variant, typed instead} pairs.
     */
    private Map<Error, Score> measure(String locale, String layoutLocale, String wordlist,
            String dictName, int[][] variants, String reportName) throws IOException {
        AospSuggestionEngine engine = SuggestTestSupport.engine(locale);
        Layout layout = new Layout(layoutLocale);
        KeyGeometry keys = layout.keys;
        int n = Integer.getInteger("suggestWords", 1000);
        List<String> words = sampleWords(engine, layout,
                new File("src/main/assets/wordlists/" + wordlist), n);
        Random random = new Random(20261114);
        Map<Error, Score> scores = new LinkedHashMap<>();
        // Warm up (page in the dictionary, JIT) so the latencies are steady state.
        for (int i = 0; i < 200; i++) {
            engine.suggestions(words.get(i % words.size()), null, null, null, keys, 3);
        }
        for (Error error : Error.values()) {
            boolean variant = error == Error.VARIANT || error == Error.VARIANT_WORD;
            if (variant && variants.length == 0) continue;
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
            scores.put(error, score);
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
            String[] parts = line.split("\t");
            entries.add(parts);
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
                int[] pick = at.get(random.nextInt(at.size()));
                s.setCharAt(pick[0], (char) pick[1]);
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
