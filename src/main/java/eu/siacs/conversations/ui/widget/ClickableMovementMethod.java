package eu.siacs.conversations.ui.widget;

import android.os.Handler;
import android.text.Layout;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import horse.vinylscratch.conversations.EmoticonSpan;

public class ClickableMovementMethod extends ArrowKeyMovementMethod {
	private static final String TAG = "Converstations/ClickableMovementMethod";
	private Handler longPressHandler = new Handler();
	private Runnable longPressCallback = null;


	private void cancelLongPress() {
		this.longPressHandler.removeCallbacks(this.longPressCallback);
	}

	private void startLongPress(Runnable callback) {
		this.cancelLongPress();
		this.longPressCallback = callback;
		this.longPressHandler.postDelayed(callback, ViewConfiguration.getLongPressTimeout());
	}

	// Just copied from android.text.method.LinkMovementMethod
	private int[] getLineOffset(TextView widget, MotionEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();
		x -= widget.getTotalPaddingLeft();
		y -= widget.getTotalPaddingTop();
		x += widget.getScrollX();
		y += widget.getScrollY();
		Layout layout = widget.getLayout();
		int line = layout.getLineForVertical(y);
		int off = layout.getOffsetForHorizontal(line, x);
		return new int[]{line, off};
	}

	@Override
	public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_CANCEL) {
			this.cancelLongPress();
		}

		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			int[] lineOff = this.getLineOffset(widget, event);
			int line = lineOff[0], off = lineOff[1];
			EmoticonSpan[] emotes = buffer.getSpans(off, off, EmoticonSpan.class);
			if (emotes.length > 0) {
				this.cancelLongPress();
				EmoticonSpan emote = emotes[0];
				this.startLongPress(() -> {
					emote.onLongPress(widget);
				});
			}
		}


		if (event.getAction() == MotionEvent.ACTION_UP) {
			int[] lineOff = this.getLineOffset(widget, event);
			int line = lineOff[0], off = lineOff[1];

			EmoticonSpan[] emote = buffer.getSpans(off, off, EmoticonSpan.class);
			if (emote.length > 0) {
				this.cancelLongPress();
				emote[0].onClick(widget);
				return true;
			}

			ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
			if (link.length != 0) {
				link[0].onClick(widget);
				return true;
			}
		}
		return super.onTouchEvent(widget, buffer, event);
	}

	public static ClickableMovementMethod getInstance() {
		if (sInstance == null) {
			sInstance = new ClickableMovementMethod();
		}
		return sInstance;
	}

	private static ClickableMovementMethod sInstance;
}