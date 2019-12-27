package horse.vinylscratch.conversations;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

public class AutoColumnSizingGridLayoutManager extends GridLayoutManager {
    static final int PREFERRED_COLUMN_SIZE = 64 + 2 * 8; // 8px padding on either side

    private Context context;

    public AutoColumnSizingGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    public AutoColumnSizingGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
        this.context = context;
    }

    public AutoColumnSizingGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
        this.context = context;
    }

    @Override
    public void onMeasure(@NonNull RecyclerView.Recycler recycler, @NonNull RecyclerView.State state, int widthSpec, int heightSpec) {
        super.onMeasure(recycler, state, widthSpec, heightSpec);
        DisplayMetrics metrics = this.context.getResources().getDisplayMetrics();
        int targetSize = (int)(metrics.density * PREFERRED_COLUMN_SIZE);

        int viewWidth =  View.MeasureSpec.getSize(widthSpec);
        int numColumns = (int)Math.floor(viewWidth / targetSize);
        int calculatedWidth = viewWidth / Math.max(1, numColumns);

        this.setSpanCount(numColumns);
    }
}
