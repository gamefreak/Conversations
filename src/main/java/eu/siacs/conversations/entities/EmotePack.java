package eu.siacs.conversations.entities;

import java.util.HashMap;
import java.util.Map;

public class EmotePack {
	private final Map<String, String> emotes;
	public EmotePack(Map<String, String> emotes) {
		this.emotes = emotes;
	}

	public String getFileForAlias(String alias) {
		return this.emotes.get(alias);
	}

	public static EmotePack parse(String text) {
		return parseTheme(text);
	}

	public static EmotePack parseTheme(String text) {
		String lines[] = text.split("\\r?\\n");
		Map<String, String> emoteMap = new HashMap<>(lines.length);
		boolean seenCategory = false;
		for (String line : lines) {
			line = line.trim();
			if (line.trim().isEmpty()) continue;
			if (line.matches("^\\[[^\\]]*\\]")) {
				seenCategory = true;
				continue;
			}
			if (seenCategory) {
				String parts[] = line.trim().split("\\s+");
				int start = "!".equals(parts[0]) ? 1 : 0;
				String filename = parts[start + 0];
				for (int i = start + 1; i < parts.length; i++) {

					emoteMap.put(parts[i], filename);
				}
			}
		}

		return new EmotePack(emoteMap);
	}
}
