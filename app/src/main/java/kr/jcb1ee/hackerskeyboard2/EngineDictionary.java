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

import java.util.List;

import kr.jcb1ee.hackerskeyboard2.suggest.KeyGeometry;
import kr.jcb1ee.hackerskeyboard2.suggest.Suggestion;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestionEngine;

/**
 * Lets the old {@link Suggest} use a {@link SuggestionEngine} as its main dictionary. A bridge
 * until the toolbar suggestion strip talks to the engine directly; goes with the old code.
 */
final class EngineDictionary extends Dictionary {
    private static final int MAX_RESULTS = 12;
    // Suggest ranks by frequency; the engine's order is kept by handing out descending values
    // above anything the old user/auto dictionaries produce.
    private static final int TOP_FREQUENCY = 1 << 24;

    private final SuggestionEngine mEngine;
    private Keyboard mKeyboard;
    private KeyGeometry mGeometry;

    EngineDictionary(SuggestionEngine engine) {
        mEngine = engine;
    }

    /** The keyboard being typed on, for the engine's spatial model; null for plain QWERTY. */
    void setKeyboard(Keyboard keyboard) {
        if (keyboard != mKeyboard) {
            mKeyboard = keyboard;
            mGeometry = keyboard != null ? KeyGeometry.fromKeyboard(keyboard) : null;
        }
    }

    @Override
    public void getWords(WordComposer composer, WordCallback callback,
            int[] nextLettersFrequencies) {
        CharSequence typed = composer.getTypedWord();
        if (typed == null || typed.length() == 0) return;
        List<Suggestion> results =
                mEngine.suggestions(typed.toString(), null, null, null, mGeometry, MAX_RESULTS);
        for (int i = 0; i < results.size(); i++) {
            char[] word = results.get(i).word.toCharArray();
            callback.addWord(word, 0, word.length, TOP_FREQUENCY - i, Suggest.DIC_MAIN,
                    DataType.UNIGRAM);
        }
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        return word != null && mEngine.isValidWord(word.toString());
    }

    @Override
    public void close() {
        mEngine.close();
    }
}
