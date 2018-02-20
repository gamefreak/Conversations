package horse.vinylscratch.conversations;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.emotes.Emote;
import eu.siacs.conversations.emotes.EmoticonService;
import eu.siacs.conversations.ui.XmppActivity;


public class EmoticonBrowserActivity extends XmppActivity {
	class EmoteAdapter extends BaseAdapter {
		private EmoticonService emoticonService = null;
		private int lastPackVersion = -1;
		private List<Emote> emotes = new ArrayList<>();
		private String searchFilter = null;
		private Context context = null;

		//		public EmoteAdapter() {
		public EmoteAdapter(@NonNull Context context) {
			this.context = context;
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

		private void applyFilter() {
			if (this.emoticonService == null) {
				this.emotes.clear();
			} else if (this.searchFilter == null || this.searchFilter.trim().isEmpty()) {
				this.emotes.clear();
				this.emotes.addAll(this.emoticonService.getAllEmotes());
			} else {
				this.emotes.clear();

				String filter = this.searchFilter.toLowerCase();
				for (Emote emote : this.emoticonService.getAllEmotes()) {
					for (String alias : emote.getAliases()) {
						if (alias.toLowerCase().contains(filter)) {
							this.emotes.add(emote);
							break;
						}
					}
				}
			}
			Collections.sort(this.emotes, new Comparator<Emote>() {
				@Override
				public int compare(Emote left, Emote right) {
					return left.getAliases().get(0).compareTo(right.getAliases().get(0));
				}
			});
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
	}


	static final String TAG = "EmoteBrowserActivity";
	public static final int REQUEST_CHOOSE_EMOTE = 0x7dd3a842;


	private EmoticonService emoticonService = null;
	private EmoteAdapter emoteAdapter = null;

	private GridView grid = null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_emoticon_browser);

		grid = (GridView) findViewById(R.id.emote_grid);
		emoteAdapter = new EmoteAdapter(getApplicationContext());
		grid.setAdapter(emoteAdapter);
		grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
				Emote emote = emoteAdapter.getEmote(position);
				emoteClicked(emote);
			}
		});


		SearchView searchField = findViewById(R.id.search_field);
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

	private void emoteClicked(Emote emote) {
		Log.i(TAG, "emote clicked: " + emote.getImageName());
		Intent response = new Intent();
		response.putExtra("emote", emote.getAliases().get(0));
		setResult(RESULT_OK, response);
		finish();
	}


	@Override
	protected void refreshUiReal() {

	}

	protected void onBackendConnected() {

	}
}
