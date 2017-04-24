package eu.siacs.conversations.emotes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.JsonReader;
import android.util.Log;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import eu.siacs.conversations.services.XmppConnectionService;

public class EmoticonService {
	private XmppConnectionService xmppConnectionService = null;
	private Map<String, String> emotes;
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
		String imageName = this.emotes.get(name);

		if (imageName == null) return null;
		Log.v("emote service", "translated emote " + name + " -> " + imageName);
		Drawable image = this.images.get(imageName);
		if (image == null) image = loadImage(imageName);
		return image;
	}

	public Drawable tryGetEmote(String name) {
		Log.v("emote service", "emote " + name + " requested");
		String imageName = this.emotes.get(name);

		if (imageName == null) return null;
		Log.v("emote service", "translated emote " + name + " -> " + imageName);
		Drawable image = this.images.get(imageName);
//		if (image == null) image = loadImage(imageName);
		return image;
	}

	private Drawable loadImage(String imageName) {
		Log.i("emote service", "loading image " + imageName);
		try (ZipFile zipFile = new ZipFile(this.file);
			InputStream stream = zipFile.getInputStream(zipFile.getEntry(imageName))) {
			BitmapFactory.Options options =  new BitmapFactory.Options();
			options.inDensity = 40 * 3;

			Bitmap image = BitmapFactory.decodeStream(stream, null, options);
			BitmapDrawable drawable = new BitmapDrawable(this.xmppConnectionService.getResources(), image);
			drawable.setFilterBitmap(false);
			int width = drawable.getIntrinsicWidth();
			int height = drawable.getIntrinsicHeight();
			drawable.setBounds(0, 0, width > 0 ? width : 0, height > 0 ? height : 0);

			this.images.put(imageName, drawable);
			return drawable;
		} catch (IOException e) {
			Log.e("emote service", "failed to load image " + imageName, e);
			return null;
		}
	}

	public void loadPack(File file) {
		loadPackJson(file);
	}

	private void loadPackJson(File file) {
		Log.i("emote service", "begin parsing emote json");
		try (ZipFile zipFile = new ZipFile(file);
			 InputStream inputStream = zipFile.getInputStream(zipFile.getEntry("emoticons.json"));
			 Reader readerx = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
			 JsonReader reader = new JsonReader(readerx);
		) {
			Map<String, String> newEmotes = new HashMap<>(2000);
			reader.beginObject();
			while (reader.hasNext()) {
				String key = reader.nextName();
				Log.v("emote service", "encountered key " + key);
				if ("emotes".equals(key)) {
					reader.beginObject();
					while (reader.hasNext()) {
						String name = reader.nextName();
						String imageName = reader.nextString();
						newEmotes.put(name, imageName);
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

	private void loadPackPidgin(File file) {
		try (ZipFile zipFile = new ZipFile(file)) {
			int count = 25;

			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				System.out.println(entry);
				if (count-- <= 0) break;;
			}
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (ZipFile zipFile = new ZipFile(file);
			 InputStream inputStream = zipFile.getInputStream(zipFile.getEntry("theme"));
			 Reader readerx = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
			 BufferedReader reader = new BufferedReader(readerx)
		) {
			String line = null;
			boolean seenCategory = false;
			Map<String, String> newEmotes = new HashMap<>(2000);
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) continue;
				if (line.matches("^\\[[^\\]]*\\]")) {
					seenCategory = true;
					continue;
				}
				if (seenCategory) {
					String parts[] = line.trim().split("\\s+");
					int start = "!".equals(parts[0]) ? 1 : 0;
					String emote = parts[start + 0];
					for (int i = start + 1; i < parts.length; i++) {

						newEmotes.put(parts[i], emote);
					}
				}
			}
			synchronized (this.emotes) {
				this.currentPack = file.getName();
				this.file = file;
				this.emotes.clear();
				this.emotes.putAll(newEmotes);
				this.images.evictAll();
			}
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
