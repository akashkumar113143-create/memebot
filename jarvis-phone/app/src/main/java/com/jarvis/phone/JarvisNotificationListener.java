package com.jarvis.phone;
import android.app.Notification;import android.service.notification.NotificationListenerService;import android.service.notification.StatusBarNotification;
public class JarvisNotificationListener extends NotificationListenerService{public void onNotificationPosted(StatusBarNotification s){Notification n=s.getNotification();String t=n.extras.getString(Notification.EXTRA_TITLE,"");CharSequence x=n.extras.getCharSequence(Notification.EXTRA_TEXT,"");new LocalVault(this).put("latest_notification",s.getPackageName()+": "+t+" — "+(x==null?"":x.toString()));}}
