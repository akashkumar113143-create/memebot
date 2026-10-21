package com.jarvis.phone;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
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
    boolean segmentedRequested = false;
    int consecutiveErrors = 0;
    long ignoreResultsUntil = 0L;
    WindowManager wm;
    TextView bubble;
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

        showBubble();

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { speaking = true; }
                    public void onDone(String id) {
                        speaking = false;
                        ignoreResultsUntil = SystemClock.elapsedRealtime() + 1200;
                        if (!listening) scheduleListen(1400);
                    }
                    public void onError(String id) {
                        speaking = false;
                        ignoreResultsUntil = SystemClock.elapsedRealtime() + 1200;
                        if (!listening) scheduleListen(1400);
                    }
                });
            }
        });

        prepareRecognizer();
        scheduleListen(600);
    }

    void showBubble() {
        if (Build.VERSION.SDK_INT < 26 || !Settings.canDrawOverlays(this)) return;
        try {
            wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            bubble = new TextView(this);
            bubble.setText("J");
            bubble.setTextColor(Color.WHITE);
            bubble.setTextSize(12f);
            bubble.setGravity(Gravity.CENTER);
            bubble.setBackgroundColor(0xAA222222);
            int px = (int)(36f * getResources().getDisplayMetrics().density);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    px, px,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = 10;
            lp.y = 180;
            wm.addView(bubble, lp);
        } catch (Exception ignored) {
            bubble = null;
        }
    }

    void removeBubble() {
        if (wm != null && bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
        }
        bubble = null;
        wm = null;
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

        segmentedRequested = false;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                ri.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                        RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS);
                ri.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 120000);
                segmentedRequested = true;
            } catch (Exception ignored) {
                segmentedRequested = false;
            }
        }
    }

    void scheduleListen(long delayMs) {
        if (!active || speaking || listening) return;
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
            scheduleListen(4000);
        }
    }

    boolean looksLikeCommand(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        String[] words = {"खोल", "open", "बंद", "close", "चालू", "start", "play", "pause", "रोक", "call", "कॉल", "home", "होम", "back", "पीछे", "tap", "दबाओ", "notification", "नोटिफिकेशन", "screen", "स्क्रीन", "wifi", "bluetooth", "टॉर्च", "torch"};
        for (String w : words) if (x.contains(w)) return true;
        return false;
    }

    void handle(ArrayList<String> results) {
        if (!active || speaking || SystemClock.elapsedRealtime() < ignoreResultsUntil) return;
        if (results == null || results.isEmpty()) return;

        String heard = results.get(0).trim();
        if (heard.isEmpty()) return;

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
                sayWithoutBreakingSession("हाँ, बोलो।");
            } else {
                executeCommand(command);
            }
            return;
        }

        if (looksLikeCommand(heard)) executeCommand(heard);
    }

    void executeCommand(String command) {
        new CommandEngine(this).execute(command, this::sayWithoutBreakingSession);
    }

    void sayWithoutBreakingSession(String text) {
        if (tts == null) {
            if (!listening) scheduleListen(1400);
            return;
        }
        speaking = true;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply");
    }

    public int onStartCommand(Intent i, int flags, int startId) {
        active = true;
        if (bubble == null) showBubble();
        if (!listening && !speaking) scheduleListen(500);
        return START_STICKY;
    }

    public void onReadyForSpeech(Bundle b) {}
    public void onBeginningOfSpeech() {}
    public void onRmsChanged(float f) {}
    public void onBufferReceived(byte[] b) {}
    public void onEndOfSpeech() {}

    public void onSegmentResults(Bundle b) {
        if (Build.VERSION.SDK_INT < 33) return;
        handle(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
    }

    public void onEndOfSegmentedSession() {
        if (Build.VERSION.SDK_INT < 33) return;
        listening = false;
        if (!speaking) scheduleListen(2500);
    }

    public void onResults(Bundle b) {
        listening = false;
        consecutiveErrors = 0;
        handle(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
        if (!speaking) scheduleListen(2200);
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
            delay = 7000;
        else
            delay = 5000;
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
        listening = false;
    }

    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        removeBubble();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    public IBinder onBind(Intent i) { return null; }
}
