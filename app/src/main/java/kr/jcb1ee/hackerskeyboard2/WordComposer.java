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

import java.util.Arrays;

/**
 * The word being typed (composed), with where each letter's key was touched: the word
 * suggestions' decoder takes the touch positions.
 */
public class WordComposer {
    private final StringBuilder mTypedWord = new StringBuilder(20);

    /** Where each keystroke touched the keyboard (keyboard pixels), -1 when not a touch. */
    private int[] mXs = new int[12], mYs = new int[12];

    private int mCapsCount;

    /** Clears out the keys registered so far. */
    public void reset() {
        mTypedWord.setLength(0);
        mCapsCount = 0;
    }

    /** Number of keystrokes in the composing word. */
    public int size() {
        return mTypedWord.length();
    }

    /**
     * Adds a keystroke, with where the key was touched, in the keyboard's coordinates (those
     * of its keys, after any width scaling); -1 when it wasn't a touch.
     */
    public void add(int primaryCode, int x, int y) {
        int n = mTypedWord.length();
        if (n == mXs.length) {
            mXs = Arrays.copyOf(mXs, n * 2);
            mYs = Arrays.copyOf(mYs, n * 2);
        }
        mXs[n] = x;
        mYs[n] = y;
        mTypedWord.append((char) primaryCode);
        if (Character.isUpperCase((char) primaryCode)) mCapsCount++;
    }

    /** Touch x per keystroke (-1: not a touch), a copy. */
    public int[] getXs() {
        return Arrays.copyOf(mXs, size());
    }

    /** Touch y per keystroke (-1: not a touch), a copy. */
    public int[] getYs() {
        return Arrays.copyOf(mYs, size());
    }

    /** Deletes the last keystroke as a result of hitting backspace. */
    public void deleteLast() {
        final int lastPos = mTypedWord.length() - 1;
        if (lastPos < 0) return;
        char last = mTypedWord.charAt(lastPos);
        mTypedWord.deleteCharAt(lastPos);
        if (Character.isUpperCase(last)) mCapsCount--;
    }

    /** The word as it was typed, without any correction applied; null when empty. */
    public CharSequence getTypedWord() {
        return mTypedWord.length() == 0 ? null : mTypedWord;
    }

    /** Whether more than one character is upper case. */
    public boolean isMostlyCaps() {
        return mCapsCount > 1;
    }
}
