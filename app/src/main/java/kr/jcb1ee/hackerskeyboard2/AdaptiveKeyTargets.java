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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Adaptive key targets: the invisible hit area of each letter key grows or shrinks
 * with how likely that letter is to come next in the word being typed.
 *
 * <p>A touch near the border between two letter keys is resolved by comparing, for each
 * candidate key, how far the touch is from the key's centre (a Gaussian touch model)
 * with how likely the key's letter is to follow the letters already typed (from a word
 * frequency list, plus words the user has typed when learning is on). A touch in the
 * middle of a key always gives that key, and a key's hit area can never reach more
 * than a fixed fraction of a key into its neighbour, so unusual words and plain
 * mistakes stay typeable.
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
     * List frequencies are AOSP's 0-255 log scale; 2^(f/10) puts "the" (222) about 10^4.5
     * times above a rare word (70), close to real usage.
     */
    private static final double FREQ_DIVISOR = 10.0;
    /** Weight of one use of a learned word: about as common as "yes" (f=160). */
    private static final double USER_WORD_WEIGHT = Math.pow(2, 160 / FREQ_DIVISOR);
    /** At the start of a word there is no context, so the bias is gentler. */
    private static final double WORD_START_STRENGTH = 0.5;
    /** Floor mixed into every letter's probability, so no key ever becomes unreachable. */
    private static final double PRIOR_FLOOR = 0.03;
    /** Standard deviation of the touch model, in key widths/heights. */
    private static final double TOUCH_SIGMA = 0.38;
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz'";

    private static AdaptiveKeyTargets sInstance;

    static synchronized AdaptiveKeyTargets get(Context context) {
        if (sInstance == null) sInstance = new AdaptiveKeyTargets(context.getApplicationContext());
        return sInstance;
    }

    /** Null until the IME has created the instance; detectors then do nothing. */
    static AdaptiveKeyTargets peek() {
        return sInstance;
    }

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Settings
    private boolean mEnabledPref;
    private boolean mLearnPref;
    private double mStrength = 0.6;   // weight of log-probability against touch distance
    private double mMaxGrow = 0.25;   // how far (in keys) a hit area may reach into a neighbour

    // Static word list: sorted words with prefix sums of their weights.
    private volatile String[] mWords;
    private volatile double[] mCumWeight;
    private String mListLanguage;

    // Learned words (word -> count), only touched on the main thread.
    private final TreeMap<String, Integer> mUserWords = new TreeMap<>();
    private boolean mUserWordsLoaded;
    private boolean mUserWordsDirty;

    // Per-field state
    private boolean mFieldAllowsAdaptive;
    private boolean mFieldAllowsLearning;
    private boolean mModifierActive;
    private String mLanguage = "";

    // Current word before the cursor, refreshed at touch down and up.
    private String mPrefix = "";
    private InputConnection mInputConnection;
    // Letters typed in sequence by the keyboard since the last word boundary or cursor jump.
    private int mTypedRun;

    // Cache of the last next-letter distribution.
    private String mCachedPrefix;
    private double[] mCachedProbs;

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
        if (mEnabledPref) ensureWordList();
    }

    // ---------------------------------------------------------------- editor state

    void onStartInput(EditorInfo info, InputConnection ic, String inputLanguage) {
        mInputConnection = ic;
        mLanguage = inputLanguage == null ? "" : inputLanguage;
        mTypedRun = 0;
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
        mInputConnection = null;
        flushUserWords();
    }

    void setModifierActive(boolean active) {
        mModifierActive = active;
    }

    /** True when touches should currently be biased. */
    boolean isActive() {
        return mEnabledPref && mFieldAllowsAdaptive && !mModifierActive
                && mWords != null && mLanguage.startsWith(mListLanguage == null ? "en" : mListLanguage);
    }

    /** Re-reads the word before the cursor; called on touch down and before a key is sent. */
    void refreshPrefix() {
        if (!mEnabledPref || !mFieldAllowsAdaptive) {
            mPrefix = "";
            return;
        }
        InputConnection ic = mInputConnection;
        CharSequence before = ic == null ? null : ic.getTextBeforeCursor(MAX_PREFIX + 1, 0);
        mPrefix = trailingWord(before);
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
            mTypedRun++;
        } else if (code == Keyboard.KEYCODE_DELETE) {
            onBackspace();
        }
    }

    void onBackspace() {
        if (mTypedRun > 0) mTypedRun--;
    }

    /** The cursor moved somewhere the keyboard did not put it. */
    void onCursorJump() {
        mTypedRun = 0;
    }

    /**
     * Called before a separator is typed. Learns the word just finished if the keyboard
     * typed all of its letters in one run (not a word the cursor was merely placed after).
     */
    void onWordBoundary() {
        if (mEnabledPref && mLearnPref && mFieldAllowsLearning && !mModifierActive) {
            InputConnection ic = mInputConnection;
            String word = trailingWord(ic == null ? null : ic.getTextBeforeCursor(MAX_PREFIX + 1, 0));
            if (word.length() >= 2 && word.length() <= mTypedRun && word.length() < MAX_PREFIX) {
                learn(word);
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
        if (letterOf(keys[geometricIndex]) < 0) return geometricIndex; // only letters compete
        double[] probs = nextLetterProbabilities(mPrefix);
        if (probs == null) return geometricIndex; // no known word continues this prefix

        double strength = mPrefix.isEmpty() ? mStrength * WORD_START_STRENGTH : mStrength;
        int best = geometricIndex;
        double bestScore = Double.NEGATIVE_INFINITY;
        double reach = 0.5 + mMaxGrow;
        for (int index : candidates) {
            Keyboard.Key key = keys[index];
            int letter = letterOf(key);
            if (letter < 0 || key.width <= 0 || key.height <= 0) continue;
            double dx = (touchX - (key.x + key.width / 2.0)) / key.width;
            double dy = (touchY - (key.y + key.height / 2.0)) / key.height;
            if (Math.abs(dx) > reach || Math.abs(dy) > reach) continue;
            double score = -(dx * dx + dy * dy) / (2 * TOUCH_SIGMA * TOUCH_SIGMA)
                    + strength * Math.log(probs[letter]);
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }

    /** Index into {@link #LETTERS} for a letter key, or -1. */
    private static int letterOf(Keyboard.Key key) {
        if (key == null || key.codes == null || key.codes.length == 0) return -1;
        return LETTERS.indexOf(Character.toLowerCase(key.codes[0]));
    }

    private static boolean isWordChar(int code) {
        return Character.isLetter(code) || code == '\'';
    }

    /** The lower-cased letters immediately before the cursor (empty at a word boundary). */
    private static String trailingWord(CharSequence text) {
        if (text == null) return "";
        int end = text.length();
        int start = end;
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        if (end - start > MAX_PREFIX) return ""; // too long to be a word we can help with
        return text.subSequence(start, end).toString().toLowerCase();
    }

    // ---------------------------------------------------------------- language model

    /**
     * Probability of each letter in {@link #LETTERS} following the prefix, or null when
     * neither the list nor the learned words contain a word starting with it.
     */
    double[] nextLetterProbabilities(String prefix) {
        if (prefix.equals(mCachedPrefix)) return mCachedProbs;
        String[] words = mWords;
        double[] cum = mCumWeight;
        if (words == null) return null;
        ensureUserWordsLoaded();

        double[] mass = new double[LETTERS.length()];
        double total = 0;
        for (int i = 0; i < mass.length; i++) {
            String p = prefix + LETTERS.charAt(i);
            int lo = lowerBound(words, p);
            int hi = lowerBound(words, p + Character.MAX_VALUE);
            double m = cum[hi] - cum[lo];
            SortedMap<String, Integer> learned = mUserWords.subMap(p, p + Character.MAX_VALUE);
            for (int count : learned.values()) m += count * USER_WORD_WEIGHT;
            mass[i] = m;
            total += m;
        }
        double[] probs = null;
        if (total > 0) {
            probs = new double[mass.length];
            for (int i = 0; i < mass.length; i++) {
                probs[i] = (1 - PRIOR_FLOOR) * mass[i] / total + PRIOR_FLOOR / mass.length;
            }
        }
        mCachedPrefix = prefix;
        mCachedProbs = probs;
        return probs;
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

    private void ensureWordList() {
        if (mWords != null || mListLanguage != null) return;
        mListLanguage = "en";
        new Thread(() -> loadWordList("wordlists/en.txt"), "AdaptiveKeysLoad").start();
    }

    private void loadWordList(String asset) {
        long start = System.currentTimeMillis();
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
                weights.add(Math.pow(2, Integer.parseInt(line.substring(tab + 1).trim()) / FREQ_DIVISOR));
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
            mCumWeight = cum;
            mWords = sorted;
            mCachedPrefix = null;
            Log.i(TAG, "Loaded " + sorted.length + " words in "
                    + (System.currentTimeMillis() - start) + " ms");
        });
    }

    // ---------------------------------------------------------------- learned words

    private void learn(String word) {
        ensureUserWordsLoaded();
        Integer count = mUserWords.get(word);
        mUserWords.put(word, Math.min(MAX_USER_COUNT, count == null ? 1 : count + 1));
        if (mUserWords.size() > MAX_USER_WORDS) evictRarest();
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
                    mUserWords.put(line.substring(0, tab),
                            Math.min(MAX_USER_COUNT, Integer.parseInt(line.substring(tab + 1))));
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
        } catch (IOException e) {
            Log.w(TAG, "Could not save learned words", e);
            return;
        }
        if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
    }
}
