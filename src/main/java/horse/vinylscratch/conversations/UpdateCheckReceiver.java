package horse.vinylscratch.conversations;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateCheckReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		VersionCheckTask task = new VersionCheckTask(context);
		task.execute();
	}
}
