package com.xiaoming.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status;
    private SpeechRecognizer recognizer;
    private Intent speechIntent;
    private TextToSpeech tts;
    private boolean listening = false;

    private static final int REQ_PERMS = 100;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        requestNeededPermissions();
        setupTts();
        setupSpeech();

        status.setText("点一下开始说话\n再点一次停止\n\n可以说：小明打电话给爸爸");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.rgb(17, 24, 39));
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("小明");
        title.setTextColor(Color.WHITE);
        title.setTextSize(56);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(24);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(8, 1.1f);

        root.addView(title);
        root.addView(status);

        root.setOnClickListener(v -> {
            if (listening) stopListening();
            else startListening();
        });

        setContentView(root);
    }

    private void requestNeededPermissions() {
        ArrayList<String> perms = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_CONTACTS);
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CALL_PHONE);
        }

        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[0]), REQ_PERMS);
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
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "xiaoming");
        }
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
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                status.setText("正在听……");
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {
                listening = false;
            }

            @Override public void onError(int error) {
                listening = false;
                speak("没听清楚，请再点一下。错误代码：" + error);
            }

            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts == null || texts.isEmpty()) {
                    speak("没听到内容。");
                    return;
                }
                String text = texts.get(0);
                status.setText("听到：" + text);
                handleCommand(text);
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        requestNeededPermissions();

        if (recognizer == null) {
            speak("语音识别不可用。");
            return;
        }

        try {
            recognizer.startListening(speechIntent);
        } catch (Exception e) {
            speak("无法开始录音：" + e.getMessage());
        }
    }

    private void stopListening() {
        try {
            if (recognizer != null) recognizer.stopListening();
        } catch (Exception ignored) {}
        listening = false;
        status.setText("已停止。点一下重新开始。");
    }

    private void handleCommand(String raw) {
        String text = raw.replace("，", "")
                .replace("。", "")
                .replace(" ", "");

        if (!text.contains("小明")) {
            speak("请先叫我小明。");
            return;
        }

        if (text.contains("几点") || text.contains("时间")) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH点mm分", Locale.CHINA);
            speak("现在是" + sdf.format(new java.util.Date()));
            return;
        }

        if (text.contains("打电话")) {
            String name = extractCallName(text);
            if (name.length() == 0) {
                speak("你要打给谁？");
                return;
            }

            String number = findContactNumber(name);
            if (number == null) {
                speak("通讯录里找不到" + name);
                return;
            }

            speak("正在打电话给" + name);
            callNumber(number);
            return;
        }

        speak("我现在会听：小明打电话给某人，或者小明现在几点。");
    }

    private String extractCallName(String text) {
        String name = text;
        name = name.replace("小明", "");
        name = name.replace("帮我", "");
        name = name.replace("请", "");
        name = name.replace("打电话给", "");
        name = name.replace("打电话", "");
        name = name.replace("给", "");
        return name.trim();
    }

    private String findContactNumber(String name) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            speak("没有通讯录权限。");
            return null;
        }

        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + name + "%"},
                null
        );

        if (cursor == null) return null;

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                ));
            }
        } finally {
            cursor.close();
        }

        return null;
    }

    private void callNumber(String number) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            speak("没有打电话权限。");
            return;
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + Uri.encode(number)));
        startActivity(callIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
