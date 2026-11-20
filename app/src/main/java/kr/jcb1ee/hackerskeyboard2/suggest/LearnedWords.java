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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * What the keyboard learned from one language's typing, in the app's private files
 * ({@code files/learning/LANG/}, kept out of backups and device transfers):
 *
 * <ul>
 * <li>{@code history/}: AOSP's user history dictionary, an updatable v4 dictionary with
 * historical info and the forgetting curve ({@code USES_FORGETTING_CURVE}): typed words and
 * word pairs with counts that decay with time. A word that is in no other dictionary is
 * suggested once it has been typed twice; pairs give next-word suggestions.
 * <li>{@code saved.txt}: the words the user saved by tapping them in the strip (one per
 * line): these never decay.
 * </ul>
 *
 * <p>One instance per directory and process ({@link #acquire}), shared by the engines that
 * use it, so a keyboard reopened for the same language sees what the old one learned. All
 * methods are thread safe; the native work runs under this object's lock.
 */
final class LearnedWords {
    static final String DIR = "learning";
    private static final String HISTORY = "history";
    private static final String SAVED = "saved.txt";
    /** AOSP's dictionary type prefix for the user history dictionary. */
    private static final String DICTIONARY_ID = "history";

    private static final Map<String, LearnedWords> sOpen = new HashMap<>();

    private final File mDir;
    private final String mLocale;
    private BinaryDictionary mHistory;
    private final Set<String> mSaved = new LinkedHashSet<>();
    private int mUsers;
    private boolean mDirty;
    /** Bumped when the saved words change (the engines rebuild their personal dictionary). */
    private int mGeneration;

    private LearnedWords(File dir, String locale) {
        mDir = dir;
        mLocale = locale;
    }

    /** The directory for {@code locale}'s learned words under the app's files directory. */
    static File directoryFor(File filesDir, String locale) {
        List<String> names = AospSuggestionEngine.dictionaryNames(locale);
        return new File(new File(filesDir, DIR), names.get(names.size() - 1));
    }

    /**
     * The learned words in {@code dir}, opened (or created empty) on first use; give it back
     * with {@link #release}. Null when the native library is missing or the directory can't
     * be written.
     */
    static LearnedWords acquire(File dir, String locale) {
        if (!NativeLibrary.load()) return null;
        synchronized (sOpen) {
            String key = dir.getAbsolutePath();
            LearnedWords w = sOpen.get(key);
            if (w == null) {
                w = new LearnedWords(dir, locale);
                if (!w.open()) return null;
                sOpen.put(key, w);
            }
            w.mUsers++;
            return w;
        }
    }

    /** Done with it: the last user writes it out and closes it. */
    void release() {
        synchronized (sOpen) {
            if (--mUsers > 0) return;
            sOpen.remove(mDir.getAbsolutePath());
        }
        synchronized (this) {
            flushLocked();
            if (mHistory != null) mHistory.close();
            mHistory = null;
        }
    }

    /**
     * Forgets everything learned, in every language: the open ones are emptied in place,
     * the rest of {@code filesDir/learning} is deleted.
     */
    static void clearAll(File filesDir) {
        synchronized (sOpen) {
            for (LearnedWords w : sOpen.values()) w.clear();
            File root = new File(filesDir, DIR);
            File[] langs = root.listFiles();
            if (langs != null) {
                for (File lang : langs) {
                    boolean open = false;
                    for (LearnedWords w : sOpen.values()) {
                        if (w.mDir.equals(lang)) open = true;
                    }
                    if (!open) BinaryDictionary.deleteRecursively(lang);
                }
            }
        }
    }

    private synchronized boolean open() {
        mDir.mkdirs();
        mHistory = BinaryDictionary.openOrCreateUpdatable(new File(mDir, HISTORY), mLocale,
                historyAttributes(mLocale));
        if (!mHistory.isValid()) {
            mHistory.close();
            mHistory = null;
            return false;
        }
        mSaved.clear();
        File saved = new File(mDir, SAVED);
        if (saved.isFile()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(saved), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    String w = line.trim();
                    if (!w.isEmpty() && w.length() <= BinaryDictionary.MAX_WORD_LENGTH) {
                        mSaved.add(w);
                    }
                }
            } catch (IOException e) {
                // unreadable: start again
            }
        }
        return true;
    }

    /** As AOSP's UserHistoryDictionary: a decaying dictionary with historical info. */
    private static Map<String, String> historyAttributes(String locale) {
        Map<String, String> a = new HashMap<>();
        a.put("dictionary", DICTIONARY_ID + ":" + locale.toLowerCase(Locale.ROOT));
        a.put("locale", locale);
        a.put("version", String.valueOf(now()));
        a.put("USES_FORGETTING_CURVE", "1");
        a.put("HAS_HISTORICAL_INFO", "1");
        return a;
    }

    /** Seconds, as AOSP's timestamps. */
    static int now() {
        return (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }

    /**
     * Records that {@code word} was typed after {@code previous} (null: unknown, "": start
     * of a sentence). {@code isValid}: it is a word in another dictionary (then it counts at
     * once, else from the second time).
     */
    synchronized void learn(String previous, String word, boolean isValid, int timestamp) {
        if (mHistory == null) return;
        if (mHistory.updateEntriesForWord(previous, word, isValid, 1, timestamp)) mDirty = true;
    }

    /** Undoes a pair learned by mistake (an auto-correction the user reverted). */
    synchronized void unlearn(String previous, String word) {
        if (mHistory == null || previous == null) return;
        if (mHistory.removeNgram(previous, word)) mDirty = true;
    }

    /** Keeps {@code word} for good (and counts it as typed, after {@code previous}). */
    synchronized void save(String previous, String word, int timestamp) {
        if (mHistory == null) return;
        mHistory.updateEntriesForWord(previous, word, true, 1, timestamp);
        mDirty = true;
        if (mSaved.add(word)) writeSaved();
    }

    /** Forgets {@code word} (as given and in lower case): saved, learned, its pairs' end. */
    synchronized boolean forget(String word, Locale locale) {
        if (mHistory == null || word.isEmpty()) return false;
        boolean any = false;
        // The strip shows words in the case they were typed in ("Kowalski" at a sentence
        // start for a learned "kowalski"): forget every case of it.
        for (java.util.Iterator<String> i = mSaved.iterator(); i.hasNext(); ) {
            if (i.next().equalsIgnoreCase(word)) {
                i.remove();
                any = true;
            }
        }
        String lower = word.toLowerCase(locale);
        int n = Character.charCount(lower.codePointAt(0));
        String capital = lower.substring(0, n).toUpperCase(locale) + lower.substring(n);
        for (String w : new LinkedHashSet<>(java.util.Arrays.asList(word, lower, capital))) {
            if (mHistory.getProbability(w) != BinaryDictionary.NOT_A_PROBABILITY) any = true;
            if (mHistory.removeUnigram(w)) mDirty = true;
        }
        writeSaved();
        flushLocked(); // the user asked: don't wait for the end of the input
        return any;
    }

    /** Whether {@code word} is suggested from here (saved, or typed often enough). */
    synchronized boolean contains(String word) {
        if (mHistory == null) return false;
        return mSaved.contains(word)
                || mHistory.getProbability(word) != BinaryDictionary.NOT_A_PROBABILITY;
    }

    synchronized boolean isSaved(String word) {
        return mSaved.contains(word);
    }

    synchronized Set<String> saved() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mSaved));
    }

    /** The learned words the decoder makes of what was typed (see BinaryDictionary). */
    synchronized List<Suggestion> suggestions(int[] codePoints, int[] xs, int[] ys,
            ProximityInfo proximity, String previous) {
        if (mHistory == null) return Collections.emptyList();
        return mHistory.getSuggestions(codePoints, xs, ys, proximity, previous, true);
    }

    /** The words seen after {@code previous}, most likely first. */
    synchronized List<Suggestion> predictions(String previous, ProximityInfo proximity) {
        if (mHistory == null || previous == null) return Collections.emptyList();
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : mHistory.getSuggestions(new int[0], null, null, proximity,
                previous, true)) {
            // A pair whose word was forgotten can outlive it until the next clean-up.
            if (mHistory.getProbability(s.word) != BinaryDictionary.NOT_A_PROBABILITY) {
                out.add(s);
            }
        }
        return out;
    }

    /** Writes what changed to the files (at the end of an input, and when closing). */
    synchronized void flush() {
        flushLocked();
    }

    private void flushLocked() {
        if (!mDirty || mHistory == null) return;
        mHistory.flush();
        mDirty = false;
        if (!mHistory.isValid()) { // reopening failed: start over rather than crash later
            mHistory.close();
            mHistory = BinaryDictionary.openOrCreateUpdatable(new File(mDir, HISTORY), mLocale,
                    historyAttributes(mLocale));
        }
    }

    /** Forgets everything in this language. */
    synchronized void clear() {
        if (mHistory != null) mHistory.close();
        mHistory = null;
        mSaved.clear();
        mDirty = false;
        mGeneration++;
        BinaryDictionary.deleteRecursively(mDir);
        open();
    }

    /** Changes with every change to the saved words (and with {@link #clear}). */
    synchronized int generation() {
        return mGeneration;
    }

    private void writeSaved() {
        mGeneration++;
        File f = new File(mDir, SAVED);
        try {
            if (mSaved.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return;
            }
            byte[] bytes = String.join("\n", mSaved).concat("\n").getBytes(StandardCharsets.UTF_8);
            File tmp = new File(mDir, SAVED + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
            }
            if (!tmp.renameTo(f)) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
            }
        } catch (IOException e) {
            // kept in memory for this session
        }
    }
}
