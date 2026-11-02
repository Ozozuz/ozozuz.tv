package it.reozoz.streamclean;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Domain migration is allowed only while resolving and validating the catalogue home. */
final class DomainResolver {
    record Response(int status,String location,String html) {}
    record Resolved(URI origin,HomeParser.Home home) {}
    interface Fetcher { Response fetch(URI uri) throws Exception; }
    static final URI DEFAULT_ORIGIN=URI.create("https://streamingcommunityz.boo/");
    private static final int MAX_REDIRECTS=5;
    private static final Pattern REFRESH=Pattern.compile("(?is)^\\s*\\d+(?:\\.\\d+)?\\s*;\\s*url\\s*=\\s*(.+?)\\s*$");
    // Recognize only a complete static navigation statement, never evaluate site JavaScript.
    private static final Pattern LOCATION=Pattern.compile("(?s)^\\s*(?:(?:window|document)\\.)?location(?:(?:\\.href)?\\s*=\\s*(['\"])([^'\"\\r\\n]+)\\1|\\.(?:replace|assign)\\(\\s*(['\"])([^'\"\\r\\n]+)\\3\\s*\\))\\s*;?\\s*$");

    static void validate(URI uri) throws IOException {
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null
                ||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IOException("Redirect del catalogo non valido: è richiesto HTTPS.");
    }
    static Resolved resolve(URI start,Fetcher fetcher) throws Exception {
        URI current=HomeParser.origin(start.toString());Set<URI> visited=new HashSet<>();
        for(int step=0;step<=MAX_REDIRECTS;step++){
            validate(current);
            if(!visited.add(current.normalize()))throw new IOException("Ciclo di redirect del catalogo.");
            Response response=fetcher.fetch(current);String destination=null;
            if(response.status()==301||response.status()==302||response.status()==303||response.status()==307||response.status()==308){
                destination=response.location();
                if(destination==null||destination.trim().isEmpty())throw new IOException("Redirect privo di destinazione.");
            }else{
                if(response.status()!=200)throw new IOException("HTTP "+response.status()+": sito non disponibile o accesso bloccato.");
                URI origin=HomeParser.origin(current.toString());
                try{return new Resolved(origin,HomeParser.parse(response.html(),origin));}
                catch(Exception invalidHome){
                    destination=htmlRedirect(response.html());
                    if(destination==null)throw invalidHome;
                }
            }
            current=current.resolve(destination.trim());
            // Fragments are browser-only and must not bypass loop detection.
            if(current.getRawFragment()!=null)current=URI.create(current.toString().split("#",2)[0]);
        }
        throw new IOException("Troppi redirect del catalogo.");
    }
    private static String htmlRedirect(String html){
        Document document=Jsoup.parse(html);
        for(Element meta:document.select("meta[http-equiv][content]")){
            if(!"refresh".equalsIgnoreCase(meta.attr("http-equiv")))continue;
            Matcher match=REFRESH.matcher(meta.attr("content"));
            if(match.matches()){
                String target=match.group(1);
                if(target.length()>1&&((target.startsWith("\"")&&target.endsWith("\""))||(target.startsWith("'")&&target.endsWith("'"))))target=target.substring(1,target.length()-1);
                return target;
            }
        }
        for(Element script:document.select("script:not([src])")){
            Matcher match=LOCATION.matcher(script.data());
            if(match.matches())return match.group(2)!=null?match.group(2):match.group(4);
        }
        return null;
    }
}
