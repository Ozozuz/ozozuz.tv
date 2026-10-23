package it.reozoz.streamclean;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class SafeHttp {
    static byte[] get(URI uri, Set<String> allowedHosts, int limit, boolean image) throws IOException {
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))
                throw new IOException("Destinazione non consentita. Se il dominio è cambiato, aggiorna l’URL manualmente.");
            HttpsURLConnection c = (HttpsURLConnection) uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false); c.setRequestProperty("Cache-Control", "no-cache");
            c.setConnectTimeout(12000); c.setReadTimeout(18000);
            c.setRequestProperty("User-Agent", "StreamClean/0.3 Android");
            c.setRequestProperty("Accept", image ? "image/webp,image/png,image/jpeg" : "text/html");
            try {
                int status = c.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect privo di destinazione.");
                    uri = uri.resolve(location); continue;
                }
                if (status != 200) throw new IOException("HTTP " + status + ": sito non disponibile o accesso bloccato.");
                String type = Optional.ofNullable(c.getContentType()).orElse("").toLowerCase(Locale.ROOT).split(";")[0];
                if (image ? !Arrays.asList("image/webp", "image/png", "image/jpeg").contains(type)
                        : !(type.equals("text/html") || type.equals("application/xhtml+xml")))
                    throw new IOException("Tipo di contenuto inatteso.");
                if (c.getContentLengthLong() > limit) throw new IOException("Risposta troppo grande.");
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n, total = 0;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > limit) throw new IOException("Risposta troppo grande.");
                        out.write(buf, 0, n);
                    }
                    return out.toByteArray();
                }
            } finally { c.disconnect(); }
        }
        throw new IOException("Troppi redirect.");
    }
    static String page(URI uri, URI origin) throws Exception {
        return new String(get(uri,Collections.singleton(origin.getHost()),4*1024*1024,false),StandardCharsets.UTF_8);
    }
    static URI detailUrl(URI origin,long id,String slug,int season) throws Exception {
        if(id<=0)throw new IllegalArgumentException("ID titolo non valido.");
        String path="it/titles/"+id+"-"+URLEncoder.encode(slug,"UTF-8").replace("+","%20");
        if(season>=0)path+="/season-"+season;
        return origin.resolve(path);
    }
    static TitleParser.Detail detail(URI origin,long id,String slug,int season) throws Exception {
        TitleParser.Detail d=TitleParser.parse(page(detailUrl(origin,id,slug,season),origin));
        if(d.id()!=id || (season>=0 && d.selectedSeason()!=season))throw new IllegalStateException("Il sito ha restituito una stagione o un titolo inatteso.");
        return d;
    }
    static TitleParser.Player player(URI origin,long titleId,long episodeId) throws Exception {
        URI watch=origin.resolve("it/watch/"+titleId+(episodeId>0?"?e="+episodeId:""));
        URI frame=TitleParser.watchFrame(page(watch,origin),origin,titleId,episodeId);
        URI fresh=URI.create(frame.toString()+(frame.getRawQuery()==null?"?":"&")+"_sc="+System.currentTimeMillis());
        return TitleParser.player(page(fresh,origin),fresh);
    }
    static HomeParser.Home search(URI origin, String query) throws Exception {
        URI endpoint = origin.resolve("it/search?q=" + URLEncoder.encode(query, "UTF-8"));
        return HomeParser.parseSearch(new String(get(endpoint, Collections.singleton(origin.getHost()), 4 * 1024 * 1024, false), StandardCharsets.UTF_8), origin);
    }
    static HomeParser.Home home(URI origin) throws Exception {
        return HomeParser.parse(new String(get(origin, Collections.singleton(origin.getHost()), 4 * 1024 * 1024, false), StandardCharsets.UTF_8), origin);
    }
}
