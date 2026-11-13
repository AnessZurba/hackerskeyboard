package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Collections;

/** The mic key: opens the system recogniser, and types what it heard into the field. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VoiceInputTest {
    private Application mApp;
    private EditText mField;
    private LatinIME mIme;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mField = new EditText(mApp);
        mField.setText("hello");
        mField.setSelection(5);
        SelectionPanelTest.sConnection = mField.onCreateInputConnection(new EditorInfo());
        SelectionPanelTest.sInputType = android.text.InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sImages = false;
        ClipboardHistory.forget();
        mIme = Robolectric.buildService(SelectionPanelTest.TestIME.class).create().get();
    }

    private void installRecognizer() {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = "com.google.android.googlequicksearchbox";
        ri.activityInfo.name = "RecognizerActivity";
        shadowOf(mApp.getPackageManager()).addResolveInfoForIntent(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), ri);
    }

    private void micKey() {
        mIme.onKey(LatinKeyboardView.KEYCODE_VOICE, new int[] {LatinKeyboardView.KEYCODE_VOICE}, 0, 0);
    }

    private String text() {
        return mField.getText().toString();
    }

    private void hear(String words) {
        ActivityController<VoiceInputActivity> c = Robolectric.buildActivity(VoiceInputActivity.class).create();
        Intent asked = shadowOf(c.get()).getNextStartedActivityForResult().intent;
        ArrayList<String> heard = new ArrayList<>(Collections.singletonList(words));
        Intent result = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, heard);
        shadowOf(c.get()).receiveResult(asked, android.app.Activity.RESULT_OK, result);
    }

    @Test
    public void micKeyOpensTheVoiceActivityInANewTask() {
        installRecognizer();
        micKey();
        Intent started = shadowOf(mApp).getNextStartedActivity();
        assertNotNull("mic key started nothing", started);
        assertEquals(VoiceInputActivity.class.getName(), started.getComponent().getClassName());
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void micKeyWithoutARecogniserToastsAndStartsNothing() {
        micKey();
        assertNull(shadowOf(mApp).getNextStartedActivity());
        assertEquals(mApp.getString(R.string.voice_no_recognizer), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void voiceActivityAsksTheSystemRecogniser() {
        installRecognizer();
        Intent in = new Intent(mApp, VoiceInputActivity.class).putExtra(VoiceInputActivity.EXTRA_LANGUAGE, "ko_KR");
        ActivityController<VoiceInputActivity> c = Robolectric.buildActivity(VoiceInputActivity.class, in).create();
        Intent asked = shadowOf(c.get()).getNextStartedActivityForResult().intent;
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, asked.getAction());
        assertEquals("ko-KR", asked.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
        assertNull("no microphone permission is asked for: the recogniser does that",
                shadowOf(c.get()).getLastRequestedPermission());
    }

    @Test
    public void whatWasHeardIsTypedIntoTheFieldWhenTheEditorIsBack() {
        installRecognizer();
        hear("world");
        assertEquals("hello", text());
        // The app's editor restarts as the recogniser goes away.
        mIme.onStartInputView(new EditorInfo(), false);
        ShadowLooper.idleMainLooper();
        assertEquals("hello world", text());
        mIme.onStartInputView(new EditorInfo(), true);
        ShadowLooper.idleMainLooper();
        assertEquals("typed once", "hello world", text());
    }

    @Test
    public void cancelledRecognitionTypesNothing() {
        installRecognizer();
        ActivityController<VoiceInputActivity> c = Robolectric.buildActivity(VoiceInputActivity.class).create();
        shadowOf(c.get()).receiveResult(shadowOf(c.get()).getNextStartedActivityForResult().intent,
                android.app.Activity.RESULT_CANCELED, null);
        mIme.onStartInputView(new EditorInfo(), false);
        ShadowLooper.idleMainLooper();
        assertEquals("hello", text());
    }
}
