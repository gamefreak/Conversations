package horse.vinylscratch.conversations;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.GridView;


public class AutoColumnSizingGridView extends GridView {
	static final int PREFERRED_COLUMN_SIZE = 64 + 2 * 8; // 8px padding on either side

	public AutoColumnSizingGridView(Context context) {
		super(context);
	}

	public AutoColumnSizingGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AutoColumnSizingGridView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int targetSize = (int)(metrics.density * PREFERRED_COLUMN_SIZE);

		int viewWidth =  View.MeasureSpec.getSize(widthMeasureSpec);
		int numColumns = (int)Math.floor(viewWidth / targetSize);
		int calculatedWidth = viewWidth / Math.max(1, numColumns);

		this.setColumnWidth(calculatedWidth);
	}
}
