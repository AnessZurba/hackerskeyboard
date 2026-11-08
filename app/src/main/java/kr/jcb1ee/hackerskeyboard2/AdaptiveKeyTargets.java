/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adaptive key targets: the invisible hit area of each letter key grows or shrinks
 * with how likely that letter is to come next in the word being typed.
 *
 * <p>A touch near the border between two letter keys is resolved by comparing, for each
 * candidate key, how far the touch is from the key's centre (a Gaussian touch model)
 * with how likely the key's letter is to follow the letters already typed (from a word
 * frequency list for the input language, plus words the user has typed when learning is
 * on). A key's hit area can never reach more than a fixed fraction of a key into its
 * neighbour, so unusual words and plain mistakes stay typeable.
 *
 * <p>Word lists exist for English, Arabic and Hebrew (assets/wordlists, built by
 * tools/mkwordlist.py). Text is compared after NFKC normalisation, lower-casing and
 * removal of combining marks and tatweel, so presentation forms such as the Arabic
 * lam-alef key (U+FEFB) count as their letters and tashkeel/niqqud are ignored.
 *
 * <p>It stays off where words are not expected: password and number fields, editors
 * that report no input type (terminals such as Termux and ConnectBot), while Ctrl,
 * Alt or Meta is active, and for input languages without a word list.
 */
final class AdaptiveKeyTargets {
    private static final String TAG = "HK/AdaptiveKeys";

    static final String PREF_ENABLED = "pref_adaptive_targets";
    static final String PREF_STRENGTH = "pref_adaptive_strength";
    static final String PREF_LEARN = "pref_adaptive_learn";
    static final String PREF_CLEAR = "pref_adaptive_clear";

    private static final String USER_WORDS_FILE = "adaptive_user_words.tsv";
    private static final int MAX_PREFIX = 24;
    private static final int MAX_USER_WORDS = 5000;
    private static final int MAX_USER_COUNT = 50;
    /**
     * A learned word that is not in the word list only counts once it has been typed this
     * many times, so a typo left in once or twice never starts attracting touches.
     */
    private static final int MIN_USES_FOR_NEW_WORD = 3;
    /** A finished word waits this long (or until the next word) before it is learned. */
    private static final long LEARN_DELAY_MS = 8000;
    /** Each correction of a word cancels this many uses of it. */
    private static final int CORRECTION_PENALTY = 2;
    /** Learned-words file lines starting with this hold corrections, not uses. */
    private static final String CORRECTION_PREFIX = "!";
    /**
     * List frequencies are a 0-255 log scale; 2^(f/10) puts "the" (222) about 10^4.5
     * times above a rare word (70), close to real usage.
     */
    private static final double FREQ_DIVISOR = 10.0;
    /** Weight of one use of a learned word: about as common as "yes" (f=160). */
    private static final double USER_WORD_WEIGHT = Math.pow(2, 160 / FREQ_DIVISOR);
    /** At the start of a word there is no context, so the bias is gentler. */
    private static final double WORD_START_STRENGTH = 0.5;
    /** Floor mixed into every letter's probability, so no key ever becomes unreachable. */
    private static final double PRIOR_FLOOR = 0.03;
    /** Rough alphabet size the floor is spread over. */
    private static final double ALPHABET_SIZE = 32;
    /** Standard deviation of the touch model, in key widths/heights. */
    private static final double TOUCH_SIGMA = 0.38;
    private static final char TATWEEL = 'ـ';

    private static AdaptiveKeyTargets sInstance;

    static synchronized AdaptiveKeyTargets get(Context context) {
        if (sInstance == null) sInstance = new AdaptiveKeyTargets(context.getApplicationContext());
        return sInstance;
    }

    /** Null until the IME has created the instance; detectors then do nothing. */
    static AdaptiveKeyTargets peek() {
        return sInstance;
    }

    /** Word-list asset for an input language code ("en_US", "ar", "iw"...), or null. */
    static String wordListFor(String language) {
        if (language == null || language.length() < 2) return null;
        switch (language.substring(0, 2).toLowerCase()) {
            case "en": return "en";
            case "ar": return "ar";
            case "iw": case "he": return "he";
            default: return null;
        }
    }

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Settings
    private boolean mEnabledPref;
    private boolean mLearnPref;
    private double mStrength = 0.6;   // weight of log-probability against touch distance
    private double mMaxGrow = 0.25;   // how far (in keys) a hit area may reach into a neighbour

    // Word list of the current input language: sorted words with prefix sums of weights.
    private String[] mWords;
    private double[] mCumWeight;
    private String mLoadedList;      // list in mWords, or null
    private String mWantedList;      // list for the current input language, or null
    private int mLoadGeneration;

    // Learned words (word -> count), all languages in one map; main thread only.
    private final TreeMap<String, Integer> mUserWords = new TreeMap<>();
    // Words the user backspaced into right after finishing them (word -> times).
    private final TreeMap<String, Integer> mCorrections = new TreeMap<>();
    // The last finished word, learned after LEARN_DELAY_MS unless it is corrected first.
    private String mPendingWord;
    private boolean mUserWordsLoaded;
    private boolean mUserWordsDirty;

    // Per-field state
    private boolean mFieldAllowsAdaptive;
    private boolean mFieldAllowsLearning;
    private boolean mModifierActive;

    // Current word before the cursor (normalised), refreshed at touch down and up.
    private String mPrefix = "";
    private InputConnection mInputConnection;
    // Letters typed in sequence by the keyboard since the last word boundary or cursor jump.
    private int mTypedRun;

    // Probabilities already computed for mCachedPrefix, by key text.
    private String mCachedPrefix;
    private double mCachedTotal;
    private final HashMap<String, Double> mCachedProbs = new HashMap<>();

    private AdaptiveKeyTargets(Context context) {
        mContext = context;
    }

    // ---------------------------------------------------------------- settings

    void loadSettings(SharedPreferences prefs) {
        mEnabledPref = prefs.getBoolean(PREF_ENABLED,
                mContext.getResources().getBoolean(R.bool.default_adaptive_targets));
        mLearnPref = prefs.getBoolean(PREF_LEARN,
                mContext.getResources().getBoolean(R.bool.default_adaptive_learn));
        String strength = prefs.getString(PREF_STRENGTH,
                mContext.getString(R.string.default_adaptive_strength));
        switch (strength) {
            case "low":
                mStrength = 0.4;
                mMaxGrow = 0.15;
                break;
            case "high":
                mStrength = 0.9;
                mMaxGrow = 0.35;
                break;
            default:
                mStrength = 0.6;
                mMaxGrow = 0.25;
                break;
        }
        loadWantedList();
    }

    /** The input language changed (or a new field started). */
    void setLanguage(String inputLanguage) {
        String wanted = wordListFor(inputLanguage);
        if (wanted == null ? mWantedList == null : wanted.equals(mWantedList)) return;
        mWantedList = wanted;
        loadWantedList();
    }

    // ---------------------------------------------------------------- editor state

    void onStartInput(EditorInfo info, InputConnection ic, String inputLanguage) {
        mInputConnection = ic;
        mTypedRun = 0;
        setLanguage(inputLanguage);
        int type = info == null ? InputType.TYPE_NULL : info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        boolean password = cls == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        // TYPE_NULL is what terminals report: what is typed there is commands, not words.
        mFieldAllowsAdaptive = cls == InputType.TYPE_CLASS_TEXT && !password;
        boolean noLearning = info != null
                && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        mFieldAllowsLearning = mFieldAllowsAdaptive && !noLearning
                && variation != InputType.TYPE_TEXT_VARIATION_URI
                && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        refreshPrefix();
    }

    void onFinishInput() {
        commitPendingWord();
        mInputConnection = null;
        flushUserWords();
    }

    void setModifierActive(boolean active) {
        mModifierActive = active;
    }

    /** True when touches should currently be biased. */
    boolean isActive() {
        return mEnabledPref && mFieldAllowsAdaptive && !mModifierActive
                && mWords != null && mLoadedList != null && mLoadedList.equals(mWantedList);
    }

    /** Re-reads the word before the cursor; called on touch down and before a key is sent. */
    void refreshPrefix() {
        if (!mEnabledPref || !mFieldAllowsAdaptive) {
            mPrefix = "";
            return;
        }
        mPrefix = wordBeforeCursor();
    }

    private String wordBeforeCursor() {
        InputConnection ic = mInputConnection;
        return trailingWord(ic == null ? null : ic.getTextBeforeCursor(MAX_PREFIX * 2, 0));
    }

    /**
     * Called for every key, before LatinIME handles it. A separator ends the word, which
     * must be read now, before the separator (or an autocorrection) is committed.
     */
    void beforeKey(int code) {
        if (code == '\n' || (code >= 32 && !isWordChar(code))) onWordBoundary();
    }

    /** Called for every key, after LatinIME has handled it. */
    void afterKey(int code) {
        if (code > 0 && isWordChar(code)) {
            mTypedRun += normalise(new String(Character.toChars(code))).length();
        } else if (code == Keyboard.KEYCODE_DELETE) {
            if (mTypedRun > 0) {
                mTypedRun--;
            } else if (mPendingWord != null) {
                // Backspacing over the separator into the word just finished: it was
                // probably a typo. Don't learn it, and remember the correction.
                recordCorrection(mPendingWord);
                mPendingWord = null;
                mMainHandler.removeCallbacks(mCommitPendingRunnable);
            }
        }
    }

    /** The cursor moved somewhere the keyboard did not put it. */
    void onCursorJump() {
        mTypedRun = 0;
    }

    /**
     * Learns the word just finished if the keyboard typed all of its letters in one run
     * (not a word the cursor was merely placed after).
     */
    private void onWordBoundary() {
        if (mEnabledPref && mLearnPref && mFieldAllowsLearning && !mModifierActive) {
            String word = wordBeforeCursor();
            if (word.length() >= 2 && word.length() <= mTypedRun && word.length() < MAX_PREFIX) {
                commitPendingWord(); // the previous word survived: the user moved on
                mPendingWord = word;
                mMainHandler.removeCallbacks(mCommitPendingRunnable);
                mMainHandler.postDelayed(mCommitPendingRunnable, LEARN_DELAY_MS);
            }
        }
        mTypedRun = 0;
    }

    // ---------------------------------------------------------------- key choice

    /**
     * Picks the key for a touch among the letter keys near it.
     *
     * @param keys all keys of the keyboard
     * @param candidates indices of the keys near the touch
     * @param geometricIndex the key the touch would give without adaptation
     * @return the key index to use
     */
    int chooseKey(Keyboard.Key[] keys, int[] candidates, int touchX, int touchY,
            int geometricIndex) {
        if (!isActive() || geometricIndex < 0 || geometricIndex >= keys.length) {
            return geometricIndex;
        }
        if (keyText(keys[geometricIndex]) == null) return geometricIndex; // only letters compete
        if (continuationMass(mPrefix) <= 0) return geometricIndex; // no known word goes on

        double strength = mPrefix.isEmpty() ? mStrength * WORD_START_STRENGTH : mStrength;
        double reach = 0.5 + mMaxGrow;
        int best = geometricIndex;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index : candidates) {
            Keyboard.Key key = keys[index];
            String text = keyText(key);
            if (text == null || key.width <= 0 || key.height <= 0) continue;
            double dx = (touchX - (key.x + key.width / 2.0)) / key.width;
            double dy = (touchY - (key.y + key.height / 2.0)) / key.height;
            if (Math.abs(dx) > reach || Math.abs(dy) > reach) continue;
            double score = -(dx * dx + dy * dy) / (2 * TOUCH_SIGMA * TOUCH_SIGMA)
                    + strength * Math.log(probability(mPrefix, text));
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }

    /** The normalised letters a key types, or null if it is not a letter key. */
    private static String keyText(Keyboard.Key key) {
        if (key == null || key.codes == null || key.codes.length == 0) return null;
        int code = key.codes[0];
        if (code <= 0 || !Character.isLetter(code)) return null;
        String text = normalise(new String(Character.toChars(code)));
        return text.isEmpty() ? null : text;
    }

    private static boolean isWordChar(int code) {
        if (code == '\'' || code == TATWEEL || Character.isLetter(code)) return true;
        int type = Character.getType(code);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /** NFKC, lower case, without combining marks (tashkeel, niqqud, accents) or tatweel. */
    static String normalise(CharSequence text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (cp == TATWEEL || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    /** The normalised word immediately before the cursor (empty at a word boundary). */
    private static String trailingWord(CharSequence text) {
        if (text == null) return "";
        int start = text.length();
        while (start > 0) {
            int cp = Character.codePointBefore(text, start);
            if (!isWordChar(cp)) break;
            start -= Character.charCount(cp);
        }
        String word = normalise(text.subSequence(start, text.length()));
        if (word.length() > MAX_PREFIX) return ""; // too long to be a word we can help with
        // A leading apostrophe is an opening quote, not part of the word.
        while (word.startsWith("'")) word = word.substring(1);
        return word;
    }

    // ---------------------------------------------------------------- language model

    /** Weight of all words that continue the prefix with at least one more letter. */
    private double continuationMass(String prefix) {
        if (!prefix.equals(mCachedPrefix)) {
            mCachedPrefix = prefix;
            mCachedProbs.clear();
            mCachedTotal = mass(prefix) - exactWeight(prefix);
        }
        return mCachedTotal;
    }

    /** Probability that the next letters after the prefix are the key's text. */
    private double probability(String prefix, String keyText) {
        double total = continuationMass(prefix);
        Double cached = mCachedProbs.get(keyText);
        if (cached != null) return cached;
        double p = (1 - PRIOR_FLOOR) * mass(prefix + keyText) / total + PRIOR_FLOOR / ALPHABET_SIZE;
        mCachedProbs.put(keyText, p);
        return p;
    }

    /** Total weight of list and learned words starting with the prefix. */
    private double mass(String prefix) {
        String[] words = mWords;
        double m = 0;
        if (words != null) {
            String end = prefix + Character.MAX_VALUE;
            m = mCumWeight[lowerBound(words, end)] - mCumWeight[lowerBound(words, prefix)];
        }
        ensureUserWordsLoaded();
        for (Map.Entry<String, Integer> e
                : mUserWords.subMap(prefix, prefix + Character.MAX_VALUE).entrySet()) {
            m += learnedWeight(e.getKey(), e.getValue());
        }
        return Math.max(0, m - correctionPenalty(prefix));
    }

    /** Weight of the prefix itself as a whole word. */
    private double exactWeight(String word) {
        double w = 0;
        String[] words = mWords;
        if (words != null) {
            int i = lowerBound(words, word);
            if (i < words.length && words[i].equals(word)) w = mCumWeight[i + 1] - mCumWeight[i];
        }
        ensureUserWordsLoaded();
        Integer count = mUserWords.get(word);
        if (count != null) w += learnedWeight(word, count);
        Integer corrections = mCorrections.get(word);
        if (corrections != null) w -= listWeight(word) * (1 - 1.0 / (1 + corrections));
        return Math.max(0, w);
    }

    /**
     * Weight of a learned word. Corrections cancel uses; a word outside the list counts
     * only once it has enough uses left.
     */
    private double learnedWeight(String word, int count) {
        Integer corrections = mCorrections.get(word);
        int uses = count - CORRECTION_PENALTY * (corrections == null ? 0 : corrections);
        if (uses <= 0) return 0;
        if (uses < MIN_USES_FOR_NEW_WORD && !inWordList(word)) return 0;
        return uses * USER_WORD_WEIGHT;
    }

    /** Weight taken away from list words the user keeps correcting, within a prefix. */
    private double correctionPenalty(String prefix) {
        double penalty = 0;
        for (Map.Entry<String, Integer> e
                : mCorrections.subMap(prefix, prefix + Character.MAX_VALUE).entrySet()) {
            double w = listWeight(e.getKey());
            if (w > 0) penalty += w * (1 - 1.0 / (1 + e.getValue()));
        }
        return penalty;
    }

    private double listWeight(String word) {
        String[] words = mWords;
        if (words == null) return 0;
        int i = lowerBound(words, word);
        return i < words.length && words[i].equals(word) ? mCumWeight[i + 1] - mCumWeight[i] : 0;
    }

    private boolean inWordList(String word) {
        String[] words = mWords;
        if (words == null) return false;
        int i = lowerBound(words, word);
        return i < words.length && words[i].equals(word);
    }

    private static int lowerBound(String[] words, String key) {
        int lo = 0, hi = words.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (words[mid].compareTo(key) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Loads the list for the current language in the background, dropping any other. */
    private void loadWantedList() {
        if (!mEnabledPref || mWantedList == null) return;
        if (mWantedList.equals(mLoadedList)) return;
        final String list = mWantedList;
        final int generation = ++mLoadGeneration;
        mWords = null;
        mCumWeight = null;
        mLoadedList = null;
        mCachedPrefix = null;
        new Thread(() -> loadWordList(list, generation), "AdaptiveKeysLoad").start();
    }

    private void loadWordList(String list, int generation) {
        long start = System.currentTimeMillis();
        String asset = "wordlists/" + list + ".txt";
        ArrayList<String> words = new ArrayList<>(60000);
        ArrayList<Double> weights = new ArrayList<>(60000);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                mContext.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                words.add(line.substring(0, tab));
                weights.add(Math.pow(2,
                        Integer.parseInt(line.substring(tab + 1).trim()) / FREQ_DIVISOR));
            }
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Could not load " + asset, e);
            return;
        }
        String[] w = words.toArray(new String[0]);
        // The asset is sorted, but don't rely on the generator's collation.
        Integer[] order = new Integer[w.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> w[a].compareTo(w[b]));
        String[] sorted = new String[w.length];
        double[] cum = new double[w.length + 1];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = w[order[i]];
            cum[i + 1] = cum[i] + weights.get(order[i]);
        }
        mMainHandler.post(() -> {
            if (generation != mLoadGeneration) return; // language changed meanwhile
            mCumWeight = cum;
            mWords = sorted;
            mLoadedList = list;
            mCachedPrefix = null;
            Log.i(TAG, "Loaded " + sorted.length + " " + list + " words in "
                    + (System.currentTimeMillis() - start) + " ms");
        });
    }

    // ---------------------------------------------------------------- learned words

    private final Runnable mCommitPendingRunnable = this::commitPendingWord;

    private void commitPendingWord() {
        mMainHandler.removeCallbacks(mCommitPendingRunnable);
        if (mPendingWord == null) return;
        String word = mPendingWord;
        mPendingWord = null;
        learn(word);
    }

    private void recordCorrection(String word) {
        ensureUserWordsLoaded();
        Integer count = mCorrections.get(word);
        mCorrections.put(word, Math.min(MAX_USER_COUNT, count == null ? 1 : count + 1));
        if (mCorrections.size() > MAX_USER_WORDS) mCorrections.remove(mCorrections.firstKey());
        markDirty();
    }

    private void learn(String word) {
        ensureUserWordsLoaded();
        Integer count = mUserWords.get(word);
        mUserWords.put(word, Math.min(MAX_USER_COUNT, count == null ? 1 : count + 1));
        if (mUserWords.size() > MAX_USER_WORDS) evictRarest();
        markDirty();
    }

    private void markDirty() {
        mUserWordsDirty = true;
        mCachedPrefix = null;
        mMainHandler.removeCallbacks(mFlushRunnable);
        mMainHandler.postDelayed(mFlushRunnable, 10000);
    }

    private void evictRarest() {
        String rarest = null;
        int min = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : mUserWords.entrySet()) {
            if (e.getValue() < min) {
                min = e.getValue();
                rarest = e.getKey();
            }
        }
        if (rarest != null) mUserWords.remove(rarest);
    }

    /** Forgets every learned word. */
    void clearLearnedWords() {
        mUserWords.clear();
        mCorrections.clear();
        mPendingWord = null;
        mMainHandler.removeCallbacks(mCommitPendingRunnable);
        mUserWordsLoaded = true;
        mUserWordsDirty = false;
        mCachedPrefix = null;
        mMainHandler.removeCallbacks(mFlushRunnable);
        //noinspection ResultOfMethodCallIgnored
        new File(mContext.getFilesDir(), USER_WORDS_FILE).delete();
    }

    int learnedWordCount() {
        ensureUserWordsLoaded();
        return mUserWords.size();
    }

    private final Runnable mFlushRunnable = this::flushUserWords;

    private void ensureUserWordsLoaded() {
        if (mUserWordsLoaded) return;
        mUserWordsLoaded = true;
        File file = new File(mContext.getFilesDir(), USER_WORDS_FILE);
        if (!file.exists()) return;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    int count = Math.min(MAX_USER_COUNT, Integer.parseInt(line.substring(tab + 1)));
                    if (line.startsWith(CORRECTION_PREFIX)) {
                        mCorrections.put(line.substring(CORRECTION_PREFIX.length(), tab), count);
                    } else {
                        mUserWords.put(line.substring(0, tab), count);
                    }
                } catch (NumberFormatException ignored) {
                    // skip a damaged line
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not read learned words", e);
        }
    }

    void flushUserWords() {
        if (!mUserWordsDirty) return;
        mUserWordsDirty = false;
        File file = new File(mContext.getFilesDir(), USER_WORDS_FILE);
        File tmp = new File(mContext.getFilesDir(), USER_WORDS_FILE + ".tmp");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Integer> e : mUserWords.entrySet()) {
                out.write(e.getKey());
                out.write('\t');
                out.write(Integer.toString(e.getValue()));
                out.write('\n');
            }
            for (Map.Entry<String, Integer> e : mCorrections.entrySet()) {
                out.write(CORRECTION_PREFIX);
                out.write(e.getKey());
                out.write('\t');
                out.write(Integer.toString(e.getValue()));
                out.write('\n');
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not save learned words", e);
            return;
        }
        if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
    }
}
