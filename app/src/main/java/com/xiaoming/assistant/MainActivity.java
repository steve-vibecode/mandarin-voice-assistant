package com.xiaoming.assistant;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private TextView status;
    private SpeechRecognizer recognizer;
    private Intent speechIntent;
    private TextToSpeech tts;
    private boolean listening = false;
    public static final String CHANNEL_ID = "xiaoming_alarm_channel";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        createNotificationChannel();
        buildUi();
        requestNeededPermissions();
        setupTts();
        setupSpeech();
        status.setText("点一下开始说话\n再点一次停止\n\n可以说：\n小明现在几点\n小明打电话给爸爸\n小明帮我弄个5点的闹钟\n小明帮我弄个5分钟的倒计时");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.rgb(17,24,39));
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40,40,40,40);

        TextView title = new TextView(this);
        title.setText("小明");
        title.setTextColor(Color.WHITE);
        title.setTextSize(56);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null,1);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(24);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(8,1.1f);

        root.addView(title);
        root.addView(status);
        root.setOnClickListener(v -> { if (listening) stopListening(); else startListening(); });
        setContentView(root);
    }

    private void requestNeededPermissions() {
        ArrayList<String> perms = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_CONTACTS);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 100);

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }
    }

    private void setupTts() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                tts.setSpeechRate(0.88f);
            }
        });
    }

    private void speak(String text) {
        status.setText(text);
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "xiaoming");
    }

    private void setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("这个手机没有可用的语音识别服务");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p){ listening=true; status.setText("正在听……"); }
            @Override public void onBeginningOfSpeech(){}
            @Override public void onRmsChanged(float r){}
            @Override public void onBufferReceived(byte[] b){}
            @Override public void onEndOfSpeech(){ listening=false; }
            @Override public void onError(int e){ listening=false; speak("没听清楚，请再点一下。错误代码：" + e); }
            @Override public void onResults(Bundle r){
                listening=false;
                ArrayList<String> texts = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts == null || texts.isEmpty()) { speak("没听到内容。"); return; }
                handleCommand(texts.get(0));
            }
            @Override public void onPartialResults(Bundle p){}
            @Override public void onEvent(int t, Bundle p){}
        });
    }

    private void startListening() {
        requestNeededPermissions();
        if (recognizer == null) { speak("语音识别不可用。"); return; }
        try { recognizer.startListening(speechIntent); }
        catch(Exception e) { speak("无法开始录音：" + e.getMessage()); }
    }

    private void stopListening() {
        try { if (recognizer != null) recognizer.stopListening(); } catch(Exception ignored){}
        listening=false;
        status.setText("已停止。点一下重新开始。");
    }

    private void handleCommand(String raw) {
        String text = raw.replace("，","").replace("。","").replace(" ","");
        if (!text.contains("小明")) { speak("请先叫我小明。"); return; }

        if (text.contains("几点") || text.contains("时间")) {
            speak("现在是" + new SimpleDateFormat("HH点mm分", Locale.CHINA).format(new Date()));
            return;
        }

        if (text.contains("打电话")) {
            String name = text.replace("小明","").replace("帮我","").replace("请","").replace("打电话给","").replace("打电话","").replace("给","").trim();
            if (name.length()==0) { speak("你要打给谁？"); return; }
            String number = findContactNumber(name);
            if (number == null) { speak("通讯录里找不到" + name); return; }
            speak("正在打电话给" + name);
            callNumber(number);
            return;
        }

        if (text.contains("倒计时")) {
            int amount = extractFirstNumber(text);
            if (amount <= 0) { speak("我没听清楚倒计时时间。"); return; }
            long millis;
            String unit;
            if (text.contains("秒")) { millis = amount * 1000L; unit = "秒"; }
            else if (text.contains("小时")) { millis = amount * 60L * 60L * 1000L; unit = "小时"; }
            else { millis = amount * 60L * 1000L; unit = "分钟"; }
            scheduleReminder(System.currentTimeMillis() + millis, "小明倒计时", amount + unit + "倒计时到了。");
            speak("好的，已设置" + amount + unit + "倒计时。");
            return;
        }

        if (text.contains("闹钟")) {
            AlarmTime at = extractAlarmTime(text);
            if (at == null) { speak("我没听清楚闹钟时间。"); return; }
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, at.hour);
            cal.set(Calendar.MINUTE, at.minute);
            cal.set(Calendar.SECOND,0);
            cal.set(Calendar.MILLISECOND,0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH,1);
            scheduleReminder(cal.getTimeInMillis(), "小明闹钟", formatAlarmText(at.hour, at.minute) + "到了。");
            speak("好的，已设置" + formatAlarmText(at.hour, at.minute) + "的闹钟。");
            return;
        }

        speak("我现在会听：几点，打电话，闹钟，倒计时。");
    }

    private String findContactNumber(String name) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { speak("没有通讯录权限。"); return null; }
        Cursor c = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            new String[]{"%" + name + "%"}, null);
        if (c == null) return null;
        try { if (c.moveToFirst()) return c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)); }
        finally { c.close(); }
        return null;
    }

    private void callNumber(String number) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) { speak("没有打电话权限。"); return; }
        Intent i = new Intent(Intent.ACTION_CALL);
        i.setData(Uri.parse("tel:" + Uri.encode(number)));
        startActivity(i);
    }

    private void scheduleReminder(long triggerAtMillis, String title, String message) {
        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, AlarmReceiver.class);
        i.putExtra("title", title);
        i.putExtra("message", message);
        PendingIntent pi = PendingIntent.getBroadcast(this, (int)(System.currentTimeMillis()%Integer.MAX_VALUE), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am == null) { speak("无法设置提醒。"); return; }
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            speak("请允许精确闹钟权限后再试。");
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        else am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
    }

    private static class AlarmTime { int hour; int minute; AlarmTime(int h,int m){hour=h;minute=m;} }

    private AlarmTime extractAlarmTime(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9零〇一二两俩三四五六七八九十]{1,3})点(?:([0-9零〇一二两俩三四五六七八九十]{1,3})分?)?").matcher(text);
        if (m.find()) {
            int h = chineseToNumber(m.group(1));
            int min = m.group(2)==null ? 0 : chineseToNumber(m.group(2));
            if ((text.contains("下午") || text.contains("晚上") || text.contains("傍晚")) && h < 12) h += 12;
            if (text.contains("凌晨") && h == 12) h = 0;
            if (h>=0 && h<=23 && min>=0 && min<=59) return new AlarmTime(h,min);
        }
        return null;
    }

    private int extractFirstNumber(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9零〇一二两俩三四五六七八九十]{1,3})").matcher(text);
        if (m.find()) return chineseToNumber(m.group(1));
        return -1;
    }

    private int chineseToNumber(String raw) {
        if (raw == null || raw.length() == 0) return -1;
        try { return Integer.parseInt(raw.replaceAll("[^0-9]","")); } catch(Exception ignored){}
        if (raw.equals("零") || raw.equals("〇")) return 0;
        if (raw.equals("一")) return 1;
        if (raw.equals("二") || raw.equals("两") || raw.equals("俩")) return 2;
        if (raw.equals("三")) return 3;
        if (raw.equals("四")) return 4;
        if (raw.equals("五")) return 5;
        if (raw.equals("六")) return 6;
        if (raw.equals("七")) return 7;
        if (raw.equals("八")) return 8;
        if (raw.equals("九")) return 9;
        if (raw.equals("十")) return 10;
        if (raw.contains("十")) {
            String[] parts = raw.split("十", -1);
            int tens = parts[0].length()==0 ? 1 : chineseToNumber(parts[0]);
            int ones = parts.length>1 && parts[1].length()>0 ? chineseToNumber(parts[1]) : 0;
            return tens*10 + ones;
        }
        return -1;
    }

    private String formatAlarmText(int hour, int minute) {
        if (minute == 0) return hour + "点整";
        return hour + "点" + String.format(Locale.CHINA, "%02d", minute) + "分";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "小明提醒", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("闹钟和倒计时提醒");
            ch.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
