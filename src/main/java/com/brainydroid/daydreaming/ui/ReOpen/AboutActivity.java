package com.brainydroid.daydreaming.ui.ReOpen;

import android.os.Bundle;
import android.view.ViewGroup;
import com.brainydroid.daydreaming.R;
import com.brainydroid.daydreaming.background.Logger;
import com.brainydroid.daydreaming.ui.FontUtils;
import com.brainydroid.daydreaming.ui.ReOpen.ReOpenActivity;
import roboguice.inject.ContentView;

/**
 * Created with IntelliJ IDEA.
 * User: vincent
 * Date: 8/14/13
 * Time: 4:40 PM
 * To change this template use File | Settings | File Templates.
 */


@ContentView(R.layout.activity_about)
public class AboutActivity extends ReOpenActivity {

    private static String TAG = "AboutActivity";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.v(TAG, "Creating");
        ViewGroup godfatherView = (ViewGroup)this.getWindow().getDecorView();
        FontUtils.setRobotoFont(this, godfatherView);

        super.onCreate(savedInstanceState);
      }


}