package horse.vinylscratch.conversations;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;

import eu.siacs.conversations.emotes.EmoticonService;

public abstract class AsyncEmoteLoaderBase extends AsyncTask<String, Void, Drawable[]> {
	@SuppressLint("StaticFieldLeak")
	private final EmoticonService emoticonService;

	protected AsyncEmoteLoaderBase(EmoticonService emoticonService) {
		this.emoticonService = emoticonService;
	}

	@Override
	protected Drawable[] doInBackground(String... emotes) {
		Drawable[] drawables = new Drawable[emotes.length];
		for (int i = 0; i < emotes.length; i++) {
			drawables[i] = emoticonService.getEmote(emotes[i]);
			if (isCancelled()) break;
		}
		return drawables;
	}
}
