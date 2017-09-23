package eu.siacs.conversations.ui;

import android.app.Activity;
import android.os.Bundle;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.ThemeHelper;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int mTheme = ThemeHelper.findTheme(this);
        setTheme(mTheme);

        setContentView(R.layout.activity_about);
    }
}
