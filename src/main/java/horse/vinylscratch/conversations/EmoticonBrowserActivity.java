package horse.vinylscratch.conversations;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.view.View;
import android.view.ViewGroup;

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

	class EmoteViewHolder extends RecyclerView.ViewHolder {
		private EmotePreviewBinding binding;
		private Drawable drawable = null;

		public EmoteViewHolder(EmotePreviewBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
		}

		public void setDrawable(Drawable drawable) {
			if (this.drawable instanceof GifDrawable) {
				GifDrawable gifDrawable = (GifDrawable)this.drawable;
				if (gifDrawable.getCallback() instanceof MultiCallback) {
					((MultiCallback)gifDrawable.getCallback()).removeView(this.binding.image);
				}
			}

			this.drawable = drawable;

			if (drawable instanceof GifDrawable) {
				GifDrawable gifDrawable = (GifDrawable)drawable;
				if (gifDrawable.getCallback() == null) {
					gifDrawable.setCallback(new MultiCallback());
				}
				if (gifDrawable.getCallback() instanceof MultiCallback) {
					((MultiCallback)gifDrawable.getCallback()).addView(this.binding.image);
				}
			}
			if (drawable != null) {
				this.binding.image.setImageDrawable(new FitDrawable(drawable));
			} else {
				this.binding.image.setImageDrawable(null);
			}

		}
	}

	interface OnEmoteClickListener {
		void onEmoteClick(EmoteAdapter adapter, View view, int position, Emote emote);
	}
	interface OnEmoteLongClickListener {
		boolean onEmoteLongClick(EmoteAdapter adapter, View view, int position, Emote emote);
	}

	class EmoteAdapter extends RecyclerView.Adapter<EmoteViewHolder> {
		private EmoticonService emoticonService = null;
		private int lastPackVersion = -1;
		private List<Emote> emotes = new ArrayList<>();
		private SortMode mode = null;
		private String searchFilter = null;
		private Context context = null;

		private SQLiteDatabase db;

		private OnEmoteClickListener onEmoteClickListener = null;
		private OnEmoteLongClickListener onEmoteLongClickListener = null;

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
			} else if (mode == null) {
				return Collections.emptyList();
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

		public Emote getEmote(int i) {
			return this.emotes.get(i);
		}

		@NonNull
		@Override
		public EmoteViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
			EmotePreviewBinding  binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.emote_preview, viewGroup, false);
			EmoteViewHolder viewHolder = new EmoteViewHolder(binding);
			return viewHolder;
		}

		@Override
		public void onBindViewHolder(@NonNull EmoteViewHolder viewHolder, int i) {
			EmotePreviewBinding binding = viewHolder.binding;
			Emote emoticon = getEmote(i);

			binding.getRoot().setOnClickListener(view -> {
				if (this.onEmoteLongClickListener != null) this.onEmoteClickListener.onEmoteClick(this, view, i, emoticon);
			});

			binding.getRoot().setOnLongClickListener(view -> {
				if (this.onEmoteLongClickListener != null) {
					return this.onEmoteLongClickListener.onEmoteLongClick(this, view, i, emoticon);
				} else {
					return false;
				}
			});

			Drawable image = emoticonService.tryGetEmote(emoticon.getAliases().get(0));
			if (image == null) {
				if (binding.getLoaderTask() != null) {
					binding.getLoaderTask().cancel(false);
				}
				AsyncEmoteLoader task = new AsyncEmoteLoader(emoticonService, viewHolder);
				task.executeOnExecutor(emoticonService().getExecutor(),  emoticon.getAliases().get(0));
				binding.setLoaderTask(task);

				image = emoticonService.makePlaceholder(emoticon);
			}


			viewHolder.setDrawable(image);

			binding.label.setText(emoticon.getAliases().get(0));
		}

		@Override
		public int getItemCount() {
			return this.emotes.size();
		}

		public void setOnEmoteClickListener(OnEmoteClickListener onEmoteClickListener) {
			this.onEmoteClickListener = onEmoteClickListener;
		}

		public void setOnEmoteLongClickListener(OnEmoteLongClickListener onEmoteLongClickListener) {
			this.onEmoteLongClickListener = onEmoteLongClickListener;
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

	/*
	void doDebug(EmotePreviewBinding binding, Drawable image) {
		if (emoticonService.areAnimationsEnabled() && image instanceof GifDrawable) {
			MultiCallback callback = (MultiCallback) image.getCallback();

			if (callback != null) {
				try {

					Class<MultiCallback> mcl = (Class<MultiCallback>) callback.getClass();
					java.lang.reflect.Field fld = mcl.getDeclaredField("mCallbacks");
					fld.setAccessible(true);
					java.util.concurrent.CopyOnWriteArrayList<?> callbackList = (java.util.concurrent.CopyOnWriteArrayList<?>) fld.get(callback);
					binding.counter.setText(callbackList.size()+"");
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (NoSuchFieldException e) {
					e.printStackTrace();
				}
			} else {
				Log.w(TAG, "GetCallback -> null");
				binding.counter.setText("X");
			}
		} else {
			binding.counter.setText(" ");
		}
	}
	//*/

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
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Workaround for animations breaking on rotation
		emoteAdapter.notifyDataSetChanged();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_emoticon_browser);

		setTitle("");

		this.dbHelper = new EmoteDbHelper(this);
		emoteAdapter = new EmoteAdapter(this, this.dbHelper.getReadableDatabase());

		binding.emoteGrid.setAdapter(emoteAdapter);

		emoteAdapter.setOnEmoteClickListener((adapter, view, position, emote) -> {
			emoteClicked(emote);
		});

		emoteAdapter.setOnEmoteLongClickListener((adapter, view, position, emote) -> {
			EmoticonBrowserActivity activity = EmoticonBrowserActivity.this;
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


	class AsyncEmoteLoader extends AsyncEmoteLoaderBase {
		private WeakReference<EmoticonBrowserActivity.EmoteViewHolder> viewHolder;

		AsyncEmoteLoader(EmoticonService emoticonService, EmoteViewHolder viewHolder) {
			super(emoticonService);
			this.viewHolder = new WeakReference<>(viewHolder);
		}

		@Override
		protected void onPostExecute(Drawable[] drawable) {
			super.onPostExecute(drawable);
			if (drawable == null || drawable[0] == null) return;
			EmoteViewHolder vh = this.viewHolder.get();
			if (vh != null) {
				vh.setDrawable(drawable[0]);
			}
		}
	}
}

