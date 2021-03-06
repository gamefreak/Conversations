package eu.siacs.conversations.emotes;

import android.os.AsyncTask;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.Config;

public class DownloadPackTask extends AsyncTask<String, Integer, Boolean> {
	public static final String PONYPACK_DOWNLOAD_URL = "https://emotes.cardboardbox.be/output/Ponypack-conversations.zip";
	public static final String PONYPACK_FILENAME = "ponypack.zip";

	private boolean downloadPack(URL url, File destination) {
		Log.i("emote downloader", "Beginning download of emote pack from " + url.toString());
		HttpsURLConnection con = null;
		try  {
			con = (HttpsURLConnection) url.openConnection();
			con.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
			con.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
			con.connect();

			try (InputStream stream = con.getInputStream();
				 FileOutputStream outStream = new FileOutputStream(destination)) {

				final int BUFFER_SIZE = 16384;
				byte buffer[] = new byte[BUFFER_SIZE];
				int reads = 0;
				int totalBytesReceved = 0;
				do {
					int bytesRead = stream.read(buffer);
					totalBytesReceved += bytesRead;
					reads++;
					if (bytesRead <= 0) break;
					if (reads % 256 == 0) {
						Log.v("emote downloader", String.format("emote downloader: %,d bytes received", totalBytesReceved));
						this.publishProgress(totalBytesReceved, con.getContentLength());
					}
					outStream.write(buffer, 0, bytesRead);
				} while (true);
			}
			Log.i("emote downloader", "download complete");
			return true;
		} catch (IOException e) {
			Log.e("emote downloader", "emote download from " + url.toString() + " failed.", e);
		} finally {
			if (con != null) con.disconnect();
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
