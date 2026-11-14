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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

/** Keyboards of an input language, loaded as the keyboard loads them (tests in other packages). */
public final class LocaleLayouts {
    private LocaleLayouts() {}

    /**
     * The 5-row PC layout of {@code locale} ("en", "iw", "ar", "de_NE"...) under that
     * language's resources, at the default height (40%), not the shared setting other tests
     * may have changed.
     */
    public static LatinKeyboard fullKeyboard(String locale) {
        Context c = LocaleContext.of(ApplicationProvider.getApplicationContext(),
                LocaleContext.parse(locale));
        return new LatinKeyboard(c, R.xml.kbd_full, 0, 40.0f);
    }
}
