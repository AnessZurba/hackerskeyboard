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

import static kr.jcb1ee.hackerskeyboard2.KeyboardSwitcher.FULL_MODE_5_ROW;
import static kr.jcb1ee.hackerskeyboard2.KeyboardSwitcher.FULL_MODE_5_ROW_COMPACT;
import kr.jcb1ee.hackerskeyboard2.LatinIMEUtil.RingCharBuffer;


import org.xmlpull.v1.XmlPullParserException;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import androidx.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements
        ComposeSequencing,
        LatinKeyboardBaseView.OnKeyboardActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PCKeyboardIME";

    private static final String PREF_VIBRATE_ON = "vibrate_on";
    static final String PREF_VIBRATE_LEN = "vibrate_len";
    private static final String PREF_SOUND_ON = "sound_on";
    private static final String PREF_POPUP_ON = "popup_on";
    private static final String PREF_AUTO_CAP = "auto_cap";
    private static final String PREF_QUICK_FIXES = "quick_fixes";
    private static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    private static final String PREF_AUTO_COMPLETE = "auto_complete";
    // private static final String PREF_BIGRAM_SUGGESTIONS =
    // "bigram_suggestion";
    private static final String PREF_VOICE_MODE = "voice_mode";

    // The private IME option used to indicate that no microphone should be
    // shown for a
    // given text field. For instance this is specified by the search dialog
    // when the
    // dialog is already showing a voice search button.
    private static final String IME_OPTION_NO_MICROPHONE = "nm";

    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_INPUT_LANGUAGE = "input_language";
    private static final String PREF_RECORRECTION_ENABLED = "recorrection_enabled";
    static final String PREF_FULLSCREEN_OVERRIDE = "fullscreen_override";
    static final String PREF_FORCE_KEYBOARD_ON = "force_keyboard_on";
    static final String PREF_FULL_KEYBOARD_IN_PORTRAIT = "full_keyboard_in_portrait";
    static final String PREF_SUGGESTIONS_IN_LANDSCAPE = "suggestions_in_landscape";
    static final String PREF_HEIGHT_PORTRAIT = "settings_height_portrait";
    static final String PREF_HEIGHT_LANDSCAPE = "settings_height_landscape";
    static final String PREF_HINT_MODE = "pref_hint_mode";
    static final String PREF_LONGPRESS_TIMEOUT = "pref_long_press_duration";
    static final String PREF_RENDER_MODE = "pref_render_mode";
    static final String PREF_SWIPE_UP = "pref_swipe_up";
    static final String PREF_SWIPE_DOWN = "pref_swipe_down";
    static final String PREF_SWIPE_LEFT = "pref_swipe_left";
    static final String PREF_SWIPE_RIGHT = "pref_swipe_right";

    private static final int MSG_UPDATE_SUGGESTIONS = 0;
    private static final int MSG_START_TUTORIAL = 1;
    private static final int MSG_UPDATE_SHIFT_STATE = 2;
    private static final int MSG_VOICE_RESULTS = 3;
    private static final int MSG_UPDATE_OLD_SUGGESTIONS = 4;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    static final int ASCII_ENTER = '\n';
    static final int ASCII_SPACE = ' ';
    static final int ASCII_PERIOD = '.';

    // Contextual menu positions

    // private LatinKeyboardView mInputView;
    private LinearLayout mCandidateViewContainer;
    private CandidateView mCandidateView;
    private Suggest mSuggest;
    private CompletionInfo[] mCompletions;

    private AlertDialog mOptionsDialog;

    /* package */KeyboardSwitcher mKeyboardSwitcher;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private AutoDictionary mAutoDictionary;

    private Resources mResources;

    private String mInputLocale;
    private String mSystemLocale;
    private LanguageSwitcher mLanguageSwitcher;

    private StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private int mCommittedLength;
    private boolean mPredicting;
    private boolean mEnableVoiceButton;
    private CharSequence mBestWord;
    private boolean mPredictionOnForMode;
    private boolean mPredictionOnPref;    
    private boolean mCompletionOn;
    private boolean mHasDictionary;
    private boolean mAutoSpace;
    private boolean mJustAddedAutoSpace;
    private boolean mAutoCorrectEnabled;
    private boolean mReCorrectionEnabled;
    // Bigram Suggestion is disabled in this version.
    private final boolean mBigramSuggestionEnabled = false;
    private boolean mAutoCorrectOn;
    // TODO move this state variable outside LatinIME
    private boolean mModCtrl;
    private boolean mModAlt;
    private boolean mModMeta;
    private boolean mModFn;
    // Saved shift state when leaving alphabet mode, or when applying multitouch shift
    private int mSavedShiftState;
    private boolean mPasswordText;
    private boolean mVibrateOn;
    private int mVibrateLen;
    private boolean mSoundOn;
    private boolean mPopupOn;
    private boolean mAutoCapPref;
    private boolean mAutoCapActive;
    private boolean mDeadKeysActive;
    private boolean mQuickFixes;
    private boolean mShowSuggestions;
    private boolean mIsShowingHint;
    private boolean mFullscreenOverride;
    private boolean mForceKeyboardOn;
    private boolean mSuggestionsInLandscape;
    private boolean mSuggestionForceOn;
    private boolean mSuggestionForceOff;
    private String mSwipeUpAction;
    private String mSwipeDownAction;
    private String mSwipeLeftAction;
    private String mSwipeRightAction;

    public static final GlobalKeyboardSettings sKeyboardSettings = new GlobalKeyboardSettings(); 
    static LatinIME sInstance;
    private AdaptiveKeyTargets mAdaptive;
    
    private int mHeightPortrait;
    private int mHeightLandscape;
    private int mNumKeyboardModes = 3;
    private int mKeyboardModeOverridePortrait;
    private int mKeyboardModeOverrideLandscape;
    private int mCorrectionMode;
    private boolean mEnableVoice = true;
    private boolean mVoiceOnPrimary;
    private int mOrientation;
    private List<CharSequence> mSuggestPuncList;
    // Keep track of the last selection range to decide if we need to show word
    // alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;

    // Input type is such that we should not auto-correct
    private boolean mInputTypeNoAutoCorrect;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
    private int mDeleteCount;
    private long mLastKeyTime;

    // Modifier keys state
    private ModifierKeyState mShiftKeyState = new ModifierKeyState();
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState();
    private ModifierKeyState mCtrlKeyState = new ModifierKeyState();
    private ModifierKeyState mAltKeyState = new ModifierKeyState();
    private ModifierKeyState mMetaKeyState = new ModifierKeyState();
    private ModifierKeyState mFnKeyState = new ModifierKeyState();

    // Compose sequence handling
    private boolean mComposeMode = false;
    private ComposeSequence mComposeBuffer = new ComposeSequence(this);
    private ComposeSequence mDeadAccentBuffer = new DeadAccentSequence(this);

    // Hangul (Korean) syllable composition
    private final HangulComposer mHangulComposer = new HangulComposer();

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private final float FX_VOLUME = -1.0f;
    private final float FX_VOLUME_RANGE_DB = 72.0f;
    private boolean mSilentMode;

    /* package */String mWordSeparators;
    private String mSentenceSeparators;
    private boolean mConfigurationChanging;

    // Keeps track of most recently inserted text (multi-character key) for
    // reverting
    private CharSequence mEnteredText;
    private boolean mRefreshKeyboardRequired;

    // For each word, a list of potential replacements, usually from voice.
    private Map<String, List<CharSequence>> mWordToSuggestions = new HashMap<String, List<CharSequence>>();

    private ArrayList<WordAlternatives> mWordHistory = new ArrayList<WordAlternatives>();
    
    private PluginManager mPluginManager;


    public abstract static class WordAlternatives {
        protected CharSequence mChosenWord;

        public WordAlternatives() {
            // Nothing
        }

        public WordAlternatives(CharSequence chosenWord) {
            mChosenWord = chosenWord;
        }

        @Override
        public int hashCode() {
            return mChosenWord.hashCode();
        }

        public abstract CharSequence getOriginalWord();

        public CharSequence getChosenWord() {
            return mChosenWord;
        }

        public abstract List<CharSequence> getAlternatives();
    }

    public class TypedWordAlternatives extends WordAlternatives {
        private WordComposer word;

        public TypedWordAlternatives() {
            // Nothing
        }

        public TypedWordAlternatives(CharSequence chosenWord,
                WordComposer wordComposer) {
            super(chosenWord);
            word = wordComposer;
        }

        @Override
        public CharSequence getOriginalWord() {
            return word.getTypedWord();
        }

        @Override
        public List<CharSequence> getAlternatives() {
            return getTypedSuggestions(word);
        }
    }

    /* package */Handler mHandler = new Handler(android.os.Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
                updateSuggestions();
                break;
            case MSG_UPDATE_OLD_SUGGESTIONS:
                setOldSuggestions();
                break;
            case MSG_UPDATE_SHIFT_STATE:
                updateShiftKeyState(getCurrentInputEditorInfo());
                break;
            }
        }
    };

    @Override
    public void onCreate() {
        Log.i("PCKeyboard", "onCreate(), os.version=" + System.getProperty("os.version"));
        KeyboardSwitcher.init(this);
        super.onCreate();
        sInstance = this;
        LatinKeyboard.loadMicKeyChoice(PreferenceManager.getDefaultSharedPreferences(this));
        listenForNavigationBar();
        mAdaptive = AdaptiveKeyTargets.get(this);
        ClipboardHistory.get(this).setEnabled(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ClipboardHistory.PREF_ENABLED, true));
        ClipboardHistory.get(this).catchUp(); // copies made while the keyboard wasn't running
        // setStatusIcon(R.drawable.ime_qwerty);
        mResources = getResources();
        final Configuration conf = mResources.getConfiguration();
        mOrientation = conf.orientation;
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mLanguageSwitcher = new LanguageSwitcher(this);
        mLanguageSwitcher.loadLocales(prefs);
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        mSystemLocale = conf.locale.toString();
        mLanguageSwitcher.setSystemLocale(conf.locale);
        String inputLanguage = mLanguageSwitcher.getInputLanguage();
        if (inputLanguage == null) {
            inputLanguage = conf.locale.toString();
        }
        Resources res = getResources();
        mReCorrectionEnabled = prefs.getBoolean(PREF_RECORRECTION_ENABLED,
                res.getBoolean(R.bool.default_recorrection_enabled));
        mFullscreenOverride = prefs.getBoolean(PREF_FULLSCREEN_OVERRIDE,
                res.getBoolean(R.bool.default_fullscreen_override));
        mForceKeyboardOn = prefs.getBoolean(PREF_FORCE_KEYBOARD_ON,
                res.getBoolean(R.bool.default_force_keyboard_on));
        mSuggestionsInLandscape = prefs.getBoolean(PREF_SUGGESTIONS_IN_LANDSCAPE,
                res.getBoolean(R.bool.default_suggestions_in_landscape));
        mHeightPortrait = getHeight(prefs, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait));
        mHeightLandscape = getHeight(prefs, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape));
        LatinIME.sKeyboardSettings.hintMode = Integer.parseInt(prefs.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode)));
        LatinIME.sKeyboardSettings.longpressTimeout = getPrefInt(prefs, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration));
        LatinIME.sKeyboardSettings.renderMode = getPrefInt(prefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode));
        mSwipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up));
        mSwipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down));
        mSwipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left));
        mSwipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right));
        sKeyboardSettings.initPrefs(prefs, res);

        updateKeyboardOptions();

        PluginManager.getPluginDictionaries(getApplicationContext());
        mPluginManager = new PluginManager(this);
        final IntentFilter pFilter = new IntentFilter();
        pFilter.addDataScheme("package");
        pFilter.addAction("android.intent.action.PACKAGE_ADDED");
        pFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        pFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        ContextCompat.registerReceiver(this, mPluginManager, pFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        LatinIMEUtil.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                initSuggest(inputLanguage);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                        inputLanguage, e);
            }
        }

        mOrientation = conf.orientation;

        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(
                AudioManager.RINGER_MODE_CHANGED_ACTION);
        ContextCompat.registerReceiver(this, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private int getKeyboardModeNum(int origMode, int override) {
        if (!sKeyboardSettings.compactModeEnabled && origMode == FULL_MODE_5_ROW)
            origMode = FULL_MODE_5_ROW_COMPACT; // skip "compact".
        int num = (origMode + override) % mNumKeyboardModes;
        if (!sKeyboardSettings.compactModeEnabled && num == FULL_MODE_5_ROW_COMPACT)
            num = FULL_MODE_5_ROW; // skip "compact".
        return num;
    }
    
    private void updateKeyboardOptions() {
        //Log.i(TAG, "setFullKeyboardOptions " + fullInPortrait + " " + heightPercentPortrait + " " + heightPercentLandscape);
        boolean isPortrait = isPortrait();
        int kbMode;
        mNumKeyboardModes = sKeyboardSettings.compactModeEnabled ? 4 : 3; // FIXME!
        if (isPortrait) {
            kbMode = getKeyboardModeNum(sKeyboardSettings.keyboardModePortrait, mKeyboardModeOverridePortrait);
        } else {
            kbMode = getKeyboardModeNum(sKeyboardSettings.keyboardModeLandscape, mKeyboardModeOverrideLandscape);
        }
        // Convert overall keyboard height to per-row percentage
        int screenHeightPercent = isPortrait ? mHeightPortrait : mHeightLandscape;
        LatinIME.sKeyboardSettings.keyboardMode = kbMode;
        LatinIME.sKeyboardSettings.keyboardHeightPercent = (float) screenHeightPercent;
    }

    private boolean isPortrait() {
        return (mOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private boolean suggestionsDisabled() {
        if (mSuggestionForceOff) return true;
        if (mSuggestionForceOn) return false;
        return !(mSuggestionsInLandscape || isPortrait());
    }

    /**
     * Loads a dictionary or multiple separated dictionary
     *
     * @return returns array of dictionary resource ids
     */
    /* package */static int[] getDictionary(Resources res) {
        String packageName = LatinIME.class.getPackage().getName();
        XmlResourceParser xrp = res.getXml(R.xml.dictionary);
        ArrayList<Integer> dictionaries = new ArrayList<Integer>();

        try {
            int current = xrp.getEventType();
            while (current != XmlResourceParser.END_DOCUMENT) {
                if (current == XmlResourceParser.START_TAG) {
                    String tag = xrp.getName();
                    if (tag != null) {
                        if (tag.equals("part")) {
                            String dictFileName = xrp.getAttributeValue(null,
                                    "name");
                            dictionaries.add(res.getIdentifier(dictFileName,
                                    "raw", packageName));
                        }
                    }
                }
                xrp.next();
                current = xrp.getEventType();
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Dictionary XML parsing failure");
        } catch (IOException e) {
            Log.e(TAG, "Dictionary XML IOException");
        }

        int count = dictionaries.size();
        int[] dict = new int[count];
        for (int i = 0; i < count; i++) {
            dict[i] = dictionaries.get(i);
        }

        return dict;
    }

    private void initSuggest(String locale) {
        mInputLocale = locale;

        Resources orig = getResources();
        Configuration conf = orig.getConfiguration();
        Locale saveLocale = conf.locale;
        conf.locale = new Locale(locale);
        orig.updateConfiguration(conf, orig.getDisplayMetrics());
        if (mSuggest != null) {
            mSuggest.close();
        }
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, getResources()
                .getBoolean(R.bool.default_quick_fixes));

        int[] dictionaries = getDictionary(orig);
        mSuggest = new Suggest(this, dictionaries);
        updateAutoTextEnabled(saveLocale);
        if (mUserDictionary != null)
            mUserDictionary.close();
        mUserDictionary = new UserDictionary(this, mInputLocale);
        if (mAutoDictionary != null) {
            mAutoDictionary.close();
        }
        mAutoDictionary = new AutoDictionary(this, this, mInputLocale,
                Suggest.DIC_AUTO);
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary.close();
        }
        mUserBigramDictionary = new UserBigramDictionary(this, this,
                mInputLocale, Suggest.DIC_USER);
        mSuggest.setUserBigramDictionary(mUserBigramDictionary);
        mSuggest.setUserDictionary(mUserDictionary);
        mSuggest.setAutoDictionary(mAutoDictionary);
        updateCorrectionMode();
        mWordSeparators = mResources.getString(R.string.word_separators);
        mSentenceSeparators = mResources
                .getString(R.string.sentence_separators);
        initSuggestPuncList();

        conf.locale = saveLocale;
        orig.updateConfiguration(conf, orig.getDisplayMetrics());
    }

    private void startVoiceInput() {
        String lang = mLanguageSwitcher != null ? mLanguageSwitcher.getInputLanguage() : null;
        // Needs the <queries> entry for RECOGNIZE_SPEECH in the manifest (Android 11+).
        if (VoiceInputActivity.recognizerIntent(lang).resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show();
            return;
        }
        InputConnection ic = appConnection();
        if (ic != null) commitTyped(ic, true); // a word in progress stays put
        Intent intent = new Intent(this, VoiceInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (lang != null) intent.putExtra(VoiceInputActivity.EXTRA_LANGUAGE, lang);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- voice

    private static String sPendingVoice;
    private static long sPendingVoiceTime;

    /**
     * Text the recogniser heard, to type into the field the user was in. The app's editor
     * restarts when it comes back in front of the recogniser, so it is typed then (or at once
     * if the keyboard is already showing for the app).
     */
    static void deliverVoiceText(String text) {
        if (text == null || text.isEmpty()) return;
        sPendingVoice = text;
        sPendingVoiceTime = SystemClock.uptimeMillis();
        LatinIME ime = sInstance;
        if (ime != null && ime.isInputViewShown()) ime.mHandler.post(ime::insertPendingVoice);
    }

    void insertPendingVoice() {
        String text = sPendingVoice;
        if (text == null) return;
        if (SystemClock.uptimeMillis() - sPendingVoiceTime > 120000) { // stale
            sPendingVoice = null;
            return;
        }
        InputConnection ic = appConnection();
        if (ic == null) return; // the app's editor isn't back yet: onStartInputView tries again
        sPendingVoice = null;
        commitTyped(ic, true);
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() > 0 && !Character.isWhitespace(before.charAt(0))) {
            text = " " + text; // not glued to the previous word
        }
        ic.commitText(text, 1);
    }

    @Override
    public void onDestroy() {
        if (mUserDictionary != null) {
            mUserDictionary.close();
        }
        unregisterReceiver(mReceiver);
        unregisterReceiver(mPluginManager);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        Log.i("PCKeyboard", "onConfigurationChanged()");
        // If the system locale changes and is different from the saved
        // locale (mSystemLocale), then reload the input locale list from the
        // latin ime settings (shared prefs) and reset the input locale
        // to the first one.
        final String systemLocale = conf.locale.toString();
        if (!TextUtils.equals(systemLocale, mSystemLocale)) {
            mSystemLocale = systemLocale;
            if (mLanguageSwitcher != null) {
                mLanguageSwitcher.loadLocales(PreferenceManager
                        .getDefaultSharedPreferences(this));
                mLanguageSwitcher.setSystemLocale(conf.locale);
                toggleLanguage(true, true);
            } else {
                reloadKeyboards();
            }
        }
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            InputConnection ic = getCurrentInputConnection();
            commitTyped(ic, true);
            if (ic != null)
                ic.finishComposingText(); // For voice input
            mOrientation = conf.orientation;
            reloadKeyboards();
            removeCandidateViewContainer();
        }
        mConfigurationChanging = true;
        super.onConfigurationChanged(conf);
        mConfigurationChanging = false;
        mKeyboardSwitcher.refreshThemeColours();
    }

    @Override
    public View onCreateInputView() {
        setCandidatesViewShown(false);  // Workaround for "already has a parent" when reconfiguring
        mKeyboardSwitcher.recreateInputView();
        mKeyboardSwitcher.makeKeyboards(true);
        mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT, 0,
                shouldShowVoiceButton(getCurrentInputEditorInfo()));
        return mKeyboardSwitcher.getInputContainer();
    }

    @Override
    public View onCreateCandidatesView() {
        //Log.i(TAG, "onCreateCandidatesView(), mCandidateViewContainer=" + mCandidateViewContainer);
        //mKeyboardSwitcher.makeKeyboards(true);
        if (mCandidateViewContainer == null) {
            mCandidateViewContainer = (LinearLayout) getLayoutInflater().inflate(
                    R.layout.candidates, null);
            mCandidateView = (CandidateView) mCandidateViewContainer
            .findViewById(R.id.candidates);
            mCandidateView.setPadding(0, 0, 0, 0);
            mCandidateView.setService(this);
            setCandidatesView(mCandidateViewContainer);
        }
        return mCandidateViewContainer;
    }

    private void removeCandidateViewContainer() {
        //Log.i(TAG, "removeCandidateViewContainer(), mCandidateViewContainer=" + mCandidateViewContainer);
        if (mCandidateViewContainer != null) {
            mCandidateViewContainer.removeAllViews();
            ViewParent parent = mCandidateViewContainer.getParent();
            if (parent != null && parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mCandidateViewContainer);
            }
            mCandidateViewContainer = null;
            mCandidateView = null;
        }
        resetPrediction();
    }

    private void resetPrediction() {
        mComposing.setLength(0);
        mPredicting = false;
        mDeleteCount = 0;
        mJustAddedAutoSpace = false;
    }
    
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        // Material You: the wallpaper or light/dark mode may have changed while hidden.
        mKeyboardSwitcher.refreshThemeColours();
        // The navigation bar (or the keyboard's own gesture bar) may have changed too.
        mHandler.post(this::refreshNavigationBar);
        if (mSearchConnection != null && mKeyboardSwitcher.getInputContainer() != null) {
            mKeyboardSwitcher.getInputContainer().closePanel(); // ends the emoji search
        }
        if (sPendingImage != null) mHandler.post(this::insertPendingImage);
        if (sPendingVoice != null) mHandler.post(this::insertPendingVoice);
        // Anything copied while the keyboard wasn't running (it only hears new copies then).
        ClipboardHistory.get(this).catchUp();
        sKeyboardSettings.editorPackageName = attribute.packageName;
        sKeyboardSettings.editorFieldName = attribute.fieldName;
        sKeyboardSettings.editorFieldId = attribute.fieldId;
        sKeyboardSettings.editorInputType = attribute.inputType;

        //Log.i("PCKeyboard", "onStartInputView " + attribute + ", inputType= " + Integer.toHexString(attribute.inputType) + ", restarting=" + restarting);
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        // In landscape mode, this method gets called without the input view
        // being created.
        if (inputView == null) {
            return;
        }

        if (mRefreshKeyboardRequired) {
            mRefreshKeyboardRequired = false;
            toggleLanguage(true, true);
        }

        mKeyboardSwitcher.makeKeyboards(false);

        TextEntryState.newSession(this);

        // Most such things we decide below in the switch statement, but we need to know
        // now whether this is a password text field, because we need to know now (before
        // the switch statement) whether we want to enable the voice button.
        mPasswordText = false;
        int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) {
            if ((attribute.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
                mPasswordText = true;
            }
        }

        mEnableVoiceButton = shouldShowVoiceButton(attribute);
        final boolean enableVoiceButton = mEnableVoiceButton && mEnableVoice;

        mInputTypeNoAutoCorrect = false;
        mPredictionOnForMode = false;
        mCompletionOn = false;
        mCompletions = null;
        mModCtrl = false;
        mModAlt = false;
        mModMeta = false;
        mModFn = false;
        mAdaptive.setModifierActive(false);
        // Terminals report no input type and don't expose their text: plain backspace there.
        mDeleteSwipeField = (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) != EditorInfo.TYPE_NULL;
        mAdaptive.onStartInput(attribute, getCurrentInputConnection(),
                mLanguageSwitcher.getInputLanguage());
        mEnteredText = null;
        mSuggestionForceOn = false;
        mSuggestionForceOff = false;
        mKeyboardModeOverridePortrait = 0;
        mKeyboardModeOverrideLandscape = 0;

        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
        case EditorInfo.TYPE_CLASS_NUMBER:
        case EditorInfo.TYPE_CLASS_DATETIME:
            // fall through
            // NOTE: For now, we use the phone keyboard for NUMBER and DATETIME
            // until we get
            // a dedicated number entry keypad.
            // TODO: Use a dedicated number entry keypad here when we get one.
        case EditorInfo.TYPE_CLASS_PHONE:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_PHONE,
                    attribute.imeOptions, enableVoiceButton);
            break;
        case EditorInfo.TYPE_CLASS_TEXT:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton);
            // startPrediction();
            mPredictionOnForMode = true;
            // Make sure that passwords are not displayed in candidate view
            if (mPasswordText) {
                mPredictionOnForMode = false;
            }
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
                    || !mLanguageSwitcher.allowAutoSpace()) {
                mAutoSpace = false;
            } else {
                mAutoSpace = true;
            }
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                mPredictionOnForMode = false;
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_EMAIL,
                        attribute.imeOptions, enableVoiceButton);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                mPredictionOnForMode = false;
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_URL,
                        attribute.imeOptions, enableVoiceButton);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_IM,
                        attribute.imeOptions, enableVoiceButton);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                mPredictionOnForMode = false;
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_WEB,
                        attribute.imeOptions, enableVoiceButton);
                // If it's a browser edit field and auto correct is not ON
                // explicitly, then
                // disable auto correction, but keep suggestions on.
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                    mInputTypeNoAutoCorrect = true;
                }
            }

            // If NO_SUGGESTIONS is set, don't do prediction.
            if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                mPredictionOnForMode = false;
                mInputTypeNoAutoCorrect = true;
            }
            // If it's not multiline and the autoCorrect flag is not set, then
            // don't correct
            if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                    && (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                mInputTypeNoAutoCorrect = true;
            }
            if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                mPredictionOnForMode = false;
                mCompletionOn = isFullscreenMode();
            }
            break;
        default:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton);
        }
        inputView.closing();
        resetPrediction();
        loadSettings();
        updateShiftKeyState(attribute);

        mPredictionOnPref = (mCorrectionMode > 0 || mShowSuggestions);
        setCandidatesViewShownInternal(isCandidateStripVisible()
                || mCompletionOn, false /* needsInputViewShown */);
        updateSuggestions();

        // If the dictionary is not big enough, don't auto correct
        mHasDictionary = mSuggest.hasMainDictionary();

        updateCorrectionMode();

        inputView.setPreviewEnabled(mPopupOn);
        inputView.setProximityCorrectionEnabled(true);
        // If we just entered a text field, maybe it has some old text that
        // requires correction
        checkReCorrectionOnStart();
    }

    private boolean shouldShowVoiceButton(EditorInfo attribute) {
        // TODO Auto-generated method stub
        return true;
    }

    private void checkReCorrectionOnStart() {
        if (mReCorrectionEnabled && isPredictionOn()) {
            // First get the cursor position. This is required by
            // setOldSuggestions(), so that
            // it can pass the correct range to setComposingRegion(). At this
            // point, we don't
            // have valid values for mLastSelectionStart/Stop because
            // onUpdateSelection() has
            // not been called yet.
            InputConnection ic = getCurrentInputConnection();
            if (ic == null)
                return;
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0; // anything is fine here
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et == null)
                return;

            mLastSelectionStart = et.startOffset + et.selectionStart;
            mLastSelectionEnd = et.startOffset + et.selectionEnd;

            // Then look for possible corrections in a delayed fashion
            if (!TextUtils.isEmpty(et.text) && isCursorTouchingWord()) {
                postUpdateOldSuggestions();
            }
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        mCursorDrag.cancel();
        mAdaptive.onFinishInput();
        commitHangulComposing();
        mHangulComposer.reset();

        onAutoCompletionStateChanged(false);

        if (mKeyboardSwitcher.getInputView() != null) {
            mKeyboardSwitcher.getInputView().closing();
        }
        if (mAutoDictionary != null)
            mAutoDictionary.flushPendingWrites();
        if (mUserBigramDictionary != null)
            mUserBigramDictionary.flushPendingWrites();
    }

    /** Navigation bar insets of the keyboard window: left, right, bottom (0 if none). */
    private androidx.core.graphics.Insets mNavBar = androidx.core.graphics.Insets.NONE;

    androidx.core.graphics.Insets navigationBarInsets() {
        return mNavBar;
    }

    /**
     * Since Android 15 the keyboard window reaches the bottom of the screen (edge to edge, and
     * InputMethodService leaves the bottom inset unfitted), so the keyboard has to keep its
     * keys above the navigation bar itself. The bar's size is read where the window gets it,
     * on its decor view; the keyboard container pads by it (KeyboardToolbar).
     */
    private void listenForNavigationBar() {
        android.app.Dialog d = getWindow();
        android.view.Window w = d != null ? d.getWindow() : null;
        if (w == null) return;
        // Edge to edge on every Android version (it is forced from 15), so that the padding
        // below is the only room made for the bar, never added to the window's own.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
        noScrimOnNavigationBar(w);
        View decor = w.getDecorView();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
            setNavigationBar(insets);
            return androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets);
        });
        androidx.core.view.ViewCompat.requestApplyInsets(decor);
    }

    /** Reads the insets again (they may not be dispatched again when the window is reshown). */
    private void refreshNavigationBar() {
        android.app.Dialog d = getWindow();
        android.view.Window w = d != null ? d.getWindow() : null;
        if (w == null) return;
        noScrimOnNavigationBar(w);
        View decor = w.getDecorView();
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor);
        if (insets != null) setNavigationBar(insets);
        androidx.core.view.ViewCompat.requestApplyInsets(decor);
    }

    void setNavigationBar(androidx.core.view.WindowInsetsCompat insets) {
        // The same rule as the platform's own frame for the gesture bar the keyboard window
        // draws (InputMethodService NavigationBarController.getSystemInsets): system bars as
        // shown, capped by their stable size. All system bars, not just navigationBars():
        // that gesture bar is a captionBar inset, and navigationBars is then 0 (Android 17:
        // nav 0, caption 180px). Nothing is kept for a bar that isn't shown (landscape).
        int bars = androidx.core.view.WindowInsetsCompat.Type.systemBars();
        androidx.core.graphics.Insets bar = androidx.core.graphics.Insets.min(
                insets.getInsets(bars), insets.getInsetsIgnoringVisibility(bars));
        // A display cutout at the side or bottom (landscape) also keeps keys clear.
        bar = androidx.core.graphics.Insets.max(bar,
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout()));
        // Only the sides and bottom matter (the window never reaches the status bar).
        bar = androidx.core.graphics.Insets.of(bar.left, 0, bar.right, bar.bottom);
        noteNavigationBar(insets, bar);
        if (bar.equals(mNavBar)) return;
        mNavBar = bar;
        KeyboardToolbar toolbar = mKeyboardSwitcher != null ? mKeyboardSwitcher.getInputContainer() : null;
        if (toolbar != null) mHandler.post(toolbar::keepClearOfNavigationBar);
    }

    /**
     * The keyboard paints the bar's space in its own colour (KeyboardToolbar); without this the
     * window lays a translucent dark scrim over it ("contrast enforcement", on by default),
     * which looked like a black band (DecorView.calculateBarColor). FlorisBoard does the same.
     */
    private static void noScrimOnNavigationBar(android.view.Window w) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.setNavigationBarContrastEnforced(false);
    }

    /** The last navigation bar reading, shown under Version in settings (diagnostic). */
    static final String PREF_NAV_DIAGNOSTIC = "nav_bar_diagnostic";

    private void noteNavigationBar(androidx.core.view.WindowInsetsCompat insets,
            androidx.core.graphics.Insets used) {
        int sys = androidx.core.view.WindowInsetsCompat.Type.systemBars();
        int cap = androidx.core.view.WindowInsetsCompat.Type.captionBar();
        int nav = androidx.core.view.WindowInsetsCompat.Type.navigationBars();
        String contrast = "?";
        android.app.Dialog d = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && d != null && d.getWindow() != null) {
            contrast = String.valueOf(d.getWindow().isNavigationBarContrastEnforced());
        }
        String note = (isPortrait() ? "portrait" : "landscape")
                + ": bars " + insets.getInsets(sys).bottom
                + " (stable " + insets.getInsetsIgnoringVisibility(sys).bottom + ")"
                + ", nav " + insets.getInsets(nav).bottom
                + ", caption " + insets.getInsets(cap).bottom
                + (insets.isVisible(cap) ? " shown" : " hidden")
                + ", used " + used.left + "," + used.right + "," + used.bottom
                + ", scrim " + contrast + " (API " + Build.VERSION.SDK_INT + ")";
        try {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(PREF_NAV_DIAGNOSTIC, note).apply();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        refreshNavigationBar();
        ClipboardHistory.get(this).catchUp(); // cheap: reads the clip only when it's new
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        clearAutofillChips();
        if (mKeyboardSwitcher.getInputContainer() != null) {
            mKeyboardSwitcher.getInputContainer().closePanel();
        }
        // Remove penging messages related to update suggestions
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        InputConnection ic = getCurrentInputConnection();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        // Typing moves the cursor by one; anything else means the word before it may not
        // be one the keyboard typed, so it must not be learned.
        int cursorDelta = newSelStart - oldSelStart;
        if (newSelStart != newSelEnd || cursorDelta < -1 || cursorDelta > 1) {
            mAdaptive.onCursorJump();
        }
        if (mCursorDrag.onUpdateSelection(newSelStart, newSelEnd)) {
            // A trackpad drag: skip the suggestion work for every step.
            mLastSelectionStart = newSelStart;
            mLastSelectionEnd = newSelEnd;
            return;
        }

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if ((((mComposing.length() > 0 && mPredicting))
                && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd) && mLastSelectionStart != newSelStart)) {
            mComposing.setLength(0);
            mPredicting = false;
            postUpdateSuggestions();
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        } else if (!mPredicting && !mJustAccepted) {
            switch (TextEntryState.getState()) {
            case ACCEPTED_DEFAULT:
                TextEntryState.reset();
                // fall through
            case SPACE_AFTER_PICKED:
                mJustAddedAutoSpace = false; // The user moved the cursor.
                break;
            }
        }
        mJustAccepted = false;
        postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;

        if (mReCorrectionEnabled) {
            // Don't look for corrections if the keyboard is not visible
            if (mKeyboardSwitcher != null
                    && mKeyboardSwitcher.getInputView() != null
                    && mKeyboardSwitcher.getInputView().isShown()) {
                // Check if we should go in or out of correction mode.
                if (isPredictionOn()
                        && mJustRevertedSeparator == null
                        && (candidatesStart == candidatesEnd
                                || newSelStart != oldSelStart || TextEntryState
                                .isCorrecting())
                        && (newSelStart < newSelEnd - 1 || (!mPredicting))) {
                    if (isCursorTouchingWord()
                            || mLastSelectionStart < mLastSelectionEnd) {
                        postUpdateOldSuggestions();
                    } else {
                        abortCorrection(false);
                        // Show the punctuation suggestions list if the current
                        // one is not
                        // and if not showing "Touch again to save".
                        if (mCandidateView != null
                                && !mSuggestPuncList.equals(mCandidateView
                                        .getSuggestions())
                                && !mCandidateView
                                        .isShowingAddToDictionaryHint()) {
                            setNextSuggestions();
                        }
                    }
                }
            }
        }
    }

    /**
     * This is called when the user has clicked on the extracted text view, when
     * running in fullscreen mode. The default implementation hides the
     * candidates view when this happens, but only if the extracted text editor
     * has a vertical scroll bar because its text doesn't fit. Here we override
     * the behavior due to the possibility that a re-correction could cause the
     * candidate strip to disappear and re-appear.
     */
    @Override
    public void onExtractedTextClicked() {
        if (mReCorrectionEnabled && isPredictionOn())
            return;

        super.onExtractedTextClicked();
    }

    /**
     * This is called when the user has performed a cursor movement in the
     * extracted text view, when it is running in fullscreen mode. The default
     * implementation hides the candidates view when a vertical movement
     * happens, but only if the extracted text editor has a vertical scroll bar
     * because its text doesn't fit. Here we override the behavior due to the
     * possibility that a re-correction could cause the candidate strip to
     * disappear and re-appear.
     */
    @Override
    public void onExtractedCursorMovement(int dx, int dy) {
        if (mReCorrectionEnabled && isPredictionOn())
            return;

        super.onExtractedCursorMovement(dx, dy);
    }

    @Override
    public void hideWindow() {
        onAutoCompletionStateChanged(false);

        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        mWordToSuggestions.clear();
        mWordHistory.clear();
        super.hideWindow();
        TextEntryState.endSession();
    }

    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                clearSuggestions();
                return;
            }

            List<CharSequence> stringList = new ArrayList<CharSequence>();
            for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null)
                    stringList.add(ci.getText());
            }
            // When in fullscreen mode, show completions generated by the
            // application
            setSuggestions(stringList, true, true, true);
            mBestWord = null;
            setCandidatesViewShown(true);
        }
    }

    private void setCandidatesViewShownInternal(boolean shown,
            boolean needsInputViewShown) {
//        Log.i(TAG, "setCandidatesViewShownInternal(" + shown + ", " + needsInputViewShown +
//                " mCompletionOn=" + mCompletionOn +
//                " mPredictionOnForMode=" + mPredictionOnForMode +
//                " mPredictionOnPref=" + mPredictionOnPref +
//                " mPredicting=" + mPredicting
//                );
        // TODO: Remove this if we support candidates with hard keyboard
        boolean visible = shown
        && onEvaluateInputViewShown()
        && mKeyboardSwitcher.getInputView() != null
        && isPredictionOn()
        && hasSuggestionSource()
        && (needsInputViewShown
                ? mKeyboardSwitcher.getInputView().isShown()
                        : true);
        if (visible) {
            if (mCandidateViewContainer == null) {
                onCreateCandidatesView();
                setNextSuggestions();
            }
        } else {
            if (mCandidateViewContainer != null) {
                removeCandidateViewContainer();
                commitTyped(getCurrentInputConnection(), true);
            }
        }
        super.setCandidatesViewShown(visible);
    }

    @Override
    public void onFinishCandidatesView(boolean finishingInput) {
        //Log.i(TAG, "onFinishCandidatesView(), mCandidateViewContainer=" + mCandidateViewContainer);
        super.onFinishCandidatesView(finishingInput);
        if (mCandidateViewContainer != null) {
            removeCandidateViewContainer();
        }
    }

    @Override
    public boolean onEvaluateInputViewShown() {
    	boolean parent = super.onEvaluateInputViewShown();
    	boolean wanted = mForceKeyboardOn || parent;
    	//Log.i(TAG, "OnEvaluateInputViewShown, parent=" + parent + " + " wanted=" + wanted);
    	return wanted;
    }
    
    @Override
    public void setCandidatesViewShown(boolean shown) {
        setCandidatesViewShownInternal(shown, true /* needsInputViewShown */);
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isFullscreenMode()) {
            // A hidden toolbar row keeps its space in the window (so the keys never move);
            // the app gets that space, and touches there go to the app.
            KeyboardToolbar toolbar = mKeyboardSwitcher.getInputContainer();
            boolean stripShown = mCandidateViewContainer != null && mCandidateViewContainer.isShown();
            if (toolbar != null && toolbar.isShown() && !stripShown) {
                outInsets.visibleTopInsets += toolbar.spaceGivenToApp();
            }
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float displayHeight = dm.heightPixels;
        // If the display is more than X inches high, don't go to fullscreen
        // mode
        float dimen = getResources().getDimension(
                R.dimen.max_height_for_fullscreen);
        if (displayHeight > dimen || mFullscreenOverride) {
            return false;
        } else {
            return super.onEvaluateFullscreenMode();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (event.getRepeatCount() == 0 && mSearchConnection != null
                    && mKeyboardSwitcher.getInputContainer() != null) {
                mKeyboardSwitcher.getInputContainer().endSearchFromBack();
                return true;
            }
            if (event.getRepeatCount() == 0
                    && mKeyboardSwitcher.getInputView() != null) {
                if (mKeyboardSwitcher.getInputView().handleBack()) {
                    return true;
                }
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
            // Enable shift key and DPAD to do selections
            if (inputView != null && inputView.isShown()
                    && inputView.getShiftState() == Keyboard.SHIFT_ON) {
                event = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), event.getKeyCode(), event
                                .getRepeatCount(), event.getDeviceId(), event
                                .getScanCode(), KeyEvent.META_SHIFT_LEFT_ON
                                | KeyEvent.META_SHIFT_ON);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                    ic.sendKeyEvent(event);
                return true;
            }
            break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** The mic/settings key changed its primary: rebuild the keyboards to show it. */
    void refreshMicKey() {
        toggleLanguage(true, true); // rebuilds and re-shows the current keyboard
    }

    private void reloadKeyboards() {
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        if (mKeyboardSwitcher.getInputView() != null
                && mKeyboardSwitcher.getKeyboardMode() != KeyboardSwitcher.MODE_NONE) {
            mKeyboardSwitcher.setVoiceMode(mEnableVoice && mEnableVoiceButton,
                    mVoiceOnPrimary);
        }
        updateKeyboardOptions();
        mKeyboardSwitcher.makeKeyboards(true);
    }

    private void commitTyped(InputConnection inputConnection, boolean manual) {
        if (mPredicting) {
            mPredicting = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                if (manual) {
                    TextEntryState.manualTyped(mComposing);
                } else {
                    TextEntryState.acceptedTyped(mComposing);
                }
                addToDictionaries(mComposing,
                        AutoDictionary.FREQUENCY_FOR_TYPED);
            }
            updateSuggestions();
        }
    }

    private void postUpdateShiftKeyState() {
        // TODO(klausw): disabling, I have no idea what this is supposed to accomplish.
//        //updateShiftKeyState(getCurrentInputEditorInfo());
//
//        // FIXME: why the delay?
//        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
//        // TODO: Should remove this 300ms delay?
//        mHandler.sendMessageDelayed(mHandler
//                .obtainMessage(MSG_UPDATE_SHIFT_STATE), 300);
    }

    public void updateShiftKeyState(EditorInfo attr) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && attr != null && mKeyboardSwitcher.isAlphabetMode()) {
            int oldState = getShiftState();
            boolean isShifted = mShiftKeyState.isChording();
            boolean isCapsLock = (oldState == Keyboard.SHIFT_CAPS_LOCKED || oldState == Keyboard.SHIFT_LOCKED);
            boolean isCaps = isCapsLock || getCursorCapsMode(ic, attr) != 0;
            //Log.i(TAG, "updateShiftKeyState isShifted=" + isShifted + " isCaps=" + isCaps + " isMomentary=" + mShiftKeyState.isMomentary() + " cursorCaps=" + getCursorCapsMode(ic, attr));
            int newState = Keyboard.SHIFT_OFF;
            if (isShifted) {
                newState = (mSavedShiftState == Keyboard.SHIFT_LOCKED) ? Keyboard.SHIFT_CAPS : Keyboard.SHIFT_ON;
            } else if (isCaps) {
                newState = isCapsLock ? getCapsOrShiftLockState() : Keyboard.SHIFT_CAPS;
            }
            //Log.i(TAG, "updateShiftKeyState " + oldState + " -> " + newState);
            mKeyboardSwitcher.setShiftState(newState);
        }
        if (ic != null) {
            // Clear modifiers other than shift, to avoid them getting stuck
            int states = 
                KeyEvent.META_FUNCTION_ON
                | KeyEvent.META_ALT_MASK
                | KeyEvent.META_CTRL_MASK
                | KeyEvent.META_META_MASK
                | KeyEvent.META_SYM_ON;
            ic.clearMetaKeyStates(states);
        }
    }

    private int getShiftState() {
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView view = mKeyboardSwitcher.getInputView();
            if (view != null) {
                return view.getShiftState();
            }
        }
        return Keyboard.SHIFT_OFF;
    }

    private boolean isShiftCapsMode() {
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView view = mKeyboardSwitcher.getInputView();
            if (view != null) {
                return view.isShiftCaps();
            }
        }
        return false;
    }

    private int getCursorCapsMode(InputConnection ic, EditorInfo attr) {
        int caps = 0;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (mAutoCapActive && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
            caps = ic.getCursorCapsMode(attr.inputType);
        }
        return caps;
    }

    private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == ASCII_SPACE
                && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
            mJustAddedAutoSpace = true;
        }
    }

    private void reswapPeriodAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && lastThree.charAt(0) == ASCII_PERIOD
                && lastThree.charAt(1) == ASCII_SPACE
                && lastThree.charAt(2) == ASCII_PERIOD) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(3, 0);
            ic.commitText(" ..", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }

    private void doubleSpace() {
        // if (!mAutoPunctuate) return;
        if (mCorrectionMode == Suggest.CORRECTION_NONE)
            return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == ASCII_SPACE
                && lastThree.charAt(2) == ASCII_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
            mJustAddedAutoSpace = true;
        }
    }

    private void maybeRemovePreviousPeriod(CharSequence text) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null || text.length() == 0)
            return;

        // When the text's first character is '.', remove the previous period
        // if there is one.
        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == ASCII_PERIOD
                && text.charAt(0) == ASCII_PERIOD) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void removeTrailingSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == ASCII_SPACE) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    public boolean addWordToDictionary(String word) {
        mUserDictionary.addWord(word, 128);
        // Suggestion strip should be updated after the operation of adding word
        // to the
        // user dictionary
        postUpdateSuggestions();
        return true;
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /** Returns true when the current input locale is Korean (ko). */
    private boolean isHangulMode() {
        String lang = mLanguageSwitcher.getInputLanguage();
        return lang != null && lang.startsWith("ko");
    }

    /**
     * Returns true for Hangul Compatibility Jamo codepoints (U+3131–U+3163).
     * These are the characters output by the Korean keyboard layout.
     */
    private static boolean isHangulJamo(int code) {
        return code >= 0x3131 && code <= 0x3163;
    }

    /**
     * Commits any in-progress Hangul syllable to the editor without starting a new one.
     * Called when a non-Jamo character is typed or special actions occur while composing.
     */
    private void commitHangulComposing() {
        if (!mHangulComposer.isComposing()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(mHangulComposer.getComposing(), 1);
        }
        mHangulComposer.reset();
    }

    /**
     * Handles a Hangul Jamo keypress by routing it through the syllable composer.
     * The composer may return text to commit (a completed syllable) plus new composing text.
     */
    private void handleHangulCharacter(int primaryCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        HangulComposer.Result result = mHangulComposer.compose(primaryCode);

        ic.beginBatchEdit();
        if (result.commit.length() > 0) {
            ic.commitText(result.commit, 1);
        }
        if (result.composing.length() > 0) {
            ic.setComposingText(result.composing, 1);
        } else {
            ic.finishComposingText();
        }
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    void showInputMethodPicker() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showInputMethodPicker();
    }

    private void onOptionKeyPressed() {
        // The system offers the input method picker itself, so just open the settings.
        // Deferred to avoid infinite recursion: launchSettings() calls handleClose() →
        // dismissPopupKeyboard() → releaseAllPointersExcept() which re-triggers onKey()
        // for any still-queued pointer, leading to a StackOverflowError.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                launchSettings();
            }
        });
    }

    private void onOptionKeyLongPressed() {
        if (!isShowingOptionDialog()) {
            showInputMethodPicker();
        }
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    private int getMetaState(boolean shifted) {
        int meta = 0;
        if (shifted) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mModCtrl) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (mModAlt) meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (mModMeta) meta |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        return meta;
    }

    private void sendKeyDown(InputConnection ic, int key, int meta) {
        long now = System.currentTimeMillis();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, key, 0, meta));
    }

    private void sendKeyUp(InputConnection ic, int key, int meta) {
        long now = System.currentTimeMillis();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_UP, key, 0, meta));
    }

    private void sendModifiedKeyDownUp(int key, boolean shifted) {
        InputConnection ic = getCurrentInputConnection();
        int meta = getMetaState(shifted);
        sendModifierKeysDown(shifted);
        sendKeyDown(ic, key, meta);
        sendKeyUp(ic, key, meta);
        sendModifierKeysUp(shifted);
    }

    private boolean isShiftMod() {
        if (mShiftKeyState.isChording()) return true;
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView kb = mKeyboardSwitcher.getInputView();
            if (kb != null) return kb.isShiftAll();
        }
        return false;
    }

    private boolean delayChordingCtrlModifier() {
        return sKeyboardSettings.chordingCtrlKey == 0;
    }

    private boolean delayChordingAltModifier() {
        return sKeyboardSettings.chordingAltKey == 0;
    }

    private boolean delayChordingMetaModifier() {
        return sKeyboardSettings.chordingMetaKey == 0;
    }

    private void sendModifiedKeyDownUp(int key) {
        sendModifiedKeyDownUp(key, isShiftMod());
    }

    private void sendShiftKey(InputConnection ic, boolean isDown) {
        int key = KeyEvent.KEYCODE_SHIFT_LEFT;
        int meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendCtrlKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingCtrlModifier()) return;

        int key = sKeyboardSettings.chordingCtrlKey;
        if (key == 0) key = KeyEvent.KEYCODE_CTRL_LEFT;
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendAltKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingAltModifier()) return;

        int key = sKeyboardSettings.chordingAltKey;
        if (key == 0) key = KeyEvent.KEYCODE_ALT_LEFT;
        int meta = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendMetaKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingMetaModifier()) return;

        int key = sKeyboardSettings.chordingMetaKey;
        if (key == 0) key = KeyEvent.KEYCODE_META_LEFT;
        int meta = KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendModifierKeysDown(boolean shifted) {
        InputConnection ic = getCurrentInputConnection();
        if (shifted) {
            //Log.i(TAG, "send SHIFT down");
            sendShiftKey(ic, true);
        }
        if (mModCtrl && (!mCtrlKeyState.isChording() || delayChordingCtrlModifier())) {
            sendCtrlKey(ic, true, false);
        }
        if (mModAlt && (!mAltKeyState.isChording() || delayChordingAltModifier())) {
            sendAltKey(ic, true, false);
        }
        if (mModMeta && (!mMetaKeyState.isChording() || delayChordingMetaModifier())) {
            sendMetaKey(ic, true, false);
        }
    }

    private void handleModifierKeysUp(boolean shifted, boolean sendKey) {
        InputConnection ic = getCurrentInputConnection();
        if (mModMeta && (!mMetaKeyState.isChording() || delayChordingMetaModifier())) {
            if (sendKey) sendMetaKey(ic, false, false);
            if (!mMetaKeyState.isChording()) setModMeta(false);
        }
        if (mModAlt && (!mAltKeyState.isChording() || delayChordingAltModifier())) {
            if (sendKey) sendAltKey(ic, false, false);
            if (!mAltKeyState.isChording()) setModAlt(false);
        }
        if (mModCtrl && (!mCtrlKeyState.isChording()  || delayChordingCtrlModifier())) {
            if (sendKey) sendCtrlKey(ic, false, false);
            if (!mCtrlKeyState.isChording()) setModCtrl(false);
        }
        if (shifted) {
            //Log.i(TAG, "send SHIFT up");
            if (sendKey) sendShiftKey(ic, false);
            int shiftState = getShiftState();
            if (!(mShiftKeyState.isChording() || shiftState == Keyboard.SHIFT_LOCKED)) {
                resetShift();
            }
        }
    }

    private void sendModifierKeysUp(boolean shifted) {
        handleModifierKeysUp(shifted, true);
    }

    private void sendSpecialKey(int code) {
        commitTyped(getCurrentInputConnection(), true);
        sendModifiedKeyDownUp(code);
    }

    private final static int asciiToKeyCode[] = new int[127];
    private final static int KF_MASK = 0xffff;
    private final static int KF_SHIFTABLE = 0x10000;
    private final static int KF_UPPER = 0x20000;
    private final static int KF_LETTER = 0x40000;

    {
        // Include RETURN in this set even though it's not printable.
        // Most other non-printable keys get handled elsewhere.
        asciiToKeyCode['\n'] = KeyEvent.KEYCODE_ENTER | KF_SHIFTABLE;

        // Non-alphanumeric ASCII codes which have their own keys
        // (on some keyboards)
        asciiToKeyCode[' '] = KeyEvent.KEYCODE_SPACE | KF_SHIFTABLE;
        //asciiToKeyCode['!'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['"'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['#'] = KeyEvent.KEYCODE_POUND;
        //asciiToKeyCode['$'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['%'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['&'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['\''] = KeyEvent.KEYCODE_APOSTROPHE;
        //asciiToKeyCode['('] = KeyEvent.KEYCODE_;
        //asciiToKeyCode[')'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['*'] = KeyEvent.KEYCODE_STAR;
        asciiToKeyCode['+'] = KeyEvent.KEYCODE_PLUS;
        asciiToKeyCode[','] = KeyEvent.KEYCODE_COMMA;
        asciiToKeyCode['-'] = KeyEvent.KEYCODE_MINUS;
        asciiToKeyCode['.'] = KeyEvent.KEYCODE_PERIOD;
        asciiToKeyCode['/'] = KeyEvent.KEYCODE_SLASH;
        //asciiToKeyCode[':'] = KeyEvent.KEYCODE_;
        asciiToKeyCode[';'] = KeyEvent.KEYCODE_SEMICOLON;
        //asciiToKeyCode['<'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['='] = KeyEvent.KEYCODE_EQUALS;
        //asciiToKeyCode['>'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['?'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['@'] = KeyEvent.KEYCODE_AT;
        asciiToKeyCode['['] = KeyEvent.KEYCODE_LEFT_BRACKET;
        asciiToKeyCode['\\'] = KeyEvent.KEYCODE_BACKSLASH;
        asciiToKeyCode[']'] = KeyEvent.KEYCODE_RIGHT_BRACKET;
        //asciiToKeyCode['^'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['_'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['`'] = KeyEvent.KEYCODE_GRAVE;
        //asciiToKeyCode['{'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['|'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['}'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['~'] = KeyEvent.KEYCODE_;


        for (int i = 0; i <= 25; ++i) {
            asciiToKeyCode['a' + i] = KeyEvent.KEYCODE_A + i | KF_LETTER;
            asciiToKeyCode['A' + i] = KeyEvent.KEYCODE_A + i | KF_UPPER | KF_LETTER;
        }

        for (int i = 0; i <= 9; ++i) {
            asciiToKeyCode['0' + i] = KeyEvent.KEYCODE_0 + i;
        }
    }

    public void sendModifiableKeyChar(char ch) {
        // Support modified key events
        boolean modShift = isShiftMod();
        if ((modShift || mModCtrl || mModAlt || mModMeta) && ch > 0 && ch < 127) {
            InputConnection ic = getCurrentInputConnection();

            // Restrict Shift modifier to ENTER and SPACE, supporting Shift-Enter etc.
            // Note that most special keys such as DEL or cursor keys aren't handled
            // by this charcode-based method.

            int combinedCode = asciiToKeyCode[ch];
            if (combinedCode > 0) {
                int code = combinedCode & KF_MASK;
                boolean shiftable = (combinedCode & KF_SHIFTABLE) > 0;
                boolean upper = (combinedCode & KF_UPPER) > 0;
                boolean letter = (combinedCode & KF_LETTER) > 0;
                boolean shifted = modShift && (upper || shiftable);
                if (letter && !mModCtrl && !mModAlt && !mModMeta) {
                    // Try workaround for issue 179 where letters don't get upcased
                    ic.commitText(Character.toString(ch), 1);
                    handleModifierKeysUp(false, false);
                } else {
                    sendModifiedKeyDownUp(code, shifted);
                }
                return;
            }
        }

        if (ch >= '0' && ch <= '9') {
            //WIP
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.clearMetaKeyStates(KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SYM_ON);
            }
            //EditorInfo ei = getCurrentInputEditorInfo();
            //Log.i(TAG, "capsmode=" + ic.getCursorCapsMode(ei.inputType));
            //sendModifiedKeyDownUp(KeyEvent.KEYCODE_0 + ch - '0');
            //return;
        }

        // Default handling for anything else, including unmodified ENTER and SPACE.
        sendKeyChar(ch);
    }
    
    private void sendTab() {
        sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB);
    }

    private void sendEscape() {
        sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE);
    }

    private boolean processMultiKey(int primaryCode) {
        if (mDeadAccentBuffer.composeBuffer.length() > 0) {
            //Log.i(TAG, "processMultiKey: pending DeadAccent, length=" + mDeadAccentBuffer.composeBuffer.length());
            mDeadAccentBuffer.execute(primaryCode);
            mDeadAccentBuffer.clear();
            return true;
        }
        if (mComposeMode) {
            mComposeMode = mComposeBuffer.execute(primaryCode);
            return true;
        }
        return false;
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
        mAdaptive.beforeKey(primaryCode);
        onKeyInternal(primaryCode, keyCodes, x, y);
        mAdaptive.afterKey(primaryCode);
    }

    private void onKeyInternal(int primaryCode, int[] keyCodes, int x, int y) {
        long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.KEYCODE_DELETE
                || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        switch (primaryCode) {
        case Keyboard.KEYCODE_DELETE:
            if (processMultiKey(primaryCode)) {
                break;
            }
            handleBackspace();
            mDeleteCount++;
            break;
        case Keyboard.KEYCODE_SHIFT:
            // Shift key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                handleShift();
            break;
        case Keyboard.KEYCODE_MODE_CHANGE:
            // Symbol key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                changeKeyboardMode();
            break;
        case LatinKeyboardView.KEYCODE_CTRL_LEFT:
            // Ctrl key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModCtrl(!mModCtrl);
            break;
        case LatinKeyboardView.KEYCODE_ALT_LEFT:
            // Alt key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModAlt(!mModAlt);
            break;
        case LatinKeyboardView.KEYCODE_META_LEFT:
            // Meta key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModMeta(!mModMeta);
            break;
        case LatinKeyboardView.KEYCODE_FN:
            if (!distinctMultiTouch)
                setModFn(!mModFn);
            break;
        case Keyboard.KEYCODE_CANCEL:
            if (!isShowingOptionDialog()) {
                handleClose();
            }
            break;
        case LatinKeyboardView.KEYCODE_OPTIONS:
            onOptionKeyPressed();
            break;
        case LatinKeyboardView.KEYCODE_OPTIONS_LONGPRESS:
            onOptionKeyLongPressed();
            break;
        case LatinKeyboardView.KEYCODE_COMPOSE:
            mComposeMode = !mComposeMode;
            mComposeBuffer.clear();
            break;
        case LatinKeyboardView.KEYCODE_NEXT_LANGUAGE:
            toggleLanguage(false, true);
            break;
        case LatinKeyboardView.KEYCODE_PREV_LANGUAGE:
            toggleLanguage(false, false);
            break;
        case LatinKeyboardView.KEYCODE_VOICE:
            startVoiceInput();
            break;
        case 9 /* Tab */:
            if (processMultiKey(primaryCode)) {
                break;
            }
            sendTab();
            break;
        case LatinKeyboardView.KEYCODE_ESCAPE:
            if (processMultiKey(primaryCode)) {
                break;
            }
            sendEscape();
            break;
        case LatinKeyboardView.KEYCODE_DPAD_UP:
        case LatinKeyboardView.KEYCODE_DPAD_DOWN:
        case LatinKeyboardView.KEYCODE_DPAD_LEFT:
        case LatinKeyboardView.KEYCODE_DPAD_RIGHT:
        case LatinKeyboardView.KEYCODE_DPAD_CENTER:
        case LatinKeyboardView.KEYCODE_HOME:
        case LatinKeyboardView.KEYCODE_END:
        case LatinKeyboardView.KEYCODE_PAGE_UP:
        case LatinKeyboardView.KEYCODE_PAGE_DOWN:
        case LatinKeyboardView.KEYCODE_FKEY_F1:
        case LatinKeyboardView.KEYCODE_FKEY_F2:
        case LatinKeyboardView.KEYCODE_FKEY_F3:
        case LatinKeyboardView.KEYCODE_FKEY_F4:
        case LatinKeyboardView.KEYCODE_FKEY_F5:
        case LatinKeyboardView.KEYCODE_FKEY_F6:
        case LatinKeyboardView.KEYCODE_FKEY_F7:
        case LatinKeyboardView.KEYCODE_FKEY_F8:
        case LatinKeyboardView.KEYCODE_FKEY_F9:
        case LatinKeyboardView.KEYCODE_FKEY_F10:
        case LatinKeyboardView.KEYCODE_FKEY_F11:
        case LatinKeyboardView.KEYCODE_FKEY_F12:
        case LatinKeyboardView.KEYCODE_FORWARD_DEL:
        case LatinKeyboardView.KEYCODE_INSERT:
        case LatinKeyboardView.KEYCODE_SYSRQ:
        case LatinKeyboardView.KEYCODE_BREAK:
        case LatinKeyboardView.KEYCODE_NUM_LOCK:
        case LatinKeyboardView.KEYCODE_SCROLL_LOCK:
            if (processMultiKey(primaryCode)) {
                break;
            }
            // send as plain keys, or as escape sequence if needed
            sendSpecialKey(-primaryCode);
            break;
        default:
            if (isHangulMode() && isHangulJamo(primaryCode)) {
                handleHangulCharacter(primaryCode);
                break;
            }
            // Commit any pending Hangul composition before handling a non-Jamo character
            if (isHangulMode() && mHangulComposer.isComposing()) {
                commitHangulComposing();
            }
            if (!mComposeMode && mDeadKeysActive && Character.getType(primaryCode) == Character.NON_SPACING_MARK) {
                //Log.i(TAG, "possible dead character: " + primaryCode);
                if (!mDeadAccentBuffer.execute(primaryCode)) {
                    //Log.i(TAG, "double dead key");
                    break; // pressing a dead key twice produces spacing equivalent
                }
                updateShiftKeyState(getCurrentInputEditorInfo());
                break;
            }
            if (processMultiKey(primaryCode)) {
                break;
            }
            if (primaryCode != ASCII_ENTER) {
                mJustAddedAutoSpace = false;
            }
            RingCharBuffer.getInstance().push((char) primaryCode, x, y);
            if (isWordSeparator(primaryCode)) {
                handleSeparator(primaryCode);
            } else {
                handleCharacter(primaryCode, keyCodes);
            }
            // Cancel the just reverted state
            mJustRevertedSeparator = null;
        }
        mKeyboardSwitcher.onKey(primaryCode);
        // Reset after any single keystroke
        mEnteredText = null;
        //mDeadAccentBuffer.clear();  // FIXME
    }

    public void onText(CharSequence text) {
        //mDeadAccentBuffer.clear();  // FIXME
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        if (mPredicting && text.length() == 1) {
            // If adding a single letter, treat it as a regular keystroke so
            // that completion works as expected.
            int c = text.charAt(0);
            if (!isWordSeparator(c)) {
                int[] codes = {c};
                handleCharacter(c, codes);
                return;
            }
        }
        abortCorrection(false);
        ic.beginBatchEdit();
        if (mPredicting) {
            commitTyped(ic, true);
        }
        maybeRemovePreviousPeriod(text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        mKeyboardSwitcher.onKey(0); // dummy key code.
        mJustRevertedSeparator = null;
        mJustAddedAutoSpace = false;
        mEnteredText = text;
    }

    public void onCancel() {
        // User released a finger outside any key
        mKeyboardSwitcher.onCancelInput();
    }

    private void handleBackspace() {
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        // Hangul composition: backspace steps back through the syllable being composed
        if (isHangulMode() && mHangulComposer.isComposing()) {
            String newComposing = mHangulComposer.backspace();
            ic.beginBatchEdit();
            if (newComposing.isEmpty()) {
                ic.finishComposingText();
            } else {
                ic.setComposingText(newComposing, 1);
            }
            ic.endBatchEdit();
            postUpdateShiftKeyState();
            return;
        }

        ic.beginBatchEdit();

        if (mPredicting) {
            final int length = mComposing.length();
            if (length > 0) {
                mComposing.delete(length - 1, length);
                mWord.deleteLast();
                ic.setComposingText(mComposing, 1);
                if (mComposing.length() == 0) {
                    mPredicting = false;
                }
                postUpdateSuggestions();
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        } else {
            deleteChar = true;
        }
        postUpdateShiftKeyState();
        TextEntryState.backspace();
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            revertLastWord(deleteChar);
            ic.endBatchEdit();
            return;
        } else if (mEnteredText != null
                && sameAsTextBeforeCursor(ic, mEnteredText)) {
            ic.deleteSurroundingText(mEnteredText.length(), 0);
        } else if (deleteChar) {
            if (mCandidateView != null
                    && mCandidateView.dismissAddToDictionaryHint()) {
                // Go back to the suggestion mode if the user canceled the
                // "Touch again to save".
                // NOTE: In gerenal, we don't revert the word when backspacing
                // from a manual suggestion pick. We deliberately chose a
                // different behavior only in the case of picking the first
                // suggestion (typed word). It's intentional to have made this
                // inconsistent with backspacing after selecting other
                // suggestions.
                revertLastWord(deleteChar);
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
            }
        }
        mJustRevertedSeparator = null;
        ic.endBatchEdit();
    }

    // ---------------------------------------------------------------- autofill chips

    /**
     * Asks autofill services (password managers) to offer their suggestions as chips for
     * the toolbar row instead of a dropdown under the field. The chips are views the
     * service draws; the keyboard only places them and never sees what they fill in.
     */
    @Override
    @androidx.annotation.RequiresApi(30)
    public android.view.inputmethod.InlineSuggestionsRequest onCreateInlineSuggestionsRequest(
            android.os.Bundle uiExtras) {
        if (isTerminalField()) return null; // see isTerminalField
        KeyboardToolbar toolbar = mKeyboardSwitcher.getInputContainer();
        int height = toolbar != null ? toolbar.chipHeight()
                : Math.round(36 * getResources().getDisplayMetrics().density);
        int maxWidth = getResources().getDisplayMetrics().widthPixels * 2 / 3;
        android.os.Bundle style = androidx.autofill.inline.UiVersions.newStylesBuilder()
                .addStyle(autofillChipStyle(toolbar))
                .build();
        int minWidth = Math.round(48 * getResources().getDisplayMetrics().density);
        android.widget.inline.InlinePresentationSpec spec =
                new android.widget.inline.InlinePresentationSpec.Builder(
                        new android.util.Size(Math.min(minWidth, height), height),
                        new android.util.Size(maxWidth, height))
                        .setStyle(style).build();
        // Three alike, as HeliBoard and Android's sample keyboard do: some password managers
        // style their chips by position and want more than one.
        List<android.widget.inline.InlinePresentationSpec> specs = new ArrayList<>();
        for (int i = 0; i < 3; i++) specs.add(spec);
        return new android.view.inputmethod.InlineSuggestionsRequest.Builder(specs)
                .setMaxSuggestionCount(6)
                .build();
    }

    /**
     * How password managers draw their chips, Samsung style: an outlined pill in the
     * keyboard's colours, and the icon-only chip (the manager's own button) as just its
     * icon, instead of the default white pill.
     */
    @androidx.annotation.RequiresApi(30)
    private androidx.autofill.inline.v1.InlineSuggestionUi.Style autofillChipStyle(KeyboardToolbar toolbar) {
        int bg = toolbar != null ? toolbar.backgroundColour() : 0xFF101010;
        boolean light = (android.graphics.Color.red(bg) * 299 + android.graphics.Color.green(bg) * 587
                + android.graphics.Color.blue(bg) * 114) / 1000 > 128;
        int text = light ? 0xFF202124 : 0xFFE8EAED;
        int subtext = light ? 0xFF5F6368 : 0xFFBDC1C6;
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        int iconSize = Math.round(24 * getResources().getDisplayMetrics().density);
        android.graphics.drawable.Icon chip = android.graphics.drawable.Icon.createWithResource(this,
                light ? R.drawable.autofill_chip_light : R.drawable.autofill_chip_dark);
        androidx.autofill.inline.common.ImageViewStyle icon =
                new androidx.autofill.inline.common.ImageViewStyle.Builder()
                        .setMaxWidth(iconSize).setMaxHeight(iconSize)
                        .setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
                        .setLayoutMargin(0, 0, pad / 2, 0)
                        .build();
        return androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder()
                .setChipStyle(new androidx.autofill.inline.common.ViewStyle.Builder()
                        .setBackground(chip)
                        .setPadding(pad, 0, pad, 0)
                        .build())
                .setStartIconStyle(icon)
                .setEndIconStyle(icon)
                .setTitleStyle(new androidx.autofill.inline.common.TextViewStyle.Builder()
                        .setTextColor(text).setTextSize(16)
                        .build())
                .setSubtitleStyle(new androidx.autofill.inline.common.TextViewStyle.Builder()
                        .setTextColor(subtext).setTextSize(13)
                        .setLayoutMargin(pad / 2, 0, 0, 0)
                        .build())
                // The password manager's own button: just its icon, as on Samsung (no pill),
                // as in the autofill library's own example.
                .setSingleIconChipStyle(new androidx.autofill.inline.common.ViewStyle.Builder()
                        .setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        .setPadding(0, 0, 0, 0)
                        .setLayoutMargin(0, 0, 0, 0)
                        .build())
                .setSingleIconChipIconStyle(new androidx.autofill.inline.common.ImageViewStyle.Builder()
                        .setMaxWidth(Math.round(32 * getResources().getDisplayMetrics().density))
                        .setMaxHeight(Math.round(32 * getResources().getDisplayMetrics().density))
                        .setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
                        .setLayoutMargin(0, 0, 0, 0)
                        .build())
                .build();
    }

    @Override
    @androidx.annotation.RequiresApi(30)
    public boolean onInlineSuggestionsResponse(
            android.view.inputmethod.InlineSuggestionsResponse response) {
        final KeyboardToolbar toolbar = mKeyboardSwitcher.getInputContainer();
        if (toolbar == null) return false;
        if (isTerminalField()) { // in case chips come anyway
            mAutofillGeneration++;
            toolbar.showChips(java.util.Collections.<View>emptyList());
            return true;
        }
        final List<android.view.inputmethod.InlineSuggestion> suggestions =
                response.getInlineSuggestions();
        if (suggestions.isEmpty()) {
            mAutofillGeneration++;
            toolbar.showChips(java.util.Collections.<View>emptyList());
            return true;
        }
        final View[] views = new View[suggestions.size()];
        final int generation = ++mAutofillGeneration;
        android.util.Size size = new android.util.Size(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        for (int i = 0; i < suggestions.size(); i++) {
            final int index = i;
            suggestions.get(i).inflate(this, size, getMainExecutor(), view -> {
                views[index] = view;
                if (view != null && view.getLayoutParams() != null) {
                    Log.i(TAG, "Autofill chip " + index + ": " + view.getLayoutParams().width
                            + "x" + view.getLayoutParams().height);
                }
                if (generation != mAutofillGeneration || view == null) return; // replaced
                // Show each chip as it arrives: one slow chip mustn't hold back the rest.
                List<View> ready = new ArrayList<>(), pinned = new ArrayList<>();
                for (int k = 0; k < views.length; k++) {
                    if (views[k] == null) continue;
                    if (suggestions.get(k).getInfo().isPinned()) pinned.add(views[k]);
                    else ready.add(views[k]);
                }
                Log.i(TAG, "Autofill chips: " + ready.size() + " + " + pinned.size()
                        + " pinned, of " + suggestions.size());
                toolbar.showChips(ready, pinned);
            });
        }
        return true;
    }

    private int mAutofillGeneration; // latest response; older chips arriving late are dropped

    /**
     * A field that hides its text from the keyboard, like Termux's terminal. Termux tells
     * autofill its terminal can take a password (for its long-press "Autofill password"),
     * so password managers offered their chips on every visit; the keyboard doesn't ask
     * for them there, and the normal toolbar stays. Termux's own menu still works.
     */
    private boolean isTerminalField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_NULL;
    }

    private void clearAutofillChips() {
        mAutofillGeneration++;
        KeyboardToolbar toolbar = mKeyboardSwitcher.getInputContainer();
        if (toolbar != null && toolbar.hasChips()) {
            toolbar.showChips(java.util.Collections.<View>emptyList());
        }
    }

    // ---------------------------------------------------------------- cursor movement

    /**
     * Moves the cursor by whole characters (or words), in the visual direction of the text
     * at the cursor (negative = left), stopping at the ends of the text. Used by the
     * space-bar slider and the toolbar's arrows instead of arrow keys, because an arrow key
     * at the start or end of a field moves focus to another view and closes the keyboard.
     *
     * @param select extend the selection instead of moving the cursor (also when Shift is on)
     * @param byWord move by words (also when Ctrl is on)
     * @return false when the editor doesn't expose its text (terminals): send arrow keys
     */
    boolean moveCursor(int steps, boolean select, boolean byWord) {
        if (steps == 0) return true;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null
                || (ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_NULL) {
            return false;
        }
        android.view.inputmethod.ExtractedText et =
                ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
        if (et == null || et.text == null || et.selectionStart < 0) return false;
        commitTyped(ic, true);
        select |= isShiftMod();
        byWord |= mModCtrl;
        int[] from = selectionToExtend(et, select);
        int[] sel = newSelection(et.text.toString(), from[0], from[1], steps, select, byWord);
        setSelectionRemembered(ic, et.startOffset + sel[0], et.startOffset + sel[1]);
        return true;
    }

    // The last selection the keyboard set (absolute anchor and moving end), or -1. Some
    // editors report a selection back normalised (start before end) or not at all, which
    // would make the next Select-arrow press extend from the wrong end.
    private int mSetAnchor = -1, mSetFocus = -1;

    private void setSelectionRemembered(InputConnection ic, int anchor, int focus) {
        mSetAnchor = anchor;
        mSetFocus = focus;
        ic.setSelection(anchor, focus);
    }

    /** Remembers a selection the keyboard set (see {@link #selectionToExtend}). */
    void rememberSelection(int anchor, int focus) {
        mSetAnchor = anchor;
        mSetFocus = focus;
    }

    /** Commits the word being typed before the cursor moves away from it. */
    void commitTypedForCursor(InputConnection ic) {
        commitTyped(ic, true);
    }

    final CursorDrag mCursorDrag = new CursorDrag(this);
    final CursorLoupe mLoupe = new CursorLoupe(this);
    {
        mCursorDrag.setListener(mLoupe); // on by default; settings may turn it off
    }

    /** The view the loupe's window hangs off (the keyboard). */
    View loupeAnchor() {
        return mKeyboardSwitcher == null ? null : mKeyboardSwitcher.getInputView();
    }

    int loupeBackground() {
        KeyboardToolbar toolbar = mKeyboardSwitcher == null ? null : mKeyboardSwitcher.getInputContainer();
        return toolbar != null ? toolbar.backgroundColour() : 0xFF202124;
    }

    private void setLoupeEnabled(boolean enabled) {
        mLoupe.setEnabled(enabled);
        mCursorDrag.setListener(enabled ? mLoupe : null);
    }

    // While the emoji panel's search is open, the keys type into its query instead.
    private InputConnection mSearchConnection;

    @Override
    public InputConnection getCurrentInputConnection() {
        return mSearchConnection != null ? mSearchConnection : appConnection();
    }

    /** The app's text field, even while the emoji search has the keys. */
    InputConnection appConnection() {
        return super.getCurrentInputConnection();
    }

    /** Sends the keys to {@code search} (the emoji search query), or back to the app (null). */
    void setSearchConnection(InputConnection search) {
        InputConnection current = getCurrentInputConnection();
        if (current != null) commitTyped(current, true); // a word in progress stays where it is
        mSearchConnection = search;
        TextEntryState.reset();
    }

    // ---------------------------------------------------------------- images

    private static java.io.File sPendingImage;
    private static String sPendingImageType;
    private static long sPendingImageTime;

    /** An image picked in the gallery, to insert when the app being typed into is back. */
    static void setPendingImage(java.io.File file, String type) {
        sPendingImage = file;
        sPendingImageType = type;
        sPendingImageTime = SystemClock.uptimeMillis();
    }

    private void insertPendingImage() {
        if (sPendingImage == null) return;
        java.io.File file = sPendingImage;
        String type = sPendingImageType;
        boolean fresh = SystemClock.uptimeMillis() - sPendingImageTime < 120000;
        sPendingImage = null;
        if (fresh && !commitImage(file, type)) imageNotAccepted();
    }

    /** Whether the field being typed into takes images of this type (rich content). */
    boolean acceptsImage(String type) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return false;
        for (String accepted : androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(ei)) {
            if (android.content.ClipDescription.compareMimeTypes(type, accepted)) return true;
        }
        return false;
    }

    /**
     * Inserts an image (a file of the keyboard's) into the app, as Gboard and Samsung do for
     * stickers and GIFs; the app gets read access to it alone.
     *
     * @return false if the field doesn't take images (most text fields and terminals)
     */
    boolean commitImage(java.io.File file, String type) {
        InputConnection ic = appConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null || !acceptsImage(type) || !file.exists()) return false;
        android.net.Uri uri;
        try {
            uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".images", file);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Not a shareable image: " + file, e);
            return false;
        }
        commitTyped(ic, true);
        androidx.core.view.inputmethod.InputContentInfoCompat info =
                new androidx.core.view.inputmethod.InputContentInfoCompat(uri,
                        new android.content.ClipDescription(file.getName(), new String[] {type}), null);
        int flags = 0;
        if (android.os.Build.VERSION.SDK_INT >= 25) {
            flags = androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        } else {
            grantUriPermission(ei.packageName, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return androidx.core.view.inputmethod.InputConnectionCompat.commitContent(ic, ei, info, flags, null);
    }

    void imageNotAccepted() {
        android.widget.Toast.makeText(this, R.string.image_not_accepted, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Types into the app while the search has the keys (an emoji picked from the results). */
    void typeIntoApp(String text) {
        InputConnection search = mSearchConnection;
        if (search != null) setSearchConnection(null);
        try {
            onText(text);
        } finally {
            if (search != null) setSearchConnection(search);
        }
    }

    /** Starts a trackpad drag; false when the editor hides its text (send arrow keys). */
    boolean beginCursorDrag(boolean select) {
        return mCursorDrag.begin(select || isShiftMod());
    }

    /** A single step (the toolbar's up/down arrows): no loupe. */
    boolean beginQuietCursorDrag(boolean select) {
        return mCursorDrag.begin(select || isShiftMod(), true);
    }

    @Override
    public void onUpdateCursorAnchorInfo(android.view.inputmethod.CursorAnchorInfo info) {
        mCursorDrag.onUpdateCursorAnchorInfo(info);
    }

    /** Forgets the remembered selection (Select turned off, or the selection dropped). */
    void forgetSelection() {
        mSetAnchor = mSetFocus = -1;
    }

    /**
     * The {anchor, end} (relative to the extracted text) to move from. When extending a
     * selection the keyboard set itself, and the editor reports that same span in another
     * order, or collapsed onto one of its ends, the remembered anchor and moving end win.
     */
    int[] selectionToExtend(android.view.inputmethod.ExtractedText et, boolean select) {
        int s = et.selectionStart, e = et.selectionEnd;
        if (!select || mSetAnchor < 0) {
            forgetSelection();
            return new int[] {s, e};
        }
        int a = mSetAnchor - et.startOffset, f = mSetFocus - et.startOffset;
        int len = et.text.length();
        boolean known = a >= 0 && f >= 0 && a <= len && f <= len
                && ((Math.min(s, e) == Math.min(a, f) && Math.max(s, e) == Math.max(a, f))
                        || (s == e && (s == f || s == a)));
        if (known) return new int[] {a, f};
        forgetSelection();
        return new int[] {s, e};
    }

    android.view.inputmethod.ExtractedText extractText(InputConnection ic) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null
                || (ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_NULL) {
            return null;
        }
        android.view.inputmethod.ExtractedText et =
                ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
        return et == null || et.text == null || et.selectionStart < 0 ? null : et;
    }

    /**
     * Swaps which end of the selection moves. A text field keeps a selection as an anchor
     * and a moving end (and reports them in that order), so setting the selection reversed
     * makes arrows, word jumps, Home/End and Shift+arrows move the other end.
     *
     * @return false if there is no selection to swap
     */
    boolean swapSelectionEnds() {
        InputConnection ic = getCurrentInputConnection();
        android.view.inputmethod.ExtractedText et = extractText(ic);
        if (et == null || et.selectionStart == et.selectionEnd) return false;
        int[] cur = selectionToExtend(et, true);
        if (cur[0] == cur[1]) return false;
        setSelectionRemembered(ic, et.startOffset + cur[1], et.startOffset + cur[0]);
        return true;
    }

    /** Selects the word (or, with {@code line}, the line) at the cursor. */
    boolean selectAtCursor(boolean line) {
        InputConnection ic = getCurrentInputConnection();
        android.view.inputmethod.ExtractedText et = extractText(ic);
        if (et == null) return false;
        commitTyped(ic, true);
        String text = et.text.toString();
        int[] range = line ? lineAt(text, et.selectionEnd) : wordAt(text, et.selectionEnd);
        if (range[0] == range[1]) return false;
        setSelectionRemembered(ic, et.startOffset + range[0], et.startOffset + range[1]);
        return true;
    }

    /** Puts the cursor where the selection's moving end is, dropping the selection. */
    boolean collapseSelection() {
        InputConnection ic = getCurrentInputConnection();
        android.view.inputmethod.ExtractedText et = extractText(ic);
        if (et == null) return false;
        int end = selectionToExtend(et, true)[1];
        forgetSelection();
        ic.setSelection(et.startOffset + end, et.startOffset + end);
        return true;
    }

    /** The word around {@code pos}: letters, digits, marks, apostrophes. */
    static int[] wordAt(String text, int pos) {
        pos = Math.max(0, Math.min(pos, text.length()));
        int start = pos, end = pos;
        while (start > 0 && isSwipeWordChar(text.charAt(start - 1))) start--;
        while (end < text.length() && isSwipeWordChar(text.charAt(end))) end++;
        return new int[] {start, end};
    }

    /** The line (between line breaks) around {@code pos}, without the break. */
    static int[] lineAt(String text, int pos) {
        pos = Math.max(0, Math.min(pos, text.length()));
        int start = text.lastIndexOf('\n', pos - 1) + 1;
        int end = text.indexOf('\n', pos);
        return new int[] {start, end < 0 ? text.length() : end};
    }

    /**
     * Up/down for the toolbar. An arrow key on the first (last) line of a field moves the
     * cursor to its start (end), but once there it moves focus to another view, which
     * closes the keyboard. So: at the start (end) do nothing; on a single-line field go
     * to the start (end) directly; otherwise let the arrow key through.
     *
     * @return true if handled here, false to send the arrow key
     */
    boolean moveCursorVertically(boolean up, boolean select) {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null
                || (ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_NULL) {
            return false;
        }
        android.view.inputmethod.ExtractedText et =
                ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
        if (et == null || et.text == null || et.selectionStart < 0) return false;
        commitTyped(ic, true);
        select |= isShiftMod();
        int len = et.text.length();
        int[] from = selectionToExtend(et, select);
        int anchor = from[0], end = from[1];
        int target = up ? 0 : len;
        boolean multiLine = (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (end == target) return true; // already there: nowhere to go
        if (multiLine) {         // the field moves between its lines itself
            forgetSelection();
            return false;
        }
        setSelectionRemembered(ic, et.startOffset + (select ? anchor : target),
                et.startOffset + target);
        return true;
    }

    /**
     * The selection {anchor, end} after moving {@code steps} visually (negative = left) from
     * the given selection, by characters (graphemes) or words, clamped to the text.
     */
    static int[] newSelection(String text, int anchor, int end, int steps, boolean select,
            boolean byWord) {
        // Visual direction: in right-to-left text, dragging right moves back in the text.
        if (isRightToLeftAt(text, end)) steps = -steps;
        java.text.BreakIterator it = byWord ? java.text.BreakIterator.getWordInstance()
                : java.text.BreakIterator.getCharacterInstance();
        it.setText(text);
        int len = text.length();
        int pos = select ? end : (steps < 0 ? Math.min(anchor, end) : Math.max(anchor, end));
        if (!select && anchor != end) steps -= Integer.signum(steps); // first step collapses
        pos = Math.max(0, Math.min(pos, len));
        for (int i = Math.abs(steps); i > 0; i--) {
            int next = steps < 0 ? (pos == 0 ? java.text.BreakIterator.DONE : it.preceding(pos))
                    : (pos == len ? java.text.BreakIterator.DONE : it.following(pos));
            if (next == java.text.BreakIterator.DONE) break; // at the end of the text: stop
            if (byWord) { // land on word boundaries, skipping spaces and punctuation
                while (next > 0 && next < len
                        && !Character.isLetterOrDigit(text.charAt(steps < 0 ? next : next - 1))) {
                    int further = steps < 0 ? it.preceding(next) : it.following(next);
                    if (further == java.text.BreakIterator.DONE) break;
                    next = further;
                }
            }
            pos = next;
        }
        return select ? new int[] {anchor, pos} : new int[] {pos, pos};
    }

    /** Whether the text around the cursor runs right to left (Arabic, Hebrew...). */
    private static boolean isRightToLeftAt(String text, int cursor) {
        for (int i = Math.min(cursor, text.length()) - 1; i >= 0; i--) {
            byte d = Character.getDirectionality(text.charAt(i));
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
        }
        for (int i = cursor; i < text.length(); i++) {
            byte d = Character.getDirectionality(text.charAt(i));
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
        }
        return false;
    }

    // ---------------------------------------------------------------- backspace swipe

    static final String PREF_DELETE_SWIPE = "pref_delete_swipe";
    private boolean mDeleteSwipePref = true;
    private boolean mDeleteSwipeField;
    private CharSequence mDeleteSwipeText;
    private int mDeleteSwipeAnchor;
    private int mDeleteSwipeChars;
    private int mDeleteSwipeWords;

    /** Whether a slide from backspace can select text in the current field. */
    static boolean deleteSwipeAvailable() {
        LatinIME ime = sInstance;
        return ime != null && ime.mDeleteSwipePref && ime.mDeleteSwipeField;
    }

    /** Starts selecting back from the cursor; false if the editor can't support it. */
    boolean startDeleteSwipe() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        commitTyped(ic, true);
        android.view.inputmethod.ExtractedText et =
                ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
        int start, end;
        if (et != null && et.selectionStart >= 0) {
            start = et.startOffset + et.selectionStart;
            end = et.startOffset + et.selectionEnd;
        } else {
            start = mLastSelectionStart;
            end = mLastSelectionEnd;
        }
        if (start != end || end <= 0) return false; // existing selection, or nothing to delete
        CharSequence before = ic.getTextBeforeCursor(2000, 0);
        if (before == null || before.length() == 0) return false;
        mDeleteSwipeText = before;
        mDeleteSwipeAnchor = end;
        mDeleteSwipeChars = 0;
        mDeleteSwipeWords = 0;
        showDeleteSwipeStatus(0);
        return true;
    }

    /** Selects the given number of words back from where the swipe started. */
    void setDeleteSwipeWords(int words) {
        if (mDeleteSwipeText == null || words == mDeleteSwipeWords) return;
        mDeleteSwipeWords = words;
        CharSequence t = mDeleteSwipeText;
        int i = t.length();
        int taken = 0; // words actually there to select
        for (int w = 0; w < words && i > 0; w++, taken++) {
            while (i > 0 && Character.isWhitespace(t.charAt(i - 1))) i--;
            if (i > 0 && isSwipeWordChar(t.charAt(i - 1))) {
                while (i > 0 && isSwipeWordChar(t.charAt(i - 1))) i--;
            } else {
                while (i > 0 && !Character.isWhitespace(t.charAt(i - 1))
                        && !isSwipeWordChar(t.charAt(i - 1))) i--;
            }
        }
        int chars = Math.min(t.length() - i, mDeleteSwipeAnchor);
        showDeleteSwipeStatus(chars == 0 ? 0 : taken);
        if (chars == mDeleteSwipeChars) return;
        mDeleteSwipeChars = chars;
        InputConnection ic = getCurrentInputConnection();
        // Anchored at the cursor, moving end at the start of the words: text fields keep the
        // moving end in view, so the field scrolls along as the swipe takes lines back.
        if (ic != null) ic.setSelection(mDeleteSwipeAnchor, mDeleteSwipeAnchor - chars);
        // The toolbar container: visible also while a panel hides the keys.
        View view = mKeyboardSwitcher.getInputContainer();
        if (view == null) view = mKeyboardSwitcher.getInputView();
        if (view != null) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    /** The keyboard's label for the backspace swipe: how many words, and how it works. */
    private void showDeleteSwipeStatus(int words) {
        KeyboardToolbar view = mKeyboardSwitcher.getInputContainer();
        if (view == null) return;
        view.setGestureLabel(words == 0 ? getString(R.string.gesture_nothing_selected)
                        : getResources().getQuantityString(R.plurals.gesture_words, words, words),
                getString(R.string.gesture_delete_hint));
    }

    private static boolean isSwipeWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '\u0640'
                || Character.getType(c) == Character.NON_SPACING_MARK;
    }

    /** Deletes the selection on release, or puts the cursor back when cancelled/empty. */
    void finishDeleteSwipe(boolean delete) {
        if (mDeleteSwipeText == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (delete && mDeleteSwipeChars > 0) {
                ic.commitText("", 1);
            } else {
                ic.setSelection(mDeleteSwipeAnchor, mDeleteSwipeAnchor);
            }
        }
        mDeleteSwipeText = null;
        mDeleteSwipeChars = 0;
        KeyboardToolbar label = mKeyboardSwitcher.getInputContainer();
        if (label != null) label.setGestureLabel(null, null);
        mDeleteSwipeWords = 0;
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void updateAdaptiveModifiers() {
        mAdaptive.setModifierActive(mModCtrl || mModAlt || mModMeta);
    }

    private void setModCtrl(boolean val) {
        // Log.i("LatinIME", "setModCtrl "+ mModCtrl + "->" + val + ", chording=" + mCtrlKeyState.isChording());
        mKeyboardSwitcher.setCtrlIndicator(val);
        mModCtrl = val;
        updateAdaptiveModifiers();
    }

    private void setModAlt(boolean val) {
        //Log.i("LatinIME", "setModAlt "+ mModAlt + "->" + val + ", chording=" + mAltKeyState.isChording());
        mKeyboardSwitcher.setAltIndicator(val);
        mModAlt = val;
        updateAdaptiveModifiers();
    }

    private void setModMeta(boolean val) {
        //Log.i("LatinIME", "setModMeta "+ mModMeta + "->" + val + ", chording=" + mMetaKeyState.isChording());
        mKeyboardSwitcher.setMetaIndicator(val);
        mModMeta = val;
        updateAdaptiveModifiers();
    }

    private void setModFn(boolean val) {
        //Log.i("LatinIME", "setModFn " + mModFn + "->" + val + ", chording=" + mFnKeyState.isChording());
        mModFn = val;
        mKeyboardSwitcher.setFn(val);
        mKeyboardSwitcher.setCtrlIndicator(mModCtrl);
        mKeyboardSwitcher.setAltIndicator(mModAlt);
        mKeyboardSwitcher.setMetaIndicator(mModMeta);
    }

    private void startMultitouchShift() {
        int newState = Keyboard.SHIFT_ON;
        if (mKeyboardSwitcher.isAlphabetMode()) {
            mSavedShiftState = getShiftState();
            if (mSavedShiftState == Keyboard.SHIFT_LOCKED) newState = Keyboard.SHIFT_CAPS;
        }
        handleShiftInternal(true, newState);
    }

    private void commitMultitouchShift() {
        if (mKeyboardSwitcher.isAlphabetMode()) {
            int newState = nextShiftState(mSavedShiftState, true);
            handleShiftInternal(true, newState);
        } else {
            // do nothing, keyboard is already flipped
        }
    }

    private void resetMultitouchShift() {
        int newState = Keyboard.SHIFT_OFF;
        if (mSavedShiftState == Keyboard.SHIFT_CAPS_LOCKED || mSavedShiftState == Keyboard.SHIFT_LOCKED) {
            newState = mSavedShiftState;
        }
        handleShiftInternal(true, newState);
    }

    private void resetShift() {
        handleShiftInternal(true, Keyboard.SHIFT_OFF);
    }

    private void handleShift() {
        handleShiftInternal(false, -1);
    }

    private static int getCapsOrShiftLockState() {
        return sKeyboardSettings.capsLock ? Keyboard.SHIFT_CAPS_LOCKED : Keyboard.SHIFT_LOCKED;
    }
    
    // Rotate through shift states by successively pressing and releasing the Shift key.
    private static int nextShiftState(int prevState, boolean allowCapsLock) {
        if (allowCapsLock) {
            if (prevState == Keyboard.SHIFT_OFF) {
                return Keyboard.SHIFT_ON;
            } else if (prevState == Keyboard.SHIFT_ON) {
                return getCapsOrShiftLockState();
            } else {
                return Keyboard.SHIFT_OFF;
            }
        } else {
            // currently unused, see toggleShift()
            if (prevState == Keyboard.SHIFT_OFF) {
                return Keyboard.SHIFT_ON;
            } else {
                return Keyboard.SHIFT_OFF;
            }
        }
    }

    private void handleShiftInternal(boolean forceState, int newState) {
        //Log.i(TAG, "handleShiftInternal forceNormal=" + forceNormal);
        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isAlphabetMode()) {
            if (forceState) {
                switcher.setShiftState(newState);
            } else {
                switcher.setShiftState(nextShiftState(getShiftState(), true));
            }
        } else {
            switcher.toggleShift();
        }
    }

    private void abortCorrection(boolean force) {
        if (force || TextEntryState.isCorrecting()) {
            getCurrentInputConnection().finishComposingText();
            clearSuggestions();
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (mLastSelectionStart == mLastSelectionEnd
                && TextEntryState.isCorrecting()) {
            abortCorrection(false);
        }

        // Composing text only helps when there are suggestions to replace it with; without
        // a dictionary it just risks editors duplicating the word (see handleSeparator).
        if (isAlphabet(primaryCode) && isPredictionOn() && hasSuggestionSource()
                && !mModCtrl && !mModAlt && !mModMeta
                && !isCursorTouchingWord()) {
            if (!mPredicting) {
                mPredicting = true;
                mComposing.setLength(0);
                saveWordInHistory(mBestWord);
                mWord.reset();
            }
        }

        if (mModCtrl || mModAlt || mModMeta) {
            commitTyped(getCurrentInputConnection(), true); // sets mPredicting=false
        }

        if (mPredicting) {
            if (isShiftCapsMode()
                    && mKeyboardSwitcher.isAlphabetMode()
                    && mComposing.length() == 0) {
                // Show suggestions with initial caps if starting out shifted,
                // could be either auto-caps or manual shift.
                mWord.setFirstCharCapitalized(true);
            }
            mComposing.append((char) primaryCode);
            mWord.add(primaryCode, keyCodes);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWord.size() == 1) {
                    mWord.setAutoCapitalized(getCursorCapsMode(ic,
                            getCurrentInputEditorInfo()) != 0);
                }
                ic.setComposingText(mComposing, 1);
            }
            postUpdateSuggestions();
        } else {
            sendModifiableKeyChar((char) primaryCode);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        TextEntryState.typedCharacter((char) primaryCode,
                isWordSeparator(primaryCode));
    }

    private void handleSeparator(int primaryCode) {

        // Should dismiss the "Touch again to save" message when handling
        // separator
        if (mCandidateView != null
                && mCandidateView.dismissAddToDictionaryHint()) {
            postUpdateSuggestions();
        }

        boolean pickedDefault = false;
        // Handle separator
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            abortCorrection(false);
        }
        if (mPredicting) {
            // In certain languages where single quote is a separator, it's
            // better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the
            // elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn
                    && primaryCode != '\''
                    && (mJustRevertedSeparator == null
                            || mJustRevertedSeparator.length() == 0
                            || mJustRevertedSeparator.charAt(0) != primaryCode)) {
                pickedDefault = pickDefaultSuggestion();
                // Picked the suggestion by the space key. We consider this
                // as "added an auto space" in autocomplete mode, but as manually
                // typed space in "quick fixes" mode.
                if (primaryCode == ASCII_SPACE) {
                    if (mAutoCorrectEnabled) {
                        mJustAddedAutoSpace = true;
                    } else {
                        TextEntryState.manualTyped("");
                    }
                }
            } else {
                commitTyped(ic, true);
            }
        }
        if (mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
            removeTrailingSpace();
            mJustAddedAutoSpace = false;
        }
        // Enter becomes a key event (or an editor action) rather than text. Some editors
        // (Chromium-based fields, many chat apps) act on it before the batch edit that
        // committed the word is applied, and the word ends up typed twice. Close the batch
        // first so the word is in place when Enter arrives.
        boolean enterAfterBatch = primaryCode == ASCII_ENTER && ic != null;
        if (enterAfterBatch) {
            ic.endBatchEdit();
        }
        sendModifiableKeyChar((char) primaryCode);
        if (enterAfterBatch) {
            ic.beginBatchEdit();
        }

        // Handle the case of ". ." -> " .." with auto-space if necessary
        // before changing the TextEntryState.
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode == ASCII_PERIOD) {
            reswapPeriodAndSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode != ASCII_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == ASCII_SPACE) {
            doubleSpace();
        }
        if (pickedDefault) {
            TextEntryState.backToAcceptedDefault(mWord.getTypedWord());
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection(), true);
        requestHideSelf(0);
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
            if (inputView != null) {
                inputView.closing();
            }
        }
        TextEntryState.endSession();
    }

    private void saveWordInHistory(CharSequence result) {
        if (mWord.size() <= 1) {
            mWord.reset();
            return;
        }
        // Skip if result is null. It happens in some edge case.
        if (TextUtils.isEmpty(result)) {
            return;
        }

        // Make a copy of the CharSequence, since it is/could be a mutable
        // CharSequence
        final String resultCopy = result.toString();
        TypedWordAlternatives entry = new TypedWordAlternatives(resultCopy,
                new WordComposer(mWord));
        mWordHistory.add(entry);
    }

    private void postUpdateSuggestions() {
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.sendMessageDelayed(mHandler
                .obtainMessage(MSG_UPDATE_SUGGESTIONS), 100);
    }

    private void postUpdateOldSuggestions() {
        mHandler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
        mHandler.sendMessageDelayed(mHandler
                .obtainMessage(MSG_UPDATE_OLD_SUGGESTIONS), 300);
    }

    private boolean isPredictionOn() {
        return mPredictionOnForMode && isPredictionWanted();
    }

    private boolean isPredictionWanted() {
        return (mShowSuggestions || mSuggestionForceOn) && !suggestionsDisabled();
    }

    private boolean isCandidateStripVisible() {
        return isPredictionOn() && hasSuggestionSource();
    }

    /**
     * Whether the suggestion strip would have anything to show. Without a main dictionary
     * (the bundled one is an empty placeholder; real ones come from dictionary plugins)
     * and without completions from the app, the strip stays empty and only leaves a gap
     * above the keyboard, so it is not shown.
     */
    private boolean hasSuggestionSource() {
        return mCompletionOn || (mSuggest != null && mSuggest.hasMainDictionary());
    }

    private void switchToKeyboardView() {
        mHandler.post(new Runnable() {
            public void run() {
                View view = mKeyboardSwitcher.getInputContainer();
                if (view != null) {
                    ViewParent p = view.getParent();
                    if (p != null && p instanceof ViewGroup) {
                        ((ViewGroup) p).removeView(view);
                    }
                    setInputView(view);
                }
                setCandidatesViewShown(true);
                updateInputViewShown();
                postUpdateSuggestions();
            }
        });
    }

    private void clearSuggestions() {
        setSuggestions(null, false, false, false);
    }

    private void setSuggestions(List<CharSequence> suggestions,
            boolean completions, boolean typedWordValid,
            boolean haveMinimalSuggestion) {

        if (mIsShowingHint) {
            setCandidatesViewShown(true);
            mIsShowingHint = false;
        }

        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions,
                    typedWordValid, haveMinimalSuggestion);
        }
    }

    private void updateSuggestions() {
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        ((LatinKeyboard) inputView.getKeyboard()).setPreferredLetters(null);

        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isPredictionOn())) {
            return;
        }
        
        if (!mPredicting) {
            setNextSuggestions();
            return;
        }
        showSuggestions(mWord);
    }

    private List<CharSequence> getTypedSuggestions(WordComposer word) {
        List<CharSequence> stringList = mSuggest.getSuggestions(
                mKeyboardSwitcher.getInputView(), word, false, null);
        return stringList;
    }

    private void showCorrections(WordAlternatives alternatives) {
        List<CharSequence> stringList = alternatives.getAlternatives();
        ((LatinKeyboard) mKeyboardSwitcher.getInputView().getKeyboard())
                .setPreferredLetters(null);
        showSuggestions(stringList, alternatives.getOriginalWord(), false,
                false);
    }

    private void showSuggestions(WordComposer word) {
        // long startTime = System.currentTimeMillis(); // TIME MEASUREMENT!
        // TODO Maybe need better way of retrieving previous word
        CharSequence prevWord = EditingUtil.getPreviousWord(
                getCurrentInputConnection(), mWordSeparators);
        List<CharSequence> stringList = mSuggest.getSuggestions(
                mKeyboardSwitcher.getInputView(), word, false, prevWord);
        // long stopTime = System.currentTimeMillis(); // TIME MEASUREMENT!
        // Log.d("LatinIME","Suggest Total Time - " + (stopTime - startTime));

        int[] nextLettersFrequencies = mSuggest.getNextLettersFrequencies();

        ((LatinKeyboard) mKeyboardSwitcher.getInputView().getKeyboard())
                .setPreferredLetters(nextLettersFrequencies);

        boolean correctionAvailable = !mInputTypeNoAutoCorrect
                && mSuggest.hasMinimalCorrection();
        // || mCorrectionMode == mSuggest.CORRECTION_FULL;
        CharSequence typedWord = word.getTypedWord();
        // If we're in basic correct
        boolean typedWordValid = mSuggest.isValidWord(typedWord)
                || (preferCapitalization() && mSuggest.isValidWord(typedWord
                        .toString().toLowerCase()));
        if (mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            correctionAvailable |= typedWordValid;
        }
        // Don't auto-correct words with multiple capital letter
        correctionAvailable &= !word.isMostlyCaps();
        correctionAvailable &= !TextEntryState.isCorrecting();

        showSuggestions(stringList, typedWord, typedWordValid,
                correctionAvailable);
    }

    private void showSuggestions(List<CharSequence> stringList,
            CharSequence typedWord, boolean typedWordValid,
            boolean correctionAvailable) {
        setSuggestions(stringList, false, typedWordValid, correctionAvailable);
        if (stringList.size() > 0) {
            if (correctionAvailable && !typedWordValid && stringList.size() > 1) {
                mBestWord = stringList.get(1);
            } else {
                mBestWord = typedWord;
            }
        } else {
            mBestWord = null;
        }
        setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
    }

    private boolean pickDefaultSuggestion() {
        // Complete any pending candidate query first
        if (mHandler.hasMessages(MSG_UPDATE_SUGGESTIONS)) {
            mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
            updateSuggestions();
        }
        if (mBestWord != null && mBestWord.length() > 0) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
            mJustAccepted = true;
            pickSuggestion(mBestWord, false);
            // Add the word to the auto dictionary if it's not a known word
            addToDictionaries(mBestWord, AutoDictionary.FREQUENCY_FOR_TYPED);
            return true;

        }
        return false;
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        List<CharSequence> suggestions = mCandidateView.getSuggestions();

        final boolean correcting = TextEntryState.isCorrecting();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            if (ic != null) {
                ic.commitCompletion(ci);
            }
            mCommittedLength = suggestion.length();
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }

        // If this is a punctuation, apply it through the normal key press
        if (suggestion.length() == 1
                && (isWordSeparator(suggestion.charAt(0)) || isSuggestedPunctuation(suggestion
                        .charAt(0)))) {
            final char primaryCode = suggestion.charAt(0);
            onKey(primaryCode, new int[] { primaryCode },
                    LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE,
                    LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE);
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }
        mJustAccepted = true;
        pickSuggestion(suggestion, correcting);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED);
        } else {
            addToBigramDictionary(suggestion, 1);
        }
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        // Follow it with a space
        if (mAutoSpace && !correcting) {
            sendSpace();
            mJustAddedAutoSpace = true;
        }

        final boolean showingAddToDictionaryHint = index == 0
                && mCorrectionMode > 0 && !mSuggest.isValidWord(suggestion)
                && !mSuggest.isValidWord(suggestion.toString().toLowerCase());

        if (!correcting) {
            // Fool the state watcher so that a subsequent backspace will not do
            // a revert, unless
            // we just did a correction, in which case we need to stay in
            // TextEntryState.State.PICKED_SUGGESTION state.
            TextEntryState.typedCharacter((char) ASCII_SPACE, true);
            setNextSuggestions();
        } else if (!showingAddToDictionaryHint) {
            // If we're not showing the "Touch again to save", then show
            // corrections again.
            // In case the cursor position doesn't change, make sure we show the
            // suggestions again.
            clearSuggestions();
            postUpdateOldSuggestions();
        }
        if (showingAddToDictionaryHint) {
            mCandidateView.showAddToDictionaryHint(suggestion);
        }
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    private void rememberReplacedWord(CharSequence suggestion) {
    }

    /**
     * Commits the chosen word to the text field and saves it for later
     * retrieval.
     *
     * @param suggestion
     *            the suggestion picked by the user to be committed to the text
     *            field
     * @param correcting
     *            whether this is due to a correction of an existing word.
     */
    private void pickSuggestion(CharSequence suggestion, boolean correcting) {
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        int shiftState = getShiftState();
        if (shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED) {
            suggestion = suggestion.toString().toUpperCase(); // all UPPERCASE
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            rememberReplacedWord(suggestion);
            ic.commitText(suggestion, 1);
        }
        saveWordInHistory(suggestion);
        mPredicting = false;
        mCommittedLength = suggestion.length();
        ((LatinKeyboard) inputView.getKeyboard()).setPreferredLetters(null);
        // If we just corrected a word, then don't show punctuations
        if (!correcting) {
            setNextSuggestions();
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Tries to apply any typed alternatives for the word if we have any cached
     * alternatives, otherwise tries to find new corrections and completions for
     * the word.
     *
     * @param touching
     *            The word that the cursor is touching, with position
     *            information
     * @return true if an alternative was found, false otherwise.
     */
    private boolean applyTypedAlternatives(EditingUtil.SelectedWord touching) {
        // If we didn't find a match, search for result in typed word history
        WordComposer foundWord = null;
        WordAlternatives alternatives = null;
        for (WordAlternatives entry : mWordHistory) {
            if (TextUtils.equals(entry.getChosenWord(), touching.word)) {
                if (entry instanceof TypedWordAlternatives) {
                    foundWord = ((TypedWordAlternatives) entry).word;
                }
                alternatives = entry;
                break;
            }
        }
        // If we didn't find a match, at least suggest completions
        if (foundWord == null
                && (mSuggest.isValidWord(touching.word) || mSuggest
                        .isValidWord(touching.word.toString().toLowerCase()))) {
            foundWord = new WordComposer();
            for (int i = 0; i < touching.word.length(); i++) {
                foundWord.add(touching.word.charAt(i),
                        new int[] { touching.word.charAt(i) });
            }
            foundWord.setFirstCharCapitalized(Character
                    .isUpperCase(touching.word.charAt(0)));
        }
        // Found a match, show suggestions
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = new TypedWordAlternatives(touching.word,
                        foundWord);
            }
            showCorrections(alternatives);
            if (foundWord != null) {
                mWord = new WordComposer(foundWord);
            } else {
                mWord.reset();
            }
            return true;
        }
        return false;
    }

    private void setOldSuggestions() {
        if (mCandidateView != null
                && mCandidateView.isShowingAddToDictionaryHint()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        if (!mPredicting) {
            // Extract the selected or touching text
            EditingUtil.SelectedWord touching = EditingUtil
                    .getWordAtCursorOrSelection(ic, mLastSelectionStart,
                            mLastSelectionEnd, mWordSeparators);

            abortCorrection(true);
            setNextSuggestions(); // Show the punctuation suggestions list
        } else {
            abortCorrection(true);
        }
    }

    private void setNextSuggestions() {
        setSuggestions(mSuggestPuncList, false, false, false);
    }

    private void addToDictionaries(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, false);
    }

    private void addToBigramDictionary(CharSequence suggestion,
            int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, true);
    }

    /**
     * Adds to the UserBigramDictionary and/or AutoDictionary
     *
     * @param addToBigramDictionary
     *            true if it should be added to bigram dictionary if possible
     */
    private void checkAddToDictionary(CharSequence suggestion,
            int frequencyDelta, boolean addToBigramDictionary) {
        if (suggestion == null || suggestion.length() < 1)
            return;
        // Only auto-add to dictionary if auto-correct is ON. Otherwise we'll be
        // adding words in situations where the user or application really
        // didn't
        // want corrections enabled or learned.
        if (!(mCorrectionMode == Suggest.CORRECTION_FULL || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM)) {
            return;
        }
        if (suggestion != null) {
            if (!addToBigramDictionary
                    && mAutoDictionary.isValidWord(suggestion)
                    || (!mSuggest.isValidWord(suggestion.toString()) && !mSuggest
                            .isValidWord(suggestion.toString().toLowerCase()))) {
                mAutoDictionary.addWord(suggestion.toString(), frequencyDelta);
            }

            if (mUserBigramDictionary != null) {
                CharSequence prevWord = EditingUtil.getPreviousWord(
                        getCurrentInputConnection(), mSentenceSeparators);
                if (!TextUtils.isEmpty(prevWord)) {
                    mUserBigramDictionary.addBigrams(prevWord.toString(),
                            suggestion.toString());
                }
            }
        }
    }

    private boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return false;
        CharSequence[] around = EditingUtil.textAround(ic, 1, 1);
        CharSequence toLeft = around[0];
        CharSequence toRight = around[1];
        if (!TextUtils.isEmpty(toLeft) && !isWordSeparator(toLeft.charAt(0))
                && !isSuggestedPunctuation(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight) && !isWordSeparator(toRight.charAt(0))
                && !isSuggestedPunctuation(toRight.charAt(0))) {
            return true;
        }
        return false;
    }

    private boolean sameAsTextBeforeCursor(InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (!mPredicting && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            mJustRevertedSeparator = ic.getTextBeforeCursor(1, 0);
            if (deleteChar)
                ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            CharSequence toTheLeft = ic
                    .getTextBeforeCursor(mCommittedLength, 0);
            if (toTheLeft != null && toTheLeft.length() > 0
                    && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            ic.setComposingText(mComposing, 1);
            TextEntryState.backspace();
            postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustRevertedSeparator = null;
        }
    }

    protected String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char) code));
    }

    private boolean isSentenceSeparator(int code) {
        return mSentenceSeparators.contains(String.valueOf((char) code));
    }

    private void sendSpace() {
        sendModifiableKeyChar((char) ASCII_SPACE);
        updateShiftKeyState(getCurrentInputEditorInfo());
        // onKey(KEY_SPACE[0], KEY_SPACE);
    }

    public boolean preferCapitalization() {
        return mWord.isFirstCharCapitalized();
    }

    void toggleLanguage(boolean reset, boolean next) {
        commitHangulComposing();
        mHangulComposer.reset();
        if (reset) {
            mLanguageSwitcher.reset();
        } else {
            if (next) {
                mLanguageSwitcher.next();
            } else {
                mLanguageSwitcher.prev();
            }
        }
        int currentKeyboardMode = mKeyboardSwitcher.getKeyboardMode();
        reloadKeyboards();
        mKeyboardSwitcher.makeKeyboards(true);
        mKeyboardSwitcher.setKeyboardMode(currentKeyboardMode, 0,
                mEnableVoiceButton && mEnableVoice);
        initSuggest(mLanguageSwitcher.getInputLanguage());
        mAdaptive.setLanguage(mLanguageSwitcher.getInputLanguage());
        mLanguageSwitcher.persist();
        mAutoCapActive = mAutoCapPref && mLanguageSwitcher.allowAutoCap();
        mDeadKeysActive = mLanguageSwitcher.allowDeadKeys();
        updateShiftKeyState(getCurrentInputEditorInfo());
        setCandidatesViewShown(isPredictionOn());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Log.i("PCKeyboard", "onSharedPreferenceChanged()");
        boolean needReload = false;
        Resources res = getResources();
        
        // Apply globally handled shared prefs
        sKeyboardSettings.sharedPreferenceChanged(sharedPreferences, key);
        mAdaptive.loadSettings(sharedPreferences);
        mDeleteSwipePref = sharedPreferences.getBoolean(PREF_DELETE_SWIPE, true);
        setLoupeEnabled(sharedPreferences.getBoolean(CursorLoupe.PREF, true));
        EdgeRepeat.sEdgeDp = getPrefInt(sharedPreferences, EdgeRepeat.PREF, EdgeRepeat.DEFAULT_DP);
        if (ClipboardHistory.PREF_ENABLED.equals(key)) {
            ClipboardHistory.get(this).setEnabled(sharedPreferences.getBoolean(key, true));
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_NEED_RELOAD)) {
            needReload = true;
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_NEW_PUNC_LIST)) {
            initSuggestPuncList();
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RECREATE_INPUT_VIEW)) {
            mKeyboardSwitcher.recreateInputView();
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RESET_MODE_OVERRIDE)) {
            mKeyboardModeOverrideLandscape = 0;
            mKeyboardModeOverridePortrait = 0;
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RESET_KEYBOARDS)) {
            toggleLanguage(true, true);
        }
        int unhandledFlags = sKeyboardSettings.unhandledFlags();
        if (unhandledFlags != GlobalKeyboardSettings.FLAG_PREF_NONE) {
            Log.w(TAG, "Not all flag settings handled, remaining=" + unhandledFlags);
        }

        if (PREF_SELECTED_LANGUAGES.equals(key)) {
            mLanguageSwitcher.loadLocales(sharedPreferences);
            mRefreshKeyboardRequired = true;
        } else if (PREF_RECORRECTION_ENABLED.equals(key)) {
            mReCorrectionEnabled = sharedPreferences.getBoolean(
                    PREF_RECORRECTION_ENABLED, res
                            .getBoolean(R.bool.default_recorrection_enabled));
            if (mReCorrectionEnabled) {
                // It doesn't work right on pre-Gingerbread phones.
                Toast.makeText(getApplicationContext(),
                        res.getString(R.string.recorrect_warning), Toast.LENGTH_LONG)
                        .show();
            }
        } else if (PREF_FULLSCREEN_OVERRIDE.equals(key)) {
            mFullscreenOverride = sharedPreferences.getBoolean(
                    PREF_FULLSCREEN_OVERRIDE, res
                            .getBoolean(R.bool.default_fullscreen_override));
            needReload = true;
        } else if (PREF_FORCE_KEYBOARD_ON.equals(key)) {
            mForceKeyboardOn = sharedPreferences.getBoolean(
                    PREF_FORCE_KEYBOARD_ON, res
                            .getBoolean(R.bool.default_force_keyboard_on));
            needReload = true;
        } else if (PREF_SUGGESTIONS_IN_LANDSCAPE.equals(key)) {
            mSuggestionsInLandscape = sharedPreferences.getBoolean(
                    PREF_SUGGESTIONS_IN_LANDSCAPE, res
                            .getBoolean(R.bool.default_suggestions_in_landscape));
            // Respect the suggestion settings in legacy Gingerbread mode,
            // in portrait mode, or if suggestions in landscape enabled.
            mSuggestionForceOff = false;
            mSuggestionForceOn = false;
            setCandidatesViewShown(isPredictionOn());
        } else if (PREF_SHOW_SUGGESTIONS.equals(key)) {
            mShowSuggestions = sharedPreferences.getBoolean(
                    PREF_SHOW_SUGGESTIONS, res.getBoolean(R.bool.default_suggestions));
            mSuggestionForceOff = false;
            mSuggestionForceOn = false;
            needReload = true;
        } else if (PREF_HEIGHT_PORTRAIT.equals(key)) {
            mHeightPortrait = getHeight(sharedPreferences,
                    PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait));
            needReload = true;
        } else if (PREF_HEIGHT_LANDSCAPE.equals(key)) {
            mHeightLandscape = getHeight(sharedPreferences,
                    PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape));
            needReload = true;
        } else if (PREF_HINT_MODE.equals(key)) {
            LatinIME.sKeyboardSettings.hintMode = Integer.parseInt(sharedPreferences.getString(PREF_HINT_MODE,
                    res.getString(R.string.default_hint_mode)));
            needReload = true;
        } else if (PREF_LONGPRESS_TIMEOUT.equals(key)) {
               LatinIME.sKeyboardSettings.longpressTimeout = getPrefInt(sharedPreferences, PREF_LONGPRESS_TIMEOUT,
                       res.getString(R.string.default_long_press_duration));
        } else if (PREF_RENDER_MODE.equals(key)) {
            LatinIME.sKeyboardSettings.renderMode = getPrefInt(sharedPreferences, PREF_RENDER_MODE,
                    res.getString(R.string.default_render_mode));
            needReload = true;
        } else if (PREF_SWIPE_UP.equals(key)) {
            mSwipeUpAction = sharedPreferences.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up));
        } else if (PREF_SWIPE_DOWN.equals(key)) {
            mSwipeDownAction = sharedPreferences.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down));
        } else if (PREF_SWIPE_LEFT.equals(key)) {
            mSwipeLeftAction = sharedPreferences.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left));
        } else if (PREF_SWIPE_RIGHT.equals(key)) {
            mSwipeRightAction = sharedPreferences.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right));
        } else if (PREF_VIBRATE_LEN.equals(key)) {
            mVibrateLen = getPrefInt(sharedPreferences, PREF_VIBRATE_LEN, getResources().getString(R.string.vibrate_duration_ms));
        }

        updateKeyboardOptions();
        if (needReload) {
            mKeyboardSwitcher.makeKeyboards(true);
        }
    }

    private boolean doSwipeAction(String action) {
        //Log.i(TAG, "doSwipeAction + " + action);
        if (action == null || action.equals("") || action.equals("none")) {
            return false;
        } else if (action.equals("close")) {
            handleClose();
        } else if (action.equals("settings")) {
            launchSettings();
        } else if (action.equals("suggestions")) {
            if (mSuggestionForceOn) {
                mSuggestionForceOn = false;
                mSuggestionForceOff = true;
            } else if (mSuggestionForceOff) {
                mSuggestionForceOn = true;
                mSuggestionForceOff = false;                
            } else if (isPredictionWanted()) {
                mSuggestionForceOff = true;
            } else {
                mSuggestionForceOn = true;
            }
            setCandidatesViewShown(isPredictionOn());
        } else if (action.equals("lang_prev")) {
            toggleLanguage(false, false);
        } else if (action.equals("lang_next")) {
            toggleLanguage(false, true);
        } else if (action.equals("full_mode")) {
            if (isPortrait()) {
                mKeyboardModeOverridePortrait = (mKeyboardModeOverridePortrait + 1) % mNumKeyboardModes;
            } else {
                mKeyboardModeOverrideLandscape = (mKeyboardModeOverrideLandscape + 1) % mNumKeyboardModes;
            }
            toggleLanguage(true, true);
        } else if (action.equals("extension") || action.equals("toolbar")) {
            // "extension" used to toggle the F-key row; the toolbar replaces it.
            KeyboardToolbar toolbar = mKeyboardSwitcher.getInputContainer();
            if (toolbar != null) toolbar.toggleToolbar();
        } else if (action.equals("height_up")) {
            if (isPortrait()) {
                mHeightPortrait += 5;
                if (mHeightPortrait > 70) mHeightPortrait = 70;
            } else {
                mHeightLandscape += 5;
                if (mHeightLandscape > 70) mHeightLandscape = 70;                
            }
            toggleLanguage(true, true);
        } else if (action.equals("height_down")) {
            if (isPortrait()) {
                mHeightPortrait -= 5;
                if (mHeightPortrait < 15) mHeightPortrait = 15;
            } else {
                mHeightLandscape -= 5;
                if (mHeightLandscape < 15) mHeightLandscape = 15;                
            }
            toggleLanguage(true, true);
        } else {
            Log.i(TAG, "Unsupported swipe action config: " + action);
        }
        return true;
    }

    public boolean swipeRight() {
        return doSwipeAction(mSwipeRightAction);
    }

    public boolean swipeLeft() {
        return doSwipeAction(mSwipeLeftAction);
    }

    public boolean swipeDown() {
        return doSwipeAction(mSwipeDownAction);
    }

    public boolean swipeUp() {
        return doSwipeAction(mSwipeUpAction);
    }

    public void onPress(int primaryCode) {
        InputConnection ic = getCurrentInputConnection();
        if (mKeyboardSwitcher.isVibrateAndSoundFeedbackRequired()) {
            vibrate();
            playKeyClick(primaryCode);
        }
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT) {
            mShiftKeyState.onPress();
            startMultitouchShift();
        } else if (distinctMultiTouch
                && primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            changeKeyboardMode();
            mSymbolKeyState.onPress();
            mKeyboardSwitcher.setAutoModeSwitchStateMomentary();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
            setModCtrl(!mModCtrl);
            mCtrlKeyState.onPress();
            sendCtrlKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT) {
            setModAlt(!mModAlt);
            mAltKeyState.onPress();
            sendAltKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_META_LEFT) {
            setModMeta(!mModMeta);
            mMetaKeyState.onPress();
            sendMetaKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_FN) {
            setModFn(!mModFn);
            mFnKeyState.onPress();
        } else {
            mShiftKeyState.onOtherKeyPressed();
            mSymbolKeyState.onOtherKeyPressed();
            mCtrlKeyState.onOtherKeyPressed();
            mAltKeyState.onOtherKeyPressed();
            mMetaKeyState.onOtherKeyPressed();
            mFnKeyState.onOtherKeyPressed();
        }
    }

    public void onRelease(int primaryCode) {
        // Reset any drag flags in the keyboard
        ((LatinKeyboard) mKeyboardSwitcher.getInputView().getKeyboard())
                .keyReleased();
        // vibrate();
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        InputConnection ic = getCurrentInputConnection();
        if (distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT) {
            if (mShiftKeyState.isChording()) {
                resetMultitouchShift();
            } else {
                commitMultitouchShift();
            }
            mShiftKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            // Snap back to the previous keyboard mode if the user chords the
            // mode change key and
            // other key, then released the mode change key.
            if (mKeyboardSwitcher.isInChordingAutoModeSwitchState())
                changeKeyboardMode();
            mSymbolKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
            if (mCtrlKeyState.isChording()) {
                setModCtrl(false);
            }
            sendCtrlKey(ic, false, true);
            mCtrlKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT) {
            if (mAltKeyState.isChording()) {
                setModAlt(false);
            }
            sendAltKey(ic, false, true);
            mAltKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_META_LEFT) {
            if (mMetaKeyState.isChording()) {
                setModMeta(false);
            }
            sendMetaKey(ic, false, true);
            mMetaKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_FN) {
            if (mFnKeyState.isChording()) {
                setModFn(false);
            }
            mFnKeyState.onRelease();
        }
        // WARNING: Adding a chording modifier key? Make sure you also
        // edit PointerTracker.isModifierInternal(), otherwise it will
        // force a release event instead of chording.
    }

    // receive ringer mode changes to detect silent mode
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRingerMode();
        }
    };

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            mSilentMode = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private float getKeyClickVolume() {
        if (mAudioManager == null) return 0.0f; // shouldn't happen
        
        // The volume calculations are poorly documented, this is the closest I could
        // find for explaining volume conversions:
        // http://developer.android.com/reference/android/media/MediaPlayer.html#setAuxEffectSendLevel(float)
        //
        //   Note that the passed level value is a raw scalar. UI controls should be scaled logarithmically:
        //   the gain applied by audio framework ranges from -72dB to 0dB, so an appropriate conversion 
        //   from linear UI input x to level is: x == 0 -> level = 0 0 < x <= R -> level = 10^(72*(x-R)/20/R)
        
        int method = sKeyboardSettings.keyClickMethod; // See click_method_values in strings.xml
        if (method == 0) return FX_VOLUME;
        
        float targetVol = sKeyboardSettings.keyClickVolume;

        if (method > 1) {
            // TODO(klausw): on some devices the media volume controls the click volume?
            // If that's the case, try to set a relative target volume.
            int mediaMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int mediaVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            //Log.i(TAG, "getKeyClickVolume relative, media vol=" + mediaVol + "/" + mediaMax);
            float channelVol = (float) mediaVol / mediaMax;
            if (method == 2) {
                targetVol *= channelVol;
            } else if (method == 3) {
                if (channelVol == 0) return 0.0f; // Channel is silent, won't get audio
                targetVol = Math.min(targetVol / channelVol, 1.0f); // Cap at 1.0
            }
        }
        // Set absolute volume, treating the percentage as a logarithmic control
        float vol = (float) Math.pow(10.0, FX_VOLUME_RANGE_DB * (targetVol - 1) / 20);
        //Log.i(TAG, "getKeyClickVolume absolute, target=" + targetVol + " amp=" + vol);
        return vol;
    }
    
    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mKeyboardSwitcher.getInputView() != null) {
                updateRingerMode();
            }
        }
        if (mSoundOn && !mSilentMode) {
            // FIXME: Volume and enable should come from UI settings
            // FIXME: These should be triggered after auto-repeat logic
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
            switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case ASCII_ENTER:
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case ASCII_SPACE:
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            }
            mAudioManager.playSoundEffect(sound, getKeyClickVolume());
        }
    }

    private void vibrate() {
        if (!mVibrateOn) {
            return;
        }
        vibrate(mVibrateLen);
    }

    void vibrate(int len) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(len);
            return;
        }

        if (mKeyboardSwitcher.getInputView() != null) {
            mKeyboardSwitcher.getInputView().performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
    
    /* package */void promoteToUserDictionary(String word, int frequency) {
        if (mUserDictionary.isValidWord(word))
            return;
        mUserDictionary.addWord(word, frequency);
    }

    /* package */WordComposer getCurrentWord() {
        return mWord;
    }

    /* package */boolean getPopupOn() {
        return mPopupOn;
    }

    private void updateCorrectionMode() {
        mHasDictionary = mSuggest != null ? mSuggest.hasMainDictionary()
                : false;
        mAutoCorrectOn = (mAutoCorrectEnabled || mQuickFixes)
                && !mInputTypeNoAutoCorrect && mHasDictionary;
        mCorrectionMode = (mAutoCorrectOn && mAutoCorrectEnabled) ? Suggest.CORRECTION_FULL
                : (mAutoCorrectOn ? Suggest.CORRECTION_BASIC
                        : Suggest.CORRECTION_NONE);
        mCorrectionMode = (mBigramSuggestionEnabled && mAutoCorrectOn && mAutoCorrectEnabled) ? Suggest.CORRECTION_FULL_BIGRAM
                : mCorrectionMode;
        if (suggestionsDisabled()) {
            mAutoCorrectOn = false;
            mCorrectionMode = Suggest.CORRECTION_NONE;
        }
        if (mSuggest != null) {
            mSuggest.setCorrectionMode(mCorrectionMode);
        }
    }

    private void updateAutoTextEnabled(Locale systemLocale) {
        if (mSuggest == null)
            return;
        boolean different = !systemLocale.getLanguage().equalsIgnoreCase(
                mInputLocale.substring(0, 2));
        mSuggest.setAutoTextEnabled(!different && mQuickFixes);
    }

    protected void launchSettings() {
        launchSettings(LatinIMESettings.class);
    }

    protected void launchSettings(
            Class<? extends android.app.Activity> settingsClass) {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, settingsClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadSettings() {
        // Get the settings preferences
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        mAdaptive.loadSettings(sp);
        mDeleteSwipePref = sp.getBoolean(PREF_DELETE_SWIPE, true);
        setLoupeEnabled(sp.getBoolean(CursorLoupe.PREF, true));
        EdgeRepeat.sEdgeDp = getPrefInt(sp, EdgeRepeat.PREF, EdgeRepeat.DEFAULT_DP);
        mVibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false);
        mVibrateLen = getPrefInt(sp, PREF_VIBRATE_LEN, getResources().getString(R.string.vibrate_duration_ms));
        mSoundOn = sp.getBoolean(PREF_SOUND_ON, false);
        mPopupOn = sp.getBoolean(PREF_POPUP_ON, mResources
                .getBoolean(R.bool.default_popup_preview));
        mAutoCapPref = sp.getBoolean(PREF_AUTO_CAP, getResources().getBoolean(
                R.bool.default_auto_cap));
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true);

        mShowSuggestions = sp.getBoolean(PREF_SHOW_SUGGESTIONS, mResources
                .getBoolean(R.bool.default_suggestions));

        final String voiceMode = sp.getString(PREF_VOICE_MODE,
                getString(R.string.voice_mode_main));
        boolean enableVoice = !voiceMode
                .equals(getString(R.string.voice_mode_off))
                && mEnableVoiceButton;
        boolean voiceOnPrimary = voiceMode
                .equals(getString(R.string.voice_mode_main));
        if (mKeyboardSwitcher != null
                && (enableVoice != mEnableVoice || voiceOnPrimary != mVoiceOnPrimary)) {
            mKeyboardSwitcher.setVoiceMode(enableVoice, voiceOnPrimary);
        }
        mEnableVoice = enableVoice;
        mVoiceOnPrimary = voiceOnPrimary;

        mAutoCorrectEnabled = sp.getBoolean(PREF_AUTO_COMPLETE, mResources
                .getBoolean(R.bool.enable_autocorrect))
                & mShowSuggestions;
        // mBigramSuggestionEnabled = sp.getBoolean(
        // PREF_BIGRAM_SUGGESTIONS, true) & mShowSuggestions;
        updateCorrectionMode();
        updateAutoTextEnabled(mResources.getConfiguration().locale);
        mLanguageSwitcher.loadLocales(sp);
        mAutoCapActive = mAutoCapPref && mLanguageSwitcher.allowAutoCap();
        mDeadKeysActive = mLanguageSwitcher.allowDeadKeys();
    }

    private void initSuggestPuncList() {
        mSuggestPuncList = new ArrayList<CharSequence>();
        String suggestPuncs = sKeyboardSettings.suggestedPunctuation;
        String defaultPuncs = getResources().getString(R.string.suggested_punctuations_default);
        if (suggestPuncs.equals(defaultPuncs) || suggestPuncs.equals("")) {
            // Not user-configured, load the language-specific default.
            suggestPuncs = getResources().getString(R.string.suggested_punctuations);
        }
        if (suggestPuncs != null) {
            for (int i = 0; i < suggestPuncs.length(); i++) {
                mSuggestPuncList.add(suggestPuncs.subSequence(i, i + 1));
            }
        }
        setNextSuggestions();
    }

    private boolean isSuggestedPunctuation(int code) {
        return sKeyboardSettings.suggestedPunctuation.contains(String.valueOf((char) code));
    }

    public void changeKeyboardMode() {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isAlphabetMode()) {
            mSavedShiftState = getShiftState();
        }
        switcher.toggleSymbols();
        if (switcher.isAlphabetMode()) {
            switcher.setShiftState(mSavedShiftState);
        }

        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    public static <E> ArrayList<E> newArrayList(E... elements) {
        int capacity = (elements.length * 110) / 100 + 5;
        ArrayList<E> list = new ArrayList<E>(capacity);
        Collections.addAll(list, elements);
        return list;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mComposing=" + mComposing.toString());
        p.println("  mPredictionOnForMode=" + mPredictionOnForMode);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mPredicting=" + mPredicting);
        p.println("  mAutoCorrectOn=" + mAutoCorrectOn);
        p.println("  mAutoSpace=" + mAutoSpace);
        p.println("  mCompletionOn=" + mCompletionOn);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSoundOn);
        p.println("  mVibrateOn=" + mVibrateOn);
        p.println("  mPopupOn=" + mPopupOn);
    }

    // Characters per second measurement

    private long mLastCpsTime;
    private static final int CPS_BUFFER_SIZE = 16;
    private long[] mCpsIntervals = new long[CPS_BUFFER_SIZE];
    private int mCpsIndex;
    private static Pattern NUMBER_RE = Pattern.compile("(\\d+).*");

    private void measureCps() {
        long now = System.currentTimeMillis();
        if (mLastCpsTime == 0)
            mLastCpsTime = now - 100; // Initial
        mCpsIntervals[mCpsIndex] = now - mLastCpsTime;
        mLastCpsTime = now;
        mCpsIndex = (mCpsIndex + 1) % CPS_BUFFER_SIZE;
        long total = 0;
        for (int i = 0; i < CPS_BUFFER_SIZE; i++)
            total += mCpsIntervals[i];
        System.out.println("CPS = " + ((CPS_BUFFER_SIZE * 1000f) / total));
    }

    public void onAutoCompletionStateChanged(boolean isAutoCompletion) {
        mKeyboardSwitcher.onAutoCompletionStateChanged(isAutoCompletion);
    }

    static int getIntFromString(String val, int defVal) {
        Matcher num = NUMBER_RE.matcher(val);
        if (!num.matches()) return defVal;
        return Integer.parseInt(num.group(1));
    }

    static int getPrefInt(SharedPreferences prefs, String prefName, int defVal) {
        String prefVal = prefs.getString(prefName, Integer.toString(defVal));
        //Log.i("PCKeyboard", "getPrefInt " + prefName + " = " + prefVal + ", default " + defVal);
        return getIntFromString(prefVal, defVal);
    }

    static int getPrefInt(SharedPreferences prefs, String prefName, String defStr) {
        int defVal = getIntFromString(defStr, 0);
        return getPrefInt(prefs, prefName, defVal);
    }

    static int getHeight(SharedPreferences prefs, String prefName, String defVal) {
        int val = getPrefInt(prefs, prefName, defVal);
        if (val < 15)
            val = 15;
        if (val > 75)
            val = 75;
        return val;
    }
}
