package com.jarvis.phone;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import java.util.*;

public class WakeWordService extends Service implements RecognitionListener {
    SpeechRecognizer sr;
    Intent ri;
    TextToSpeech tts;
    LocalVault v;
    final Handler handler = new Handler(Looper.getMainLooper());
    boolean active = true;
    boolean listening = false;
    boolean waitingForCommand = false;
    int consecutiveErrors = 0;
    static final String CH = "jarvis_listener";

    public void onCreate() {
        super.onCreate();
        v = new LocalVault(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "Jarvis Listener", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        startForeground(21, new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Jarvis listening")
                .setContentText("Say Jarvis, then your command")
                .build());

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("hi", "IN"));
        });
        prepareRecognizer();
        scheduleListen(500);
    }

    void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }
        destroyRecognizer();
        // Use the phone's default recognition service. Forcing the on-device
        // recognizer can fail immediately when hi-IN is not installed/supported.
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);

        ri = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        ri.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        ri.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        ri.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        ri.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    }

    void scheduleListen(long delayMs) {
        if (!active) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListening, delayMs);
    }

    void startListening() {
        if (!active || listening) return;
        try {
            if (sr == null) prepareRecognizer();
            if (sr == null) return;
            listening = true;
            sr.startListening(ri);
        } catch (Exception e) {
            listening = false;
            prepareRecognizer();
            scheduleListen(1200);
        }
    }

    void handle(ArrayList<String> results) {
        if (results == null || results.isEmpty()) {
            scheduleListen(500);
            return;
        }

        String heard = results.get(0).trim();
        if (heard.isEmpty()) {
            scheduleListen(500);
            return;
        }

        String wakeName = v.get("wake_name", "Jarvis");
        String lower = heard.toLowerCase(Locale.ROOT);
        String wakeLower = wakeName.toLowerCase(Locale.ROOT);
        int wakeIndex = lower.indexOf(wakeLower);

        if (waitingForCommand) {
            waitingForCommand = false;
            executeCommand(heard);
            return;
        }

        if (wakeIndex < 0) {
            scheduleListen(350);
            return;
        }

        String command = heard.substring(wakeIndex + wakeName.length()).trim();
        if (command.isEmpty()) {
            waitingForCommand = true;
            say("हाँ, बोलो।");
            scheduleListen(1000);
        } else {
            executeCommand(command);
        }
    }

    void executeCommand(String command) {
        new CommandEngine(this).execute(command, reply -> {
            say(reply);
            scheduleListen(1600);
        });
    }

    void say(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    public int onStartCommand(Intent i, int flags, int startId) {
        if (active && !listening) scheduleListen(300);
        return START_STICKY;
    }

    public void onReadyForSpeech(Bundle b) {}
    public void onBeginningOfSpeech() {}
    public void onRmsChanged(float f) {}
    public void onBufferReceived(byte[] b) {}
    public void onEndOfSpeech() {}

    public void onResults(Bundle b) {
        listening = false;
        consecutiveErrors = 0;
        handle(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
    }

    public void onError(int error) {
        listening = false;
        if (!active) return;

        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf();
            return;
        }

        consecutiveErrors++;
        if (error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                consecutiveErrors >= 3) {
            prepareRecognizer();
            consecutiveErrors = 0;
        }

        long delay = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 700 : 1400;
        scheduleListen(delay);
    }

    public void onPartialResults(Bundle b) {}
    public void onEvent(int eventType, Bundle params) {}

    void destroyRecognizer() {
        if (sr != null) {
            try { sr.cancel(); } catch (Exception ignored) {}
            try { sr.destroy(); } catch (Exception ignored) {}
            sr = null;
        }
    }

    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    public IBinder onBind(Intent i) { return null; }
}
