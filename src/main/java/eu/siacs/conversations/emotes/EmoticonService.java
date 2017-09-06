package eu.siacs.conversations.emotes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import eu.siacs.conversations.services.XmppConnectionService;
import pl.droidsonroids.gif.GifDrawable;

public class EmoticonService {
	private XmppConnectionService xmppConnectionService = null;
	private Map<String, Emote> emotes;
	private File file = null;
	private String currentPack = null;
	private LruCache<String, Drawable> images;

	public EmoticonService(XmppConnectionService service) {
		this.xmppConnectionService = service;
		this.emotes = new HashMap<>(4000);
		this.images = new LruCache<>(256);
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
	private AnimationDrawable generateColorAnimation() {
		AnimationDrawable animationDrawable = new AnimationDrawable();

		for (int i = 0; i < 16; i++) {
			ShapeDrawable shape = new ShapeDrawable(new RectShape());
			shape.getPaint().setColor(0xff000000 | (0xff << i));
			shape.setIntrinsicWidth(72);
			shape.setIntrinsicHeight(72);
			animationDrawable.addFrame(shape, 100);
		}

		animationDrawable.setBounds(0, 0, 72, 72);
		animationDrawable.setOneShot(false);
		return animationDrawable;
	}
	private Drawable loadImage(Emote emote) {
		String imageName = emote.getImageName();
		Log.i("emote service", "loading image " + imageName);
		String emotePath = "emotes/" + imageName;
		try (ZipFile zipFile = new ZipFile(this.file);
			InputStream stream = zipFile.getInputStream(zipFile.getEntry(emotePath))) {
			DisplayMetrics metrics = xmppConnectionService.getResources().getDisplayMetrics();

			ZipEntry entry = zipFile.getEntry(emotePath);
			Drawable drawable = null;

			if (emotePath.endsWith(".gif")) {
				byte[] buffer = new byte[(int)entry.getSize()];
				int bytesRead = 0;
				while (bytesRead < buffer.length) {
					bytesRead += stream.read(buffer, bytesRead, buffer.length - bytesRead);
				}
//				drawable = new GifDrawable(buffer);
				drawable = generateColorAnimation();
			} else {
				Bitmap image = BitmapFactory.decodeStream(stream);
				drawable = new BitmapDrawable(this.xmppConnectionService.getResources(), image);
			}
			drawable.setFilterBitmap(false);
			int width = (int)(drawable.getIntrinsicWidth() * Math.ceil(metrics.density));
			int height = (int)(drawable.getIntrinsicHeight() * Math.ceil(metrics.density));
			Log.i("emote loader", String.format("translated %dx%d -> %dx%d @ %g", drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), width, height, metrics.density));
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
			 InputStream inputStream = zipFile.getInputStream(zipFile.getEntry("emotes/emoticons.json"));
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
						String imageName = reader.nextName();
						reader.beginObject();
						int width = 0, height = 0;
						List<String> aliases = new ArrayList<>();
						while (reader.hasNext()) {
							String key2 = reader.nextName();
							if ("width".equals(key2)) width = reader.nextInt();
							else if ("height".equals(key2)) height = reader.nextInt();
							else if ("aliases".equals(key2)) {
								reader.beginArray();
								while (reader.hasNext()) {
									aliases.add(reader.nextString());
								}
								reader.endArray();
							} else {
								reader.skipValue();
							}

						}
						reader.endObject();

						Emote emote = new Emote(imageName, width, height);
						for (String name : aliases) {
							newEmotes.put(name, emote);
						}
					}
					reader.endObject();
				} else {
					reader.skipValue();
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
		} catch (ZipException  e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}
}
