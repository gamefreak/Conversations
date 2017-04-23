package eu.siacs.conversations.emotes;

import android.os.AsyncTask;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import eu.siacs.conversations.Config;

public class DownloadPackTask extends AsyncTask<String, Void, Boolean> {
	public static final String PONYPACK_DOWNLOAD_URL = "https://emotes.cardboardbox.be/output/Ponypack-conversations.zip";
	public static final String PONYPACK_FILENAME = "ponypack.zip";

	private boolean downloadPack(URL url, File destination) {
		Log.i("EMOTE_DL", "Beginning download of emote pack from " + url.toString());
		try  {
			URLConnection con = url.openConnection();
			con.connect();

			InputStream stream = con.getInputStream();
			FileOutputStream outStream = new FileOutputStream(destination);
			final int BUFFER_SIZE = 16384;
			byte buffer[] = new byte[BUFFER_SIZE];
			int reads = 0;
			do {

				int bytesRead = stream.read(buffer);
				reads++;
				if (bytesRead <= 0) break;
				if (reads % 16 == 0) {
					Log.v(Config.LOGTAG, "emote downloader: " + (reads * BUFFER_SIZE + bytesRead) + " bytes recieved");
				}
				outStream.write(buffer, 0, bytesRead);
			} while (true);
			outStream.close();
			stream.close();
			Log.i(Config.LOGTAG, "emote downloader: download complete");
			return true;
		} catch (IOException e) {
			Log.e(Config.LOGTAG, "emote downloader: emote download from " + url.toString() + " failed.", e);
		}
		return false;
	}


	@Override
	protected Boolean doInBackground(String... params) {
		try {
			URL url = new URL(params[0]);
			File destination = new File(params[1]);
			if (!destination.exists()) {
				boolean ok = downloadPack(url, destination);
				if (!ok) return false;
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
