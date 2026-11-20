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

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.UserDictionary;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Android's personal dictionary (the system UserDictionary provider, Settings > Languages >
 * Personal dictionary) for one input locale: its words for that locale, its language and
 * all languages.
 *
 * <p>Since Android 6 only the current keyboard (and spell checker) may read the provider, and
 * no permission is needed or grantable for it; any other time the read fails and the words
 * are simply missing (until the next change or keyboard start). The provider's backup is the
 * system's business: this keyboard never writes to it (its own saved words stay in its
 * private files, see {@link LearnedWords}).
 */
public final class PersonalDictionary {
    private static final String TAG = "HK/PersonalDictionary";
    /** AOSP's UserBinaryDictionary scale: the provider's default 250 is 160 for the decoder. */
    static final int PROVIDER_DEFAULT_FREQUENCY = 250;
    static final int DECODER_DEFAULT_FREQUENCY = 160;

    private final ContentResolver mResolver;
    private final String mLocale;
    private ContentObserver mObserver;

    public PersonalDictionary(ContentResolver resolver, String locale) {
        mResolver = resolver;
        mLocale = locale;
    }

    /**
     * Calls {@code onChange} (on {@code handler}'s thread) whenever the personal dictionary
     * changes, until {@link #stopWatching}.
     */
    public void watch(Handler handler, Runnable onChange) {
        stopWatching();
        mObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                onChange.run();
            }
        };
        try {
            mResolver.registerContentObserver(UserDictionary.Words.CONTENT_URI, true, mObserver);
        } catch (RuntimeException e) { // SecurityException, or no such provider
            Log.w(TAG, "cannot watch the personal dictionary: " + e);
            mObserver = null;
        }
    }

    public void stopWatching() {
        if (mObserver == null) return;
        try {
            mResolver.unregisterContentObserver(mObserver);
        } catch (RuntimeException e) {
            // already gone
        }
        mObserver = null;
    }

    /**
     * The words for this locale, with the decoder's probability (0-255) for each; empty when
     * the provider can't be read. Blocks for the query: call it off the main thread.
     */
    public Map<String, Integer> read() {
        Map<String, Integer> words = new HashMap<>();
        String[] projection = {UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY,
                UserDictionary.Words.LOCALE};
        try (Cursor c = mResolver.query(UserDictionary.Words.CONTENT_URI, projection, null, null,
                null)) {
            if (c == null) return words;
            int wordColumn = c.getColumnIndex(UserDictionary.Words.WORD);
            int frequencyColumn = c.getColumnIndex(UserDictionary.Words.FREQUENCY);
            int localeColumn = c.getColumnIndex(UserDictionary.Words.LOCALE);
            while (c.moveToNext()) {
                String word = wordColumn >= 0 ? c.getString(wordColumn) : null;
                if (word == null) continue;
                word = word.trim();
                if (word.isEmpty() || word.length() > BinaryDictionary.MAX_WORD_LENGTH) continue;
                String locale = localeColumn >= 0 ? c.getString(localeColumn) : null;
                if (!localeMatches(locale, mLocale)) continue;
                int frequency = frequencyColumn >= 0 && !c.isNull(frequencyColumn)
                        ? c.getInt(frequencyColumn) : PROVIDER_DEFAULT_FREQUENCY;
                int p = decoderProbability(frequency);
                Integer had = words.get(word);
                if (had == null || had < p) words.put(word, p);
            }
        } catch (RuntimeException e) { // SecurityException when not the current keyboard
            Log.w(TAG, "cannot read the personal dictionary: " + e);
            words.clear();
        }
        return words;
    }

    /** AOSP's scaleFrequencyFromDefaultToLatinIme, kept within the decoder's 0-255. */
    static int decoderProbability(int providerFrequency) {
        int f = Math.max(0, Math.min(providerFrequency, 255));
        return Math.min(255, f * DECODER_DEFAULT_FREQUENCY / PROVIDER_DEFAULT_FREQUENCY);
    }

    /**
     * Whether a word stored for {@code rowLocale} (null or "": all languages) belongs to the
     * input locale {@code inputLocale}: the same locale, or its language ("en" words for
     * en_US; he and iw are both Hebrew).
     */
    static boolean localeMatches(String rowLocale, String inputLocale) {
        if (rowLocale == null || rowLocale.trim().isEmpty()) return true;
        List<String> row = AospSuggestionEngine.dictionaryNames(rowLocale.trim());
        List<String> input = AospSuggestionEngine.dictionaryNames(inputLocale);
        String rowLanguage = row.get(row.size() - 1);
        String inputLanguage = input.get(input.size() - 1);
        if (!rowLanguage.equals(inputLanguage)) return false;
        // A word for a region is for that region only; one for the language is for all.
        return row.size() == 1 || input.get(0).equals(row.get(0));
    }

    @Override
    public String toString() {
        return "PersonalDictionary(" + mLocale.toLowerCase(Locale.ROOT) + ")";
    }
}
