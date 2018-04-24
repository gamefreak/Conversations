package eu.siacs.conversations.ui.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.util.AttributeSet;

import eu.siacs.conversations.R;

public class CollapsibleTextView extends android.support.v7.widget.AppCompatTextView {
	public interface StateListener {
		void onOverflowStateChanged(boolean hasOverflow);
		void onCollapse();
		void onExpand();
	}


	public CollapsibleTextView(Context context) {
		super(context);
	}

	public CollapsibleTextView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		initXMLAttrs(context, attrs);
	}

	public CollapsibleTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		initXMLAttrs(context, attrs);
	}

	private void initXMLAttrs(Context context, AttributeSet attrs) {
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CollapsibleTextView);
		isCollapsed = (a.getBoolean(R.styleable.CollapsibleTextView_collapsed, false));
		lineLimit = (a.getInteger(R.styleable.CollapsibleTextView_lineLimit, DEFAULT_MAX_LINES));
		a.recycle();
	}


	public static final int DEFAULT_MAX_LINES = 3;
	private boolean hasOverflow = false;
	private boolean isCollapsed = false;
	private boolean hasLayoutYet = false;

	private StateListener stateListener = null;
	private int lineLimit = DEFAULT_MAX_LINES;



	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (!hasLayoutYet) {
			if (isCollapsed) doCollapse(); else doExpand();
			hasLayoutYet = true;
		}
		int height = getLayout().getHeight();
		int lines = getLayout().getLineCount();
		if (lines > lineLimit && !hasOverflow) {
			hasOverflow = true;
			if (this.stateListener != null) stateListener.onOverflowStateChanged(true);
		} else if (lines <= lineLimit && hasOverflow) {
			hasOverflow = false;
			if (this.stateListener != null) stateListener.onOverflowStateChanged(false);
		}
	}


	public void setLineLimit(int lineLimit) {
		this.lineLimit = lineLimit;
		this.requestLayout();
	}

	public int getLineLimit() {
		return lineLimit;
	}


	public boolean hasOveflow() {
		return hasOverflow;
	}

	private void doCollapse() {
		isCollapsed = true;
		if (this.stateListener != null) stateListener.onCollapse();
		Layout layout = getLayout();
		if (layout == null) return;

		int height = layout.getLineTop(Math.min(layout.getLineCount(),  lineLimit));

		ObjectAnimator.ofInt(this, "maxHeight", getHeight(), height).start();
	}
	public void collapse() {
		if (isCollapsed) return;
		doCollapse();
	}


	private void doExpand() {
		isCollapsed = false;

		if (this.stateListener != null) stateListener.onExpand();
		if (getLayout() == null) return;
		int lineCount = getLayout().getLineCount();

		int height = getLayout().getLineTop(lineCount);

		ObjectAnimator animator = ObjectAnimator.ofInt(this, "maxHeight", getHeight(), height);

		animator.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {

			}

			@Override
			public void onAnimationEnd(Animator animation) {
				CollapsibleTextView.this.setMaxHeight(Integer.MAX_VALUE);
			}

			@Override
			public void onAnimationCancel(Animator animation) {

			}

			@Override
			public void onAnimationRepeat(Animator animation) {

			}
		});
		animator.start();
	}

	public void expand() {
		if (!isCollapsed) return;
		doExpand();
	}

	public void toggle() {
		setCollapsed(!isCollapsed);
	}

	public boolean isCollapsed() {
		return isCollapsed;
	}

	public void setCollapsed(boolean collapsed) {
		if (isCollapsed == collapsed) return;
		if (collapsed) {
			collapse();
		} else {
			expand();
		}
	}

	public StateListener getStateListener() {
		return stateListener;
	}

	public void setStateListener(StateListener stateListener) {
		this.stateListener = stateListener;
	}
}
