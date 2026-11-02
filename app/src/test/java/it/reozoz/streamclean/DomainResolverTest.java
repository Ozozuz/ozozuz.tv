package it.reozoz.streamclean;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DomainResolverTest {
    private static final URI OLD=URI.create("https://old.invalid/");
    private static final String HOME="<div data-page='{\"props\":{\"sliders\":[]}}'></div>";
    private static DomainResolver.Response home(){return new DomainResolver.Response(200,null,HOME);}
    @Test public void unchangedHomeNeedsOnlyOneRequest()throws Exception{
        List<URI> visited=new ArrayList<>();
        var result=DomainResolver.resolve(OLD,uri->{visited.add(uri);return home();});
        assertEquals(OLD,result.origin());assertEquals(List.of(OLD),visited);
    }
    @Test public void followsEveryHttpRedirectAndUsesFinalHostForImages()throws Exception{
        for(int code:new int[]{301,302,303,307,308}){
            List<URI> visited=new ArrayList<>();
            var result=DomainResolver.resolve(OLD,uri->{visited.add(uri);return uri.equals(OLD)?new DomainResolver.Response(code,"https://new.invalid/it",""):home();});
            assertEquals(URI.create("https://new.invalid/"),result.origin());
            assertEquals("cdn.new.invalid",result.home().cdnHost());assertEquals(2,visited.size());
        }
    }
    @Test public void followsRelativeAndProtocolRelativeRedirects()throws Exception{
        var result=DomainResolver.resolve(OLD,uri->{
            if(uri.equals(OLD))return new DomainResolver.Response(302,"/redirect","");
            if(uri.getPath().equals("/redirect"))return new DomainResolver.Response(307,"//new.invalid/it","");
            return home();
        });
        assertEquals(URI.create("https://new.invalid/"),result.origin());
    }
    @Test public void followsBrowserMetaRefresh()throws Exception{
        var result=DomainResolver.resolve(OLD,uri->uri.equals(OLD)?new DomainResolver.Response(200,null,"<meta http-equiv='Refresh' content='0; URL=https://new.invalid/'>"):home());
        assertEquals(URI.create("https://new.invalid/"),result.origin());
    }
    @Test public void followsOnlyStaticJavascriptNavigation()throws Exception{
        for(String script:new String[]{"window.location.replace('https://new.invalid/');","window.location = 'https://new.invalid/';","location.assign(\"https://new.invalid/\")","window.location.href = 'https://new.invalid/';"}){
            var result=DomainResolver.resolve(OLD,uri->uri.equals(OLD)?new DomainResolver.Response(200,null,"<script>"+script+"</script>"):home());
            assertEquals(URI.create("https://new.invalid/"),result.origin());
        }
        assertThrows(Exception.class,()->DomainResolver.resolve(OLD,uri->new DomainResolver.Response(200,null,"<script>ad(); window.location.replace('https://ads.invalid/');</script>")));
    }
    @Test public void validCatalogTakesPrecedenceOverHtmlNavigation()throws Exception{
        var result=DomainResolver.resolve(OLD,uri->new DomainResolver.Response(200,null,HOME+"<meta http-equiv='refresh' content='0;url=https://ads.invalid/'>"));
        assertEquals(OLD,result.origin());
    }
    @Test public void rejectsDowngradeCredentialsAndCustomPortsBeforeFetching()throws Exception{
        for(String target:new String[]{"http://new.invalid/","https://user@new.invalid/","https://new.invalid:8443/","javascript:alert(1)","https:///broken"}){
            List<URI> visited=new ArrayList<>();
            assertThrows(Exception.class,()->DomainResolver.resolve(OLD,uri->{visited.add(uri);return new DomainResolver.Response(302,target,"");}));
            assertEquals(List.of(OLD),visited);
        }
    }
    @Test public void rejectsLoopsIncludingFragmentOnlyRedirects()throws Exception{
        assertThrows(IOException.class,()->DomainResolver.resolve(OLD,uri->new DomainResolver.Response(302,"#loop","")));
    }
    @Test public void capsRedirectChain()throws Exception{
        List<URI> visited=new ArrayList<>();
        assertThrows(IOException.class,()->DomainResolver.resolve(OLD,uri->{visited.add(uri);return new DomainResolver.Response(302,"https://next"+visited.size()+".invalid/","");}));
        assertEquals(6,visited.size());
    }
    @Test public void rejectsMissingLocationChallengeAndNonCatalogFinalPage(){
        assertThrows(IOException.class,()->DomainResolver.resolve(OLD,uri->new DomainResolver.Response(302,null,"")));
        assertThrows(IOException.class,()->DomainResolver.resolve(OLD,uri->new DomainResolver.Response(403,null,"challenge")));
        assertThrows(Exception.class,()->DomainResolver.resolve(OLD,uri->new DomainResolver.Response(200,null,"<h1>Parked domain</h1>")));
    }
}
