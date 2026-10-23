package it.reozoz.streamclean;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Forwards playlist responses unchanged, including the server's CORS headers. */
final class PlaylistHttp {
    record Response(URI source,int status,String reason,String mime,Map<String,String> headers,byte[] body) {}
    static Response get(URI uri,Map<String,String> requestHeaders,MediaAllowlist media) throws Exception {
        URI initial=uri;
        for(int redirect=0;redirect<4;redirect++) {
            if(!media.playlist(uri.toString()))throw new IOException("Playlist non autorizzata.");
            HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setUseCaches(false);
            for(Map.Entry<String,String> h:requestHeaders.entrySet()) {
                String key=h.getKey().toLowerCase(Locale.ROOT);
                if(Arrays.asList("accept","origin","user-agent","range").contains(key))c.setRequestProperty(h.getKey(),h.getValue());
                if(key.equals("referer"))c.setRequestProperty("Referer",initial.getHost().equals(uri.getHost())?h.getValue():"https://vixcloud.cc/");
                if(key.equals("cookie") && initial.getHost().equals(uri.getHost()))c.setRequestProperty("Cookie",h.getValue());
            }
            c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("Accept-Encoding","identity");
            try {
                int status=c.getResponseCode();
                if(Arrays.asList(301,302,303,307,308).contains(status)) {
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("Redirect playlist non valido.");
                    URI next=uri.resolve(location);media.registerRedirect(uri,next);uri=next;continue;
                }
                Map<String,String> headers=new HashMap<>();
                for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet()) {
                    if(h.getKey()==null||h.getValue()==null)continue;
                    if(Arrays.asList("transfer-encoding","content-length","connection").contains(h.getKey().toLowerCase(Locale.ROOT)))continue;
                    headers.put(h.getKey(),String.join(", ",h.getValue()));
                }
                String type=Optional.ofNullable(c.getContentType()).orElse("application/vnd.apple.mpegurl").split(";")[0];
                InputStream stream=status>=400?c.getErrorStream():c.getInputStream();byte[] body;
                if(stream!=null && "gzip".equalsIgnoreCase(c.getContentEncoding())) {
                    stream=new java.util.zip.GZIPInputStream(stream);
                    headers.keySet().removeIf(key->key.equalsIgnoreCase("content-encoding"));
                }
                try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    if(in!=null){byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>2*1024*1024)throw new IOException("Playlist troppo grande.");out.write(b,0,n);}}
                    body=out.toByteArray();
                }
                if(status==200)media.registerPlaylist(uri,new String(body,StandardCharsets.UTF_8));
                String reason=c.getResponseMessage();
                return new Response(uri,status,reason==null||reason.trim().isEmpty()?"Response":reason,type,headers,body);
            }finally{c.disconnect();}
        }
        throw new IOException("Troppi redirect della playlist.");
    }
}
