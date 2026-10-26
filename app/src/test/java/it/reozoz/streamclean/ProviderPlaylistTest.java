package it.reozoz.streamclean;
import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProviderPlaylistTest {
    private final URI embed=URI.create("https://vixcloud.cc/embed/123?canPlayFHD=1&scz=1&lang=it");
    @Test public void matchesLiteralProviderConfigAndOmitsEmptyParams()throws Exception{
        String html="<script>window.masterPlaylist = {params:{'token':'a+b/=',expires:'42',asn:'',zero:0,disabled:false},url:'https://vixcloud.cc/playlist/123'}; window.ignored=evil();</script>";
        String uri=ProviderPlaylist.parse(html,embed).toString();assertTrue(uri.startsWith("https://vixcloud.cc/playlist/123?"));assertTrue(uri.contains("token=a%2Bb%2F%3D"));assertTrue(uri.contains("expires=42"));assertTrue(uri.endsWith("h=1&scz=1&lang=it"));assertFalse(uri.contains("asn="));assertFalse(uri.contains("zero="));assertFalse(uri.contains("disabled="));
    }
    @Test public void defaultsLanguageAndRetainsSignedQuery()throws Exception{String s=ProviderPlaylist.parse("window.masterPlaylist={url:'https://vixcloud.cc/playlist/123?ub=1',params:{token:'x'}}",URI.create("https://vixcloud.cc/embed/123")).toString();assertTrue(s.startsWith("https://vixcloud.cc/playlist/123?ub=1&"));assertTrue(s.endsWith("lang=en"));}
    @Test(expected=Exception.class) public void rejectsDifferentVideo()throws Exception{ProviderPlaylist.parse("window.masterPlaylist={url:'https://vixcloud.cc/playlist/456',params:{}}",embed);}
    @Test(expected=Exception.class) public void rejectsUntrustedHost()throws Exception{ProviderPlaylist.parse("window.masterPlaylist={url:'https://ads.example/playlist/123',params:{}}",embed);}
    @Test(expected=Exception.class) public void rejectsExecutableValue()throws Exception{ProviderPlaylist.parse("window.masterPlaylist={url:evil(),params:{}}",embed);}
    @Test(expected=Exception.class) public void rejectsMissingConfig()throws Exception{ProviderPlaylist.parse("<script>alert('x')</script>",embed);}
    @Test(expected=Exception.class) public void rejectsUnsafeEmbed()throws Exception{ProviderPlaylist.parse("window.masterPlaylist={url:'https://vixcloud.cc/playlist/123',params:{}}",URI.create("https://user@vixcloud.cc/embed/123"));}
}
