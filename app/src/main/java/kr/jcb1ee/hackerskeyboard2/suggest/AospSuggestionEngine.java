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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link SuggestionEngine} on the AOSP LatinIME decoder (libjni_latinime) and an AOSP-format
 * main dictionary (assets/dicts/main_LANG.dict, stored uncompressed and mapped in place).
 */
public final class AospSuggestionEngine implements SuggestionEngine {
    /** Where the main dictionaries are, inside the assets. */
    public static final String ASSET_DIR = "dicts";

    private final String mLocale;
    private BinaryDictionary mDict;
    private KeyGeometry mGeometry;
    private ProximityInfo mProximity;
    private KeyGeometry mDefaultGeometry;

    private AospSuggestionEngine(BinaryDictionary dict, String locale) {
        mDict = dict;
        mLocale = locale;
    }

    /** The asset name of the main dictionary for {@code language} ("en", "iw", ...). */
    public static String assetName(String language) {
        return ASSET_DIR + "/main_" + language + ".dict";
    }

    /**
     * Opens the bundled dictionary for {@code locale} (e.g. "en_US": tries main_en_us, then
     * main_en). Returns null when there is none, the native library is missing or the file
     * is not a dictionary.
     */
    public static AospSuggestionEngine open(Context context, String locale) {
        if (!NativeLibrary.load()) return null;
        String lang = locale.toLowerCase(Locale.ROOT);
        String[] names = lang.contains("_")
                ? new String[] {lang, lang.substring(0, lang.indexOf('_'))}
                : new String[] {lang};
        for (String name : names) {
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
    public synchronized List<Suggestion> suggestions(String typed, int[] xs, int[] ys,
            String previousWord, KeyGeometry keys, int max) {
        if (mDict == null || typed == null || typed.isEmpty() || max <= 0) {
            return Collections.emptyList();
        }
        ProximityInfo proximity = proximityFor(keys);
        int[] codePoints = typed.codePoints().toArray();
        List<Suggestion> raw = mDict.getSuggestions(codePoints, xs, ys, proximity,
                previousWord, true /* block offensive words */);
        // Native results come sorted; keep it explicit (stable for equal scores).
        List<Suggestion> sorted = new ArrayList<>(raw);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        CaseShape shape = CaseShape.of(typed);
        // The decoder ignores case, so "creek" can come back as "Creek" first; when the word
        // as typed is in the dictionary, its case variants become the typed word.
        boolean typedIsWord = mDict.getProbability(typed) != BinaryDictionary.NOT_A_PROBABILITY;
        Set<String> seen = new LinkedHashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : sorted) {
            String w = typedIsWord && s.word.equalsIgnoreCase(typed)
                    ? typed : shape.apply(s.word, mLocale);
            if (!seen.add(w)) continue;
            out.add(w.equals(s.word) ? s : s.withWord(w));
            if (out.size() >= max) break;
        }
        return out;
    }

    @Override
    public synchronized boolean isValidWord(String word) {
        if (mDict == null || word == null || word.isEmpty()) return false;
        if (mDict.getProbability(word) != BinaryDictionary.NOT_A_PROBABILITY) return true;
        String lower = word.toLowerCase(localeObject());
        return !lower.equals(word)
                && mDict.getProbability(lower) != BinaryDictionary.NOT_A_PROBABILITY;
    }

    @Override
    public synchronized float autoCorrectionConfidence(String typed, Suggestion suggestion) {
        if (mDict == null) return 0f;
        return BinaryDictionaryUtils.calcNormalizedScore(typed, suggestion.word,
                suggestion.score);
    }

    @Override
    public synchronized void close() {
        if (mProximity != null) {
            mProximity.close();
            mProximity = null;
        }
        mGeometry = null;
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
        if (keys != mGeometry) {
            if (mProximity != null) mProximity.close();
            mProximity = new ProximityInfo(keys);
            mGeometry = keys;
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
