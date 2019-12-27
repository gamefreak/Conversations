package horse.vinylscratch.conversations;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Used to scale a drawable without adjusting the drawable's bounds.
 *
 * There was an issue where putting the shared drawable instances into ImageViews was changing
 * the drawn size in the message view.
 */
public class FitDrawable extends Drawable {
	static final String TAG = "FitDrawable";
	private Drawable image;

	public FitDrawable(@NonNull Drawable image) {
		super();
		this.image = image;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Matrix matrix = new Matrix();
		RectF src = new RectF(image.getBounds());
		RectF dst = new RectF(this.getBounds());
		matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);

		canvas.save();
		canvas.concat(matrix);
		image.draw(canvas);
		canvas.restore();
	}

	public Drawable getChildImage() {
		return this.image;
	}

	// Mandatory overrides
	@Override
	public int getOpacity() {
		return image.getOpacity();
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter) {
		image.setColorFilter(colorFilter);
	}

	@Override
	public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
		image.setAlpha(alpha);
	}
}
