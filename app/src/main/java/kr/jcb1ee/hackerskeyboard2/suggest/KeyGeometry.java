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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.jcb1ee.hackerskeyboard2.Keyboard;

/**
 * Where the character keys are, for the decoder's spatial model: which keys are near each other
 * and how far a touch was from each. Immutable; build one per keyboard layout and size.
 */
public final class KeyGeometry {
    /** One character key: the code it types (lower case) and its rectangle in pixels. */
    public static final class Key {
        public final int code, x, y, width, height;

        public Key(int code, int x, int y, int width, int height) {
            this.code = code;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }

        int squaredDistanceToEdge(int px, int py) {
            int right = x + width, bottom = y + height;
            int edgeX = px < x ? x : Math.min(px, right);
            int edgeY = py < y ? y : Math.min(py, bottom);
            int dx = px - edgeX, dy = py - edgeY;
            return dx * dx + dy * dy;
        }
    }

    final int width, height, mostCommonKeyWidth, mostCommonKeyHeight;
    final List<Key> keys;
    private final Map<Integer, Key> mByCode = new HashMap<>();

    public KeyGeometry(int width, int height, List<Key> keys) {
        this.width = width;
        this.height = height;
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        mostCommonKeyWidth = mostCommon(keys, true);
        mostCommonKeyHeight = mostCommon(keys, false);
        for (Key k : keys) mByCode.putIfAbsent(k.code, k);
    }

    /** The key that types {@code code} (lower-cased), or null. */
    public Key keyFor(int code) {
        Key k = mByCode.get(code);
        if (k == null) k = mByCode.get(Character.toLowerCase(code));
        return k;
    }

    public List<Key> keys() {
        return keys;
    }

    /**
     * The character keys of one of our keyboards (letters and other printable characters;
     * modifiers, space and function keys are left out, as in AOSP).
     */
    public static KeyGeometry fromKeyboard(Keyboard keyboard) {
        List<Key> keys = new ArrayList<>();
        for (Keyboard.Key k : keyboard.getKeys()) {
            if (k.codes == null || k.codes.length == 0) continue;
            int code = k.codes[0];
            if (code <= ' ' || k.modifier || k.width <= 0 || k.height <= 0) continue;
            if (!Character.isLetter(code) && code != '\'' && code != '-') continue;
            keys.add(new Key(Character.toLowerCase(code), k.x, k.y, k.width, k.height));
        }
        return new KeyGeometry(keyboard.getMinWidth(), keyboard.getHeight(), keys);
    }

    /**
     * A plain QWERTY of unit keys, for when no on-screen keyboard is known (hardware keyboard,
     * tests). The decoder needs some geometry to run.
     */
    public static KeyGeometry defaultQwerty() {
        String[] rows = {"qwertyuiop", "asdfghjkl'", "zxcvbnm-"};
        int kw = 100, kh = 150;
        List<Key> keys = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            int offset = r * kw / 3;
            for (int i = 0; i < rows[r].length(); i++) {
                keys.add(new Key(rows[r].charAt(i), offset + i * kw, r * kh, kw, kh));
            }
        }
        return new KeyGeometry(10 * kw + kw, rows.length * kh, keys);
    }

    private static int mostCommon(List<Key> keys, boolean widths) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = 0, bestCount = 0;
        for (Key k : keys) {
            int v = widths ? k.width : k.height;
            int c = counts.merge(v, 1, Integer::sum);
            if (c > bestCount) {
                best = v;
                bestCount = c;
            }
        }
        return best;
    }
}
