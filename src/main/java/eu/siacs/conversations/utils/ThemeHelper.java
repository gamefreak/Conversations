package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;


public class ThemeHelper {
	public static int findTheme(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
		String theme  = preferences.getString(SettingsActivity.THEME, context.getResources().getString(R.string.theme));

		if("dark".equals(theme)) {
			return R.style.ConversationsTheme_Dark;
		} else if ("moon".equals(theme)) {
			return R.style.ConversationsTheme_Moon;
		} else if ("moon_dark".equals(theme)) {
			return R.style.ConversationsTheme_Moon_Dark;
		} else {
			return R.style.ConversationsTheme;
		}
	}

	public static boolean isDarkTheme(int theme) {
		return theme == R.style.ConversationsTheme_Dark
				|| theme == R.style.ConversationsTheme_Moon_Dark;
	}
}
