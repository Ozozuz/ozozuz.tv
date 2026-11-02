package it.reozoz.streamclean;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class PlaybackHistoryTest {
    private PlaybackHistory history(){return new PlaybackHistory(RuntimeEnvironment.getApplication());}
    @Test public void latestChronologicalOpenWinsEvenWhenGoingBackwards(){
        history().record(42,17,1709);history().record(42,3,301);
        assertEquals(new PlaybackHistory.Entry(3,301),history().last(42));
    }
    @Test public void persistsAcrossInstancesByTitleAndPreservesWatchedFlags(){
        var watched=new WatchedEpisodes(RuntimeEnvironment.getApplication());watched.set(42,1709,true);
        history().record(42,17,1709);history().record(99,2,202);
        assertEquals(new PlaybackHistory.Entry(17,1709),history().last(42));
        assertEquals(new PlaybackHistory.Entry(2,202),history().last(99));assertTrue(watched.contains(42,1709));
    }
    @Test public void missingAndInvalidEntriesDoNotReplaceLastPlayback(){
        assertNull(history().last(42));history().record(42,0,100);
        history().record(42,-1,200);history().record(42,1,0);history().record(0,1,100);
        assertEquals(new PlaybackHistory.Entry(0,100),history().last(42));assertNull(history().last(0));
    }
    @Test public void restoresByEpisodeIdAndHandlesRemovedSeasonsAndEpisodes(){
        var detail=new TitleParser.Detail(42,"south-park","South Park","","tv",List.of(new TitleParser.Season(17,"",2)),17,
            List.of(new TitleParser.Episode(1708,8,"","",true,""),new TitleParser.Episode(1709,9,"","",true,"")),false,"");
        var entry=new PlaybackHistory.Entry(17,1709);
        assertTrue(entry.hasSeason(detail));assertEquals(1,entry.indexIn(detail));
        assertFalse(new PlaybackHistory.Entry(18,1801).hasSeason(detail));
        assertEquals(-1,new PlaybackHistory.Entry(17,1710).indexIn(detail));
    }
}
