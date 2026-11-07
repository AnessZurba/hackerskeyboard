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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the last crash so it can be reported without adb: an uncaught exception is
 * written to the app's files directory, and the next time the settings open they show
 * it with a button that copies it to the clipboard.
 */
public final class CrashLog extends Application {
    private static final String TAG = "HK/CrashLog";
    private static final String FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        install(this);
    }

    static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof Handler) return;
        Thread.setDefaultUncaughtExceptionHandler(new Handler(app, previous));
    }

    private static final class Handler implements Thread.UncaughtExceptionHandler {
        private final Context mApp;
        private final Thread.UncaughtExceptionHandler mPrevious;

        Handler(Context app, Thread.UncaughtExceptionHandler previous) {
            mApp = app;
            mPrevious = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable error) {
            try {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                String version = "?";
                try {
                    version = mApp.getPackageManager()
                            .getPackageInfo(mApp.getPackageName(), 0).versionName;
                } catch (Exception ignored) {
                    // keep "?"
                }
                String report = mApp.getString(R.string.english_ime_name) + " " + version
                        + " crashed\nthread: " + thread.getName()
                        + "\ndevice: " + Build.MANUFACTURER + " " + Build.MODEL
                        + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                        + "\n\n" + trace;
                try (FileOutputStream out = new FileOutputStream(new File(mApp.getFilesDir(), FILE))) {
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Could not save crash report", t);
            }
            if (mPrevious != null) mPrevious.uncaughtException(thread, error);
        }
    }

    /** Shows the saved crash, if any, with Copy and Dismiss buttons. */
    static void showIfAny(final Activity activity) {
        final File file = new File(activity.getFilesDir(), FILE);
        if (!file.exists()) return;
        final String report;
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int off = 0, n;
                while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n;
            }
            report = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return;
        }
        TextView text = new TextView(activity);
        text.setText(report);
        text.setTextIsSelectable(true);
        text.setTextSize(11);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad / 2, pad, 0);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.crash_report_title)
                .setView(scroll)
                .setPositiveButton(R.string.crash_report_copy, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager)
                            activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crash report", report));
                    Toast.makeText(activity, R.string.crash_report_copied, Toast.LENGTH_SHORT).show();
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                })
                .setNegativeButton(R.string.crash_report_dismiss, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                })
                .show();
    }
}
