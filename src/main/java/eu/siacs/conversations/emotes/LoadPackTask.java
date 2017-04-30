package eu.siacs.conversations.emotes;

import android.os.AsyncTask;

import java.io.File;

public class LoadPackTask extends AsyncTask<String, Integer, Boolean> {
	private final EmoticonService service;

	public LoadPackTask(EmoticonService service) {
		this.service = service;
	}

	@Override
	protected Boolean doInBackground(String... files) {
		if (files[0] == null || "".equals(files[0])) {
			service.loadPack(null);
		} else {
			service.loadPack(new File(files[0]));
		}
		return null;
	}
}
