package horse.vinylscratch.conversations;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.emotes.Emote;
import eu.siacs.conversations.emotes.EmoticonService;
import eu.siacs.conversations.ui.XmppActivity;
import horse.vinylscratch.conversations.entities.EmoteDbHelper;
import horse.vinylscratch.conversations.entities.RecentEmoteContract.RecentEmote;


public class EmoticonBrowserActivity extends XmppActivity {
	enum BrowserMode {
		FREQUENT("Most Used"),
		RECENT("Recently Used"),
		ALL("All");

		final private String label;

		BrowserMode(String label) {
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
		private BrowserMode mode = BrowserMode.ALL;
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

		private List<Emote> getSortedEmotes() {
			if (this.emoticonService == null) return new ArrayList<>();
			if (this.mode == BrowserMode.ALL) {
				List<Emote> theEmotes = this.emoticonService.getAllEmotes();
				Collections.sort(theEmotes, new Comparator<Emote>() {
					@Override
					public int compare(Emote left, Emote right) {
						return left.getAliases().get(0).compareTo(right.getAliases().get(0));
					}
				});
				return theEmotes;
			} else {
				String sortBy = (this.mode == BrowserMode.FREQUENT ? RecentEmote.COLUMN_NAME_HIT_COUNT : RecentEmote.COLUMN_NAME_LAST_USE) + " DESC";
				Cursor cursor = db.query(RecentEmote.TABLE_NAME, new String[]{RecentEmote.COLUMN_NAME_EMOTE}, null,  null, null, null, sortBy);
				List emotes = new ArrayList<Emote>();
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
			if (this.emoticonService == null) {
				this.emotes.clear();
			} else if (this.searchFilter == null || this.searchFilter.trim().isEmpty()) {
				this.emotes.clear();
				this.emotes.addAll(getSortedEmotes());
			} else {
				this.emotes.clear();

				String filter = this.searchFilter.toLowerCase();
				for (Emote emote : this.getSortedEmotes()) {
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
			Emote emoticon = getEmote(position);
			Drawable image = emoticonService.getEmote(emoticon.getAliases().get(0));

			image = new FitDrawable(image);


			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View emoteView = convertView != null ? convertView : inflater.inflate(R.layout.emote_preview, parent, false);
			ImageView imageView = (ImageView) emoteView.findViewById(R.id.image);
			TextView textView = (TextView) emoteView.findViewById(R.id.label);
			imageView.setImageDrawable(image);
			textView.setText(emoticon.getAliases().get(0));
			// tried setting it in the XML but it was crashing for some reason
			textView.setTextColor(EmoticonBrowserActivity.this.getPrimaryTextColor());
			return emoteView;
		}

		public void setSearchFilter(String newText) {
			this.searchFilter = newText;
			this.applyFilter();
			this.notifyDataSetChanged();
		}

		public BrowserMode getMode() {
			return mode;
		}

		public void setMode(BrowserMode mode) {
			this.mode = mode;
			this.applyFilter();
			this.notifyDataSetChanged();
		}
	}


	static final String TAG = "EmoteBrowserActivity";
	public static final int REQUEST_CHOOSE_EMOTE = 0x7dd3a842;
	static final String PREF_SORT_MODE = "SORT_MODE";

	private EmoteDbHelper dbHelper = null;


	private EmoticonService emoticonService = null;
	private EmoteAdapter emoteAdapter = null;

	private GridView grid = null;
	private Spinner modeSpinner = null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_emoticon_browser);

		setTitle("");

		grid = (GridView) findViewById(R.id.emote_grid);

		this.dbHelper = new EmoteDbHelper(this);
		emoteAdapter = new EmoteAdapter(this, this.dbHelper.getReadableDatabase());
		grid.setAdapter(emoteAdapter);
		grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
				Emote emote = emoteAdapter.getEmote(position);
				emoteClicked(emote);
			}
		});

		ActionBar actionBar = getActionBar();
		actionBar.setCustomView(R.layout.emoticon_browser_toolbar);
		actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM);

		View actionBarView = actionBar.getCustomView();

		modeSpinner = actionBarView.findViewById(R.id.spinner);
		ArrayAdapter<BrowserMode> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BrowserMode.values());
		modeSpinner.setAdapter(adapter);
		modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
				BrowserMode newMode = (BrowserMode) adapterView.getItemAtPosition(position);
				emoteAdapter.setMode(newMode);
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});


		SearchView searchField = actionBarView.findViewById(R.id.search_view);
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


		bindService(new Intent(this, EmoticonService.class), new ServiceConnection() {
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
		}, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStart() {
		super.onStart();
		BrowserMode mode = BrowserMode.valueOf(getPreferences().getString(PREF_SORT_MODE, BrowserMode.ALL.name()));
		modeSpinner.setSelection(mode.ordinal());
	}

	@Override
	public void onStop() {
		super.onStop();
		SharedPreferences.Editor editor = getPreferences().edit();
		editor.putString(PREF_SORT_MODE, emoteAdapter.getMode().name());
		editor.apply();
	}

	private void emoteClicked(Emote emote) {
		Log.i(TAG, "emote clicked: " + emote.getImageName());
		Intent response = new Intent();
		response.putExtra("emote", emote.getAliases().get(0));
		setResult(RESULT_OK, response);
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (dbHelper != null) dbHelper.close();
	}

	@Override
	protected void refreshUiReal() {

	}

	protected void onBackendConnected() {

	}
}
