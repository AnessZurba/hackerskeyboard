package kr.jcb1ee.hackerskeyboard2.theme;

/**
 * The kinds of key a theme can colour differently. The keyboard asks {@link #of} for each key
 * and takes that kind's background, label colour and icon tint from its {@link KeyboardLook}.
 */
public final class KeyKind {
    /** Plain letters, digits and the space bar. */
    public static final int NORMAL = 0;
    /** Modifiers and editing keys: Shift, Ctrl, Alt, Tab, Backspace, arrows, F-keys... */
    public static final int MODIFIER = 1;
    /** Esc and Enter, in an accent colour. */
    public static final int ACCENT = 2;
    public static final int COUNT = 3;

    /** Key codes (the same as LatinIME.ASCII_ENTER, ASCII_SPACE, LatinKeyboardView.KEYCODE_ESCAPE). */
    static final int CODE_ENTER = '\n';
    static final int CODE_SPACE = ' ';
    static final int CODE_ESCAPE = -111;

    private KeyKind() {}

    /** The kind of a key with this first code; {@code modifier} is the layout's flag. */
    public static int of(int code, boolean modifier) {
        if (code == CODE_ENTER || code == CODE_ESCAPE) return ACCENT;
        if (modifier && code != CODE_SPACE) return MODIFIER;
        return NORMAL;
    }
}
