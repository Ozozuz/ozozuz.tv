package it.reozoz.streamclean;
import java.net.URI;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HlsDownloadPlanTest {
    private final URI base=URI.create("https://cdn.example/path/master.m3u8");
    @Test public void selects720pAndItalianAudioFromMatchingGroup()throws Exception{
        String m="#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"dub\",LANGUAGE=\"en\",DEFAULT=YES,URI=\"en.m3u8\"\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"other\",LANGUAGE=\"it\",URI=\"wrong.m3u8\"\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"dub\",LANGUAGE=\"it\",URI=\"it.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,AUDIO=\"dub\",CODECS=\"avc1.640028,mp4a.40.2\"\n1080.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,AUDIO=\"dub\"\n720.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480,AUDIO=\"dub\"\n480.m3u8\n";
        var s=HlsDownloadPlan.select(base,m);assertEquals(base.resolve("720.m3u8"),s.video());assertEquals(base.resolve("it.m3u8"),s.audio());
    }
    @Test public void usesLowestAvailableAbove720()throws Exception{var s=HlsDownloadPlan.select(base,"#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=3840x2160\n4k.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=1920x1080\nhd.m3u8\n");assertEquals(base.resolve("hd.m3u8"),s.video());}
    @Test public void keepsHtmlFragmentsAndSignedQuery()throws Exception{var p=HlsDownloadPlan.parts(base,"#EXTM3U\n#EXTINF:6,\n../audio/1.html?token=abc&expires=9\n#EXT-X-ENDLIST\n");assertEquals("https://cdn.example/audio/1.html?token=abc&expires=9",p.get(0).uri().toString());assertEquals(-1,p.get(0).length());}
    @Test public void handlesFmp4InitAndImplicitByteRanges()throws Exception{var p=HlsDownloadPlan.parts(base,"#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"100@0\"\n#EXT-X-BYTERANGE:50@100\nsame.mp4\n#EXT-X-BYTERANGE:70\nsame.mp4\n#EXT-X-ENDLIST");assertEquals(3,p.size());assertEquals(150,p.get(2).offset());assertEquals(70,p.get(2).length());}
    @Test public void carriesAesSequenceAndResetsKey()throws Exception{var p=HlsDownloadPlan.parts(base,"#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:42\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\na.ts\nb.ts\n#EXT-X-KEY:METHOD=NONE\nc.ts\n#EXT-X-ENDLIST");assertEquals(42,p.get(0).key().sequence());assertEquals(43,p.get(1).key().sequence());assertNull(p.get(2).key());}
    @Test public void buildsExplicit128BitIvAndSequenceIv()throws Exception{
        byte[] explicit=HlsDownloadPlan.iv(new HlsDownloadPlan.Key(base,"0xFF000000000000000000000000000001",0));assertEquals(16,explicit.length);assertEquals((byte)255,explicit[0]);assertEquals(1,explicit[15]);
        byte[] sequence=HlsDownloadPlan.iv(new HlsDownloadPlan.Key(base,"",258));assertEquals(1,sequence[14]);assertEquals(2,sequence[15]);
    }
    @Test(expected=java.io.IOException.class) public void rejectsLivePlaylist()throws Exception{HlsDownloadPlan.parts(base,"#EXTM3U\nsegment.ts\n");}
    @Test(expected=java.io.IOException.class) public void rejectsUnusableDiscontinuity()throws Exception{HlsDownloadPlan.parts(base,"#EXTM3U\n1.ts\n#EXT-X-DISCONTINUITY\n2.ts\n#EXT-X-ENDLIST");}
    @Test(expected=java.io.IOException.class) public void rejectsSampleAes()throws Exception{HlsDownloadPlan.parts(base,"#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"\na.ts\n#EXT-X-ENDLIST");}
    @Test(expected=java.io.IOException.class) public void rejectsUnsafeResource()throws Exception{HlsDownloadPlan.parts(base,"#EXTM3U\nfile:///etc/passwd\n#EXT-X-ENDLIST");}
    @Test(expected=java.io.IOException.class) public void rejectsImplicitRangeAcrossDifferentFiles()throws Exception{HlsDownloadPlan.parts(base,"#EXTM3U\n#EXT-X-BYTERANGE:50@0\na.mp4\n#EXT-X-BYTERANGE:50\nb.mp4\n#EXT-X-ENDLIST");}
}
