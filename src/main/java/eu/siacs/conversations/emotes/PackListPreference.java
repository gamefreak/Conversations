package eu.siacs.conversations.emotes;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.io.File;
import java.util.ArrayList;

public class PackListPreference extends ListPreference {
	public PackListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PackListPreference(Context context) {
		super(context);
	}

	@Override
	protected void onClick() {
		// This *should* be doable in an OnPreferenceClickListener but that seems to cause it to get updated too late
		File emoteDir = new File(getContext().getFilesDir(), "emoticons");

		ArrayList<String> entries = new ArrayList<>();
		entries.add("None");
		ArrayList<String> entryValues = new ArrayList<>();
		entryValues.add("");

		if (emoteDir.exists()) {
			for (File file : emoteDir.listFiles()) {
				entries.add(file.getName());
				entryValues.add(file.getAbsolutePath());
			}
		}
		setEntries(entries.toArray(new String[0]));
		setEntryValues(entryValues.toArray(new String[0]));
		super.onClick();
	}
}
