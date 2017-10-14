package eu.siacs.conversations.emotes;

import java.util.Collections;
import java.util.List;

public class Emote {
	private final String imageName;
	private final int width;
	private final int height;
	private final List<String> aliases;

	public Emote(String imageName, int width, int height, List<String> aliases) {
		this.imageName = imageName;
		this.width = width;
		this.height = height;
		this.aliases = Collections.unmodifiableList(aliases);
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

	public List<String> getAliases() {
		return this.aliases;
	}
}
