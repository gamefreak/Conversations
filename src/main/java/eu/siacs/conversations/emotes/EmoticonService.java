package eu.siacs.conversations.emotes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.MultiCallback;

class EmoteHolder {
	Drawable drawable;
	// need to hold on to the MultiCallback object since GifDrawable uses a WeakReference
	MultiCallback callback;

	EmoteHolder(@NonNull Drawable drawable, @Nullable MultiCallback callback) {
		this.drawable = drawable;
		this.callback = callback;
	}
}

public class EmoticonService {
	private XmppConnectionService xmppConnectionService = null;
	private Map<String, Emote> emotes;
	private File file = null;
	private String currentPack = null;
	private LruCache<String, EmoteHolder> images;
	private boolean enableAnimations = true;
	private SerialSingleThreadExecutor executor;

	public EmoticonService(XmppConnectionService service) {
		this.xmppConnectionService = service;
		this.emotes = new HashMap<>(4000);
		this.images = new LruCache<>(128);
		this.executor = new SerialSingleThreadExecutor();
	}

	public Executor getExecutor() {
		return executor;
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
		Emote emote = this.emotes.get(name);

		if (emote == null) return null;
		EmoteHolder image = this.images.get(emote.getImageName());
		if (image == null) image = loadImage(emote);
		return image.drawable;
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
		EmoteHolder image = this.images.get(imageName);
		if (image == null) return null;
		return image.drawable;
	}

	private EmoteHolder loadImage(Emote emote) {
		String imageName = emote.getImageName();
		Log.i("emote service", "loading image " + imageName);
		String emotePath = "emotes/" + imageName;
		try (ZipFile zipFile = new ZipFile(this.file);
			InputStream stream = zipFile.getInputStream(zipFile.getEntry(emotePath))) {
			DisplayMetrics metrics = xmppConnectionService.getResources().getDisplayMetrics();

			ZipEntry entry = zipFile.getEntry(emotePath);
			EmoteHolder holder = null;
			Drawable drawable = null;
			if (this.enableAnimations && emotePath.endsWith(".gif")) {
				byte[] buffer = new byte[(int)entry.getSize()];
				int bytesRead = 0;
				while (bytesRead < buffer.length) {
					bytesRead += stream.read(buffer, bytesRead, buffer.length - bytesRead);
				}
				final GifDrawable gifDrawable = new GifDrawable(buffer);
				MultiCallback callback = new MultiCallback(true);

				gifDrawable.setCallback(callback);

				drawable = gifDrawable;
				holder = new EmoteHolder(gifDrawable, callback);
			} else {
				Bitmap image = BitmapFactory.decodeStream(stream);
				drawable = new BitmapDrawable(this.xmppConnectionService.getResources(), image);
				holder = new EmoteHolder(drawable, null);
			}
			drawable.setFilterBitmap(false);
			int width = (int)(drawable.getIntrinsicWidth() * Math.ceil(metrics.density));
			int height = (int)(drawable.getIntrinsicHeight() * Math.ceil(metrics.density));
//			Log.v("emote loader", String.format("translated %dx%d -> %dx%d @ %g", drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), width, height, metrics.density));
			drawable.setBounds(0, 0, width > 0 ? width : 0, height > 0 ? height : 0);

			this.images.put(imageName, holder);
			return holder;
		} catch (IOException e) {
			Log.e("emote service", "failed to load image " + imageName, e);
			return null;
		}
	}

	public void loadPack(final File file) {
		if (file == null) {
			synchronized (this.emotes) {
				this.currentPack = null;
				this.file = null;
				this.emotes.clear();
				this.images.evictAll();
				Log.i("emote service", "emote active emotes cleared");
			}
		} else {
			AsyncTask.execute(new Runnable() {
				@Override
				public void run() {
					loadPackJson(file);
				}
			});
		}
	}

	public void setEnableAnimations(boolean newState) {
		if (this.enableAnimations != newState) {
			this.enableAnimations = newState;
			// could also loop through and evict only gif
			this.images.evictAll();
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
