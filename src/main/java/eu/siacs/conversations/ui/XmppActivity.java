package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatDelegate;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.emotes.EmoticonService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.BarcodeProvider;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import rocks.xmpp.addr.Jid;

public abstract class XmppActivity extends ActionBarActivity {

	public static final String EXTRA_ACCOUNT = "account";
	protected static final int REQUEST_ANNOUNCE_PGP = 0x0101;
	protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x0102;
	protected static final int REQUEST_CHOOSE_PGP_ID = 0x0103;
	protected static final int REQUEST_BATTERY_OP = 0x49ff;
	public XmppConnectionService xmppConnectionService;
	public boolean xmppConnectionServiceBound = false;
	protected final AtomicBoolean registeredListeners = new AtomicBoolean(false);

	protected int mColorRed;

	protected static final String FRAGMENT_TAG_DIALOG = "dialog";

	private boolean isCameraFeatureAvailable = false;

	protected boolean mUseSubject = true;
	protected int mTheme;
	protected boolean mUsingEnterKey = false;
	protected Toast mToast;
	public Runnable onOpenPGPKeyPublished = () -> Toast.makeText(XmppActivity.this, R.string.openpgp_has_been_published, Toast.LENGTH_SHORT).show();
	protected ConferenceInvite mPendingConferenceInvite = null;
	protected ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			XmppConnectionBinder binder = (XmppConnectionBinder) service;
			xmppConnectionService = binder.getService();
			xmppConnectionServiceBound = true;
			if (registeredListeners.compareAndSet(false,true)) {
				registerListeners();
			}
			onBackendConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionServiceBound = false;
		}
	};
	private DisplayMetrics metrics;
	private long mLastUiRefresh = 0;
	private Handler mRefreshUiHandler = new Handler();
	private Runnable mRefreshUiRunnable = () -> {
		mLastUiRefresh = SystemClock.elapsedRealtime();
		refreshUiReal();
	};
	private UiCallback<Conversation> adhocCallback = new UiCallback<Conversation>() {
		@Override
		public void success(final Conversation conversation) {
			runOnUiThread(() -> {
				switchToConversation(conversation);
				hideToast();
			});
		}

		@Override
		public void error(final int errorCode, Conversation object) {
			runOnUiThread(() -> replaceToast(getString(errorCode)));
		}

		@Override
		public void userInputRequried(PendingIntent pi, Conversation object) {

		}
	};
	public boolean mSkipBackgroundBinding = false;

	public static boolean cancelPotentialWork(Message message, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Message oldMessage = bitmapWorkerTask.message;
			if (oldMessage == null || message != oldMessage) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	protected void hideToast() {
		if (mToast != null) {
			mToast.cancel();
		}
	}

	protected void replaceToast(String msg) {
		replaceToast(msg, true);
	}

	protected void replaceToast(String msg, boolean showlong) {
		hideToast();
		mToast = Toast.makeText(this, msg, showlong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
		mToast.show();
	}

	protected final void refreshUi() {
		final long diff = SystemClock.elapsedRealtime() - mLastUiRefresh;
		if (diff > Config.REFRESH_UI_INTERVAL) {
			mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
			runOnUiThread(mRefreshUiRunnable);
		} else {
			final long next = Config.REFRESH_UI_INTERVAL - diff;
			mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
			mRefreshUiHandler.postDelayed(mRefreshUiRunnable, next);
		}
	}

	abstract protected void refreshUiReal();

	@Override
	protected void onStart() {
		super.onStart();
		if (!xmppConnectionServiceBound) {
			if (this.mSkipBackgroundBinding) {
				Log.d(Config.LOGTAG,"skipping background binding");
			} else {
				connectToBackend();
			}
		} else {
			if (registeredListeners.compareAndSet(false,true)) {
				this.registerListeners();
			}
			this.onBackendConnected();
		}
	}

	public void connectToBackend() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("ui");
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (xmppConnectionServiceBound) {
			if (registeredListeners.compareAndSet(true, false)) {
				this.unregisterListeners();
			}
			unbindService(mConnection);
			xmppConnectionServiceBound = false;
		}
	}


	public boolean hasPgp() {
		return xmppConnectionService.getPgpEngine() != null;
	}

	public void showInstallPgpDialog() {
		Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.openkeychain_required));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getText(R.string.openkeychain_required_long));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setNeutralButton(getString(R.string.restart),
				(dialog, which) -> {
					if (xmppConnectionServiceBound) {
						unbindService(mConnection);
						xmppConnectionServiceBound = false;
					}
					stopService(new Intent(XmppActivity.this,
							XmppConnectionService.class));
					finish();
				});
		builder.setPositiveButton(getString(R.string.install),
				(dialog, which) -> {
					Uri uri = Uri
							.parse("market://details?id=org.sufficientlysecure.keychain");
					Intent marketIntent = new Intent(Intent.ACTION_VIEW,
							uri);
					PackageManager manager = getApplicationContext()
							.getPackageManager();
					List<ResolveInfo> infos = manager
							.queryIntentActivities(marketIntent, 0);
					if (infos.size() > 0) {
						startActivity(marketIntent);
					} else {
						uri = Uri.parse("http://www.openkeychain.org/");
						Intent browserIntent = new Intent(
								Intent.ACTION_VIEW, uri);
						startActivity(browserIntent);
					}
					finish();
				});
		builder.create().show();
	}

	protected abstract void onBackendConnected();

	protected void registerListeners() {
		if (this instanceof XmppConnectionService.OnConversationUpdate) {
			this.xmppConnectionService.setOnConversationListChangedListener((XmppConnectionService.OnConversationUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnAccountUpdate) {
			this.xmppConnectionService.setOnAccountListChangedListener((XmppConnectionService.OnAccountUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnCaptchaRequested) {
			this.xmppConnectionService.setOnCaptchaRequestedListener((XmppConnectionService.OnCaptchaRequested) this);
		}
		if (this instanceof XmppConnectionService.OnRosterUpdate) {
			this.xmppConnectionService.setOnRosterUpdateListener((XmppConnectionService.OnRosterUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
			this.xmppConnectionService.setOnMucRosterUpdateListener((XmppConnectionService.OnMucRosterUpdate) this);
		}
		if (this instanceof OnUpdateBlocklist) {
			this.xmppConnectionService.setOnUpdateBlocklistListener((OnUpdateBlocklist) this);
		}
		if (this instanceof XmppConnectionService.OnShowErrorToast) {
			this.xmppConnectionService.setOnShowErrorToastListener((XmppConnectionService.OnShowErrorToast) this);
		}
		if (this instanceof OnKeyStatusUpdated) {
			this.xmppConnectionService.setOnKeyStatusUpdatedListener((OnKeyStatusUpdated) this);
		}
	}

	protected void unregisterListeners() {
		if (this instanceof XmppConnectionService.OnConversationUpdate) {
			this.xmppConnectionService.removeOnConversationListChangedListener((XmppConnectionService.OnConversationUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnAccountUpdate) {
			this.xmppConnectionService.removeOnAccountListChangedListener((XmppConnectionService.OnAccountUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnCaptchaRequested) {
			this.xmppConnectionService.removeOnCaptchaRequestedListener((XmppConnectionService.OnCaptchaRequested) this);
		}
		if (this instanceof XmppConnectionService.OnRosterUpdate) {
			this.xmppConnectionService.removeOnRosterUpdateListener((XmppConnectionService.OnRosterUpdate) this);
		}
		if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
			this.xmppConnectionService.removeOnMucRosterUpdateListener((XmppConnectionService.OnMucRosterUpdate) this);
		}
		if (this instanceof OnUpdateBlocklist) {
			this.xmppConnectionService.removeOnUpdateBlocklistListener((OnUpdateBlocklist) this);
		}
		if (this instanceof XmppConnectionService.OnShowErrorToast) {
			this.xmppConnectionService.removeOnShowErrorToastListener((XmppConnectionService.OnShowErrorToast) this);
		}
		if (this instanceof OnKeyStatusUpdated) {
			this.xmppConnectionService.removeOnNewKeysAvailableListener((OnKeyStatusUpdated) this);
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.action_accounts:
				startActivity(new Intent(this, ManageAccountActivity.class));
				break;
			case android.R.id.home:
				finish();
				break;
			case R.id.action_show_qr_code:
				showQrCode();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	public void selectPresence(final Conversation conversation, final PresenceSelector.OnPresenceSelected listener) {
		final Contact contact = conversation.getContact();
		if (!contact.showInRoster()) {
			showAddToRosterDialog(conversation.getContact());
		} else {
			final Presences presences = contact.getPresences();
			if (presences.size() == 0) {
				if (!contact.getOption(Contact.Options.TO)
						&& !contact.getOption(Contact.Options.ASKING)
						&& contact.getAccount().getStatus() == Account.State.ONLINE) {
					showAskForPresenceDialog(contact);
				} else if (!contact.getOption(Contact.Options.TO)
						|| !contact.getOption(Contact.Options.FROM)) {
					PresenceSelector.warnMutualPresenceSubscription(this, conversation, listener);
				} else {
					conversation.setNextCounterpart(null);
					listener.onPresenceSelected();
				}
			} else if (presences.size() == 1) {
				String presence = presences.toResourceArray()[0];
				try {
					conversation.setNextCounterpart(Jid.of(contact.getJid().getLocal(), contact.getJid().getDomain(), presence));
				} catch (IllegalArgumentException e) {
					conversation.setNextCounterpart(null);
				}
				listener.onPresenceSelected();
			} else {
				PresenceSelector.showPresenceSelectionDialog(this, conversation, listener);
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		metrics = getResources().getDisplayMetrics();
		ExceptionHelper.init(getApplicationContext());
		this.isCameraFeatureAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
		UIHelper.init(getApplicationContext());

		mColorRed = ContextCompat.getColor(this, R.color.red800);

		this.mTheme = findTheme();
		setTheme(this.mTheme);

		this.mUsingEnterKey = usingEnterKey();
		mUseSubject = getPreferences().getBoolean("use_subject", getResources().getBoolean(R.bool.use_subject));
	}

	protected boolean isCameraFeatureAvailable() {
		return this.isCameraFeatureAvailable;
	}

	public boolean isDarkTheme() {
		return ThemeHelper.isDark(mTheme);
	}

	public int getThemeResource(int r_attr_name, int r_drawable_def) {
		int[] attrs = {r_attr_name};
		TypedArray ta = this.getTheme().obtainStyledAttributes(attrs);

		int res = ta.getResourceId(0, r_drawable_def);
		ta.recycle();

		return res;
	}

	@Override
	protected void onApplyThemeResource(Resources.Theme theme, @StyleRes int resid, boolean first) {
		super.onApplyThemeResource(theme, resid, first);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// Need to do this manually
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(android.R.attr.colorPrimary, tv, true);

			ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, tv.data);
			setTaskDescription(taskDescription);
		}
	}

	protected boolean isOptimizingBattery() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			return pm != null
					&& !pm.isIgnoringBatteryOptimizations(getPackageName());
		} else {
			return false;
		}
	}

	protected boolean isAffectedByDataSaver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			return cm != null
					&& cm.isActiveNetworkMetered()
					&& cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
		} else {
			return false;
		}
	}

	protected boolean usingEnterKey() {
		return getPreferences().getBoolean("display_enter_key", getResources().getBoolean(R.bool.display_enter_key));
	}

	protected SharedPreferences getPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	public boolean useSubjectToIdentifyConference() {
		return mUseSubject;
	}

	public void switchToConversation(Conversation conversation) {
		switchToConversation(conversation, null, false);
	}

	public void switchToConversationAndQuote(Conversation conversation, String text) {
		switchToConversation(conversation, text, true, null, false, false);
	}

	public void switchToConversation(Conversation conversation, String text, boolean newTask) {
		switchToConversation(conversation, text, false, null, false, newTask);
	}

	public void highlightInMuc(Conversation conversation, String nick) {
		switchToConversation(conversation, null, false, nick, false, false);
	}

	public void privateMsgInMuc(Conversation conversation, String nick) {
		switchToConversation(conversation, null, false, nick, true, false);
	}

	private void switchToConversation(Conversation conversation, String text, boolean asQuote, String nick, boolean pm, boolean newTask) {
		Intent intent = new Intent(this, ConversationsActivity.class);
		intent.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
		intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getUuid());
		if (text != null) {
			intent.putExtra(ConversationsActivity.EXTRA_TEXT, text);
			if (asQuote) {
				intent.putExtra(ConversationsActivity.EXTRA_AS_QUOTE, asQuote);
			}
		}
		if (nick != null) {
			intent.putExtra(ConversationsActivity.EXTRA_NICK, nick);
			intent.putExtra(ConversationsActivity.EXTRA_IS_PRIVATE_MESSAGE, pm);
		}
		if (newTask) {
			intent.setFlags(intent.getFlags()
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		} else {
			intent.setFlags(intent.getFlags()
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		startActivity(intent);
		finish();
	}

	public void switchToContactDetails(Contact contact) {
		switchToContactDetails(contact, null);
	}

	public void switchToContactDetails(Contact contact, String messageFingerprint) {
		Intent intent = new Intent(this, ContactDetailsActivity.class);
		intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
		intent.putExtra(EXTRA_ACCOUNT, contact.getAccount().getJid().asBareJid().toString());
		intent.putExtra("contact", contact.getJid().toString());
		intent.putExtra("fingerprint", messageFingerprint);
		startActivity(intent);
	}

	public void switchToAccount(Account account, String fingerprint) {
		switchToAccount(account, false, fingerprint);
	}

	public void switchToAccount(Account account) {
		switchToAccount(account, false, null);
	}

	public void switchToAccount(Account account, boolean init, String fingerprint) {
		Intent intent = new Intent(this, EditAccountActivity.class);
		intent.putExtra("jid", account.getJid().asBareJid().toString());
		intent.putExtra("init", init);
		if (init) {
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		}
		if (fingerprint != null) {
			intent.putExtra("fingerprint", fingerprint);
		}
		startActivity(intent);
		if (init) {
			overridePendingTransition(0, 0);
		}
	}

	protected void delegateUriPermissionsToService(Uri uri) {
		Intent intent = new Intent(this,XmppConnectionService.class);
		intent.setAction(Intent.ACTION_SEND);
		intent.setData(uri);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		startService(intent);
	}

	protected void inviteToConversation(Conversation conversation) {
		startActivityForResult(ChooseContactActivity.create(this,conversation), REQUEST_INVITE_TO_CONVERSATION);
	}

	protected void announcePgp(final Account account, final Conversation conversation, Intent intent, final Runnable onSuccess) {
		if (account.getPgpId() == 0) {
			choosePgpSignId(account);
		} else {
			String status = null;
			if (manuallyChangePresence()) {
				status = account.getPresenceStatusMessage();
			}
			if (status == null) {
				status = "";
			}
			xmppConnectionService.getPgpEngine().generateSignature(intent, account, status, new UiCallback<String>() {

				@Override
				public void userInputRequried(PendingIntent pi, String signature) {
					try {
						startIntentSenderForResult(pi.getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
					} catch (final SendIntentException ignored) {
					}
				}

				@Override
				public void success(String signature) {
					account.setPgpSignature(signature);
					xmppConnectionService.databaseBackend.updateAccount(account);
					xmppConnectionService.sendPresence(account);
					if (conversation != null) {
						conversation.setNextEncryption(Message.ENCRYPTION_PGP);
						xmppConnectionService.updateConversation(conversation);
						refreshUi();
					}
					if (onSuccess != null) {
						runOnUiThread(onSuccess);
					}
				}

				@Override
				public void error(int error, String signature) {
					if (error == 0) {
						account.setPgpSignId(0);
						account.unsetPgpSignature();
						xmppConnectionService.databaseBackend.updateAccount(account);
						choosePgpSignId(account);
					} else {
						displayErrorDialog(error);
					}
				}
			});
		}
	}

	protected boolean noAccountUsesPgp() {
		if (!hasPgp()) {
			return true;
		}
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getPgpId() != 0) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	protected void setListItemBackgroundOnView(View view) {
		int sdk = android.os.Build.VERSION.SDK_INT;
		if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
			view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
		} else {
			view.setBackground(getResources().getDrawable(R.drawable.greybackground));
		}
	}

	protected void choosePgpSignId(Account account) {
		xmppConnectionService.getPgpEngine().chooseKey(account, new UiCallback<Account>() {
			@Override
			public void success(Account account1) {
			}

			@Override
			public void error(int errorCode, Account object) {

			}

			@Override
			public void userInputRequried(PendingIntent pi, Account object) {
				try {
					startIntentSenderForResult(pi.getIntentSender(),
							REQUEST_CHOOSE_PGP_ID, null, 0, 0, 0);
				} catch (final SendIntentException ignored) {
				}
			}
		});
	}

	protected void displayErrorDialog(final int errorCode) {
		runOnUiThread(() -> {
			Builder builder = new Builder(XmppActivity.this);
			builder.setIconAttribute(android.R.attr.alertDialogIcon);
			builder.setTitle(getString(R.string.error));
			builder.setMessage(errorCode);
			builder.setNeutralButton(R.string.accept, null);
			builder.create().show();
		});

	}

	protected void showAddToRosterDialog(final Contact contact) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(contact.getJid().toString());
		builder.setMessage(getString(R.string.not_in_roster));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.add_contact), (dialog, which) -> xmppConnectionService.createContact(contact,true));
		builder.create().show();
	}

	private void showAskForPresenceDialog(final Contact contact) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(contact.getJid().toString());
		builder.setMessage(R.string.request_presence_updates);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.request_now,
				(dialog, which) -> {
					if (xmppConnectionServiceBound) {
						xmppConnectionService.sendPresencePacket(contact
								.getAccount(), xmppConnectionService
								.getPresenceGenerator()
								.requestPresenceUpdatesFrom(contact));
					}
				});
		builder.create().show();
	}

	protected void quickEdit(String previousValue, int hint, OnValueEdited callback) {
		quickEdit(previousValue, callback, hint, false);
	}

	protected void quickPasswordEdit(String previousValue, OnValueEdited callback) {
		quickEdit(previousValue, callback, R.string.password, true);
	}

	@SuppressLint("InflateParams")
	private void quickEdit(final String previousValue,
	                       final OnValueEdited callback,
	                       final int hint,
	                       boolean password) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.quickedit, null);
		final EditText editor = view.findViewById(R.id.editor);
		if (password) {
			editor.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}
		builder.setPositiveButton(R.string.accept, null);
		if (hint != 0) {
			editor.setHint(hint);
		}
		editor.requestFocus();
		editor.setText("");
		if (previousValue != null) {
			editor.getText().append(previousValue);
		}
		builder.setView(view);
		builder.setNegativeButton(R.string.cancel, null);
		final AlertDialog dialog = builder.create();
		dialog.show();
		View.OnClickListener clickListener = v -> {
			String value = editor.getText().toString();
			if (!value.equals(previousValue) && value.trim().length() > 0) {
				String error = callback.onValueEdited(value);
				if (error != null) {
					editor.setError(error);
					return;
				}
			}
			dialog.dismiss();
		};
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clickListener);
	}

	protected boolean hasStoragePermission(int requestCode) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
				return false;
			} else {
				return true;
			}
		} else {
			return true;
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
			mPendingConferenceInvite = ConferenceInvite.parse(data);
			if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
				if (mPendingConferenceInvite.execute(this)) {
					mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
					mToast.show();
				}
				mPendingConferenceInvite = null;
			}
		}
	}

	public int getWarningTextColor() {
		return this.mColorRed;
	}

	public int getPixel(int dp) {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	public boolean copyTextToClipboard(String text, int labelResId) {
		ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		String label = getResources().getString(labelResId);
		if (mClipBoardManager != null) {
			ClipData mClipData = ClipData.newPlainText(label, text);
			mClipBoardManager.setPrimaryClip(mClipData);
			return true;
		}
		return false;
	}

	protected boolean neverCompressPictures() {
		return getPreferences().getString("picture_compression", getResources().getString(R.string.picture_compression)).equals("never");
	}

	protected boolean manuallyChangePresence() {
		return getPreferences().getBoolean(SettingsActivity.MANUALLY_CHANGE_PRESENCE, getResources().getBoolean(R.bool.manually_change_presence));
	}

	protected String getShareableUri() {
		return getShareableUri(false);
	}

	protected String getShareableUri(boolean http) {
		return null;
	}

	protected void shareLink(boolean http) {
		String uri = getShareableUri(http);
		if (uri == null || uri.isEmpty()) {
			return;
		}
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, getShareableUri(http));
		try {
			startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
		}
	}

	protected void launchOpenKeyChain(long keyId) {
		PgpEngine pgp = XmppActivity.this.xmppConnectionService.getPgpEngine();
		try {
			startIntentSenderForResult(
					pgp.getIntentForKey(keyId).getIntentSender(), 0, null, 0,
					0, 0);
		} catch (Throwable e) {
			Toast.makeText(XmppActivity.this, R.string.openpgp_error, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	protected int findTheme() {
		return ThemeHelper.find(this);
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public boolean onMenuOpened(int id, Menu menu) {
		if(id == AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR && menu != null) {
			MenuDoubleTabUtil.recordMenuOpen();
		}
		return super.onMenuOpened(id, menu);
	}

	protected void showQrCode() {
		showQrCode(getShareableUri());
	}

	protected void showQrCode(final String uri) {
		if (uri == null || uri.isEmpty()) {
			return;
		}
		Point size = new Point();
		getWindowManager().getDefaultDisplay().getSize(size);
		final int width = (size.x < size.y ? size.x : size.y);
		Bitmap bitmap = BarcodeProvider.create2dBarcodeBitmap(uri, width);
		ImageView view = new ImageView(this);
		view.setBackgroundColor(Color.WHITE);
		view.setImageBitmap(bitmap);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(view);
		builder.create().show();
	}

	protected Account extractAccount(Intent intent) {
		String jid = intent != null ? intent.getStringExtra(EXTRA_ACCOUNT) : null;
		try {
			return jid != null ? xmppConnectionService.findAccountByJid(Jid.of(jid)) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public AvatarService avatarService() {
		return xmppConnectionService.getAvatarService();
	}

	public void loadBitmap(Message message, ImageView imageView) {
		Bitmap bm;
		try {
			bm = xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), true);
		} catch (FileNotFoundException e) {
			bm = null;
		}
		if (bm != null) {
			cancelPotentialWork(message, imageView);
			imageView.setImageBitmap(bm);
			imageView.setBackgroundColor(0x00000000);
		} else {
			if (cancelPotentialWork(message, imageView)) {
				imageView.setBackgroundColor(0xff333333);
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(this, imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(
						getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(message);
				} catch (final RejectedExecutionException ignored) {
					ignored.printStackTrace();
				}
			}
		}
	}

	protected interface OnValueEdited {
		String onValueEdited(String value);
	}

	public static class ConferenceInvite {
		private String uuid;
		private List<Jid> jids = new ArrayList<>();

		public static ConferenceInvite parse(Intent data) {
			ConferenceInvite invite = new ConferenceInvite();
			invite.uuid = data.getStringExtra("conversation");
			if (invite.uuid == null) {
				return null;
			}
			try {
				if (data.getBooleanExtra("multiple", false)) {
					String[] toAdd = data.getStringArrayExtra("contacts");
					for (String item : toAdd) {
						invite.jids.add(Jid.of(item));
					}
				} else {
					invite.jids.add(Jid.of(data.getStringExtra("contact")));
				}
			} catch (final IllegalArgumentException ignored) {
				return null;
			}
			return invite;
		}

		public boolean execute(XmppActivity activity) {
			XmppConnectionService service = activity.xmppConnectionService;
			Conversation conversation = service.findConversationByUuid(this.uuid);
			if (conversation == null) {
				return false;
			}
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				for (Jid jid : jids) {
					service.invite(conversation, jid);
				}
				return false;
			} else {
				jids.add(conversation.getJid().asBareJid());
				return service.createAdhocConference(conversation.getAccount(), null, jids, activity.adhocCallback);
			}
		}
	}

	public EmoticonService emoticonService() {
		return xmppConnectionService.getEmoticonService();
	}


	static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private final WeakReference<XmppActivity> activity;
		private Message message = null;

		private BitmapWorkerTask(XmppActivity activity, ImageView imageView) {
			this.activity = new WeakReference<>(activity);
			this.imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Message... params) {
			if (isCancelled()) {
				return null;
			}
			message = params[0];
			try {
				XmppActivity activity = this.activity.get();
				if (activity != null && activity.xmppConnectionService != null) {
					return activity.xmppConnectionService.getFileBackend().getThumbnail(message, (int) (activity.metrics.density * 288), false);
				} else {
					return null;
				}
			} catch (FileNotFoundException e) {
				return null;
			}
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

	private static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		private AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		private BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
}
