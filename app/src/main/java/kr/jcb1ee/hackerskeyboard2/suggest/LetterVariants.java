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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Letters that are one letter for matching purposes in a script, and marks that are not
 * matched at all: Hebrew final forms (ך ם ן ף ץ are כ מ נ פ צ at the end of a word), Arabic
 * alef with or without hamza or madda (ا أ إ آ), alef maqsura and ya (ى ي), ta marbuta and
 * ha (ة ه); niqqud, harakat and tatweel are dropped from what was typed.
 *
 * <p>The decoder (unmodified AOSP) only folds Latin accents, so variants are made close
 * through the key geometry instead: each variant gets a key of its own on top of its base
 * letter's key (its own key, if the layout has one elsewhere, is moved there), and taps on
 * a moved key are given to the decoder on the key it was moved to. A variant then costs the
 * decoder a "proximity" correction (about a fifth of a wrong letter), so the dictionary's
 * own spelling wins whichever form was typed, and suggestions keep that spelling.
 *
 * <p>The decoder only looks at neighbouring keys for letters with a position, so a letter
 * of a variant group typed without one (a hardware keyboard, or a variant tapped on its own
 * key) is placed at its key's centre in the adapted geometry.
 */
final class LetterVariants {
    /** No variants (Latin, Cyrillic, ... and anything without a bundled dictionary). */
    static final LetterVariants NONE = new LetterVariants(new int[0][], 0, 0);

    static final LetterVariants HEBREW = new LetterVariants(new int[][] {
            {'כ', 'ך'}, // כ ך
            {'מ', 'ם'}, // מ ם
            {'נ', 'ן'}, // נ ן
            {'פ', 'ף'}, // פ ף
            {'צ', 'ץ'}, // צ ץ
    }, 0x0591, 0x05C7); // cantillation and niqqud (points, meteg, rafe...)

    static final LetterVariants ARABIC = new LetterVariants(new int[][] {
            {'ا', 'أ', 'إ', 'آ'}, // ا أ إ آ
            {'ي', 'ى'}, // ي ى
            {'ه', 'ة'}, // ه ة
    }, 0x064B, 0x065F); // harakat (fatha ... sukun, shadda, hamza above/below marks)

    private static final int TATWEEL = 0x0640, SUPERSCRIPT_ALEF = 0x0670;

    /** Each group: the base letter first, then its variants. */
    private final int[][] mGroups;
    private final int mMarksFrom, mMarksTo;

    private LetterVariants(int[][] groups, int marksFrom, int marksTo) {
        mGroups = groups;
        mMarksFrom = marksFrom;
        mMarksTo = marksTo;
    }

    /** The rules for an input locale ("iw", "he", "ar", "ar_EG", "en_US"...). */
    static LetterVariants forLocale(String locale) {
        String lang = locale == null ? "" : locale.toLowerCase(Locale.ROOT);
        int sep = lang.indexOf('_');
        if (sep >= 0) lang = lang.substring(0, sep);
        switch (lang) {
            case "iw":
            case "he":
                return HEBREW;
            case "ar":
                return ARABIC;
            default:
                return NONE;
        }
    }

    boolean isEmpty() {
        return mGroups.length == 0 && mMarksTo == 0;
    }

    /** Whether {@code c} is a mark that is not matched (niqqud, harakat, tatweel). */
    boolean isIgnored(int c) {
        if (mMarksTo == 0) return false;
        return (c >= mMarksFrom && c <= mMarksTo)
                || (this == ARABIC && (c == TATWEEL || c == SUPERSCRIPT_ALEF));
    }

    /** {@code keys} with each variant's key on its base letter's key (see the class doc). */
    KeyGeometry adapt(KeyGeometry keys) {
        if (mGroups.length == 0) return keys;
        List<KeyGeometry.Key> out = new ArrayList<>();
        List<KeyGeometry.Key> shadows = new ArrayList<>();
        boolean[] moved = new boolean[0x10000];
        for (int[] group : mGroups) {
            KeyGeometry.Key base = keys.keyFor(group[0]);
            if (base == null) continue;
            for (int i = 1; i < group.length; i++) {
                moved[group[i]] = true;
                shadows.add(new KeyGeometry.Key(group[i], base.x, base.y, base.width,
                        base.height));
            }
        }
        if (shadows.isEmpty()) return keys;
        for (KeyGeometry.Key k : keys.keys()) {
            if (k.code < moved.length && moved[k.code]) continue;
            out.add(k);
        }
        out.addAll(shadows);
        return new KeyGeometry(keys.width, keys.height, out);
    }

    /** What the decoder is given for a typed word: marks dropped, positions adjusted. */
    static final class Input {
        final String text;
        final int[] xs, ys;

        Input(String text, int[] xs, int[] ys) {
            this.text = text;
            this.xs = xs;
            this.ys = ys;
        }
    }

    /**
     * {@code typed} without ignored marks; a letter of a variant group tapped somewhere else
     * than on its key in {@code adapted} (the result of {@link #adapt}), or typed without a
     * position, is placed at that key's centre.
     */
    Input input(String typed, int[] xs, int[] ys, KeyGeometry adapted) {
        if (isEmpty()) return new Input(typed, xs, ys);
        StringBuilder text = new StringBuilder(typed.length());
        int n = typed.codePointCount(0, typed.length());
        int[] x = new int[n], y = new int[n];
        int out = 0, index = 0;
        for (int i = 0; i < typed.length(); index++) {
            int c = typed.codePointAt(i);
            i += Character.charCount(c);
            if (isIgnored(c)) continue;
            int px = xs != null && index < xs.length ? xs[index] : BinaryDictionary.NOT_A_COORDINATE;
            int py = ys != null && index < ys.length ? ys[index] : BinaryDictionary.NOT_A_COORDINATE;
            KeyGeometry.Key k = inGroup(c) ? adapted.keyFor(c) : null;
            if (k != null && (px < k.x || px >= k.x + k.width || py < k.y
                    || py >= k.y + k.height)) {
                px = k.centerX();
                py = k.centerY();
            }
            text.appendCodePoint(c);
            x[out] = px;
            y[out] = py;
            out++;
        }
        if (out == 0) return new Input("", new int[0], new int[0]);
        int[] fx = new int[out], fy = new int[out];
        System.arraycopy(x, 0, fx, 0, out);
        System.arraycopy(y, 0, fy, 0, out);
        return new Input(text.toString(), fx, fy);
    }

    /** {@code word} without ignored marks. */
    String strip(String word) {
        if (mMarksTo == 0 || word == null) return word;
        StringBuilder sb = null;
        for (int i = 0; i < word.length(); ) {
            int c = word.codePointAt(i);
            int n = Character.charCount(c);
            if (isIgnored(c)) {
                if (sb == null) sb = new StringBuilder(word.substring(0, i));
            } else if (sb != null) {
                sb.appendCodePoint(c);
            }
            i += n;
        }
        return sb == null ? word : sb.toString();
    }

    /**
     * Whether {@code word} is {@code typed} with some base letters as one of their variants
     * (typed "اكثر", word "أكثر"; typed "מינ", word "מין").
     */
    boolean isVariantSpelling(String typed, String word) {
        if (mGroups.length == 0 || typed.length() != word.length() || typed.equals(word)) {
            return false;
        }
        for (int i = 0; i < typed.length(); i++) {
            char t = typed.charAt(i), w = word.charAt(i);
            if (t != w && !isVariantOf(w, t)) return false;
        }
        return true;
    }

    private boolean isVariantOf(int variant, int base) {
        for (int[] group : mGroups) {
            if (group[0] != base) continue;
            for (int i = 1; i < group.length; i++) {
                if (group[i] == variant) return true;
            }
        }
        return false;
    }

    private boolean inGroup(int c) {
        for (int[] group : mGroups) {
            for (int g : group) {
                if (g == c) return true;
            }
        }
        return false;
    }
}
