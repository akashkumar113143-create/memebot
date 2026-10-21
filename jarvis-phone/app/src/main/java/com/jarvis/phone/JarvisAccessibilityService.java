package com.jarvis.phone;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

public class JarvisAccessibilityService extends AccessibilityService {
    private static volatile JarvisAccessibilityService instance;

    public static JarvisAccessibilityService get() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            StringBuilder b = new StringBuilder();
            collectText(root, b, 0);
            String s = b.toString().trim();
            if (!s.isEmpty()) new LocalVault(this).put("latest_screen_text", s.length() > 2500 ? s.substring(0, 2500) : s);
        } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public boolean tapText(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text.trim());
        if (nodes != null) {
            for (AccessibilityNodeInfo n : nodes) {
                if (clickNodeOrParent(n)) return true;
            }
        }
        return tapBestMatch(text);
    }

    public boolean tapAny(String... labels) {
        if (labels == null) return false;
        for (String s : labels) if (tapText(s)) return true;
        return false;
    }

    public boolean typeText(String text) {
        if (text == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isEditable()) target = firstEditable(root);
        if (target == null) return false;

        try {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean pressEnter() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null) target = firstEditable(root);
        if (target == null) return false;
        if (Build.VERSION.SDK_INT >= 30) {
            try { return target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()); }
            catch (Exception ignored) {}
        }
        return tapAny("Search", "खोजें", "Go", "Done", "Enter");
    }

    public boolean tapBestMatch(String query) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || query == null) return false;
        String[] words = normalize(query).split("\\s+");
        Best best = new Best();
        scoreTree(root, words, best, 0);
        return best.node != null && best.score > 0 && clickNodeOrParent(best.node);
    }

    public boolean back() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean home() { return performGlobalAction(GLOBAL_ACTION_HOME); }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo out = firstEditable(n.getChild(i));
            if (out != null) return out;
        }
        return null;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; cur != null && i < 6; i++) {
            try {
                if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            } catch (Exception ignored) {}
            cur = cur.getParent();
        }
        return false;
    }

    private static class Best { AccessibilityNodeInfo node; int score = 0; }

    private void scoreTree(AccessibilityNodeInfo n, String[] words, Best best, int depth) {
        if (n == null || depth > 40) return;
        String text = "";
        if (n.getText() != null) text += " " + n.getText();
        if (n.getContentDescription() != null) text += " " + n.getContentDescription();
        String norm = normalize(text);
        int score = 0;
        for (String w : words) if (w.length() >= 2 && norm.contains(w)) score += 10;
        if (score > best.score && (n.isClickable() || hasClickableParent(n))) {
            best.score = score;
            best.node = n;
        }
        for (int i = 0; i < n.getChildCount(); i++) scoreTree(n.getChild(i), words, best, depth + 1);
    }

    private boolean hasClickableParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo p = n == null ? null : n.getParent();
        for (int i = 0; p != null && i < 5; i++) {
            if (p.isClickable()) return true;
            p = p.getParent();
        }
        return false;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{L}]+", " ").trim();
    }

    private void collectText(AccessibilityNodeInfo n, StringBuilder b, int depth) {
        if (n == null || depth > 35 || b.length() > 2800) return;
        if (n.getText() != null) b.append(n.getText()).append(' ');
        if (n.getContentDescription() != null) b.append(n.getContentDescription()).append(' ');
        for (int i = 0; i < n.getChildCount(); i++) collectText(n.getChild(i), b, depth + 1);
    }
}
