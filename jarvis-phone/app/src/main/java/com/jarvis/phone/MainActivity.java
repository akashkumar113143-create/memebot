package com.jarvis.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity implements RecognitionListener {
    LocalVault vault;
    EditText wake, cmd;
    TextView status;
    TextToSpeech tts;
    SpeechRecognizer recognizer;
    Intent recognizerIntent;
    boolean voiceBusy = false;
    static final int SPEECH = 90;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        vault = new LocalVault(this);
        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("hi", "IN"));
        });

        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText("Jarvis Phone v2.0.3\nVOICE-FIRST TEST BUILD");
        title.setTextSize(23);
        x.addView(title);

        wake = new EditText(this);
        wake.setHint("Wake name");
        wake.setText(vault.get("wake_name", "Jarvis"));
        x.addView(wake);

        add(x, "Save name", () -> {
            vault.put("wake_name", wake.getText().toString().trim());
            say("नाम सेव हो गया।");
        });

        add(x, "START LISTENING", this::startVoiceOnce);
        add(x, "STOP LISTENING", this::stopVoice);
        add(x, "SPEAK ONCE (Google fallback)", this::speakOnce);

        cmd = new EditText(this);
        cmd.setHint("Optional typing fallback");
        x.addView(cmd);
        add(x, "RUN COMMAND", () -> new CommandEngine(this).execute(cmd.getText().toString(), this::say));

        add(x, "SCREEN CONTROL PERMISSION", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(x, "NOTIFICATION ACCESS", () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        add(x, "CLEAR LOCAL MEMORY", () -> {
            vault.clear();
            wake.setText("Jarvis");
            say("Local memory साफ़।");
        });

        status = new TextView(this);
        status.setText("Ready. START LISTENING दबाओ और बोलो: Jarvis YouTube खोलो");
        x.addView(status);

        ScrollView s = new ScrollView(this);
        s.addView(x);
        setContentView(s);
        request();
        prepareRecognizer();
    }

    void add(LinearLayout x, String n, Runnable r) {
        Button b = new Button(this);
        b.setText(n);
        b.setOnClickListener(v -> r.run());
        x.addView(b);
    }

    void request() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.CAMERA);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 33);
    }

    void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (status != null) status.setText("Phone speech recognizer available नहीं है।");
            return;
        }
        destroyRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    }

    void startVoiceOnce() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            request();
            return;
        }
        stopService(new Intent(this, WakeWordService.class));
        if (recognizer == null) prepareRecognizer();
        if (recognizer == null) return;
        try {
            if (voiceBusy) recognizer.cancel();
            voiceBusy = true;
            status.setText("Listening... बोलो: Jarvis YouTube खोलो");
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            voiceBusy = false;
            prepareRecognizer();
            status.setText("Voice start नहीं हुआ। फिर START LISTENING दबाओ।");
        }
    }

    void stopVoice() {
        voiceBusy = false;
        stopService(new Intent(this, WakeWordService.class));
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
        status.setText("Voice listener बंद।");
    }

    void executeHeard(String heard) {
        if (heard == null || heard.trim().isEmpty()) return;
        String text = heard.trim();
        cmd.setText(text);
        status.setText("सुना: " + text);
        new CommandEngine(this).execute(text, this::say);
    }

    void speakOnce() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            startActivityForResult(i, SPEECH);
        } catch (Exception e) {
            say("Speech recognizer नहीं मिला।");
        }
    }

    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == SPEECH && c != RESULT_OK) {
            status.setText("Google voice पहचान नहीं हुई।");
        }
        if (r == SPEECH && c == RESULT_OK && d != null) {
            ArrayList<String> a = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (a != null && !a.isEmpty()) executeHeard(a.get(0));
        }
    }

    public void onReadyForSpeech(Bundle b) { status.setText("Listening... अब बोलो"); }
    public void onBeginningOfSpeech() { status.setText("सुन रहा हूँ..."); }
    public void onRmsChanged(float f) {}
    public void onBufferReceived(byte[] b) {}
    public void onEndOfSpeech() { status.setText("समझ रहा हूँ..."); }

    public void onResults(Bundle b) {
        voiceBusy = false;
        ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (a != null && !a.isEmpty()) executeHeard(a.get(0));
        else status.setText("कुछ सुनाई नहीं दिया। फिर START LISTENING दबाओ।");
    }

    public void onPartialResults(Bundle b) {
        ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (a != null && !a.isEmpty()) status.setText("सुन रहा हूँ: " + a.get(0));
    }

    public void onError(int error) {
        voiceBusy = false;
        status.setText("Voice नहीं पकड़ पाया (" + error + "). फिर START LISTENING दबाओ।");
        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            prepareRecognizer();
    }

    public void onEvent(int eventType, Bundle params) {}

    void say(String s) {
        runOnUiThread(() -> {
            status.setText(s);
            if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "main");
        });
    }

    void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    protected void onDestroy() {
        destroyRecognizer();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
