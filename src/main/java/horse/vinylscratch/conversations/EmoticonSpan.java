package horse.vinylscratch.conversations;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.Toast;

import static android.content.Context.CLIPBOARD_SERVICE;

public class EmoticonSpan extends ImageSpan {
	private final String text;

	public EmoticonSpan(Drawable d, String text) {
		super(d);
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void onClick(@NonNull View widget) {
		Toast.makeText(widget.getContext(), this.text, Toast.LENGTH_SHORT).show();
	}
	public void onLongPress(@NonNull View widget) {
		Context context = widget.getContext();
		ClipboardManager clipBoardManager = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
		if (clipBoardManager != null) {
			ClipData clipData = ClipData.newPlainText("Emoticon Name", this.getText());
			clipBoardManager.setPrimaryClip(clipData);
		}
	}
}
