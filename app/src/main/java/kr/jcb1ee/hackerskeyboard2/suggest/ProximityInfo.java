/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified for Hacker's Keyboard 2: from AOSP LatinIME
 * java/src/com/android/inputmethod/keyboard/ProximityInfo.java at
 * 127336e9f29d69607eab55982324b210279ae8c5; works on KeyGeometry instead of AOSP's Key and
 * Keyboard, no touch position correction (sweet spots), explicit close instead of finalize(),
 * debug output and getNearestKeys dropped.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

import java.util.Arrays;

/** The native spatial model of one keyboard: key rectangles and, per grid cell, nearby keys. */
final class ProximityInfo {
    // Must be equal to MAX_PROXIMITY_CHARS_SIZE in native/jni/src/defines.h
    static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static final float SEARCH_DISTANCE = 1.2f;
    // AOSP's config_keyboard_grid_width / _height.
    private static final int GRID_WIDTH = 32;
    private static final int GRID_HEIGHT = 16;
    private static final int NOT_A_CODE = -1;

    private static native long setProximityInfoNative(int displayWidth, int displayHeight,
            int gridWidth, int gridHeight, int mostCommonKeyWidth, int mostCommonKeyHeight,
            int[] proximityCharsArray, int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes, float[] sweetSpotCenterXs,
            float[] sweetSpotCenterYs, float[] sweetSpotRadii);
    private static native void releaseProximityInfoNative(long nativeProximityInfo);

    private long mNativeProximityInfo;

    ProximityInfo(KeyGeometry geometry) {
        int cellWidth = (geometry.width + GRID_WIDTH - 1) / GRID_WIDTH;
        int cellHeight = (geometry.height + GRID_HEIGHT - 1) / GRID_HEIGHT;
        int[] proximityChars = computeProximityChars(geometry, cellWidth, cellHeight);
        int keyCount = geometry.keys.size();
        int[] xs = new int[keyCount], ys = new int[keyCount];
        int[] widths = new int[keyCount], heights = new int[keyCount], codes = new int[keyCount];
        for (int i = 0; i < keyCount; i++) {
            KeyGeometry.Key key = geometry.keys.get(i);
            xs[i] = key.x;
            ys[i] = key.y;
            widths[i] = key.width;
            heights[i] = key.height;
            codes[i] = key.code;
        }
        mNativeProximityInfo = setProximityInfoNative(geometry.width, geometry.height,
                GRID_WIDTH, GRID_HEIGHT, geometry.mostCommonKeyWidth,
                geometry.mostCommonKeyHeight, proximityChars, keyCount, xs, ys, widths, heights,
                codes, null, null, null);
    }

    long getNativeProximityInfo() {
        return mNativeProximityInfo;
    }

    void close() {
        if (mNativeProximityInfo != 0) {
            releaseProximityInfoNative(mNativeProximityInfo);
            mNativeProximityInfo = 0;
        }
    }

    /**
     * For each grid cell, the codes of the keys whose edge is within SEARCH_DISTANCE key widths
     * of the cell's centre (AOSP computeNearestNeighbors, which explains the cell walk at length).
     */
    private static int[] computeProximityChars(KeyGeometry g, int cellWidth, int cellHeight) {
        int gridSize = GRID_WIDTH * GRID_HEIGHT;
        int[] result = new int[gridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(result, NOT_A_CODE);
        if (g.width == 0 || g.height == 0) return result;
        int[] counts = new int[gridSize];
        int threshold = (int) (g.mostCommonKeyWidth * SEARCH_DISTANCE);
        int thresholdSquared = threshold * threshold;
        int lastPixelX = GRID_WIDTH * cellWidth - 1;
        int lastPixelY = GRID_HEIGHT * cellHeight - 1;
        int halfCellWidth = cellWidth / 2;
        int halfCellHeight = cellHeight / 2;
        for (KeyGeometry.Key key : g.keys) {
            int topPixelWithinThreshold = key.y - threshold;
            int yDeltaToGrid = Math.floorMod(topPixelWithinThreshold, cellHeight);
            int yMiddleOfTopCell = topPixelWithinThreshold - yDeltaToGrid + halfCellHeight;
            int yStart = Math.max(halfCellHeight,
                    yMiddleOfTopCell + (yDeltaToGrid <= halfCellHeight ? 0 : cellHeight));
            int yEnd = Math.min(lastPixelY, key.y + key.height + threshold);

            int leftPixelWithinThreshold = key.x - threshold;
            int xDeltaToGrid = Math.floorMod(leftPixelWithinThreshold, cellWidth);
            int xMiddleOfLeftCell = leftPixelWithinThreshold - xDeltaToGrid + halfCellWidth;
            int xStart = Math.max(halfCellWidth,
                    xMiddleOfLeftCell + (xDeltaToGrid <= halfCellWidth ? 0 : cellWidth));
            int xEnd = Math.min(lastPixelX, key.x + key.width + threshold);

            for (int centerY = yStart; centerY <= yEnd; centerY += cellHeight) {
                for (int centerX = xStart; centerX <= xEnd; centerX += cellWidth) {
                    int index = (centerY / cellHeight) * GRID_WIDTH + centerX / cellWidth;
                    if (index >= gridSize || counts[index] >= MAX_PROXIMITY_CHARS_SIZE) continue;
                    if (key.squaredDistanceToEdge(centerX, centerY) < thresholdSquared) {
                        result[index * MAX_PROXIMITY_CHARS_SIZE + counts[index]] = key.code;
                        counts[index]++;
                    }
                }
            }
        }
        return result;
    }
}
