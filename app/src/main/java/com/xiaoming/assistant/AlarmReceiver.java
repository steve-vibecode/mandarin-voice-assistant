package com.xiaoming.assistant;

import android.app.*;
import android.content.*;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        if (title == null) title = "小明提醒";
        if (message == null) message = "时间到了。";

        Intent alert = new Intent(context, AlarmAlertActivity.class);
        alert.putExtra("title", title);
        alert.putExtra("message", message);
        alert.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent full = PendingIntent.getActivity(context, (int)(System.currentTimeMillis()%Integer.MAX_VALUE), alert, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, MainActivity.CHANNEL_ID) : new Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full);

        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE), b.build());

        context.startActivity(alert);
    }
}
