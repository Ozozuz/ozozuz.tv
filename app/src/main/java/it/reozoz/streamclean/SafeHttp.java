package it.reozoz.streamclean;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class SafeHttp {
    static byte[] get(URI uri, Set<String> allowedHosts, int limit, boolean image) throws IOException {
        return get(uri,allowedHosts,limit,image,Collections.emptyMap());
    }
    static byte[] get(URI uri, Set<String> allowedHosts, int limit, boolean image,Map<String,String> headers) throws IOException {
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
            for(Map.Entry<String,String> h:headers.entrySet())if(Arrays.asList("user-agent","referer","origin").contains(h.getKey().toLowerCase(Locale.ROOT)))c.setRequestProperty(h.getKey(),h.getValue());
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
    static DomainResolver.Resolved resolveHome(URI origin) throws Exception {
        return DomainResolver.resolve(origin,SafeHttp::domainPage);
    }
    private static DomainResolver.Response domainPage(URI uri) throws IOException {
        DomainResolver.validate(uri);
        okhttp3.Request request=new okhttp3.Request.Builder().url(uri.toString())
            .header("User-Agent","Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .header("Accept","text/html,application/xhtml+xml").header("Cache-Control","no-cache").build();
        try(okhttp3.Response response=CatalogClient.INSTANCE.newCall(request).execute()){
            int status=response.code();
            if(status!=200)return new DomainResolver.Response(status,response.header("Location"),"");
            okhttp3.ResponseBody body=response.body();if(body==null)throw new IOException("Risposta vuota.");
            String type=Optional.ofNullable(response.header("Content-Type")).orElse("").toLowerCase(Locale.ROOT).split(";")[0].trim();
            if(!type.equals("text/html")&&!type.equals("application/xhtml+xml"))throw new IOException("Tipo di contenuto inatteso.");
            int limit=4*1024*1024;
            if(body.contentLength()>limit)throw new IOException("Risposta troppo grande.");
            try(InputStream in=body.byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=in.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("Risposta troppo grande.");out.write(buffer,0,count);}
                return new DomainResolver.Response(status,null,out.toString(StandardCharsets.UTF_8.name()));
            }
        }
    }
    private static final class CatalogClient {
        static final okhttp3.OkHttpClient INSTANCE=create();
        private static okhttp3.OkHttpClient create(){
            okhttp3.OkHttpClient bootstrap=new okhttp3.OkHttpClient.Builder()
                .connectTimeout(12,java.util.concurrent.TimeUnit.SECONDS).readTimeout(18,java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(25,java.util.concurrent.TimeUnit.SECONDS).build();
            try{
                okhttp3.Dns secure=new okhttp3.dnsoverhttps.DnsOverHttps.Builder().client(bootstrap)
                    .url(okhttp3.HttpUrl.get("https://dns.google/dns-query")).includeIPv6(false)
                    .bootstrapDnsHosts(Arrays.asList(InetAddress.getByAddress(new byte[]{8,8,8,8}),InetAddress.getByAddress(new byte[]{8,8,4,4}))).build();
                return bootstrap.newBuilder().dns(new CatalogDns(okhttp3.Dns.SYSTEM,secure))
                    .followRedirects(false).followSslRedirects(false).build();
            }catch(UnknownHostException impossible){throw new IllegalStateException(impossible);}
        }
    }
}
