package it.reozoz.streamclean;
import org.junit.Test;
import static org.junit.Assert.*;
public class DownloadMetadataTest {
    @Test public void migratesLegacyFilenamesWithoutNetwork(){var m=DownloadMetadata.parse("{}","South Park - S11E2 - Cartman fa schifo.mp4",4800,28567);assertEquals("South Park",m.title());assertEquals(11,m.season());assertEquals(2,m.number());assertEquals("Cartman fa schifo",m.name());}
    @Test public void usesSavedMetadataEvenIfFileRenamed(){var m=DownloadMetadata.parse("{titleName:'Show',season:2,number:4,name:'Episode'}","renamed.mp4",1,2);assertEquals("Show",m.title());assertEquals(2,m.season());assertEquals("Episode",m.name());}
    @Test public void toleratesMissingFileAndMetadata(){var m=DownloadMetadata.parse("broken",null,4,5);assertEquals("Titolo 4",m.title());assertEquals(-1,m.season());assertEquals("Episodio 5",m.name());}
    @Test public void recognizesSpecials(){assertEquals(0,DownloadMetadata.parse("{}","Show - S0E1 - Extra.mp4",1,2).season());}
    @Test public void retainsUnknownLegacyFilename(){var m=DownloadMetadata.parse("{}","manual.mp4",1,2);assertEquals("manual.mp4",m.name());assertEquals(-1,m.season());}
}
