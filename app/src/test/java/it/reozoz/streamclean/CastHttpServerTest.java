package it.reozoz.streamclean;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
public class CastHttpServerTest {
 @Test public void handlesWholeExplicitOpenAndSuffixRanges(){
  assertEquals(new CastHttpServer.Range(0,9,false),CastHttpServer.Range.parse(null,10));
  assertEquals(new CastHttpServer.Range(2,5,true),CastHttpServer.Range.parse("bytes=2-5",10));
  assertEquals(new CastHttpServer.Range(7,9,true),CastHttpServer.Range.parse("bytes=7-",10));
  assertEquals(new CastHttpServer.Range(7,9,true),CastHttpServer.Range.parse("bytes=-3",10));
  assertEquals(new CastHttpServer.Range(0,9,true),CastHttpServer.Range.parse("bytes=-999",10));
  assertEquals(8,CastHttpServer.Range.parse("bytes=2-999",10).length());
 }
 @Test public void rejectsInvalidAndUnsatisfiableRanges(){
  for(String value:new String[]{"bytes=10-","bytes=5-2","bytes=-0","bytes=-","bytes=1-2,4-5","items=1-3","bytes=999999999999999999999-"}){
   try{CastHttpServer.Range.parse(value,10);fail(value);}catch(IllegalArgumentException expected){}
  }
 }
 private static String request(String url,String method,String headers)throws Exception{
  URI uri=URI.create(url);try(Socket s=new Socket(uri.getHost(),uri.getPort())){s.setSoTimeout(3000);s.getOutputStream().write((method+" "+uri.getRawPath()+" HTTP/1.1\r\nHost: local\r\n"+headers+"\r\n").getBytes(StandardCharsets.US_ASCII));return new String(s.getInputStream().readAllBytes(),StandardCharsets.US_ASCII);}
 }
 @Test public void servesRangeAndHeadClosesBodiesAndRejectsUnscopedRequests()throws Exception{
  InetAddress loop=InetAddress.getByName("127.0.0.1");AtomicInteger opened=new AtomicInteger(),closed=new AtomicInteger();byte[] media="0123456789".getBytes(StandardCharsets.US_ASCII);
  try(CastHttpServer server=new CastHttpServer(loop,loop,(resource,range,head)->{
   opened.incrementAndGet();if(!resource.equals("video.mp4"))return new CastHttpServer.Response(404,"text/plain",0,Map.of(),null);
   CastHttpServer.Range selected=CastHttpServer.Range.parse(range,media.length);
   InputStream in=head?null:new ByteArrayInputStream(Arrays.copyOfRange(media,(int)selected.start(),media.length)){public void close(){closed.incrementAndGet();}};
   return new CastHttpServer.Response(selected.partial()?206:200,"video/mp4",selected.length(),selected.partial()?Map.of("Content-Range","bytes "+selected.start()+"-"+selected.end()+"/10"):Map.of(),in);
  })){
   String url=server.url("video.mp4"),reply=request(url,"GET","");assertTrue(reply.startsWith("HTTP/1.1 200"));assertTrue(reply.endsWith("0123456789"));assertTrue(reply.contains("Access-Control-Allow-Origin: *"));
   reply=request(url,"GET","Range: bytes=2-5\r\n");assertTrue(reply.startsWith("HTTP/1.1 206"));assertTrue(reply.contains("Content-Range: bytes 2-5/10"));assertTrue(reply.endsWith("\r\n\r\n2345"));
   reply=request(url,"HEAD","");assertTrue(reply.contains("Content-Length: 10"));assertTrue(reply.endsWith("\r\n\r\n"));assertEquals(2,closed.get());
   int before=opened.get();assertTrue(request(url,"POST","").startsWith("HTTP/1.1 405"));
   URI u=URI.create(url);assertTrue(request("http://"+u.getAuthority()+"/wrong/video.mp4","GET","").startsWith("HTTP/1.1 404"));assertEquals(before,opened.get());
   assertTrue(request(url,"OPTIONS","").startsWith("HTTP/1.1 204"));assertEquals(before,opened.get());
   assertTrue(request(url,"GET","Range: bytes=99-\r\n").startsWith("HTTP/1.1 416"));
  }
 }
 @Test public void disallowsDifferentReceiverAndClosesListeningSocket()throws Exception{
  InetAddress loop=InetAddress.getByName("127.0.0.1");AtomicInteger opened=new AtomicInteger();CastHttpServer server=new CastHttpServer(loop,InetAddress.getByName("127.0.0.2"),(p,r,h)->{opened.incrementAndGet();return null;});
  URI url=URI.create(server.url("video.mp4"));
  try(Socket s=new Socket(url.getHost(),url.getPort())){s.setSoTimeout(3000);assertEquals(-1,s.getInputStream().read());}finally{server.close();}
  assertEquals(0,opened.get());try(Socket ignored=new Socket(url.getHost(),url.getPort())){fail();}catch(ConnectException expected){}
 }
}
