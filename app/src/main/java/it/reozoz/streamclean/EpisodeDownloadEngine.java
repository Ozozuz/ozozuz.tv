package it.reozoz.streamclean;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.system.*;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.net.ssl.HttpsURLConnection;

/** Downloads bounded VOD tracks, then losslessly remuxes them into an ordinary MP4. */
final class EpisodeDownloadEngine {
    static void cleanupInterrupted(Context context){
        Set<File> parents=new HashSet<>();parents.add(context.getFilesDir());File external=context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);if(external!=null)parents.add(external);
        for(File parent:parents){File[] dirs=parent.listFiles();if(dirs==null)continue;for(File dir:dirs)if(dir.isDirectory()&&dir.getName().matches("episode-[a-fA-F0-9-]{36}")){
            new File(dir,"video.bin").delete();new File(dir,"audio.bin").delete();dir.delete();
        }}
    }
    interface Progress {void update(int percent,String message);}
    private final Context context;
    private final PlaybackSnapshot snapshot;
    private final AtomicBoolean cancelled;
    private final Progress progress;
    private final MediaAllowlist scope=new MediaAllowlist();
    private final Map<URI,byte[]> keys=new HashMap<>();
    private long downloaded;
    private File temp;
    EpisodeDownloadEngine(Context c,PlaybackSnapshot s,AtomicBoolean stop,Progress p){context=c;snapshot=s;cancelled=stop;progress=p;}
    void run(Uri destination)throws Exception{
        File parent=context.getFilesDir();
        temp=new File(parent,"episode-"+UUID.randomUUID());if(!temp.mkdirs())throw new IOException("Spazio temporaneo non disponibile.");
        try(ParcelFileDescriptor output=context.getContentResolver().openFileDescriptor(destination,"rw")){
            if(output==null)throw new IOException("File di destinazione non disponibile.");
            try{Os.lseek(output.getFileDescriptor(),0,OsConstants.SEEK_CUR);}catch(ErrnoException e){throw new IOException("Lo spazio download non consente la creazione del file MP4.");}
            scope.registerRedirect(snapshot.initial(),snapshot.source());scope.registerPlaylist(snapshot.source(),snapshot.manifest());
            HlsDownloadPlan.Selection choice=HlsDownloadPlan.select(snapshot.source(),snapshot.manifest());
            List<HlsDownloadPlan.Part> video=playlist(choice.video());List<HlsDownloadPlan.Part> audio=choice.audio()==null?Collections.emptyList():playlist(choice.audio());
            int total=video.size()+audio.size();File v=new File(temp,"video.bin"),a=new File(temp,"audio.bin");
            track(video,v,0,total);if(!audio.isEmpty())track(audio,a,video.size(),total);
            check();if(temp.getUsableSpace()<downloaded+32L*1024*1024)throw new IOException("Spazio insufficiente per completare il file MP4.");
            progress.update(92,"Preparazione MP4…");mux(output.getFileDescriptor(),v,a.exists()?a:null);
        }finally{File[] files=temp.listFiles();if(files!=null)for(File f:files)f.delete();temp.delete();}
    }
    private List<HlsDownloadPlan.Part> playlist(URI uri)throws Exception{
        if(uri.equals(snapshot.source()))return HlsDownloadPlan.parts(uri,snapshot.manifest());
        PlaylistHttp.Response response=PlaylistHttp.get(uri,snapshot.headers(),scope);
        if(response.status()!=200)throw new IOException("Video HTTP "+response.status()+". Ricarica il player e riprova.");
        return HlsDownloadPlan.parts(response.source(),new String(response.body(),StandardCharsets.UTF_8));
    }
    private void track(List<HlsDownloadPlan.Part> parts,File file,int completed,int total)throws Exception{
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
            for(int i=0;i<parts.size();i++){
                check();if(temp.getUsableSpace()<128L*1024*1024)throw new IOException("Spazio libero insufficiente.");
                HlsDownloadPlan.Part part=parts.get(i);Cipher cipher=null;
                if(part.key()!=null){byte[] key=keys.get(part.key().uri());if(key==null){ByteArrayOutputStream k=new ByteArrayOutputStream();copy(new HlsDownloadPlan.Part(part.key().uri(),0,-1,null),k,null,16);key=k.toByteArray();if(key.length!=16)throw new IOException("Chiave HLS non valida.");keys.put(part.key().uri(),key);}
                    byte[] iv=HlsDownloadPlan.iv(part.key());
                    cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));
                }
                copy(part,out,cipher,64L*1024*1024);
                progress.update((int)(90L*(completed+i+1)/total),"Scaricamento · "+(downloaded/(1024*1024))+" MB");
            }
        }
    }
    private void copy(HlsDownloadPlan.Part part,OutputStream output,Cipher cipher,long limit)throws Exception{
        check();if(!scope.allows(part.uri().toString()))throw new IOException("Risorsa non dichiarata nella playlist.");
        HttpsURLConnection c=(HttpsURLConnection)part.uri().toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setUseCaches(false);
        c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Referer","https://vixcloud.cc/");
        for(Map.Entry<String,String> h:snapshot.headers().entrySet()){
            String k=h.getKey().toLowerCase(Locale.ROOT);if(k.equals("user-agent")||k.equals("origin"))c.setRequestProperty(h.getKey(),h.getValue());
            if((k.equals("cookie")||k.equals("referer"))&&part.uri().getHost().equals(snapshot.initial().getHost()))c.setRequestProperty(h.getKey(),h.getValue());
        }
        if(part.length()>0)c.setRequestProperty("Range","bytes="+part.offset()+"-"+(part.offset()+part.length()-1));
        try{
            int status=c.getResponseCode();if(status!=(part.length()>0?206:200))throw new IOException("Frammento video HTTP "+status+". Ricarica il player e riprova.");
            if(part.length()>0&&!Objects.toString(c.getHeaderField("Content-Range"),"").startsWith("bytes "+part.offset()+"-"+(part.offset()+part.length()-1)+"/"))throw new IOException("Intervallo video inatteso.");
            long expected=c.getContentLengthLong();if(expected>limit)throw new IOException("Frammento video troppo grande.");
            try(Counted raw=new Counted(c.getInputStream());InputStream in=cipher==null?raw:new CipherInputStream(raw,cipher)){
                byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){check();if(raw.count>limit||downloaded+n>4L*1024*1024*1024)throw new IOException("Limite di dimensione del download raggiunto.");output.write(b,0,n);downloaded+=n;}
                if((expected>=0&&raw.count!=expected)||(part.length()>0&&raw.count!=part.length()))throw new IOException("Frammento video incompleto.");
            }
        }finally{c.disconnect();}
    }
    private void mux(FileDescriptor output,File video,File audio)throws Exception{
        List<Track> tracks=new ArrayList<>();MediaMuxer muxer=null;boolean started=false;
        try{
            muxer=new MediaMuxer(output,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            addTracks(video,audio!=null,tracks,muxer);if(audio!=null)addTracks(audio,false,tracks,muxer);
            boolean hasVideo=false,hasAudio=false;for(Track t:tracks){hasVideo|=t.video;hasAudio|=!t.video;}
            if(!hasVideo||!hasAudio)throw new IOException("Traccia video o audio assente: il file non verrà salvato incompleto.");
            muxer.start();started=true;ByteBuffer buffer=ByteBuffer.allocateDirect(8*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(true){check();Track next=null;long time=Long.MAX_VALUE;for(Track t:tracks){long pts=t.extractor.getSampleTime();if(pts>=0&&pts-t.base<time){time=pts-t.base;next=t;}}
                if(next==null)break;buffer.clear();int size=next.extractor.readSampleData(buffer,0);if(size<0){next.extractor.advance();continue;}
                int flags=next.extractor.getSampleFlags();if((flags&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new IOException("Traccia protetta non supportata.");
                info.set(0,size,Math.max(0,time),(flags&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);muxer.writeSampleData(next.output,buffer,info);next.samples++;next.extractor.advance();
            }
            for(Track t:tracks)if(t.samples==0)throw new IOException("Traccia vuota: download incompleto.");
            muxer.stop();started=false;
        }finally{if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}for(Track t:tracks)t.extractor.release();}
    }
    private void addTracks(File file,boolean videoOnly,List<Track> tracks,MediaMuxer muxer)throws Exception{
        MediaExtractor probe=new MediaExtractor();try{probe.setDataSource(file.toString());
            for(int i=0;i<probe.getTrackCount();i++){MediaFormat f=probe.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);boolean video=mime!=null&&mime.startsWith("video/"),audio=mime!=null&&mime.startsWith("audio/");
                if(!video&&!audio||videoOnly&&audio)continue;if(!video&&tracks.stream().anyMatch(t->!t.video))continue;
                if(!Arrays.asList("video/avc","video/hevc","audio/mp4a-latm").contains(mime))throw new IOException("Codec "+mime+" non supportato per l'esportazione MP4.");
                MediaExtractor e=new MediaExtractor();try{e.setDataSource(file.toString());e.selectTrack(i);tracks.add(new Track(e,muxer.addTrack(f),video,Math.max(0,e.getSampleTime())));}catch(Exception failure){e.release();throw failure;}
            }
        }finally{probe.release();}
    }
    private static final class Track{final MediaExtractor extractor;final int output;final boolean video;final long base;long samples;Track(MediaExtractor e,int o,boolean v,long b){extractor=e;output=o;video=v;base=b;}}
    private static final class Counted extends InputStream{final InputStream in;long count;Counted(InputStream i){in=i;}public int read()throws IOException{int n=in.read();if(n>=0)count++;return n;}public int read(byte[] b,int o,int l)throws IOException{int n=in.read(b,o,l);if(n>0)count+=n;return n;}public void close()throws IOException{in.close();}}
    private void check(){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new CancellationException("Download annullato.");}
}
