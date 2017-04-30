package eu.siacs.conversations.emotes;

import java.io.File;

public class Emote {
	private final String imageName;
	private final int width;
	private final int height;

	public Emote(String imageName, int width, int height) {
		this.imageName = imageName;
		this.width = width;
		this.height = height;
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
}
