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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Measures the decoder on synthetic typing errors made on our real English 5-row layout:
 * takes common words from the word list, mistypes them, and reports how often the intended
 * word is the first suggestion (top-1) or among the first three (top-3), and how long a
 * query takes. The report goes to app/build/reports/suggestion-quality.txt and stdout.
 *
 * <p>Error kinds: none (taps at key centres), a neighbouring key instead of the right one,
 * a letter left out, two letters swapped, an extra neighbouring letter, and "noisy" taps
 * (each tap displaced by Gaussian noise of a third of a key; the letter typed is the key
 * under the tap, as on a real keyboard). Deterministic: fixed seed.
 *
 * <p>Run alone with {@code ./gradlew testDebugUnitTest --tests '*SuggestionQualityTest*'};
 * {@code -PsuggestWords=N} changes the sample size (default 1000).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class SuggestionQualityTest {
    private static final File WORDLIST = new File("src/main/assets/wordlists/en.txt");
    private static final File REPORT = new File("build/reports/suggestion-quality.txt");

    private enum Error { NONE, NEIGHBOUR, OMISSION, TRANSPOSITION, EXTRA, NOISY_TAPS }

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

    @Test
    public void measure() throws IOException {
        AospSuggestionEngine engine = SuggestTestSupport.englishEngine();
        KeyGeometry keys = SuggestTestSupport.englishFullLayout();
        int n = Integer.getInteger("suggestWords", 1000);
        List<String> words = sampleWords(engine, keys, n);
        Random random = new Random(20261114);
        Map<Error, Score> scores = new LinkedHashMap<>();
        // Warm up (page in the dictionary, JIT) so the latencies are steady state.
        for (int i = 0; i < 200; i++) {
            engine.suggestions(words.get(i % words.size()), null, null, null, keys, 3);
        }
        for (Error error : Error.values()) {
            Score score = new Score();
            for (String word : words) {
                Typed typed = mistype(word, error, keys, random);
                if (typed == null) continue;
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
        report.append(String.format(Locale.ROOT, "AOSP decoder, main_en.dict, kbd_full at %dx%d px,"
                + " %d words (length 3-12, frequency rank < 20000)%n",
                keys.width, keys.height, words.size()));
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
        REPORT.getParentFile().mkdirs();
        Files.write(REPORT.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));

        // Loose floors, to catch a broken engine or layout mapping, not to tune against.
        assertTrue(report.toString(), rate(scores.get(Error.NONE), true) > 0.90);
        assertTrue(report.toString(), rate(scores.get(Error.NEIGHBOUR), false) > 0.60);
        assertTrue(report.toString(), rate(scores.get(Error.TRANSPOSITION), false) > 0.60);
    }

    private static double rate(Score s, boolean top1) {
        return (double) (top1 ? s.top1 : s.top3) / s.n;
    }

    /**
     * Every k-th of the 20000 most frequent words of 3-12 letters that the dictionary has in
     * lower case and that the layout can type, n in all.
     */
    private static List<String> sampleWords(AospSuggestionEngine engine, KeyGeometry keys, int n)
            throws IOException {
        List<String[]> entries = new ArrayList<>();
        for (String line : Files.readAllLines(WORDLIST.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] parts = line.split("\t");
            entries.add(parts);
        }
        entries.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        List<String> pool = new ArrayList<>();
        for (String[] e : entries) {
            if (pool.size() >= 20000) break;
            String w = e[0];
            if (w.length() < 3 || w.length() > 12 || !typeable(w, keys)) continue;
            if (!engine.isValidWord(w) || !w.equals(w.toLowerCase(Locale.ROOT))) continue;
            pool.add(w);
        }
        List<String> out = new ArrayList<>();
        int step = Math.max(1, pool.size() / n);
        for (int i = 0; i < pool.size() && out.size() < n; i += step) out.add(pool.get(i));
        return out;
    }

    private static boolean typeable(String w, KeyGeometry keys) {
        for (int i = 0; i < w.length(); i++) {
            if (!Character.isLetter(w.charAt(i)) || keys.keyFor(w.charAt(i)) == null) return false;
        }
        return true;
    }

    private static Typed mistype(String word, Error error, KeyGeometry keys, Random random) {
        StringBuilder s = new StringBuilder(word);
        int len = word.length();
        switch (error) {
            case NONE:
                break;
            case NEIGHBOUR: {
                int i = random.nextInt(len);
                KeyGeometry.Key k = neighbour(keys, keys.keyFor(word.charAt(i)), random);
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
                KeyGeometry.Key k = neighbour(keys, keys.keyFor(word.charAt(i)), random);
                s.insert(i + random.nextInt(2), (char) k.code);
                break;
            }
            case NOISY_TAPS:
                return noisyTaps(word, keys, random);
        }
        String text = s.toString();
        int[] xs = new int[text.length()], ys = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            KeyGeometry.Key k = keys.keyFor(text.charAt(i));
            xs[i] = k.centerX();
            ys[i] = k.centerY();
        }
        return new Typed(text, xs, ys);
    }

    /** A key adjacent to {@code k} (sharing an edge or corner, within a key's width). */
    private static KeyGeometry.Key neighbour(KeyGeometry keys, KeyGeometry.Key k, Random r) {
        List<KeyGeometry.Key> near = new ArrayList<>();
        for (KeyGeometry.Key o : keys.keys()) {
            if (o == k || !Character.isLetter(o.code)) continue;
            int dx = Math.abs(o.centerX() - k.centerX()), dy = Math.abs(o.centerY() - k.centerY());
            if (dx <= k.width * 1.1 && dy <= k.height * 1.1) near.add(o);
        }
        return near.get(r.nextInt(near.size()));
    }

    private static Typed noisyTaps(String word, KeyGeometry keys, Random r) {
        StringBuilder text = new StringBuilder();
        int[] xs = new int[word.length()], ys = new int[word.length()];
        for (int i = 0; i < word.length(); i++) {
            KeyGeometry.Key k = keys.keyFor(word.charAt(i));
            int x = (int) Math.round(k.centerX() + r.nextGaussian() * k.width / 3.0);
            int y = (int) Math.round(k.centerY() + r.nextGaussian() * k.height / 3.0);
            KeyGeometry.Key hit = keyAt(keys, x, y);
            if (hit == null) hit = k; // off the letter keys: count it as a hit
            text.append((char) hit.code);
            xs[i] = x;
            ys[i] = y;
        }
        return new Typed(text.toString(), Arrays.copyOf(xs, xs.length), ys);
    }

    private static KeyGeometry.Key keyAt(KeyGeometry keys, int x, int y) {
        for (KeyGeometry.Key k : keys.keys()) {
            if (x >= k.x && x < k.x + k.width && y >= k.y && y < k.y + k.height) return k;
        }
        return null;
    }
}
