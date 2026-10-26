package it.reozoz.streamclean;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;
import java.util.*;
public class CastPlaylistTest {
 @Test public void rewritesVariantsAudioKeysMapsAndSignedFragments()throws Exception{
  URI source=URI.create("https://video.example/show/master.m3u8?auth=master");List<URI> seen=new ArrayList<>();
  String manifest="#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"it\",URI=\"audio.m3u8?token=a\"\n#EXT-X-STREAM-INF:BANDWIDTH=123,AUDIO=\"it\"\n720/main.m3u8?token=b\n#EXT-X-KEY:METHOD=AES-128,URI=\"/keys/a?token=c\"\n#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"100@0\"\n#EXTINF:9.5,\nhttps://cdn.example/chunk.html?sig=ab%2Fcd\n#EXT-X-ENDLIST\n";
  String result=CastPlaylist.rewrite(source,manifest,u->{seen.add(u);return "http://192.168.1.2/secret/"+seen.size();});
  assertEquals(5,seen.size());assertEquals("https://video.example/show/audio.m3u8?token=a",seen.get(0).toString());assertEquals("https://video.example/show/720/main.m3u8?token=b",seen.get(1).toString());assertEquals("https://cdn.example/chunk.html?sig=ab%2Fcd",seen.get(4).toString());
  assertTrue(result.contains("URI=\"http://192.168.1.2/secret/1\""));assertTrue(result.contains("BYTERANGE=\"100@0\""));assertTrue(result.endsWith("#EXT-X-ENDLIST\n"));assertFalse(result.contains("token="));assertFalse(result.contains("sig="));
 }
 @Test public void rejectsNonHttpsCredentialsAndUnexpectedPorts()throws Exception{
  for(String target:new String[]{"http://cdn.example/a.ts","file:///etc/passwd","https://user:pw@cdn.example/a.ts","https://cdn.example:9443/a.ts"}){
   try{CastPlaylist.rewrite(URI.create("https://video.example/master.m3u8"),"#EXTM3U\n"+target,u->"relay");fail(target);}catch(Exception expected){}
  }
 }
 @Test public void handlesUtf8Bom()throws Exception{assertEquals("#EXTM3U\nrelay\n",CastPlaylist.rewrite(URI.create("https://video.example/master.m3u8"),"\uFEFF#EXTM3U\nsegment.ts",u->"relay"));}
 @Test public void refusesHtmlInPlaceOfManifest()throws Exception{
  try{CastPlaylist.rewrite(URI.create("https://video.example/master.m3u8"),"<html>error</html>",URI::toString);fail();}catch(IllegalArgumentException expected){}
 }
}
