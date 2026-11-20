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

package kr.jcb1ee.hackerskeyboard2.suggest;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link SuggestionEngine} on the AOSP LatinIME decoder (libjni_latinime) and an AOSP-format
 * main dictionary (assets/dicts/main_LANG.dict, stored uncompressed and mapped in place, or
 * one the user imported into files/dicts, see {@link DictionaryFiles}).
 *
 * <p>Two more dictionaries join it, as in AOSP's DictionaryFacilitator, each searched by the
 * same decoder and the results merged by score: the personal one (Android's personal
 * dictionary and the words saved from the strip; an updatable dictionary held in memory and
 * rebuilt when they change) and the learned one ({@link LearnedWords}: AOSP's user history
 * dictionary, which also gives the next-word suggestions). A word in either counts as a word:
 * it is suggested and never auto-corrected.
 */
public final class AospSuggestionEngine implements SuggestionEngine {
    /** Where the main dictionaries are, inside the assets. */
    public static final String ASSET_DIR = "dicts";

    /**
     * How much more probable (on the dictionaries' 0-255 log scale, about 21 per tenfold)
     * a variant spelling must be to go before the typed word (see preferVariantSpelling).
     */
    static final int VARIANT_SPELLING_MARGIN = 20;

    private final String mLocale;
    private final LetterVariants mVariants;
    private BinaryDictionary mDict;
    /** Android's personal dictionary (word to probability); with the saved words, on memory. */
    private Map<String, Integer> mPersonalWords = Collections.emptyMap();
    private BinaryDictionary mPersonal;
    private int mPersonalBuiltFor = -1; // mLearned's generation the dictionary was built from
    private LearnedWords mLearned;
    /** The keys the decoder was last given, and them as the decoder sees them. */
    private KeyGeometry mKeys, mGeometry;
    private ProximityInfo mProximity;
    private KeyGeometry mDefaultGeometry;

    private AospSuggestionEngine(BinaryDictionary dict, String locale) {
        mDict = dict;
        mLocale = locale;
        mVariants = LetterVariants.forLocale(locale);
    }

    /** The asset name of the main dictionary for {@code language} ("en", "iw", ...). */
    public static String assetName(String language) {
        return ASSET_DIR + "/main_" + language + ".dict";
    }

    /**
     * The dictionary names to try for an input locale, most specific first: "en_US" gives
     * en_us, en; Hebrew is "iw" (AOSP's code) whether the locale says "he" or "iw";
     * pseudo-regions ("de_NE") fall back to the language.
     */
    public static List<String> dictionaryNames(String locale) {
        String full = locale.toLowerCase(Locale.ROOT).replace('-', '_');
        int sep = full.indexOf('_');
        String lang = sep >= 0 ? full.substring(0, sep) : full;
        String region = sep >= 0 ? full.substring(sep) : "";
        if (lang.equals("he")) lang = "iw";
        List<String> names = new ArrayList<>();
        if (!region.isEmpty()) names.add(lang + region);
        names.add(lang);
        return names;
    }

    /** Whether a dictionary for {@code locale} is bundled or imported (see {@link #dictionaryNames}). */
    public static boolean hasDictionary(Context context, String locale) {
        for (String name : dictionaryNames(locale)) {
            if (DictionaryFiles.isImported(context, name)) return true;
            try (AssetFileDescriptor fd = context.getAssets().openFd(assetName(name))) {
                return true;
            } catch (IOException e) {
                // not bundled under this name
            }
        }
        return false;
    }

    /**
     * Opens the dictionary for {@code locale} (see {@link #dictionaryNames}): for each name,
     * one the user imported ({@link DictionaryFiles}), else the bundled one. Returns null when
     * there is none, the native library is missing or the file is not a dictionary.
     */
    public static AospSuggestionEngine open(Context context, String locale) {
        if (!NativeLibrary.load()) return null;
        for (String name : dictionaryNames(locale)) {
            File imported = DictionaryFiles.file(context, name);
            if (imported.isFile()) {
                AospSuggestionEngine engine = open(imported, locale);
                if (engine != null) return engine;
            }
            try (AssetFileDescriptor fd = context.getAssets().openFd(assetName(name))) {
                String apk = context.getApplicationInfo().sourceDir;
                return open(apk, fd.getStartOffset(), fd.getLength(), locale);
            } catch (IOException e) {
                // Not bundled (or compressed in the APK): try the next name.
            }
        }
        return null;
    }

    /** Opens a dictionary file (tests, tools, downloaded dictionaries). */
    public static AospSuggestionEngine open(File file, String locale) {
        if (!NativeLibrary.load() || !file.isFile()) return null;
        return open(file.getAbsolutePath(), 0, file.length(), locale);
    }

    private static AospSuggestionEngine open(String path, long offset, long length,
            String locale) {
        BinaryDictionary dict = new BinaryDictionary(path, offset, length, locale);
        if (!dict.isValid()) {
            dict.close();
            return null;
        }
        return new AospSuggestionEngine(dict, locale);
    }

    public String getLocale() {
        return mLocale;
    }

    /** The dictionary header (dictionary, locale, version, description, date...). */
    public synchronized Map<String, String> getHeader() {
        return mDict != null ? mDict.getHeaderAttributes() : Collections.emptyMap();
    }

    @Override
    public List<Suggestion> suggestions(String typed, int[] xs, int[] ys,
            String previousWord, KeyGeometry keys, int max) {
        return suggestionsAfter(typed, xs, ys, WordContext.of(previousWord, false), keys, max);
    }

    /**
     * Like {@link #suggestions(String, int[], int[], String, KeyGeometry, int)}; the word
     * before steers only the learned words (the main dictionaries have no word pairs).
     */
    @Override
    public synchronized List<Suggestion> suggestionsAfter(String typed, int[] xs, int[] ys,
            WordContext previous, KeyGeometry keys, int max) {
        if (mDict == null || typed == null || typed.isEmpty() || max <= 0) {
            return Collections.emptyList();
        }
        ProximityInfo proximity = proximityFor(keys);
        LetterVariants.Input input = mVariants.input(typed, xs, ys, mGeometry);
        if (input.text.isEmpty()) return Collections.emptyList();
        typed = input.text;
        int[] codePoints = typed.codePoints().toArray();
        List<Suggestion> raw = mDict.getSuggestions(codePoints, input.xs, input.ys, proximity,
                null, true /* block offensive words */);
        BinaryDictionary personal = personal();
        if (personal != null || mLearned != null) {
            raw = new ArrayList<>(raw);
            if (personal != null) {
                raw.addAll(personal.getSuggestions(codePoints, input.xs, input.ys, proximity,
                        null, false));
            }
            if (mLearned != null) {
                raw.addAll(mLearned.suggestions(codePoints, input.xs, input.ys, proximity,
                        contextWord(previous)));
            }
        }
        // Native results come sorted; keep it explicit (stable for equal scores).
        List<Suggestion> sorted = new ArrayList<>(raw);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        CaseShape shape = CaseShape.of(typed);
        // The decoder ignores case, so "creek" can come back as "Creek" first; when the word
        // as typed is in a dictionary, its case variants become the typed word.
        boolean typedIsWord = isExactlyAWord(typed);
        Set<String> seen = new LinkedHashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : sorted) {
            String w = typedIsWord && s.word.equalsIgnoreCase(typed)
                    ? typed : shape.apply(s.word, mLocale);
            if (!seen.add(w)) continue;
            out.add(w.equals(s.word) ? s : s.withWord(w));
        }
        if (typedIsWord) {
            if (mDict.getProbability(typed) != BinaryDictionary.NOT_A_PROBABILITY) {
                preferVariantSpelling(typed, out);
            } else {
                typedFirst(typed, out);
            }
        }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /**
     * The user's own word typed exactly goes first: a learned word's probability comes from
     * how often it was typed among everything learned, often far below the main
     * dictionary's near misses ("Zanzibarr", typed on purpose, before "Zanzibar").
     */
    private static void typedFirst(String typed, List<Suggestion> out) {
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).word.equals(typed)) {
                if (i > 0) out.add(0, out.remove(i));
                return;
            }
        }
        int score = out.isEmpty() ? 0 : out.get(0).score;
        out.add(0, new Suggestion(typed, score,
                Suggestion.KIND_CORRECTION | Suggestion.FLAG_EXACT_MATCH));
    }

    /**
     * Typed words spelt with base letters for variants (Arabic "اكثر" for "أكثر", "انه" for
     * "أنه": common, and in the corpus too) are words, so the decoder puts them first. When
     * the variant spelling is much more frequent (by {@link #VARIANT_SPELLING_MARGIN}, about
     * ten times), it goes first instead and the typed word next to it. Only the order
     * changes: a typed word that is a word is never auto-corrected.
     */
    private void preferVariantSpelling(String typed, List<Suggestion> out) {
        if (out.isEmpty() || !out.get(0).word.equals(typed)) return;
        int typedProbability = mDict.getProbability(typed);
        for (int i = 1; i < out.size(); i++) {
            Suggestion s = out.get(i);
            if (mVariants.isVariantSpelling(typed, s.word)
                    && mDict.getProbability(s.word)
                            >= typedProbability + VARIANT_SPELLING_MARGIN) {
                out.remove(i);
                out.add(0, s);
                return;
            }
        }
    }

    @Override
    public synchronized boolean isValidWord(String word) {
        if (mDict == null || word == null) return false;
        word = mVariants.strip(word);
        if (word.isEmpty()) return false;
        if (isExactlyAWord(word)) return true;
        String lower = word.toLowerCase(localeObject());
        return !lower.equals(word) && isExactlyAWord(lower);
    }

    /** Whether {@code word}, exactly so, is in a dictionary or learned. */
    private boolean isExactlyAWord(String word) {
        return isWordElsewhere(word) || (mLearned != null && mLearned.contains(word));
    }

    /** Whether {@code word}, exactly so, is in the main or the personal dictionary. */
    private boolean isWordElsewhere(String word) {
        if (mDict.getProbability(word) != BinaryDictionary.NOT_A_PROBABILITY) return true;
        return mPersonalWords.containsKey(word)
                || (mLearned != null && mLearned.isSaved(word));
    }

    // ---------------------------------------------------------------- the user's words

    @Override
    public synchronized void attachLearnedWords(File dir) {
        if (mDict == null || mLearned != null) return;
        mLearned = LearnedWords.acquire(dir, mLocale);
        mPersonalBuiltFor = -1;
    }

    @Override
    public synchronized void setPersonalWords(Map<String, Integer> words) {
        mPersonalWords = new HashMap<>(words);
        mPersonalBuiltFor = -1;
    }

    /**
     * The personal dictionary, rebuilt when Android's personal dictionary or the saved words
     * changed (a few hundred words take a millisecond or two); null when both are empty.
     */
    private BinaryDictionary personal() {
        int generation = mLearned != null ? mLearned.generation() : 0;
        if (generation == mPersonalBuiltFor) return mPersonal;
        mPersonalBuiltFor = generation;
        if (mPersonal != null) mPersonal.close();
        mPersonal = null;
        Map<String, Integer> words = new HashMap<>(mPersonalWords);
        if (mLearned != null) {
            for (String w : mLearned.saved()) {
                if (!words.containsKey(w)) {
                    words.put(w, PersonalDictionary.DECODER_DEFAULT_FREQUENCY);
                }
            }
        }
        if (words.isEmpty()) return null;
        Map<String, String> attributes = new HashMap<>();
        attributes.put("dictionary", "user:" + mLocale.toLowerCase(Locale.ROOT));
        attributes.put("locale", mLocale);
        attributes.put("version", String.valueOf(LearnedWords.now()));
        BinaryDictionary d = BinaryDictionary.createInMemory(mLocale, attributes);
        if (!d.isValid()) {
            d.close();
            return null;
        }
        for (Map.Entry<String, Integer> e : words.entrySet()) {
            d.addUnigram(mVariants.strip(e.getKey()), e.getValue());
        }
        mPersonal = d;
        return d;
    }

    @Override
    public synchronized List<Suggestion> predictions(WordContext previous, int max) {
        if (mDict == null || mLearned == null || previous == null || !previous.hasWord()
                || max <= 0) {
            return Collections.emptyList();
        }
        List<Suggestion> raw = mLearned.predictions(contextWord(previous), proximityFor(mKeys));
        List<Suggestion> sorted = new ArrayList<>(raw);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        Set<String> seen = new LinkedHashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : sorted) {
            if (seen.add(s.word)) out.add(s);
            if (out.size() == max) break;
        }
        return out;
    }

    @Override
    public synchronized void learn(WordContext previous, String word,
            boolean capitalizedForSentence) {
        String w = learnable(word, capitalizedForSentence);
        if (w == null) return;
        mLearned.learn(contextWord(previous), w, isWordElsewhere(w), LearnedWords.now());
    }

    @Override
    public synchronized void unlearn(WordContext previous, String word,
            boolean capitalizedForSentence) {
        String w = learnable(word, capitalizedForSentence);
        String before = contextWord(previous);
        if (w == null || before == null) return;
        mLearned.unlearn(before, w);
    }

    @Override
    public synchronized void save(WordContext previous, String word,
            boolean capitalizedForSentence) {
        String w = learnable(word, capitalizedForSentence);
        if (w == null) return;
        mLearned.save(contextWord(previous), w, LearnedWords.now());
    }

    @Override
    public synchronized boolean forget(String word) {
        if (mLearned == null || word == null) return false;
        return mLearned.forget(mVariants.strip(word), localeObject());
    }

    @Override
    public synchronized WordSource source(String word) {
        if (mDict == null || word == null) return WordSource.NONE;
        word = mVariants.strip(word);
        if (word.isEmpty()) return WordSource.NONE;
        String lower = word.toLowerCase(localeObject());
        if (mPersonalWords.containsKey(word) || mPersonalWords.containsKey(lower)) {
            return WordSource.PERSONAL_DICTIONARY;
        }
        if (mDict.getProbability(word) != BinaryDictionary.NOT_A_PROBABILITY
                || mDict.getProbability(lower) != BinaryDictionary.NOT_A_PROBABILITY) {
            return WordSource.DICTIONARY;
        }
        if (mLearned != null && (mLearned.contains(word) || mLearned.contains(lower))) {
            return WordSource.LEARNED;
        }
        return WordSource.NONE;
    }

    @Override
    public synchronized void flushLearnedWords() {
        if (mLearned != null) mLearned.flush();
    }

    /**
     * The form a committed word is learned in (null: not learned): niqqud and harakat
     * dropped; a capital that may be the automatic one at the start of a sentence made lower
     * case unless only the capitalised word is known (AOSP's rule); never words with digits
     * or without letters, all capitals ("OK", shouting) or longer than the decoder takes.
     */
    private String learnable(String word, boolean capitalizedForSentence) {
        if (mDict == null || mLearned == null || word == null) return null;
        String w = mVariants.strip(word.trim());
        if (w.isEmpty() || w.length() > BinaryDictionary.MAX_WORD_LENGTH) return null;
        int letters = 0, upper = 0;
        for (int i = 0; i < w.length(); ) {
            int c = w.codePointAt(i);
            if (Character.isDigit(c) || Character.isWhitespace(c)) return null;
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
            i += Character.charCount(c);
        }
        if (letters == 0 || (letters > 1 && upper == letters)) return null;
        return sentenceCase(w, capitalizedForSentence);
    }

    /** The word as the learned dictionary keys it (see {@link #learnable}). */
    private String sentenceCase(String w, boolean capitalizedForSentence) {
        if (!capitalizedForSentence || !Character.isUpperCase(w.codePointAt(0))) return w;
        String lower = w.toLowerCase(localeObject());
        if (lower.equals(w)) return w;
        if (isExactlyAWord(w) && !isExactlyAWord(lower)) return w; // "Paris", a saved name
        return lower;
    }

    /** The previous word as the learned dictionary keys it; null none, "" sentence start. */
    private String contextWord(WordContext previous) {
        if (previous == null) return null;
        if (!previous.hasWord()) return previous.word;
        return sentenceCase(mVariants.strip(previous.word), previous.startedSentence);
    }

    @Override
    public synchronized float autoCorrectionConfidence(String typed, Suggestion suggestion) {
        if (mDict == null) return 0f;
        return BinaryDictionaryUtils.calcNormalizedScore(mVariants.strip(typed), suggestion.word,
                suggestion.score);
    }

    @Override
    public synchronized void close() {
        if (mLearned != null) {
            mLearned.release();
            mLearned = null;
        }
        if (mPersonal != null) {
            mPersonal.close();
            mPersonal = null;
        }
        if (mProximity != null) {
            mProximity.close();
            mProximity = null;
        }
        mGeometry = null;
        mKeys = null;
        if (mDict != null) {
            mDict.close();
            mDict = null;
        }
    }

    private ProximityInfo proximityFor(KeyGeometry keys) {
        if (keys == null) {
            if (mDefaultGeometry == null) mDefaultGeometry = KeyGeometry.defaultQwerty();
            keys = mDefaultGeometry;
        }
        if (keys != mKeys) {
            if (mProximity != null) mProximity.close();
            mGeometry = mVariants.adapt(keys);
            mProximity = new ProximityInfo(mGeometry);
            mKeys = keys;
        }
        return mProximity;
    }

    private Locale localeObject() {
        String[] parts = mLocale.split("_");
        return parts.length > 1 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
    }

    /** How the typed word was capitalised, to give the suggestions the same shape. */
    private enum CaseShape {
        AS_IS, FIRST_UPPER, ALL_UPPER;

        static CaseShape of(String typed) {
            int first = typed.codePointAt(0);
            if (!Character.isUpperCase(first)) return AS_IS;
            int letters = 0, upper = 0;
            for (int i = 0; i < typed.length(); ) {
                int c = typed.codePointAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (Character.isUpperCase(c)) upper++;
                }
                i += Character.charCount(c);
            }
            return letters > 1 && upper == letters ? ALL_UPPER : FIRST_UPPER;
        }

        String apply(String word, String locale) {
            Locale l = new Locale(locale.split("_")[0]);
            switch (this) {
                case ALL_UPPER:
                    return word.toUpperCase(l);
                case FIRST_UPPER:
                    int n = Character.charCount(word.codePointAt(0));
                    return word.substring(0, n).toUpperCase(l) + word.substring(n);
                default:
                    return word;
            }
        }
    }
}
