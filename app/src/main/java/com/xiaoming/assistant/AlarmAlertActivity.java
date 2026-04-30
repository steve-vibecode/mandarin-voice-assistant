package com.xiaoming.assistant;

import android.app.Activity;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

public class AlarmAlertActivity extends Activity {
    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
    
        String title = getIntent().getStringExtra("title");
        String msg = getIntent().getStringExtra("message");
        if (title == null) title = "小明提醒";
        if (msg == null) msg = "时间到了。";
    
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.rgb(153, 27, 27));
    
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(48);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, 1);
    
        TextView m = new TextView(this);
        m.setText(msg + "\n\n（点击任意位置停止）");
        m.setTextColor(Color.WHITE);
        m.setTextSize(28);
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, 40, 0, 0);
    
        root.addView(t);
        root.addView(m);
    
        // 🔥 KEY: tap anywhere to stop
        root.setOnClickListener(v -> finish());
    
        setContentView(root);
    
        startAlarmSound();
    }

    private void startAlarmSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) ringtone.play();
        } catch(Exception ignored){}
        try {
            vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = new long[]{0,700,300,700,300,1000};
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern,0));
                else vibrator.vibrate(pattern,0);
            }
        } catch(Exception ignored){}
    }

    private void stopAlarmSound() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch(Exception ignored){}
        try { if (vibrator != null) vibrator.cancel(); } catch(Exception ignored){}
    }

    @Override protected void onDestroy() {
        stopAlarmSound();
        super.onDestroy();
    }
}
