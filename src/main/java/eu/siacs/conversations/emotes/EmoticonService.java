package eu.siacs.conversations.emotes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.DisplayMetrics;
import android.util.JsonReader;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import eu.siacs.conversations.services.XmppConnectionService;

public class EmoticonService {
	private XmppConnectionService xmppConnectionService = null;
	private Map<String, Emote> emotes;
	private File file = null;
	private String currentPack = null;
	private LruCache<String, Drawable> images;

	public EmoticonService(XmppConnectionService service) {
		this.xmppConnectionService = service;
		this.emotes = new HashMap<>(4000);
		this.images = new LruCache<>(128);
	}

	public boolean hasPack() {
		return this.currentPack != null;
	}

	public String getCurrentPack() {
		return this.currentPack;
	}

	public boolean isEmote(String name) {
		return this.emotes.containsKey(name);
	}

	public Drawable getEmote(String name) {
		Log.v("emote service", "emote " + name + " requested");
		Emote emote = this.emotes.get(name);

		if (emote == null) return null;
		Log.v("emote service", "translated emote " + name + " -> " + emote.getImageName());
		Drawable image = this.images.get(emote.getImageName());
		if (image == null) image = loadImage(emote);
		return image;
	}

	public Drawable makePlaceholder(String name) {
		return this.makePlaceholder(this.emotes.get(name));
	}
	public Drawable makePlaceholder(Emote emote) {
		RectShape shape = new RectShape();
		DisplayMetrics metrics = xmppConnectionService.getResources().getDisplayMetrics();
		ShapeDrawable sd = new ShapeDrawable(shape);
		sd.getPaint().setColor(0xff0000ff);
		sd.getPaint().setStyle(Paint.Style.FILL);
		sd.setBounds(
				0,
				0,
				(int)(emote.getWidth() * Math.ceil(metrics.density)),
				(int)(emote.getHeight() * Math.ceil(metrics.density))
		);
//		Log.i("emote loader", String.format("make placeholder %s %dx%d -> %dx%d", emote.getImageName(), emote.getWidth(), emote.getHeight(), sd.getBounds().width(), sd.getBounds().height()));
		return sd;
	}

	public Drawable tryGetEmote(String name) {
		Log.v("emote service", "emote " + name + " requested");
		Emote emote = this.emotes.get(name);

		if (emote == null) return null;
		String imageName = emote.getImageName();
		Log.v("emote service", "translated emote " + name + " -> " + imageName);
		Drawable image = this.images.get(imageName);
//		if (image == null) image = loadImage(imageName);
		return image;
	}

	private Drawable loadImage(Emote emote) {
		String imageName = emote.getImageName();
		Log.i("emote service", "loading image " + imageName);
		try (ZipFile zipFile = new ZipFile(this.file);
			InputStream stream = zipFile.getInputStream(zipFile.getEntry(imageName))) {
			DisplayMetrics metrics = xmppConnectionService.getResources().getDisplayMetrics();

			Bitmap image = BitmapFactory.decodeStream(stream);
			BitmapDrawable drawable = new BitmapDrawable(this.xmppConnectionService.getResources(), image);
			drawable.setFilterBitmap(false);
			int width = (int)(image.getWidth() * Math.ceil(metrics.density));
			int height = (int)(image.getHeight() * Math.ceil(metrics.density));
			Log.i("emote loader", String.format("translated %dx%d -> %dx%d @ %g", image.getWidth(), image.getHeight(), width, height, metrics.density));
			drawable.setBounds(0, 0, width > 0 ? width : 0, height > 0 ? height : 0);

			this.images.put(imageName, drawable);
			return drawable;
		} catch (IOException e) {
			Log.e("emote service", "failed to load image " + imageName, e);
			return null;
		}
	}

	public void loadPack(File file) {
		if (file == null) {
			synchronized (this.emotes) {
				this.currentPack = null;
				this.file = null;
				this.emotes.clear();
				this.images.evictAll();
				Log.i("emote service", "emote active emotes cleared");
			}
		} else {
			loadPackJson(file);
		}
	}

	public File getPackDirectory() {
		return new File(xmppConnectionService.getFilesDir(), "emoticons");
	}

	private void loadPackJson(File file) {
		Log.i("emote service", "begin parsing emote json");
		try (ZipFile zipFile = new ZipFile(file);
			 InputStream inputStream = zipFile.getInputStream(zipFile.getEntry("emoticons.json"));
			 Reader readerx = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
			 JsonReader reader = new JsonReader(readerx);
		) {
			Map<String, Emote> newEmotes = new HashMap<>(2000);
			reader.beginObject();
			while (reader.hasNext()) {
				String key = reader.nextName();
				Log.v("emote service", "encountered key " + key);
				if ("emotes".equals(key)) {
					reader.beginObject();
					while (reader.hasNext()) {
						String name = reader.nextName();
						String imageName = reader.nextString();

						try (InputStream imageStream = zipFile.getInputStream(zipFile.getEntry(imageName))) {
							BitmapFactory.Options options = new BitmapFactory.Options();
							options.inJustDecodeBounds = true;
							BitmapFactory.decodeStream(imageStream, null, options);
							int width = options.outWidth;
							int height = options.outHeight;
							newEmotes.put(name, new Emote(imageName, width, height));
						}
					}
					reader.endObject();
				}
			}
			reader.endObject();
			synchronized (this.emotes) {
				this.currentPack = file.getName();
				this.file = file;
				this.emotes.clear();
				this.emotes.putAll(newEmotes);
				this.images.evictAll();
				Log.i("emote service", "emote data load complete (found " + this.emotes.size() + " emotes)");
			}
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
