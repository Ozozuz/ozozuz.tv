package it.reozoz.streamclean;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Provider requests stay on the phone, preserving exact playlist scope and required headers. */
final class CastHlsSource implements CastHttpServer.Endpoint {
    private final PlaybackSnapshot source;
    private final MediaAllowlist scope=new MediaAllowlist();
    private final Map<URI,String> ids=new HashMap<>();
    private final Map<String,URI> resources=new ConcurrentHashMap<>();
    private CastHttpServer server;
    CastHlsSource(PlaybackSnapshot source){this.source=source;scope.registerRedirect(source.initial(),source.source());scope.registerPlaylist(source.source(),source.manifest());}
    void server(CastHttpServer server){this.server=server;}
    private synchronized String local(URI uri){
        if(!scope.allows(uri.toString())&&!scope.playlist(uri.toString()))throw new IllegalArgumentException("Undeclared HLS URI");
        if(resources.size()>=20000)throw new IllegalArgumentException("Too many HLS resources");
        String id=ids.get(uri);if(id==null){id=Integer.toString(ids.size(),36)+(scope.playlist(uri.toString())?".m3u8":".bin");ids.put(uri,id);resources.put(id,uri);}return server.url(id);
    }
    private CastHttpServer.Response playlist(URI uri,String text,boolean head)throws Exception{
        byte[] body=CastPlaylist.rewrite(uri,text,this::local).getBytes(StandardCharsets.UTF_8);
        return new CastHttpServer.Response(200,"application/vnd.apple.mpegurl",body.length,Collections.emptyMap(),head?null:new ByteArrayInputStream(body));
    }
    public CastHttpServer.Response get(String resource,String range,boolean head)throws Exception{
        if(resource.equals("master.m3u8"))return playlist(source.source(),source.manifest(),head);
        URI uri=resources.get(resource);if(uri==null)return new CastHttpServer.Response(404,"text/plain",0,Collections.emptyMap(),null);
        if(scope.playlist(uri.toString())){PlaylistHttp.Response r=PlaylistHttp.get(uri,source.headers(),scope);if(r.status()!=200)throw new IOException("Playlist unavailable");return playlist(r.source(),new String(r.body(),StandardCharsets.UTF_8),head);}
        if(!scope.allows(uri.toString()))throw new IOException("Undeclared media resource");
        HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();c.setRequestMethod(head?"HEAD":"GET");c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setUseCaches(false);
        c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Referer","https://vixcloud.cc/");
        for(Map.Entry<String,String> h:source.headers().entrySet()){
            String key=h.getKey().toLowerCase(Locale.ROOT);if(key.equals("user-agent")||key.equals("origin"))c.setRequestProperty(h.getKey(),h.getValue());
            if((key.equals("cookie")||key.equals("referer"))&&uri.getHost().equals(source.initial().getHost()))c.setRequestProperty(h.getKey(),h.getValue());
        }
        if(range!=null){if(!range.matches("bytes=[0-9]+-[0-9]*")){c.disconnect();throw new IllegalArgumentException("Invalid range");}c.setRequestProperty("Range",range);}
        try{
            int status=c.getResponseCode();if(status!=200&&status!=206)throw new IOException("Media unavailable");
            long length=c.getContentLengthLong();if(length>64L*1024*1024)throw new IOException("HLS fragment too large");
            Map<String,String> headers=new HashMap<>();String contentRange=c.getHeaderField("Content-Range");if(status==206){if(contentRange==null||!contentRange.matches("bytes [0-9]+-[0-9]+/[0-9*]+"))throw new IOException("Invalid upstream range");headers.put("Content-Range",contentRange);}headers.put("Accept-Ranges","bytes");
            if(head){c.disconnect();return new CastHttpServer.Response(status,"application/octet-stream",length,headers,null);}
            InputStream body=new FilterInputStream(c.getInputStream()){
                long total;public int read(byte[] b,int o,int n)throws IOException{int got=super.read(b,o,n);if(got>0&&((total+=got)>64L*1024*1024))throw new IOException("Fragment limit");return got;}
                public void close()throws IOException{try{super.close();}finally{c.disconnect();}}
            };
            return new CastHttpServer.Response(status,"application/octet-stream",length,headers,body);
        }catch(Exception e){c.disconnect();throw e;}
    }
}
