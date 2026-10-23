package it.reozoz.streamclean;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Finite HLS VOD selection. URLs come exclusively from the supplied manifests. */
public final class HlsDownloadPlan {
    public record Selection(URI video,URI audio) {}
    public record Key(URI uri,String iv,long sequence) {}
    public record Part(URI uri,long offset,long length,Key key) {}
    private static final Pattern ATTR=Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)");
    static Map<String,String> attributes(String line){
        Map<String,String> a=new HashMap<>();Matcher m=ATTR.matcher(line.substring(line.indexOf(':')+1));
        while(m.find()){String v=m.group(2);a.put(m.group(1),v.startsWith("\"")?v.substring(1,v.length()-1):v);}return a;
    }
    static URI uri(URI base,String value)throws IOException{
        try{URI u=base.resolve(value);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new Exception();return u;}
        catch(Exception e){throw new IOException("URL HLS non valido.");}
    }
    public static Selection select(URI base,String text)throws IOException{
        check(text);String[] lines=text.split("\\r?\\n");Map<String,String> pending=null,best=null;URI video=null;long rank=Long.MIN_VALUE;
        List<Map<String,String>> audio=new ArrayList<>();
        for(String raw:lines){String l=raw.trim();if(l.startsWith("#EXT-X-STREAM-INF:")){pending=attributes(l);continue;}
            if(l.startsWith("#EXT-X-MEDIA:")){Map<String,String> a=attributes(l);if("AUDIO".equals(a.get("TYPE"))&&a.containsKey("URI"))audio.add(a);}
            if(!l.isEmpty()&&!l.startsWith("#")&&pending!=null){
                String[] size=pending.getOrDefault("RESOLUTION","0x0").split("x");int h=0;try{h=Integer.parseInt(size[size.length-1]);}catch(Exception ignored){}
                long bandwidth=0;try{bandwidth=Long.parseLong(pending.getOrDefault("BANDWIDTH","0"));}catch(Exception ignored){}
                long score=(h<=720?1_000_000_000_000L:0)+(h<=720?h:10000-h)*1_000_000L+Math.min(bandwidth,999999);
                if(score>rank){rank=score;video=uri(base,l);best=pending;}pending=null;
            }
        }
        if(video==null)return new Selection(base,null);
        URI selectedAudio=null;int audioRank=-1;
        for(Map<String,String> a:audio){if(best!=null&&!Objects.equals(best.get("AUDIO"),a.get("GROUP-ID")))continue;
            String language=a.getOrDefault("LANGUAGE","").toLowerCase(Locale.ROOT),name=a.getOrDefault("NAME","").toLowerCase(Locale.ROOT);
            int score=(language.startsWith("it")||name.contains("italian")?10:0)+("YES".equals(a.get("DEFAULT"))?2:0);
            if(score>audioRank){audioRank=score;selectedAudio=uri(base,a.get("URI"));}
        }
        return new Selection(video,selectedAudio);
    }
    public static List<Part> parts(URI base,String text)throws IOException{
        check(text);if(!text.contains("#EXT-X-ENDLIST"))throw new IOException("Il download richiede una puntata completa, non uno stream live.");
        List<Part> parts=new ArrayList<>();URI keyUri=null,lastRangeUri=null;String iv="";long sequence=0,nextOffset=0;String range=null;boolean mapped=false;
        for(String raw:text.split("\\r?\\n")){String l=raw.trim();
            if(l.startsWith("#EXT-X-MEDIA-SEQUENCE:"))sequence=Long.parseLong(l.substring(l.indexOf(':')+1).trim());
            else if(l.equals("#EXT-X-DISCONTINUITY"))throw new IOException("Stream con discontinuità: download MP4 non supportato.");
            else if(l.startsWith("#EXT-X-KEY:")){Map<String,String>a=attributes(l);String method=a.get("METHOD");
                if("NONE".equals(method)){keyUri=null;iv="";}else if("AES-128".equals(method)&&"identity".equals(a.getOrDefault("KEYFORMAT","identity"))){keyUri=uri(base,a.get("URI"));iv=a.getOrDefault("IV","");}
                else throw new IOException("Formato di cifratura non supportato per il download.");
            }else if(l.startsWith("#EXT-X-MAP:")){if(keyUri!=null)throw new IOException("Inizializzazione cifrata non supportata.");if(mapped)throw new IOException("Cambio formato durante la puntata non supportato.");mapped=true;Map<String,String>a=attributes(l);URI u=uri(base,a.get("URI"));long[] r=range(a.get("BYTERANGE"),0);parts.add(new Part(u,r[0],r[1],null));}
            else if(l.startsWith("#EXT-X-BYTERANGE:"))range=l.substring(l.indexOf(':')+1).trim();
            else if(!l.isEmpty()&&!l.startsWith("#")){URI u=uri(base,l);if(range!=null&&!range.contains("@")&&!u.equals(lastRangeUri))throw new IOException("Intervallo HLS senza offset.");long[] r=range(range,nextOffset);
                parts.add(new Part(u,r[0],r[1],keyUri==null?null:new Key(keyUri,iv,sequence)));sequence++;nextOffset=r[0]+r[1];lastRangeUri=u;range=null;
                if(parts.size()>15000)throw new IOException("Puntata troppo lunga.");
            }
        }
        if(parts.isEmpty())throw new IOException("La playlist non contiene video.");return Collections.unmodifiableList(new ArrayList<>(parts));
    }
    public static byte[] iv(Key key)throws IOException{
        byte[] iv=new byte[16];if(key.iv().isEmpty()){if(key.sequence()<0)throw new IOException("Sequenza HLS non valida.");java.nio.ByteBuffer.wrap(iv).putLong(8,key.sequence());return iv;}
        String hex=key.iv().replaceFirst("^0[xX]","");if(!hex.matches("[a-fA-F0-9]{1,32}"))throw new IOException("IV HLS non valido.");
        byte[] b=new java.math.BigInteger(hex,16).toByteArray();System.arraycopy(b,Math.max(0,b.length-16),iv,Math.max(0,16-b.length),Math.min(16,b.length));return iv;
    }
    private static long[] range(String value,long implicit)throws IOException{
        if(value==null)return new long[]{0,-1};try{String[] a=value.split("@");long size=Long.parseLong(a[0]),offset=a.length>1?Long.parseLong(a[1]):implicit;if(size<=0||size>64L*1024*1024||offset<0)throw new Exception();return new long[]{offset,size};}catch(Exception e){throw new IOException("Intervallo HLS non valido.");}
    }
    private static void check(String text)throws IOException{if(!text.replace("\uFEFF","").trim().startsWith("#EXTM3U"))throw new IOException("Playlist HLS non valida.");}
}
