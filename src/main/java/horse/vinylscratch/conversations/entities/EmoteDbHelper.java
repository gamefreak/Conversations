package horse.vinylscratch.conversations.entities;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static horse.vinylscratch.conversations.entities.RecentEmoteContract.*;


public class EmoteDbHelper extends SQLiteOpenHelper {
	public static final int DATABASE_VERESION = 1;
	public static final String DATABASE_NAME = "emotes.db";

	public EmoteDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERESION);
	}
	@Override
	public void onCreate(SQLiteDatabase sql) {
		sql.execSQL("CREATE TABLE " + RecentEmote.TABLE_NAME + "(\n" +
				RecentEmote.COLUMN_NAME_EMOTE + " VARCHAR(128) PRIMARY KEY,\n" +
				RecentEmote.COLUMN_NAME_HIT_COUNT+ " INTEGER NOT NULL DEFAULT 1,\n" +
				RecentEmote.COLUMN_NAME_LAST_USE + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
				");");
		sql.execSQL("CREATE INDEX recent_emotes_hit_count_idx ON recent_emotes(hit_count DESC)");
		sql.execSQL("CREATE INDEX recent_emotes_last_use_idx ON recent_emotes(last_use DESC)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase sql, int ov, int nv) {
		System.out.println("onUpgrade");
	}
}
