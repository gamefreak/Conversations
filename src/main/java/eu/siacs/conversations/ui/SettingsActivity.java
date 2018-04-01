package eu.siacs.conversations.ui;

import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.emotes.DownloadPackTask;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.ExportLogsService;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.ui.util.Color;
import eu.siacs.conversations.utils.TimeframeUtils;
import rocks.xmpp.addr.Jid;
import horse.vinylscratch.conversations.VersionCheckTask;

public class SettingsActivity extends XmppActivity implements
		OnSharedPreferenceChangeListener {

	public static final String KEEP_FOREGROUND_SERVICE = "enable_foreground_service";
	public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
	public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
	public static final String DND_ON_SILENT_MODE = "dnd_on_silent_mode";
	public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
	public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";
	public static final String AUTOMATIC_MESSAGE_DELETION = "automatic_message_deletion";
	public static final String BROADCAST_LAST_ACTIVITY = "last_activity";
	public static final String THEME = "theme";
	public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";

	public static final String ACTIVE_EMOTE_PACK = "active_emote_pack";
	public static final String ENABLE_GIF_EMOTES = "enable_gif_emotes";

	public static final int REQUEST_WRITE_LOGS = 0xbf8701;
	private SettingsFragment mSettingsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		FragmentManager fm = getFragmentManager();
		mSettingsFragment = (SettingsFragment) fm.findFragmentById(R.id.settings_content);
		if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
			mSettingsFragment = new SettingsFragment();
			fm.beginTransaction().replace(R.id.settings_content, mSettingsFragment).commit();
		}
		mSettingsFragment.setActivityIntent(getIntent());
		this.mTheme = findTheme();
		setTheme(this.mTheme);
		getWindow().getDecorView().setBackgroundColor(Color.get(this, R.attr.color_background_primary));
		setSupportActionBar(findViewById(R.id.toolbar));
		configureActionBar(getSupportActionBar());
	}

	@Override
	protected void onBackendConnected() {

	}

	class LocalDownloadPackTask extends DownloadPackTask {
		static final int NOTIFICATION_ID = 0x8be86803;
		private NotificationManager notificationManager = null;
		private Notification.Builder builder = null;

		private Context context;
		private final File packFile;
		private ListPreference activeEmotePackPreference;
		LocalDownloadPackTask(Context context, File packFile, ListPreference activeEmotePackPreference) {
			this.context = context;
			this.packFile = packFile;
			this.activeEmotePackPreference = activeEmotePackPreference;
		}


		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			notificationManager = (NotificationManager) SettingsActivity.this.getSystemService(Context.NOTIFICATION_SERVICE);
			builder = new Notification.Builder(SettingsActivity.this);
			builder.setContentTitle("Downloading ponymotes...")
					.setSmallIcon(R.drawable.ic_action_download);

			notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
			if (packFile.exists()) {
				Log.i("preferences", "deleting existing emote pack");
				packFile.delete();
			}
		}

		// downloaded, total
		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			builder.setProgress(values[1], values[0], false)
			.setContentText(String.format("%s of %s downloaded",
					Formatter.formatFileSize(context, values[0]),
					Formatter.formatFileSize(context, values[1])));
			notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
		}

		@Override
		protected void onPostExecute(Boolean success) {
			super.onPostExecute(success);
			if (success) {
				activeEmotePackPreference.setValue(packFile.getAbsolutePath());
				notificationManager.cancel(NOTIFICATION_ID);
			} else {
				notificationManager.notify(NOTIFICATION_ID, new Notification.Builder(context)
						.setSmallIcon(android.R.drawable.stat_notify_error)
						.setContentTitle("Emote pack download failed")
						.getNotification());
			}
		}
	}


	@Override
	public void onStart() {
		super.onStart();
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		if (Config.FORCE_ORBOT) {
			PreferenceCategory connectionOptions = (PreferenceCategory) mSettingsFragment.findPreference("connection_options");
			PreferenceScreen expert = (PreferenceScreen) mSettingsFragment.findPreference("expert");
			if (connectionOptions != null) {
				expert.removePreference(connectionOptions);
			}
		}

		PreferenceScreen mainPreferenceScreen = (PreferenceScreen) mSettingsFragment.findPreference("main_screen");

		//this feature is only available on Huawei Android 6.
		PreferenceScreen huaweiPreferenceScreen = (PreferenceScreen) mSettingsFragment.findPreference("huawei");
		if (huaweiPreferenceScreen != null) {
			Intent intent = huaweiPreferenceScreen.getIntent();
			//remove when Api version is above M (Version 6.0) or if the intent is not callable
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || !isCallable(intent)) {
				PreferenceCategory generalCategory = (PreferenceCategory) mSettingsFragment.findPreference("general");
				generalCategory.removePreference(huaweiPreferenceScreen);
				if (generalCategory.getPreferenceCount() == 0) {
					if (mainPreferenceScreen != null) {
						mainPreferenceScreen.removePreference(generalCategory);
					}
				}
			}
		}

		ListPreference automaticMessageDeletionList = (ListPreference) mSettingsFragment.findPreference(AUTOMATIC_MESSAGE_DELETION);
		if (automaticMessageDeletionList != null) {
			final int[] choices = getResources().getIntArray(R.array.automatic_message_deletion_values);
			CharSequence[] entries = new CharSequence[choices.length];
			CharSequence[] entryValues = new CharSequence[choices.length];
			for (int i = 0; i < choices.length; ++i) {
				entryValues[i] = String.valueOf(choices[i]);
				if (choices[i] == 0) {
					entries[i] = getString(R.string.never);
				} else {
					entries[i] = TimeframeUtils.resolve(this, 1000L * choices[i]);
				}
			}
			automaticMessageDeletionList.setEntries(entries);
			automaticMessageDeletionList.setEntryValues(entryValues);
		}


		boolean removeLocation = new Intent("eu.siacs.conversations.location.request").resolveActivity(getPackageManager()) == null;
		boolean removeVoice = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).resolveActivity(getPackageManager()) == null;

		final ListPreference activeEmotePackPreference = (ListPreference)mSettingsFragment.findPreference(ACTIVE_EMOTE_PACK);
		activeEmotePackPreference.setEntries(new String[]{"None"});
		activeEmotePackPreference.setDefaultValue("");
		activeEmotePackPreference.setEntryValues(new String[]{""});


		final Preference downloadPonymotesPreference = mSettingsFragment.findPreference("download_ponymotes");
		if (downloadPonymotesPreference != null) {
			downloadPonymotesPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					File dir = emoticonService().getPackDirectory();
					if (!dir.exists()) dir.mkdir();

					final File packFile = new File(dir, DownloadPackTask.PONYPACK_FILENAME);
					activeEmotePackPreference.setValue(null);

					DownloadPackTask downloadPackTask=  new LocalDownloadPackTask(SettingsActivity.this, packFile, activeEmotePackPreference);
					downloadPackTask.execute(DownloadPackTask.PONYPACK_DOWNLOAD_URL, packFile.getAbsolutePath());
					return false;
				}
			});
		}

		ListPreference quickAction = (ListPreference) mSettingsFragment.findPreference("quick_action");
		if (quickAction != null && (removeLocation || removeVoice)) {
			ArrayList<CharSequence> entries = new ArrayList<>(Arrays.asList(quickAction.getEntries()));
			ArrayList<CharSequence> entryValues = new ArrayList<>(Arrays.asList(quickAction.getEntryValues()));
			int index = entryValues.indexOf("location");
			if (index > 0 && removeLocation) {
				entries.remove(index);
				entryValues.remove(index);
			}
			index = entryValues.indexOf("voice");
			if (index > 0 && removeVoice) {
				entries.remove(index);
				entryValues.remove(index);
			}
			quickAction.setEntries(entries.toArray(new CharSequence[entries.size()]));
			quickAction.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
		}

		final Preference removeCertsPreference = mSettingsFragment.findPreference("remove_trusted_certificates");
		if (removeCertsPreference != null) {
			removeCertsPreference.setOnPreferenceClickListener(preference -> {
				final MemorizingTrustManager mtm = xmppConnectionService.getMemorizingTrustManager();
				final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
				if (aliases.size() == 0) {
					displayToast(getString(R.string.toast_no_trusted_certs));
					return true;
				}
				final ArrayList<Integer> selectedItems = new ArrayList<>();
				final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SettingsActivity.this);
				dialogBuilder.setTitle(getResources().getString(R.string.dialog_manage_certs_title));
				dialogBuilder.setMultiChoiceItems(aliases.toArray(new CharSequence[aliases.size()]), null,
						(dialog, indexSelected, isChecked) -> {
							if (isChecked) {
								selectedItems.add(indexSelected);
							} else if (selectedItems.contains(indexSelected)) {
								selectedItems.remove(Integer.valueOf(indexSelected));
							}
							if (selectedItems.size() > 0)
								((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
							else {
								((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
							}
						});

				dialogBuilder.setPositiveButton(
						getResources().getString(R.string.dialog_manage_certs_positivebutton), (dialog, which) -> {
							int count = selectedItems.size();
							if (count > 0) {
								for (int i = 0; i < count; i++) {
									try {
										Integer item = Integer.valueOf(selectedItems.get(i).toString());
										String alias = aliases.get(item);
										mtm.deleteCertificate(alias);
									} catch (KeyStoreException e) {
										e.printStackTrace();
										displayToast("Error: " + e.getLocalizedMessage());
									}
								}
								if (xmppConnectionServiceBound) {
									reconnectAccounts();
								}
								displayToast(getResources().getQuantityString(R.plurals.toast_delete_certificates, count, count));
							}
						});
				dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_manage_certs_negativebutton), null);
				AlertDialog removeCertsDialog = dialogBuilder.create();
				removeCertsDialog.show();
				removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
				return true;
			});
		}

		final Preference exportLogsPreference = mSettingsFragment.findPreference("export_logs");
		if (exportLogsPreference != null) {
			exportLogsPreference.setOnPreferenceClickListener(preference -> {
				if (hasStoragePermission(REQUEST_WRITE_LOGS)) {
					startExport();
				}
				return true;
			});
		}

		if (Config.ONLY_INTERNAL_STORAGE) {
			final Preference cleanCachePreference = mSettingsFragment.findPreference("clean_cache");
			if (cleanCachePreference != null) {
				cleanCachePreference.setOnPreferenceClickListener(preference -> cleanCache());
			}

			final Preference cleanPrivateStoragePreference = mSettingsFragment.findPreference("clean_private_storage");
			if (cleanPrivateStoragePreference != null) {
				cleanPrivateStoragePreference.setOnPreferenceClickListener(preference -> cleanPrivateStorage());
			}
		}

		final Preference deleteOmemoPreference = mSettingsFragment.findPreference("delete_omemo_identities");
		if (deleteOmemoPreference != null) {
			deleteOmemoPreference.setOnPreferenceClickListener(preference -> deleteOmemoIdentities());
		}

		final Preference checkForUpdatesPreference = mSettingsFragment.findPreference("check_for_updates");
		checkForUpdatesPreference.setOnPreferenceClickListener(preference -> {
			VersionCheckTask task = new VersionCheckTask(this);
			task.execute();
			return true;
		});
	}

	private boolean isCallable(final Intent i) {
		return i != null && getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
	}


	private boolean cleanCache() {
		Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.parse("package:" + getPackageName()));
		startActivity(intent);
		return true;
	}

	private boolean cleanPrivateStorage() {
		cleanPrivatePictures();
		cleanPrivateFiles();
		return true;
	}

	private void cleanPrivatePictures() {
		try {
			File dir = new File(getFilesDir().getAbsolutePath(), "/Pictures/");
			File[] array = dir.listFiles();
			if (array != null) {
				for (int b = 0; b < array.length; b++) {
					String name = array[b].getName().toLowerCase();
					if (name.equals(".nomedia")) {
						continue;
					}
					if (array[b].isFile()) {
						array[b].delete();
					}
				}
			}
		} catch (Throwable e) {
			Log.e("CleanCache", e.toString());
		}
	}

	private void cleanPrivateFiles() {
		try {
			File dir = new File(getFilesDir().getAbsolutePath(), "/Files/");
			File[] array = dir.listFiles();
			if (array != null) {
				for (int b = 0; b < array.length; b++) {
					String name = array[b].getName().toLowerCase();
					if (name.equals(".nomedia")) {
						continue;
					}
					if (array[b].isFile()) {
						array[b].delete();
					}
				}
			}
		} catch (Throwable e) {
			Log.e("CleanCache", e.toString());
		}
	}

	private boolean deleteOmemoIdentities() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.pref_delete_omemo_identities);
		final List<CharSequence> accounts = new ArrayList<>();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.isEnabled()) {
				accounts.add(account.getJid().asBareJid().toString());
			}
		}
		final boolean[] checkedItems = new boolean[accounts.size()];
		builder.setMultiChoiceItems(accounts.toArray(new CharSequence[accounts.size()]), checkedItems, (dialog, which, isChecked) -> {
			checkedItems[which] = isChecked;
			final AlertDialog alertDialog = (AlertDialog) dialog;
			for (boolean item : checkedItems) {
				if (item) {
					alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
					return;
				}
			}
			alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
		});
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.delete_selected_keys, (dialog, which) -> {
			for (int i = 0; i < checkedItems.length; ++i) {
				if (checkedItems[i]) {
					try {
						Jid jid = Jid.of(accounts.get(i).toString());
						Account account = xmppConnectionService.findAccountByJid(jid);
						if (account != null) {
							account.getAxolotlService().regenerateKeys(true);
						}
					} catch (IllegalArgumentException e) {
						//
					}

				}
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
		return true;
	}

	@Override
	public void onStop() {
		super.onStop();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
		final List<String> resendPresence = Arrays.asList(
				"confirm_messages",
				DND_ON_SILENT_MODE,
				AWAY_WHEN_SCREEN_IS_OFF,
				"allow_message_correction",
				TREAT_VIBRATE_AS_SILENT,
				MANUALLY_CHANGE_PRESENCE,
				BROADCAST_LAST_ACTIVITY,
				"enable_custom_priority",
				"priority");
		if (name.equals(KEEP_FOREGROUND_SERVICE)) {
			xmppConnectionService.toggleForegroundService();
		} else if (resendPresence.contains(name)) {
			if (xmppConnectionServiceBound) {
				if (name.equals(AWAY_WHEN_SCREEN_IS_OFF) || name.equals(MANUALLY_CHANGE_PRESENCE)) {
					xmppConnectionService.toggleScreenEventReceiver();
				}
				xmppConnectionService.refreshAllPresences();
			}
		} else if (name.equals("dont_trust_system_cas")) {
			xmppConnectionService.updateMemorizingTrustmanager();
			reconnectAccounts();
		} else if (name.equals("use_tor")) {
			reconnectAccounts();
		} else if (name.equals(AUTOMATIC_MESSAGE_DELETION)) {
			xmppConnectionService.expireOldMessages(true);
		} else if (name.equals(THEME)) {
			final int theme = findTheme();
			if (this.mTheme != theme) {
				recreate();
			}
		} else if (name.equals(ACTIVE_EMOTE_PACK)) {
			xmppConnectionService.getEmoticonService().doLoad();
		} else if (name.equals(ENABLE_GIF_EMOTES)) {
			xmppConnectionService.getEmoticonService().setEnableAnimations(getPreferences().getBoolean(ENABLE_GIF_EMOTES, true));
		}

	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_WRITE_LOGS) {
					startExport();
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	private void startExport() {
		startService(new Intent(getApplicationContext(), ExportLogsService.class));
	}

	private void displayToast(final String msg) {
		runOnUiThread(() -> Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show());
	}

	private void reconnectAccounts() {
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.isEnabled()) {
				xmppConnectionService.reconnectAccountInBackground(account);
			}
		}
	}

	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}

}
