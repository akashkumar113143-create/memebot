package com.jarvis.phone;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.*;
import java.util.regex.*;

public class CommandEngine {
    public interface Reply { void say(String s); }
    final Context c;
    final LocalVault v;
    final Handler handler = new Handler(Looper.getMainLooper());

    public CommandEngine(Context c) {
        this.c = c;
        v = new LocalVault(c);
    }

    public void execute(String q, Reply r) {
        if (q == null || q.trim().isEmpty()) return;
        String cleaned = stripWake(q.trim());
        List<String> steps = splitMulti(cleaned);
        if (steps.size() > 1) {
            runSequence(steps, r);
            return;
        }
        executeSingle(cleaned, r);
    }

    String stripWake(String q) {
        String n = v.get("wake_name", "Jarvis");
        return q.replaceFirst("(?i)^" + Pattern.quote(n) + "[ ,:-]*", "").trim();
    }

    List<String> splitMulti(String q) {
        String s = q.replace(" और फिर ", " || ")
                .replace(" फिर ", " || ")
                .replaceAll("(?i)\\s+then\\s+", " || ")
                .replace(" उसके बाद ", " || ");
        String[] raw = s.split("\\|\\|");
        ArrayList<String> out = new ArrayList<>();
        for (String x : raw) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    void runSequence(List<String> steps, Reply r) {
        long delay = 0;
        for (int i = 0; i < steps.size(); i++) {
            final String step = steps.get(i);
            final boolean last = i == steps.size() - 1;
            handler.postDelayed(() -> executeSingle(step, last ? r : s -> {}), delay);
            delay += 1800;
        }
        r.say("ठीक है, काम एक-एक करके कर रहा हूँ।");
    }

    void executeSingle(String q, Reply r) {
        String l = stripWake(q).toLowerCase(Locale.ROOT).trim();

        if (l.contains("youtube") || l.contains("यूट्यूब")) {
            String query = extractYoutubeQuery(q);
            if (!query.isEmpty() && wantsYoutubeSearch(l)) {
                boolean ok = openYoutubeSearch(query);
                r.say(ok ? "YouTube पर " + query + " खोज रहा हूँ।" : "YouTube search नहीं खुल पाया।");
            } else {
                r.say(launch("com.google.android.youtube", "https://www.youtube.com") ? "YouTube खोल रहा हूँ।" : backgroundPermissionReply());
            }
            return;
        }

        if (l.contains("whatsapp") || l.contains("व्हाट्सएप") || l.contains("वाट्सएप")) {
            r.say(launch("com.whatsapp", "https://wa.me/") ? "WhatsApp खोल रहा हूँ।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("instagram") || l.contains("इंस्टाग्राम")) {
            r.say(launch("com.instagram.android", "https://www.instagram.com") ? "Instagram खोल रहा हूँ।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("tradingview") || l.contains("trading view") || l.contains("ट्रेडिंगव्यू") || l.contains("ट्रेडिंग व्यू")) {
            if (launchInstalledByName("TradingView")) r.say("TradingView खोल रहा हूँ।");
            else r.say(canLaunchFromHere() ? "TradingView app नहीं मिला।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("telegram") || l.contains("टेलीग्राम")) {
            if (launchInstalledByName("Telegram")) r.say("Telegram खोल रहा हूँ।");
            else r.say(canLaunchFromHere() ? "Telegram app नहीं मिला।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("mt5") || l.contains("metatrader") || l.contains("मेटाट्रेडर")) {
            r.say(launch("net.metaquotes.metatrader5", "https://www.metatrader5.com") ? "MT5 खोल रहा हूँ।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("phonepe") || l.contains("फोनपे") || l.contains("फोन पे")) {
            r.say(launch("com.phonepe.app", "https://www.phonepe.com") ? "PhonePe खोल रहा हूँ।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("paytm") || l.contains("पेटीएम")) {
            r.say(launch("net.one97.paytm", "https://paytm.com") ? "Paytm खोल रहा हूँ।" : backgroundPermissionReply());
            return;
        }

        if (l.startsWith("search ") || l.startsWith("सर्च ") || l.startsWith("खोजो ")) {
            String text = q.substring(q.indexOf(' ') + 1).trim();
            boolean ok = openWebSearch(text);
            r.say(ok ? text + " search कर रहा हूँ।" : "Search नहीं खुल पाया।");
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
            r.say(open(Settings.ACTION_BLUETOOTH_SETTINGS) ? "Bluetooth settings खोल दी।" : backgroundPermissionReply());
            return;
        }
        if (l.contains("wifi") || l.contains("wi-fi") || l.contains("वाईफाई")) {
            r.say(open(Settings.ACTION_WIFI_SETTINGS) ? "Wi-Fi settings खोल दी।" : backgroundPermissionReply());
            return;
        }

        if (isCloseCommand(l) || l.equals("home") || l.contains("होम जाओ")) {
            r.say(goHome() ? "Home screen पर आ गया।" : backgroundPermissionReply());
            return;
        }

        if (l.contains("गाना चल") || l.equals("play") || l.contains("music play")) {
            media(KeyEvent.KEYCODE_MEDIA_PLAY);
            r.say("Play।");
            return;
        }
        if (l.contains("गाना रोक") || l.equals("pause") || l.contains("music pause")) {
            media(KeyEvent.KEYCODE_MEDIA_PAUSE);
            r.say("Pause।");
            return;
        }
        if (l.contains("next song") || l.contains("अगला गाना")) {
            media(KeyEvent.KEYCODE_MEDIA_NEXT);
            r.say("Next।");
            return;
        }
        if (l.contains("previous song") || l.contains("पिछला गाना")) {
            media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            r.say("Previous।");
            return;
        }

        if (l.startsWith("call ") || l.startsWith("कॉल ")) {
            String x = q.substring(q.indexOf(' ') + 1).replaceAll("[^0-9+]", "");
            if (x.isEmpty()) {
                r.say("Number बोलो।");
                return;
            }
            if (!canLaunchFromHere()) {
                r.say(backgroundPermissionReply());
                return;
            }
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + x));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
                r.say("Dialer खोल दिया।");
            } catch (Exception e) {
                r.say("Dialer नहीं खुल पाया।");
            }
            return;
        }

        String appName = extractAppName(q);
        if (!appName.isEmpty() && launchInstalledByName(appName)) {
            r.say(appName + " खोल दिया।");
            return;
        }

        if (l.contains("like") || l.contains("लाइक") || l.contains("watch later") || l.contains("watch list") || l.contains("टाइप") || l.contains("लिखो") || l.contains("tap") || l.contains("दबाओ")) {
            r.say("ये काम दूसरे app के अंदर tap या type माँगता है। Safe build में Accessibility नहीं है।");
            return;
        }

        r.say("ये command अभी समझ नहीं पाया।");
    }

    boolean wantsYoutubeSearch(String l) {
        return l.contains("song") || l.contains("गाना") || l.contains("play") || l.contains("बजाओ") || l.contains("लगाओ") || l.contains("search") || l.contains("खोज");
    }

    String extractYoutubeQuery(String q) {
        String s = stripWake(q);
        s = s.replaceAll("(?i)youtube|यूट्यूब", " ");
        s = s.replaceAll("(?i)\\b(open|play|search|start)\\b", " ");
        s = s.replace("खोलो", " ").replace("खोल", " ").replace("चालू करो", " ");
        s = s.replace("गाना बजाओ", " ").replace("गाना चलाओ", " ").replace("गाना लगाओ", " ");
        s = s.replace("बजाओ", " ").replace("चलाओ", " ").replace("लगाओ", " ").replace("खोजो", " ");
        int likeAt = indexOfAny(s.toLowerCase(Locale.ROOT), " like", " लाइक", " watch later", " watch list", " सेव", " save");
        if (likeAt > 0) s = s.substring(0, likeAt);
        return s.replaceAll("\\s+", " ").trim();
    }

    int indexOfAny(String s, String... needles) {
        int best = -1;
        for (String n : needles) {
            int i = s.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    boolean openYoutubeSearch(String query) {
        if (!canLaunchFromHere()) return false;
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
        i.setPackage("com.google.android.youtube");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(web);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    boolean openWebSearch(String query) {
        if (!canLaunchFromHere()) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String backgroundPermissionReply() {
        return "Background App Control permission चाहिए। Jarvis app में जाकर Allow करो।";
    }

    boolean canLaunchFromHere() {
        if (c instanceof Activity) return true;
        if (Build.VERSION.SDK_INT < 23) return true;
        return Settings.canDrawOverlays(c);
    }

    boolean isCloseCommand(String l) {
        return l.contains("बंद करो") || l.contains("बंद कर") || l.contains("close") || l.contains("exit") || l.contains("बाहर आओ");
    }

    String extractAppName(String q) {
        String s = stripWake(q).trim();
        s = s.replaceFirst("(?i)^open\\s+", "");
        s = s.replaceFirst("(?i)^launch\\s+", "");
        s = s.replaceFirst("^खोलो\\s+", "");
        s = s.replaceFirst("^खोल\\s+", "");
        s = s.replaceAll("(?i)\\s+(open|launch|start)(\\s+karo)?$", "");
        s = s.replaceAll("\\s+(खोलो|खोल|चालू करो|चालू)$", "");
        return s.trim();
    }

    boolean launchInstalledByName(String name) {
        if (!canLaunchFromHere()) return false;
        Intent i = findInstalledApp(name);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
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
            CharSequence cs = ri.loadLabel(pm);
            String label = cs == null ? "" : cs.toString();
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

    boolean launch(String pkg, String fallback) {
        if (!canLaunchFromHere()) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse(fallback));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean open(String action) {
        if (!canLaunchFromHere()) return false;
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean goHome() {
        if (!canLaunchFromHere()) return false;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void media(int k) {
        AudioManager a = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, k));
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, k));
    }
}
