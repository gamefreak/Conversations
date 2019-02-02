package eu.siacs.conversations.emotes;

import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Emote {
	public static final String PATTERN = "(:[\\w\\-?]+:|:-?[\\w()]|\\([\\w*{}?]\\))";
	private final String imageName;
	private final int width;
	private final int height;
	private final List<String> aliases;
	private final int[] dpiLevels;

	public Emote(String imageName, int width, int height,  List<String> aliases, @NonNull List<Integer> dpiLevels) {
		this.imageName = imageName;
		this.width = width;
		this.height = height;
		this.aliases = Collections.unmodifiableList(aliases);
		this.dpiLevels = new int[dpiLevels.size()];
		for (int i = 0; i < this.dpiLevels.length; i++) this.dpiLevels[i] = dpiLevels.get(i);
		Arrays.sort(this.dpiLevels);

	}

	public String getImageName() {
		return this.imageName;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public String getFirstAlias() {
		return this.aliases.isEmpty() ? this.getImageName() : this.aliases.get(0);
	}

	public List<String> getAliases() {
		return this.aliases;
	}

	public int[] getDpiLevels() { return this.dpiLevels; }

	public int selectDpiForScreen(int screenDPI) {
		// If the screen DPI is 1 we can skip this whole thing.
		if (screenDPI == 1) return 1;
		// If we have no entries, we only have the base image
		if (dpiLevels.length == 0) return 1;
		if (dpiLevels.length == 1) return dpiLevels[0];
		// find the first entry above the screen DPI
		for (int level : dpiLevels) {
			// Since it is already sorted we can just return right away
			if (level >= screenDPI) return level;
		}
		/// if none higher than the screen dpi return the highest
		return dpiLevels[dpiLevels.length - 1];
	}
}
