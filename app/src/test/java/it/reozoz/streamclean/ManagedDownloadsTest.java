package it.reozoz.streamclean;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
public class ManagedDownloadsTest {
    private Context context;
    @Before public void provider(){
        context=RuntimeEnvironment.getApplication();String authority=context.getPackageName()+".downloads";
        ProviderInfo info=context.getPackageManager().resolveContentProvider(authority,PackageManager.GET_META_DATA);assertNotNull(info);assertFalse(info.exported);
        FileProvider provider=new FileProvider();provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal(authority,provider);
    }
    @Test public void privateOutputCanBeReadListedAndDeletedInApp()throws Exception{
        ManagedDownloads.Output output=ManagedDownloads.prepare(context,42,11,28759);assertTrue(output.temporary().getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()+"/downloads/"));
        assertFalse(output.completed().exists());assertTrue(ManagedDownloads.owns(context,output.uri()));
        try(OutputStream out=context.getContentResolver().openOutputStream(output.uri(),"w")){out.write(new byte[]{1,2,3,4});}
        Uri uri=ManagedDownloads.complete(context,output);assertFalse(output.temporary().exists());assertTrue(output.completed().exists());
        try(InputStream in=context.getContentResolver().openInputStream(uri)){assertEquals(1,in.read());}
        EpisodeDownloadService.prefs(context).edit().putString("file:42:28759",uri.toString()).putString("meta:42:28759",DownloadLibrary.metadata(42,28759,"South Park",11,2,"Cartman fa schifo")).commit();
        new WatchedEpisodes(context).set(42,28759,true);DownloadLibrary.Entry entry=DownloadLibrary.list(context).get(0);assertTrue(entry.available());assertEquals(4,entry.bytes());assertEquals("Cartman fa schifo",entry.name());
        DownloadLibrary.delete(context,entry);assertFalse(output.completed().exists());assertTrue(DownloadLibrary.list(context).isEmpty());assertTrue(new WatchedEpisodes(context).contains(42,28759));
    }
    @Test public void interruptedCleanupKeepsCompletedEpisodesAndOtherFiles()throws Exception{
        ManagedDownloads.Output output=ManagedDownloads.prepare(context,42,1,10);ManagedDownloads.Output complete=ManagedDownloads.prepare(context,42,1,11);
        try(FileOutputStream out=new FileOutputStream(complete.temporary())){out.write(1);}ManagedDownloads.complete(context,complete);
        File unrelated=new File(context.getFilesDir(),"settings.txt");unrelated.createNewFile();ManagedDownloads.cleanup(context);
        assertFalse(output.temporary().exists());assertTrue(complete.completed().exists());assertTrue(unrelated.exists());assertNotNull(ManagedDownloads.finished(context,42,1,11));
    }
    @Test public void providerCannotExposeOtherPrivateAppFiles()throws Exception{
        File secret=new File(context.getFilesDir(),"private.txt");secret.createNewFile();assertThrows(IllegalArgumentException.class,()->ManagedDownloads.uri(context,secret));
        assertThrows(IOException.class,()->ManagedDownloads.prepare(context,-1,0,1));
    }
}
