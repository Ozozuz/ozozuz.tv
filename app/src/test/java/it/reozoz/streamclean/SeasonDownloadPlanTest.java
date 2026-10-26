package it.reozoz.streamclean;
import java.net.URI;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeasonDownloadPlanTest {
    @Test public void roundTripsPendingIdentityWithoutTokens()throws Exception{
        var plan=new SeasonDownloadPlan(URI.create("https://catalog.example/"),4800,"south-park","South Park",1,Arrays.asList(new SeasonDownloadPlan.Job(4,2,"Two"),new SeasonDownloadPlan.Job(5,3,"Three")));
        var saved=SeasonDownloadPlan.decode(plan.encode());assertEquals("season:4800:1",saved.key());assertEquals(2,saved.jobs().size());assertEquals("4800:4",saved.episodeKey(saved.jobs().get(0)));assertFalse(plan.encode().contains("token"));
    }
    @Test public void filtersUnavailableAndDeduplicatesEpisodes(){
        var detail=new TitleParser.Detail(1,"show","Show","","tv",Collections.emptyList(),0,Arrays.asList(new TitleParser.Episode(7,2,"B","",true,""),new TitleParser.Episode(8,1,"A","",false,""),new TitleParser.Episode(7,2,"B","",true,""),new TitleParser.Episode(6,1,"Extra","",true,"")),false,"");
        var plan=SeasonDownloadPlan.create(URI.create("https://catalog.example/"),detail);assertEquals(2,plan.jobs().size());assertEquals(6,plan.jobs().get(0).id());assertEquals(0,plan.season());
    }
    @Test public void sanitizesAndBoundsFilenames(){assertEquals("A_B_C.mp4",SeasonDownloadPlan.filename("A/B:C"));assertEquals(154,SeasonDownloadPlan.filename(String.join("",Collections.nCopies(200,"A"))).length());}
    @Test(expected=Exception.class) public void rejectsDuplicateIds()throws Exception{SeasonDownloadPlan.decode("{origin:'https://catalog.example',title:1,slug:'x',name:'X',season:1,jobs:[{id:2,number:1,name:'A'},{id:2,number:2,name:'B'}]}");}
    @Test(expected=Exception.class) public void rejectsUnsafeOrigin()throws Exception{SeasonDownloadPlan.decode("{origin:'http://catalog.example',title:1,slug:'x',name:'X',season:1,jobs:[]}");}
}
