package it.reozoz.streamclean;

import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerLanguageUrlTest {
    @Test public void preferredEnglishReplacesProviderItalianDefault(){
        URI source=URI.create("https://vixcloud.cc/embed/402042?token=example%2Btoken&lang=it&scz=1&t=South%20Park");
        assertEquals("https://vixcloud.cc/embed/402042?token=example%2Btoken&scz=1&t=South%20Park&lang=en",PlayerLanguageUrl.audio(source,"en").toString());
    }
    @Test public void providerDefaultKeepsOriginalUrlByteForByte(){
        URI source=URI.create("https://vixcloud.cc/embed/42?token=abc%2B%2F%3D&lang=it&t=a+b#here");
        assertSame(source,PlayerLanguageUrl.audio(source,"default"));
    }
    @Test public void otherSignedParametersAndFragmentRemainEncoded(){
        URI source=URI.create("https://vixcloud.cc/embed/42?token=a%2Bb%2Fc%3D&l%61ng=it&lang=it&t=a+b#anchor");
        assertEquals("https://vixcloud.cc/embed/42?token=a%2Bb%2Fc%3D&t=a+b&lang=en#anchor",PlayerLanguageUrl.audio(source,"en").toString());
    }
    @Test public void missingQueryGetsLanguageAndUnsupportedTargetsAreRejected(){
        assertEquals("https://vixcloud.cc/embed/42?lang=it",PlayerLanguageUrl.audio(URI.create("https://vixcloud.cc/embed/42"),"it").toString());
        assertThrows(IllegalArgumentException.class,()->PlayerLanguageUrl.audio(URI.create("https://other.invalid/embed/42"),"en"));
        assertThrows(IllegalArgumentException.class,()->PlayerLanguageUrl.audio(URI.create("https://vixcloud.cc/embed/42"),"en&extra=1"));
    }
}
