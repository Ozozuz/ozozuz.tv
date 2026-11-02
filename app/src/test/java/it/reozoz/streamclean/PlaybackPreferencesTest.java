package it.reozoz.streamclean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class PlaybackPreferencesTest {
    private PlaybackPreferences prefs(){return new PlaybackPreferences(RuntimeEnvironment.getApplication());}
    @Test public void firstUseDefaultsToEnglishAndItalian(){assertEquals("en",prefs().audio());assertEquals("it",prefs().subtitles());}
    @Test public void preferencesPersistIndependentlyAcrossInstances(){prefs().audio("ja");prefs().subtitles("off");assertEquals("ja",prefs().audio());assertEquals("off",prefs().subtitles());}
    @Test public void providerDefaultsAreExplicitOptions(){prefs().audio("default");prefs().subtitles("default");assertEquals("default",prefs().audio());assertEquals("default",prefs().subtitles());}
    @Test public void invalidValuesCannotBecomeJavascript(){prefs().audio("');alert(1)");prefs().subtitles("bad");assertEquals("en",prefs().audio());assertEquals("it",prefs().subtitles());}
    @Test public void packagedScriptContainsOnlyTheSavedSnapshot()throws Exception{
        prefs().audio("de");prefs().subtitles("off");String script=prefs().script(RuntimeEnvironment.getApplication());
        assertTrue(script.contains("\"audio\":\"de\""));assertTrue(script.contains("\"subtitles\":\"off\""));assertFalse(script.contains("__OZOZUZ_PREFERENCES__"));
    }
}
