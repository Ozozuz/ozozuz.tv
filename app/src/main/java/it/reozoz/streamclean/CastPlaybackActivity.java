package it.reozoz.streamclean;
import android.view.*;
import androidx.core.view.MenuItemCompat;
import androidx.mediarouter.app.MediaRouteActionProvider;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity;
public final class CastPlaybackActivity extends ExpandedControllerActivity {
 @Override public boolean onCreateOptionsMenu(Menu menu){
  super.onCreateOptionsMenu(menu);MenuItem item=menu.add(Menu.NONE,R.id.cast_menu,Menu.NONE,"Cast");item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
  MenuItemCompat.setActionProvider(item,new MediaRouteActionProvider(this));CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),menu,R.id.cast_menu);return true;
 }
}
