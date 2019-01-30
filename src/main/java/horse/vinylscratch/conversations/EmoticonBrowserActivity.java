package horse.vinylscratch.conversations;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEmoticonBrowserBinding;
import eu.siacs.conversations.databinding.EmotePreviewBinding;
import eu.siacs.conversations.emotes.Emote;
import eu.siacs.conversations.emotes.EmoticonService;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.XmppActivity;
import horse.vinylscratch.conversations.entities.EmoteDbHelper;
import horse.vinylscratch.conversations.entities.RecentEmoteContract.RecentEmote;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.MultiCallback;


public class EmoticonBrowserActivity extends XmppActivity {
	enum SortMode {
		ALL("All"),
		FREQUENT("Most Used"),
		RECENT("Recent");

		final private String label;

		SortMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return this.label;
		}
	}

	class EmoteAdapter extends BaseAdapter {
		private EmoticonService emoticonService = null;
		private int lastPackVersion = -1;
		private List<Emote> emotes = new ArrayList<>();
		private SortMode mode = SortMode.ALL;
		private String searchFilter = null;
		private Context context = null;

		private SQLiteDatabase db;

		public EmoteAdapter(@NonNull Context context, SQLiteDatabase db) {
			this.context = context;

			this.db = db;
		}

		public void setEmoticonService(EmoticonService emoticonService) {
			this.emoticonService = emoticonService;
			this.lastPackVersion = -1;
			this.checkEmoteVersion();
			this.notifyDataSetChanged();
		}

		private void checkEmoteVersion() {
			if (this.emoticonService == null) return;

			if (this.lastPackVersion == this.emoticonService.getLoadedPackVersion()) return;

			this.applyFilter();
			this.notifyDataSetChanged();

		}

		private List<Emote> getSortedEmotes(SortMode mode) {
			if (this.emoticonService == null) return new ArrayList<>();
			if (!this.emoticonService.hasPack()) return new ArrayList<>();
			if (mode == SortMode.ALL) {
				List<Emote> theEmotes = this.emoticonService.getAllEmotes();
				Collections.sort(theEmotes, (left, right) -> left.getFirstAlias().compareTo(right.getFirstAlias()));
				return theEmotes;
			} else {
				String sortBy = (mode == SortMode.FREQUENT ? RecentEmote.COLUMN_NAME_HIT_COUNT : RecentEmote.COLUMN_NAME_LAST_USE) + " DESC";
				Cursor cursor = db.query(RecentEmote.TABLE_NAME, new String[]{RecentEmote.COLUMN_NAME_EMOTE}, null,  null, null, null, sortBy);
				List<Emote> emotes = new ArrayList<>();
				while(cursor.moveToNext()) {
					String emoteName = cursor.getString(cursor.getColumnIndexOrThrow(RecentEmote.COLUMN_NAME_EMOTE));
					Emote emote = this.emoticonService.getEmoteInfo(emoteName);
					if (emote == null) continue;
					emotes.add(emote);
				}
				return emotes;
			}
		}

		private void applyFilter() {
			if (this.emoticonService == null || !this.emoticonService.hasPack()) {
				this.emotes.clear();
			} else if (this.searchFilter == null || this.searchFilter.trim().isEmpty()) {
				this.emotes.clear();
				this.emotes.addAll(getSortedEmotes(this.mode));
			} else {
				this.emotes.clear();

				String filter = this.searchFilter.toLowerCase().trim();
				for (Emote emote : this.getSortedEmotes(SortMode.ALL)) {
					for (String alias : emote.getAliases()) {
						if (alias.toLowerCase().contains(filter)) {
							this.emotes.add(emote);
							break;
						}
					}
				}
			}
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public int getCount() {
			return this.emotes.size();
		}

		public Emote getEmote(int i) {
			return this.emotes.get(i);
		}

		@Override
		public Object getItem(int i) {
			return getEmote(i);
		}

		@Override
		public long getItemId(int i) {
			return getEmote(i).getImageName().hashCode();
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			EmotePreviewBinding  binding= convertView != null ? DataBindingUtil.getBinding(convertView) : DataBindingUtil.inflate(getLayoutInflater(), R.layout.emote_preview, parent, false);

			Emote emoticon = getEmote(position);
			Drawable image = emoticonService.tryGetEmote(emoticon.getAliases().get(0));
			if (image == null) {
				if (binding.getLoaderTask() != null) {
					binding.getLoaderTask().cancel(false);
				}
				AsyncEmoteLoader task = new AsyncEmoteLoader(emoticonService, binding.image);
				task.executeOnExecutor(emoticonService().getExecutor(),  emoticon.getAliases().get(0));
				binding.setLoaderTask(task);

				image = emoticonService.makePlaceholder(emoticon);
			}

			if (emoticonService.areAnimationsEnabled() && image instanceof GifDrawable) {
				((MultiCallback) image.getCallback()).addView(binding.image);
			}
			image = new FitDrawable(image);

			binding.image.setImageDrawable(image);
			binding.label.setText(emoticon.getAliases().get(0));
			// tried setting it in the XML but it was crashing for some reason
//			binding.label.setTextColor(EmoticonBrowserActivity.this.getPrimaryTextColor());
			return binding.getRoot();
		}

		public void setSearchFilter(String newText) {
			this.searchFilter = newText;
			this.applyFilter();
			this.notifyDataSetChanged();
		}

		public SortMode getMode() {
			return mode;
		}

		public void setMode(SortMode mode) {
			this.mode = mode;
			this.applyFilter();
			this.notifyDataSetChanged();
		}
	}


	public static final String ACTION_PICK_EMOTE = "pick_emote";
	static final String TAG = "EmoteBrowserActivity";
	static final String PREF_SORT_MODE = "SORT_MODE";
	private ActivityEmoticonBrowserBinding binding;
	private EmoteDbHelper dbHelper = null;


	private ServiceConnection emoteServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			emoticonService = ((EmoticonService.Binder) iBinder).getService();

			emoteAdapter.setEmoticonService(emoticonService);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			emoticonService = null;
			emoteAdapter.setEmoticonService(null);
		}
	};;
	private EmoticonService emoticonService = null;
	private EmoteAdapter emoteAdapter = null;

	private String uuid = null;
	private Conversation mConversation = null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_emoticon_browser);

		setTitle("");

		this.dbHelper = new EmoteDbHelper(this);
		emoteAdapter = new EmoteAdapter(this, this.dbHelper.getReadableDatabase());

		binding.emoteGrid.setAdapter(emoteAdapter);
		binding.emoteGrid.setOnItemClickListener((adapterView, view, position, id) -> {
			Emote emote = emoteAdapter.getEmote(position);
			emoteClicked(emote);
		});
		binding.emoteGrid.setOnItemLongClickListener((parent, view, position, id) -> {
			EmoticonBrowserActivity activity = EmoticonBrowserActivity.this;
			Emote emote = (Emote) parent.getAdapter().getItem(position);
			if (emote.getAliases().size() < 2) {
				return false;
			}
			PopupMenu popupMenu = new PopupMenu(activity, view);
			popupMenu.setOnMenuItemClickListener(item -> {
				EmoticonBrowserActivity.this.emoteClicked((String)item.getTitle());
				return true;
			});
			Menu menu = popupMenu.getMenu();
			for (String alias : emote.getAliases()) {
				menu.add(alias);
			}
			popupMenu.show();

			return true;
		});

		setSupportActionBar(binding.toolbar);
		configureActionBar(getSupportActionBar());

		ArrayAdapter<SortMode> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SortMode.values());
		binding.spinner.setAdapter(adapter);
		binding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
				SortMode newMode = (SortMode) adapterView.getItemAtPosition(position);
				emoteAdapter.setMode(newMode);
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});


		SearchView searchField = this.findViewById(R.id.search_view);
		searchField.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				Log.i(TAG, "onQueryTextChange: " + newText);
				emoteAdapter.setSearchFilter(newText);
				return true;
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		startService(new Intent(this, EmoticonService.class));
		bindService(new Intent(this, EmoticonService.class), emoteServiceConnection, Context.BIND_AUTO_CREATE);
		SortMode mode = SortMode.valueOf(getPreferences().getString(PREF_SORT_MODE, SortMode.ALL.name()));
		binding.spinner.setSelection(mode.ordinal());
	}

	@Override
	public void onStop() {
		super.onStop();
		unbindService(emoteServiceConnection);
		SharedPreferences.Editor editor = getPreferences().edit();
		editor.putString(PREF_SORT_MODE, emoteAdapter.getMode().name());
		editor.apply();
	}

	@Override
	protected void onBackendConnected() {
		if (getIntent().getAction().equals(ACTION_PICK_EMOTE)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		if (uuid != null) {
			this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
		}
	}

	private void emoteClicked(Emote emote) {
		emoteClicked(emote.getAliases().get(0));
	}

	private void emoteClicked(String emote) {
		Log.i(TAG, "emote clicked: " + emote);
		if (mConversation != null) {
			switchToConversation(mConversation, emote);
		}
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (emoteAdapter != null) emoteAdapter.setEmoticonService(null);
		if (dbHelper != null) dbHelper.close();
	}

	@Override
	protected void refreshUiReal() {

	}

}

class AsyncEmoteLoader extends AsyncEmoteLoaderBase {
	private WeakReference<ImageView> view;

	AsyncEmoteLoader(EmoticonService emoticonService, ImageView view) {
		super(emoticonService);
		this.view = new WeakReference<>(view);
	}

	@Override
	protected void onPostExecute(Drawable[] drawable) {
		super.onPostExecute(drawable);
		if (drawable == null || drawable[0] == null) return;
		ImageView v = this.view.get();
		if (v != null) {
			v.setImageDrawable(new FitDrawable(drawable[0]));
		}
	}
}