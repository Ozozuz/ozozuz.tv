package it.reozoz.streamclean;
import java.net.URI;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FavoriteCodecTest {
    private final URI origin=URI.create("https://new.example/");
    @Test public void preservesTitleAndRebasesPosterAcrossDomains()throws Exception{
        HomeParser.Title t=new HomeParser.Title(4800,"South Park","tv","8.5","Plot","https://cdn.old.example/images/poster.webp","south-park");
        String encoded=FavoriteCodec.encode(Collections.singleton(t));assertFalse(encoded.contains("old.example"));
        var result=FavoriteCodec.decode(encoded,origin).get(0);assertEquals(4800,result.id());assertEquals("south-park",result.slug());assertEquals("https://cdn.new.example/images/poster.webp",result.poster());assertEquals("8.5",result.score());
    }
    @Test public void ignoresMalformedStorage(){assertTrue(FavoriteCodec.decode("bad json",origin).isEmpty());}
    @Test public void rejectsUnsafePosterNames(){assertEquals("",FavoriteCodec.posterName("https://external/x.svg"));assertEquals("",FavoriteCodec.posterName("file:///tmp/<bad>.png"));}
    @Test public void deduplicatesStableIds(){var list=FavoriteCodec.decode("[{id:1,name:'A',type:'tv'},{id:1,name:'B',type:'tv'},{id:2,type:'script'},{id:-1,type:'movie'}]",origin);assertEquals(1,list.size());assertEquals("B",list.get(0).name());}
    @Test public void supportsMovieAndEmptyArtwork()throws Exception{var t=new HomeParser.Title(4,"Film","movie","","","","film");assertEquals("",FavoriteCodec.decode(FavoriteCodec.encode(Collections.singleton(t)),origin).get(0).poster());}
}
