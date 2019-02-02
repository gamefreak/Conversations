package horse.vinylscratch.conversations;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class VersionCheckTask extends AsyncTask<Void, Void, Void> {
	public static final boolean CHECKING_ENABLED = true;
	public static final String TAG = "UpdateChecker";
	public static final String UPDATE_URL = "https://api.github.com/repos/gamefreak/Conversations/releases/latest";
	public static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

	private Context context;
	public VersionCheckTask(Context context) {
		this.context = context;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Log.i(TAG, "Checking for updates");
		HttpsURLConnection connection = null;
		String jsonString = "";
		try  {
			URL url = new URL(UPDATE_URL);
			connection = (HttpsURLConnection)url.openConnection();
			connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
			connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) jsonString += line;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (connection != null) connection.disconnect();
		}

		try {
			JSONObject json = new JSONObject(jsonString);
			if (json.has("tag_name") && json.has("assets") && json.has("html_url")) {
				String version = json.getString("tag_name");
				String ownVersion = BuildConfig.VERSION_NAME;
				if (ownVersion.contains("+")) {
					// upstream changes caused VERSION_NAME to look like 1.2.3.4+fcd
					ownVersion = ownVersion.substring(0, ownVersion.indexOf("+"));
				}
				String url = json.getString("html_url");
				Log.i(TAG, "compare version " + version + " to " + ownVersion);
				if (compareVersions(version, ownVersion) != 1) {
					Log.i(TAG, "Version " + version + " up to date");
					return null;
				}

				JSONArray assets = json.getJSONArray("assets");
				for (int i = 0; i < assets.length(); i++) {
					JSONObject asset = assets.getJSONObject(i);
					if (asset.has("content_type") && asset.has("browser_download_url")) {
						if (!APK_CONTENT_TYPE.equals(asset.getString("content_type"))) continue;
						Log.i(TAG, "New version available at " + url);
						showNotification(version, url);
						return null;
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void showNotification(String version, String url) {
		Intent download = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		PendingIntent pi = PendingIntent.getActivity(context, 0, download, PendingIntent.FLAG_UPDATE_CURRENT);
		NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder builder = new Notification.Builder(context);
		TypedValue tv = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
		builder.setSmallIcon(R.drawable.ic_file_download_white_24dp)
				.setContentTitle(context.getString(R.string.ponverstations_update_available))
				.setContentText(String.format(context.getString(R.string.new_version_notification_message), version))
				.setContentIntent(pi)
				.setOngoing(false)
				.setAutoCancel(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setChannelId("new_updates");
		}

		notificationManager.notify(7, builder.build());
	}

	public static int compareVersions(String v1s, String v2s) {
		String v1sa[] = v1s.split("\\.");
		String v2sa[] = v2s.split("\\.");

		int v1[] = new int[Math.max(v1sa.length, v2sa.length)];
		int v2[] = new int[v1.length];

		Arrays.fill(v1, 0);
		Arrays.fill(v2, 0);

		for (int i = 0; i < v1sa.length; i++) v1[i]  = Integer.parseInt(v1sa[i]);
		for (int i = 0; i < v2sa.length; i++) v2[i]  = Integer.parseInt(v2sa[i]);

		for (int i = 0; i < v1.length; i++) {
			int c = ((Integer)v1[i]).compareTo(v2[i]);
			if (c != 0) return c;
		}
		return 0;
	}

}
