package com.jarvis.phone;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
public class JarvisAccessibilityService extends AccessibilityService {
  private static volatile JarvisAccessibilityService instance;
  public static JarvisAccessibilityService get(){ return instance; }
  @Override public void onServiceConnected(){ instance=this; }
  @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
  @Override public void onInterrupt() { }
  @Override public void onDestroy(){ instance=null; super.onDestroy(); }
  public boolean tapText(String text){ return false; }
}
