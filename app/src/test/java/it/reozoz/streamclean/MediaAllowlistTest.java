package it.reozoz.streamclean;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

public class MediaAllowlistTest {
    private final URI source=URI.create("https://vixcloud.cc/playlist/412134?token=test");
    @Test public void authorizesHtmlChunksFromRotatingCdnOnlyWhenDeclared(){
        MediaAllowlist m=new MediaAllowlist();
        String chunk="https://sc-u1-01.blackpuma78.boats/hls/33/2/9b/29b16380-a5f9-490b-aa6d-ff6ae32e2891/audio/ita/0000-0183.html";
        assertFalse(PlayerPolicy.resource(chunk));assertFalse(m.allows(chunk));
        m.registerPlaylist(source,"#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://vixcloud.cc/storage/enc.key\"\n#EXTINF:6,\n"+chunk+"\n");
        assertTrue(m.allows(chunk));assertTrue(m.allows("https://vixcloud.cc/storage/enc.key"));
        assertFalse(m.allows(chunk.replace("0000-0183","0001-0186")));
        assertFalse(m.allows("https://spbgc.com/tag.min.js"));
    }
    @Test public void followsDeclaredVariantPlaylistsAndRelativeChunks(){
        MediaAllowlist m=new MediaAllowlist();
        String variant="https://rotating.example/live/level?id=3";
        m.registerPlaylist(source,"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\n"+variant+"\n");
        assertTrue(m.playlist(variant));
        m.registerPlaylist(URI.create(variant),"#EXTM3U\n#EXTINF:6,\nchunk.html?part=1\n");
        assertTrue(m.allows("https://rotating.example/live/chunk.html?part=1"));
        assertFalse(m.allows("https://rotating.example/live/chunk.html?part=2"));
    }
    @Test public void includesSeparateAudioAndRejectsUntrustedManifest(){
        MediaAllowlist m=new MediaAllowlist();
        m.registerPlaylist(source,"#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"it\",URI=\"https://audio.example/a/list.m3u8\"\n");
        assertTrue(m.playlist("https://audio.example/a/list.m3u8"));
        m.registerPlaylist(URI.create("https://ads.example/fake.m3u8"),"#EXTM3U\nhttps://ads.example/payload.js\n");
        assertFalse(m.allows("https://ads.example/payload.js"));
    }
    @Test public void rejectsNonHttpsAndResetsBetweenPlayers(){
        MediaAllowlist m=new MediaAllowlist();
        m.registerPlaylist(source,"#EXTM3U\nhttp://cdn.example/1.ts\nfile:///tmp/1.ts\nhttps://u:p@cdn.example/1.ts\nhttps://cdn.example:8080/1.ts\nhttps://cdn.example/1.ts\n");
        assertFalse(m.allows("http://cdn.example/1.ts"));assertFalse(m.allows("file:///tmp/1.ts"));
        assertFalse(m.allows("https://u:p@cdn.example/1.ts"));assertFalse(m.allows("https://cdn.example:8080/1.ts"));
        assertTrue(m.allows("https://cdn.example/1.ts"));assertFalse(new MediaAllowlist().allows("https://cdn.example/1.ts"));
    }
}
