/*
 * Copyright (C) 2008 The Android Open Source Project
 * 
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
import android.util.Log;


public class TextEntryState {
    
    private static final boolean DBG = false;

    private static final String TAG = "TextEntryState";

    public enum State {
        UNKNOWN,
        START,
        IN_WORD,
        ACCEPTED_DEFAULT,
        PICKED_SUGGESTION,
        PUNCTUATION_AFTER_WORD,
        PUNCTUATION_AFTER_ACCEPTED,
        SPACE_AFTER_ACCEPTED,
        SPACE_AFTER_PICKED,
        UNDO_COMMIT,
        CORRECTING,
        PICKED_CORRECTION;
    }

    private static State sState = State.UNKNOWN;

    public static void newSession(Context context) {
        sState = State.START;
    }

    public static void acceptedDefault(CharSequence typedWord, CharSequence actualWord) {
        if (typedWord == null) return;
        sState = State.ACCEPTED_DEFAULT;
        displayState();
    }

    // State.ACCEPTED_DEFAULT will be changed to other sub-states
    // (see "case ACCEPTED_DEFAULT" in typedCharacter() below),
    // and should be restored back to State.ACCEPTED_DEFAULT after processing for each sub-state.
    public static void backToAcceptedDefault(CharSequence typedWord) {
        if (typedWord == null) return;
        switch (sState) {
            case SPACE_AFTER_ACCEPTED:
            case PUNCTUATION_AFTER_ACCEPTED:
            case IN_WORD:
                sState = State.ACCEPTED_DEFAULT;
                break;
        }
        displayState();
    }

    public static void manualTyped(CharSequence typedWord) {
        sState = State.START;
        displayState();
    }

    public static void acceptedTyped(CharSequence typedWord) {
        sState = State.PICKED_SUGGESTION;
        displayState();
    }

    public static void acceptedSuggestion(CharSequence typedWord, CharSequence actualWord) {
        State oldState = sState;
        if (typedWord.equals(actualWord)) {
            acceptedTyped(typedWord);
        }
        if (oldState == State.CORRECTING || oldState == State.PICKED_CORRECTION) {
            sState = State.PICKED_CORRECTION;
        } else {
            sState = State.PICKED_SUGGESTION;
        }
        displayState();
    }

    public static void selectedForCorrection() {
        sState = State.CORRECTING;
        displayState();
    }

    public static void typedCharacter(char c, boolean isSeparator) {
        boolean isSpace = c == ' ';
        switch (sState) {
            case IN_WORD:
                if (isSpace || isSeparator) {
                    sState = State.START;
                } else {
                    // State hasn't changed.
                }
                break;
            case ACCEPTED_DEFAULT:
            case SPACE_AFTER_PICKED:
                if (isSpace) {
                    sState = State.SPACE_AFTER_ACCEPTED;
                } else if (isSeparator) {
                    sState = State.PUNCTUATION_AFTER_ACCEPTED;
                } else {
                    sState = State.IN_WORD;
                }
                break;
            case PICKED_SUGGESTION:
            case PICKED_CORRECTION:
                if (isSpace) {
                    sState = State.SPACE_AFTER_PICKED;
                } else if (isSeparator) {
                    // Swap 
                    sState = State.PUNCTUATION_AFTER_ACCEPTED;
                } else {
                    sState = State.IN_WORD;
                }
                break;
            case START:
            case UNKNOWN:
            case SPACE_AFTER_ACCEPTED:
            case PUNCTUATION_AFTER_ACCEPTED:
            case PUNCTUATION_AFTER_WORD:
                if (!isSpace && !isSeparator) {
                    sState = State.IN_WORD;
                } else {
                    sState = State.START;
                }
                break;
            case UNDO_COMMIT:
                if (isSpace || isSeparator) {
                    sState = State.ACCEPTED_DEFAULT;
                } else {
                    sState = State.IN_WORD;
                }
                break;
            case CORRECTING:
                sState = State.START;
                break;
        }
        displayState();
    }
    
    public static void backspace() {
        if (sState == State.ACCEPTED_DEFAULT) {
            sState = State.UNDO_COMMIT;
        } else if (sState == State.UNDO_COMMIT) {
            sState = State.IN_WORD;
        }
        displayState();
    }

    public static void reset() {
        sState = State.START;
        displayState();
    }

    public static State getState() {
        if (DBG) {
            Log.d(TAG, "Returning state = " + sState);
        }
        return sState;
    }

    public static boolean isCorrecting() {
        return sState == State.CORRECTING || sState == State.PICKED_CORRECTION;
    }

    private static void displayState() {
        if (DBG) {
            //Log.w(TAG, "State = " + sState, new Throwable());
            Log.i(TAG, "State = " + sState);
        }
    }
}

