package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.ThemeHelper;

import static eu.siacs.conversations.ui.XmppActivity.configureActionBar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int mTheme = ThemeHelper.findTheme(this);
        setTheme(mTheme);

        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
    }
}
