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
		Boolean larger = preferences.getBoolean("use_larger_font", context.getResources().getBoolean(R.bool.use_larger_font));

		if("dark".equals(theme)) {
			if(larger)
				return R.style.ConversationsTheme_Dark_LargerText;
			else
				return R.style.ConversationsTheme_Dark;
		} else if ("moon".equals(theme)) {
			if (larger)
				return R.style.ConversationsTheme_Moon_LargerText;
			else
				return R.style.ConversationsTheme_Moon;
		} else if ("moon_dark".equals(theme)) {
			if (larger)
				return R.style.ConversationsTheme_Moon_Dark_LargerText;
			else
				return R.style.ConversationsTheme_Moon_Dark;
		} else {
			if (larger)
				return R.style.ConversationsTheme_LargerText;
			else
				return R.style.ConversationsTheme;
		}
	}

	public static boolean isDarkTheme(int theme) {
		return theme == R.style.ConversationsTheme_Dark
				|| theme == R.style.ConversationsTheme_Dark_LargerText
				|| theme == R.style.ConversationsTheme_Moon_Dark
				|| theme == R.style.ConversationsTheme_Moon_Dark_LargerText;
	}
}
