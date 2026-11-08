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

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The emoji panel's gallery button: opens the system photo picker and keeps a copy of the
 * chosen image for the keyboard, which inserts it once the app it was typing into is back.
 */
public class ImagePickActivity extends ComponentActivity {
    private static final String TAG = "HK/ImagePick";
    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private ActivityResultLauncher<PickVisualMediaRequest> mPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPicker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::picked);
        if (savedInstanceState == null) {
            mPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        }
    }

    private void picked(Uri uri) {
        if (uri != null) {
            String type = getContentResolver().getType(uri);
            if (type == null || !type.startsWith("image/")) type = "image/jpeg";
            File dir = new File(getCacheDir(), "picked");
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            File file = new File(dir, "image-" + System.currentTimeMillis() + "." + (ext != null ? ext : "img"));
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(file)) {
                if (in == null) throw new java.io.IOException("no stream");
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new java.io.IOException("too large");
                    out.write(buf, 0, n);
                }
                LatinIME.setPendingImage(file, type);
            } catch (Exception e) {
                Log.w(TAG, "Could not copy the picked image", e);
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
