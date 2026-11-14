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

import android.annotation.SuppressLint;

/**
 * Loads libjni_latinime, the AOSP LatinIME decoder (app/src/main/cpp/aosp).
 *
 * <p>On a device it comes from the APK. The JVM unit tests set the system property
 * {@value #HOST_LIBRARY_PROPERTY} to a desktop build of the same sources.
 */
public final class NativeLibrary {
    /** System property naming a host (desktop) build of the library, for unit tests. */
    public static final String HOST_LIBRARY_PROPERTY = "hk.suggest.hostJni";
    private static final String LIBRARY = "jni_latinime";

    private static boolean sTried;
    private static boolean sLoaded;
    private static Throwable sError;

    private NativeLibrary() {}

    /** Loads the library once; returns whether it is usable. */
    // System.load is only reached in JVM unit tests, which name the desktop build.
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static synchronized boolean load() {
        if (sTried) return sLoaded;
        sTried = true;
        try {
            String host = System.getProperty(HOST_LIBRARY_PROPERTY);
            if (host != null && !host.isEmpty()) {
                System.load(host);
            } else {
                System.loadLibrary(LIBRARY);
            }
            sLoaded = true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            sError = e;
            sLoaded = false;
        }
        return sLoaded;
    }

    /** Why {@link #load()} failed, or null. */
    public static synchronized Throwable loadError() {
        return sError;
    }
}
