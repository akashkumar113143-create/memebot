package com.jarvis.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LocalVault vault;
    EditText wake, cmd;
    TextView status;
    TextToSpeech tts;
    boolean waitingOverlayPermission = false;
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
        title.setText("Jarvis Phone v2.0.6\nMULTI-WORK BUILD");
        title.setTextSize(23);
        x.addView(title);

        wake = new EditText(this);
        wake.setHint("Wake name");
        wake.setText(vault.get("wake_name", "Jarvis"));
        x.addView(wake);

        add(x, "SAVE NAME", () -> {
            vault.put("wake_name", wake.getText().toString().trim());
            say("नाम सेव हो गया।");
        });

        add(x, "START LISTENING", this::startBackgroundVoice);
        add(x, "STOP LISTENING", this::stopBackgroundVoice);
        add(x, "BACKGROUND APP CONTROL PERMISSION", this::requestOverlayPermission);
        add(x, "SCREEN CONTROL / MULTI-WORK PERMISSION", () -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            status.setText("Accessibility में Jarvis Phone को ON करो। इससे type, tap, search और multi-work चलेगा।");
        });
        add(x, "SPEAK ONCE (Google fallback)", this::speakOnce);

        cmd = new EditText(this);
        cmd.setHint("Optional typing fallback");
        x.addView(cmd);
        add(x, "RUN COMMAND", () -> new CommandEngine(this).execute(cmd.getText().toString(), this::say));

        add(x, "NOTIFICATION ACCESS", () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        add(x, "CLEAR LOCAL MEMORY", () -> {
            vault.clear();
            wake.setText("Jarvis");
            say("Local memory साफ़।");
        });

        status = new TextView(this);
        status.setText("Ready. START LISTENING ON करो। Multi-work के लिए SCREEN CONTROL permission भी ON करो।");
        x.addView(status);

        ScrollView s = new ScrollView(this);
        s.addView(x);
        setContentView(s);
        requestRuntimePermissions();
    }

    void add(LinearLayout x, String n, Runnable r) {
        Button b = new Button(this);
        b.setText(n);
        b.setOnClickListener(v -> r.run());
        x.addView(b);
    }

    void requestRuntimePermissions() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.CAMERA);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 33);
    }

    void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            status.setText("Background App Control permission already ON.");
            return;
        }
        waitingOverlayPermission = true;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
        status.setText("Display over other apps में Jarvis Phone को Allow करो।");
    }

    void startBackgroundVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            waitingOverlayPermission = true;
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("Jarvis ON. Home/दूसरे apps में भी voice सुनने की कोशिश करेगा। Multi-work के लिए Screen Control ON होना चाहिए।");
    }

    void stopBackgroundVoice() {
        stopService(new Intent(this, WakeWordService.class));
        status.setText("Jarvis OFF.");
    }

    protected void onResume() {
        super.onResume();
        if (waitingOverlayPermission && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
            waitingOverlayPermission = false;
            startBackgroundVoice();
        }
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
        if (r == SPEECH && c == RESULT_OK && d != null) {
            ArrayList<String> a = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (a != null && !a.isEmpty()) {
                cmd.setText(a.get(0));
                new CommandEngine(this).execute(a.get(0), this::say);
            }
        } else if (r == SPEECH) {
            status.setText("Google voice पहचान नहीं हुई।");
        }
    }

    void say(String s) {
        runOnUiThread(() -> {
            status.setText(s);
            if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "main");
        });
    }

    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
