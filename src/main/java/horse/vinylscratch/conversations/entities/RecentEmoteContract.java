package horse.vinylscratch.conversations.entities;

import android.provider.BaseColumns;

public final class RecentEmoteContract {
	private RecentEmoteContract() {}

	public static class RecentEmote implements BaseColumns {
		public static final String TABLE_NAME = "recent_emotes";
		public static final String _ID = "emote";
		public static final String COLUMN_NAME_EMOTE = "emote";
		public static final String COLUMN_NAME_HIT_COUNT = "hit_count";
		public static final String COLUMN_NAME_LAST_USE = "last_use";
	}
}

