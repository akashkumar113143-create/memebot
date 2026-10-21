package com.jarvis.phone;

import android.content.*;
import android.content.pm.*;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.*;
import java.util.regex.*;

public class CommandEngine {
    public interface Reply { void say(String s); }
    final Context c;
    final LocalVault v;

    public CommandEngine(Context c) {
        this.c = c;
        v = new LocalVault(c);
    }

    public void execute(String q, Reply r) {
        if (q == null) return;
        String l = q.toLowerCase(Locale.ROOT).trim();
        String n = v.get("wake_name", "Jarvis");
        l = l.replaceFirst("(?i)^" + Pattern.quote(n) + "[ ,:-]*", "").trim();

        if (isCloseCommand(l)) {
            goHome();
            r.say("ठीक है, app से बाहर आ गया।");
            return;
        }

        if (l.contains("youtube") || l.contains("यूट्यूब")) {
            launch("com.google.android.youtube", "https://www.youtube.com");
            r.say("YouTube खोल रहा हूँ।");
            return;
        }
        if (l.contains("whatsapp") || l.contains("व्हाट्सएप") || l.contains("वाट्सएप")) {
            launch("com.whatsapp", "https://wa.me/");
            r.say("WhatsApp खोल रहा हूँ।");
            return;
        }
        if (l.contains("instagram") || l.contains("इंस्टाग्राम")) {
            launch("com.instagram.android", "https://www.instagram.com");
            r.say("Instagram खोल रहा हूँ।");
            return;
        }
        if (l.contains("tradingview") || l.contains("trading view") || l.contains("ट्रेडिंगव्यू") || l.contains("ट्रेडिंग व्यू")) {
            if (launchInstalledByName("TradingView")) r.say("TradingView खोल रहा हूँ।");
            else r.say("TradingView app नहीं मिला।");
            return;
        }
        if (l.contains("telegram") || l.contains("टेलीग्राम")) {
            if (launchInstalledByName("Telegram")) r.say("Telegram खोल रहा हूँ।");
            else r.say("Telegram app नहीं मिला।");
            return;
        }
        if (l.contains("mt5") || l.contains("metatrader")) {
            launch("net.metaquotes.metatrader5", "https://www.metatrader5.com");
            r.say("MT5 खोल रहा हूँ।");
            return;
        }
        if (l.contains("phonepe") || l.contains("फोनपे") || l.contains("फोन पे")) {
            launch("com.phonepe.app", "https://www.phonepe.com");
            r.say("PhonePe खोल रहा हूँ।");
            return;
        }
        if (l.contains("paytm") || l.contains("पेटीएम")) {
            launch("net.one97.paytm", "https://paytm.com");
            r.say("Paytm खोल रहा हूँ।");
            return;
        }

        if (l.contains("torch") || l.contains("flashlight") || l.contains("टॉर्च") || l.contains("लाइट चालू") || l.contains("लाइट बंद")) {
            try {
                android.hardware.camera2.CameraManager m = (android.hardware.camera2.CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
                boolean on = !(l.contains("बंद") || l.contains("off"));
                m.setTorchMode(m.getCameraIdList()[0], on);
                r.say(on ? "टॉर्च चालू।" : "टॉर्च बंद।");
            } catch (Exception e) {
                r.say("टॉर्च control नहीं हो पाया।");
            }
            return;
        }
        if (l.contains("bluetooth") || l.contains("ब्लूटूथ")) {
            open(Settings.ACTION_BLUETOOTH_SETTINGS);
            r.say("Bluetooth settings खोल दी।");
            return;
        }
        if (l.contains("wifi") || l.contains("wi-fi") || l.contains("वाईफाई")) {
            open(Settings.ACTION_WIFI_SETTINGS);
            r.say("Wi-Fi settings खोल दी।");
            return;
        }
        if (l.equals("home") || l.contains("होम जाओ")) {
            goHome();
            r.say("Home।");
            return;
        }
        if (l.equals("back") || l.contains("पीछे जाओ")) {
            JarvisAccessibilityService s = JarvisAccessibilityService.get();
            if (s != null) s.performGlobalAction(1);
            r.say("Back।");
            return;
        }
        if (l.startsWith("tap ") || l.startsWith("दबाओ ")) {
            String t = q.substring(q.indexOf(' ') + 1);
            JarvisAccessibilityService s = JarvisAccessibilityService.get();
            r.say(s != null && s.tapText(t) ? "दबा दिया।" : "वो text नहीं मिला।");
            return;
        }
        if (l.contains("notification") || l.contains("नोटिफिकेशन")) {
            r.say(v.get("latest_notification", "कोई notification capture नहीं हुई।"));
            return;
        }
        if (l.contains("screen पर क्या") || l.contains("स्क्रीन पर क्या")) {
            r.say(v.get("latest_screen_text", "Screen text उपलब्ध नहीं।"));
            return;
        }
        if (l.contains("गाना चल") || l.equals("play")) {
            media(KeyEvent.KEYCODE_MEDIA_PLAY);
            r.say("Play।");
            return;
        }
        if (l.contains("गाना रोक") || l.equals("pause")) {
            media(KeyEvent.KEYCODE_MEDIA_PAUSE);
            r.say("Pause।");
            return;
        }
        if (l.startsWith("call ") || l.startsWith("कॉल ")) {
            String x = q.substring(q.indexOf(' ') + 1).replaceAll("[^0-9+]", "");
            if (x.isEmpty()) {
                r.say("Number बोलो।");
                return;
            }
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + x));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            r.say("Dialer खोल दिया।");
            return;
        }

        String appName = extractAppName(q);
        if (!appName.isEmpty() && launchInstalledByName(appName)) {
            r.say(appName + " खोल दिया।");
            return;
        }

        r.say("ये command अभी समझ नहीं पाया।");
    }

    boolean isCloseCommand(String l) {
        return l.contains("बंद करो") || l.contains("बंद कर") || l.contains("close") || l.contains("exit") || l.contains("बाहर आओ");
    }

    String extractAppName(String q) {
        String s = q.trim();
        s = s.replaceFirst("(?i)^open\\s+", "");
        s = s.replaceFirst("(?i)^launch\\s+", "");
        s = s.replaceFirst("^खोलो\\s+", "");
        s = s.replaceFirst("^खोल\\s+", "");
        s = s.replaceAll("(?i)\\s+(open|launch|start)(\\s+karo)?$", "");
        s = s.replaceAll("\\s+(खोलो|खोल|चालू करो|चालू)$", "");
        return s.trim();
    }

    boolean launchInstalledByName(String name) {
        Intent i = findInstalledApp(name);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    Intent findInstalledApp(String name) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) return null;
        PackageManager pm = c.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN, null);
        probe.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);

        ResolveInfo best = null;
        int bestScore = -1;
        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm) == null ? "" : ri.loadLabel(pm).toString();
            String pkg = ri.activityInfo.packageName;
            String nl = normalize(label);
            String np = normalize(pkg);
            int score = -1;
            if (nl.equals(wanted)) score = 100;
            else if (nl.contains(wanted) || wanted.contains(nl)) score = 80;
            else if (np.contains(wanted)) score = 50;
            if (score > bestScore) {
                bestScore = score;
                best = ri;
            }
        }

        if (best == null || bestScore < 0) return null;
        Intent out = new Intent(Intent.ACTION_MAIN);
        out.addCategory(Intent.CATEGORY_LAUNCHER);
        out.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        return out;
    }

    String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{L}]", "");
    }

    void launch(String pkg, String fallback) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse(fallback));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) {}
    }

    void open(String action) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    void media(int k) {
        AudioManager a = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, k));
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, k));
    }
}
