/*
 * The mic key's trampoline: opens the system speech recogniser (RecognizerIntent) from the
 * keyboard and hands what was heard to the keyboard, which types it into the field the user
 * was in. The recogniser asks for the microphone itself, so the keyboard needs no permission.
 */
package kr.jcb1ee.hackerskeyboard2;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class VoiceInputActivity extends Activity {
    private static final String TAG = "HK/Voice";
    static final String EXTRA_LANGUAGE = "language";
    private static final int REQ_VOICE = 2;

    /** The request to the system recogniser, for the given input language ("ko_KR"; may be null). */
    static Intent recognizerIntent(String language) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (language != null) {
            // "ko_KR" -> "ko-KR" (BCP-47, as the recogniser expects)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.replace('_', '-'));
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return; // recreated while listening: the result will come
        try {
            startActivityForResult(recognizerIntent(getIntent().getStringExtra(EXTRA_LANGUAGE)),
                    REQ_VOICE);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "No speech recogniser", e);
            Toast.makeText(this, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) LatinIME.deliverVoiceText(results.get(0));
        }
        finish();
    }
}
