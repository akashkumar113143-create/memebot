package com.jarvis.phone;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.*;

public class WakeWordService extends Service implements RecognitionListener {
    SpeechRecognizer sr;
    Intent ri;
    TextToSpeech tts;
    LocalVault v;
    final Handler handler = new Handler(Looper.getMainLooper());
    boolean active = true;
    boolean listening = false;
    boolean speaking = false;
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
                .setContentText("Background voice control is on")
                .build());

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { speaking = true; }
                    public void onDone(String id) {
                        speaking = false;
                        scheduleListen(700);
                    }
                    public void onError(String id) {
                        speaking = false;
                        scheduleListen(700);
                    }
                });
            }
        });

        prepareRecognizer();
        scheduleListen(600);
    }

    void prepareRecognizer() {
        if (!active || !SpeechRecognizer.isRecognitionAvailable(this)) return;
        destroyRecognizer();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
        ri = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        ri.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        ri.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        ri.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        ri.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    }

    void scheduleListen(long delayMs) {
        if (!active || speaking) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListening, delayMs);
    }

    void startListening() {
        if (!active || speaking || listening) return;
        try {
            if (sr == null) prepareRecognizer();
            if (sr == null) return;
            listening = true;
            sr.startListening(ri);
        } catch (Exception e) {
            listening = false;
            prepareRecognizer();
            scheduleListen(1800);
        }
    }

    boolean looksLikeCommand(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        String[] words = {"खोल", "open", "बंद", "close", "चालू", "start", "play", "pause", "रोक", "call", "कॉल", "home", "होम", "back", "पीछे", "tap", "दबाओ", "notification", "नोटिफिकेशन", "screen", "स्क्रीन", "wifi", "bluetooth", "टॉर्च", "torch"};
        for (String w : words) if (x.contains(w)) return true;
        return false;
    }

    void handle(ArrayList<String> results) {
        if (results == null || results.isEmpty()) {
            scheduleListen(1200);
            return;
        }

        String heard = results.get(0).trim();
        if (heard.isEmpty()) {
            scheduleListen(1200);
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

        if (wakeIndex >= 0) {
            String command = heard.substring(wakeIndex + wakeName.length()).trim();
            if (command.isEmpty()) {
                waitingForCommand = true;
                sayAndResume("हाँ, बोलो।");
            } else {
                executeCommand(command);
            }
            return;
        }

        // Once the user explicitly enabled listening, allow clear command-like
        // phrases even without saying the wake word every time.
        if (looksLikeCommand(heard)) {
            executeCommand(heard);
        } else {
            scheduleListen(900);
        }
    }

    void executeCommand(String command) {
        try {
            if (sr != null) sr.cancel();
        } catch (Exception ignored) {}
        listening = false;
        new CommandEngine(this).execute(command, this::sayAndResume);
    }

    void sayAndResume(String text) {
        if (tts == null) {
            scheduleListen(700);
            return;
        }
        speaking = true;
        try {
            if (sr != null) sr.cancel();
        } catch (Exception ignored) {}
        listening = false;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply");
    }

    public int onStartCommand(Intent i, int flags, int startId) {
        active = true;
        if (!listening && !speaking) scheduleListen(400);
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
        if (!active || speaking) return;
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

        long delay;
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            delay = 2200;
        else
            delay = 3000;
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
