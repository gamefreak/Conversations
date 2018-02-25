package horse.vinylscratch.conversations.entities;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class EmoteDbHelper extends SQLiteOpenHelper {
	public static final int DATABASE_VERESION = 1;
	public static final String DATABASE_NAME = "emotes.db";

	public EmoteDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERESION);
	}
	@Override
	public void onCreate(SQLiteDatabase sql) {
		sql.execSQL("CREATE TABLE recent_emotes(\n" +
				"  emote VARCHAR(128) PRIMARY KEY,\n" +
				"  hit_count INTEGER NOT NULL DEFAULT 1,\n" +
				"  last_use TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
				");");
		sql.execSQL("CREATE INDEX recent_emotes_hit_count_idx ON recent_emotes(hit_count DESC)");
		sql.execSQL("CREATE INDEX recent_emotes_last_use_idx ON recent_emotes(last_use DESC)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase sql, int ov, int nv) {
		System.out.println("onUpgrade");
	}
}
