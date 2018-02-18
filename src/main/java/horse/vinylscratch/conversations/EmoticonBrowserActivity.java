package horse.vinylscratch.conversations;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import eu.siacs.conversations.R;

public class EmoticonBrowserActivity extends Activity {
	public static final int REQUEST_CHOOSE_EMOTE = 0x7dd3a842;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_emoticon_browser);
	}

	private void emoteSelectedStub() {
		Intent response = new Intent();
		response.putExtra("emote", ":vinylstare:");
		setResult(RESULT_FIRST_USER, response);;
	}
}
