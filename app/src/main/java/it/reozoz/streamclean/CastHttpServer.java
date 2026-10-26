package it.reozoz.streamclean;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived, receiver-only HTTP endpoint. No arbitrary URLs, files or write methods. */
public final class CastHttpServer implements AutoCloseable {
    public record Response(int status,String mime,long length,Map<String,String> headers,InputStream body) implements AutoCloseable {
        public void close()throws IOException{if(body!=null)body.close();}
    }
    public interface Endpoint {Response get(String resource,String range,boolean head)throws Exception;}
    public record Range(long start,long end,boolean partial){
        public long length(){return end-start+1;}
        public static Range parse(String header,long size){
            if(size<=0)throw new IllegalArgumentException("Empty media");if(header==null||header.isEmpty())return new Range(0,size-1,false);
            if(!header.matches("bytes=[0-9]*-[0-9]*"))throw new IllegalArgumentException("Invalid range");
            try{String[] a=header.substring(6).split("-",-1);long start,end;
                if(a[0].isEmpty()){long tail=Long.parseLong(a[1]);if(tail<=0)throw new IllegalArgumentException();start=Math.max(0,size-tail);end=size-1;}
                else{start=Long.parseLong(a[0]);end=a[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(a[1]));}
                if(start<0||start>=size||end<start)throw new IllegalArgumentException();return new Range(start,end,true);
            }catch(Exception e){throw new IllegalArgumentException("Invalid range");}
        }
    }
    private final ServerSocket server;
    private final InetAddress receiver;
    private final Endpoint endpoint;
    private final String token;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,6,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"cast-http");t.setDaemon(true);return t;});
    private volatile boolean closed;
    public CastHttpServer(InetAddress address,InetAddress receiver,Endpoint endpoint)throws IOException{
        this.receiver=receiver;this.endpoint=endpoint;byte[] random=new byte[32];new SecureRandom().nextBytes(random);StringBuilder id=new StringBuilder();for(byte b:random)id.append(String.format(Locale.ROOT,"%02x",b&255));token=id.toString();
        server=new ServerSocket();server.bind(new InetSocketAddress(address,0),8);Thread accept=new Thread(this::accept,"cast-listener");accept.setDaemon(true);accept.start();
    }
    public String url(String resource){return "http://"+server.getInetAddress().getHostAddress()+":"+server.getLocalPort()+"/"+token+"/"+resource;}
    private void accept(){while(!closed)try{
        Socket socket=server.accept();if(!socket.getInetAddress().equals(receiver)){socket.close();continue;}
        socket.setSoTimeout(8000);sockets.add(socket);try{clients.execute(()->serve(socket));}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}
    }catch(IOException e){if(!closed)close();}}
    private static String line(InputStream in,int maximum)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();for(int n;(n=in.read())!=-1;){if(n=='\n')return b.toString("US-ASCII").replace("\r","");if(b.size()>=maximum)throw new IOException("Header too large");b.write(n);}throw new EOFException();
    }
    private void serve(Socket socket){
        try(Socket s=socket){InputStream in=new BufferedInputStream(s.getInputStream());String[] request=line(in,4096).split(" ");if(request.length!=3)return;
            Map<String,String> headers=new HashMap<>();int total=0;for(int i=0;;i++){String l=line(in,8192);total+=l.length();if(total>16384||i>64)return;if(l.isEmpty())break;int c=l.indexOf(':');if(c>0)headers.put(l.substring(0,c).toLowerCase(Locale.ROOT),l.substring(c+1).trim());}
            String method=request[0];boolean head=method.equals("HEAD");URI target=URI.create(request[1]);String path=target.getRawPath(),prefix="/"+token+"/";
            if(target.isAbsolute()||path==null||!path.startsWith(prefix)){write(s.getOutputStream(),error(404),head);return;}
            if(method.equals("OPTIONS")){write(s.getOutputStream(),new Response(204,"text/plain",0,Collections.emptyMap(),null),true);return;}
            if(!method.equals("GET")&&!head){write(s.getOutputStream(),error(405),head);return;}
            Response response;
            try{response=endpoint.get(path.substring(prefix.length()),headers.get("range"),head);}
            catch(IllegalArgumentException e){response=error(416);}catch(Exception e){response=error(502);}
            try(Response r=response){write(s.getOutputStream(),r,head);}
        }catch(Exception ignored){}finally{sockets.remove(socket);}
    }
    private static Response error(int status){return new Response(status,"text/plain",0,Collections.emptyMap(),null);}
    private static void write(OutputStream out,Response response,boolean head)throws IOException{
        String reason=response.status()==200?"OK":response.status()==206?"Partial Content":response.status()==204?"No Content":"Request Failed";
        StringBuilder h=new StringBuilder("HTTP/1.1 "+response.status()+" "+reason+"\r\nConnection: close\r\nCache-Control: no-store\r\nContent-Type: "+response.mime()+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: Range, Content-Type\r\nAccess-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\nAccess-Control-Allow-Private-Network: true\r\n");
        if(response.length()>=0)h.append("Content-Length: ").append(response.length()).append("\r\n");
        for(Map.Entry<String,String> e:response.headers().entrySet())h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");h.append("\r\n");out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        if(!head&&response.body()!=null){byte[] b=new byte[32768];long left=response.length();while(left!=0){int n=response.body().read(b,0,left<0?b.length:(int)Math.min(left,b.length));if(n<0)break;out.write(b,0,n);if(left>0)left-=n;}}
        out.flush();
    }
    public void close(){if(closed)return;closed=true;try{server.close();}catch(IOException ignored){}for(Socket s:sockets)try{s.close();}catch(IOException ignored){}sockets.clear();clients.shutdownNow();}
}
