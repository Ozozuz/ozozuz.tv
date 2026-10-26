package it.reozoz.streamclean;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.core.content.FileProvider;
import java.io.*;

/** Private episode storage. Final files appear in the library only after a completed MP4. */
final class ManagedDownloads {
    record Output(File temporary,File completed,Uri uri) {}
    static File root(Context c){return new File(c.getFilesDir(),"downloads");}
    static Uri finished(Context c,long title,int season,long episode)throws IOException{
        if(title<=0||episode<=0||season<0)throw new IOException("Identità episodio non valida.");
        File file=new File(root(c),"title-"+title+"/season-"+season+"/episode-"+episode+".mp4");return file.isFile()&&file.length()>0?uri(c,file):null;
    }
    static Output prepare(Context c,long title,int season,long episode)throws IOException{
        if(title<=0||episode<=0||season<0)throw new IOException("Identità episodio non valida.");
        File dir=new File(root(c),"title-"+title+"/season-"+season);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Spazio download non disponibile.");
        File completed=new File(dir,"episode-"+episode+".mp4"),temporary=new File(dir,"episode-"+episode+".partial");
        if(completed.exists())throw new IOException("File già presente: controlla la libreria Download.");
        if(temporary.exists()&&!temporary.delete())throw new IOException("Impossibile pulire il download interrotto.");
        if(!temporary.createNewFile())throw new IOException("Impossibile creare il download.");
        return new Output(temporary,completed,uri(c,temporary));
    }
    static Uri complete(Context c,Output output)throws IOException{
        if(output.temporary().length()==0||!output.temporary().renameTo(output.completed()))throw new IOException("Impossibile completare il file MP4.");
        return uri(c,output.completed());
    }
    static Uri uri(Context c,File file){return FileProvider.getUriForFile(c,c.getPackageName()+".downloads",file);}
    static boolean owns(Context c,Uri uri){return "content".equals(uri.getScheme())&&(c.getPackageName()+".downloads").equals(uri.getAuthority());}
    static boolean delete(Context c,Uri uri)throws Exception{return owns(c,uri)?c.getContentResolver().delete(uri,null,null)>0:DocumentsContract.deleteDocument(c.getContentResolver(),uri);}
    static void cleanup(Context c){cleanup(root(c),0);}
    private static void cleanup(File folder,int depth){if(depth>3)return;File[] files=folder.listFiles();if(files==null)return;for(File file:files){if(file.isDirectory())cleanup(file,depth+1);else if(file.getName().matches("episode-[1-9][0-9]*\\.partial"))file.delete();}}
}
