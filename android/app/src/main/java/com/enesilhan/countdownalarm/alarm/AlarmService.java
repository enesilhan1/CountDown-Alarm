package com.enesilhan.countdownalarm.alarm;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.enesilhan.countdownalarm.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Süresi dolan alarmı çalar: alarm sesini döngüde çalar, titreştirir ve
 * Ertele/Kapat butonlu kalıcı bir bildirim gösterir. Kullanıcı yanıt vermezse
 * 10 dakika sonra susar.
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";

    public static final String ACTION_RING = "com.enesilhan.countdownalarm.RING";
    public static final String ACTION_DISMISS = "com.enesilhan.countdownalarm.DISMISS";
    public static final String ACTION_SNOOZE = "com.enesilhan.countdownalarm.SNOOZE";
    public static final String ACTION_STOP = "com.enesilhan.countdownalarm.STOP";
    public static final String EXTRA_NID = "nid";
    public static final String EXTRA_MINUTES = "minutes";

    private static final String CHANNEL_RING = "alarm_ring";
    private static final String CHANNEL_MISSED = "alarm_missed";
    private static final int NOTIF_RINGING = 4242;
    private static final int SNOOZE_MINUTES = 5;
    private static final long TIMEOUT_MS = 10 * 60 * 1000;

    /** Şu an çalan alarmın kimliği (-1: çalan yok). Eklenti bunu okur. */
    static volatile int ringingNid = -1;

    /**
     * Alarm çalmaya başladığında telefon kilitli ya da ekran kapalı mıydı?
     * Uygulama açıldığında kilit ekranı zaten örtülmüş olduğundan bunu
     * ancak burada, çalma anında doğru ölçebiliyoruz.
     */
    public static volatile boolean lockedWhenRang = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::onTimeout;
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        int nid = intent == null ? -1 : intent.getIntExtra(EXTRA_NID, -1);

        if (ACTION_RING.equals(action)) {
            AlarmStore.Item it = AlarmStore.get(this, nid);
            if (it != null) {
                startRinging(it);
            } else {
                // startForegroundService ile başlatıldık; boş da olsa önce öne geçmeliyiz
                startForegroundCompat(buildPlaceholder());
                finishOrNext();
            }
        } else if (ACTION_DISMISS.equals(action)) {
            AlarmStore.Item it = AlarmStore.get(this, nid);
            if (it != null) {
                AlarmStore.remove(this, nid);
                AlarmStore.addAction(this, "dismiss", it, 0);
                AlarmPlugin.emitAction();
            }
            if (nid == ringingNid || ringingNid == -1) finishOrNext();
        } else if (ACTION_SNOOZE.equals(action)) {
            AlarmStore.Item it = AlarmStore.get(this, nid);
            if (it != null) {
                int minutes = intent.getIntExtra(EXTRA_MINUTES, SNOOZE_MINUTES);
                it.at = System.currentTimeMillis() + minutes * 60_000L;
                AlarmStore.put(this, it);
                AlarmScheduler.schedule(this, it);
                AlarmStore.addAction(this, "snooze", it, it.at);
                AlarmPlugin.emitAction();
            }
            if (nid == ringingNid || ringingNid == -1) finishOrNext();
        } else if (ACTION_STOP.equals(action)) {
            // Web tarafı alarmı kendisi kapattı/erteledi
            if (nid == ringingNid || ringingNid == -1) finishOrNext();
        } else if (ringingNid == -1) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRinging(AlarmStore.Item it) {
        ringingNid = it.nid;
        KeyguardManager km = getSystemService(KeyguardManager.class);
        PowerManager pm = getSystemService(PowerManager.class);
        lockedWhenRang = (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
        startForegroundCompat(buildRingingNotification(it));
        startSound();
        startVibration();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    /** Çalan alarm bitti; zamanı geçmiş başka alarm varsa onu çal, yoksa dur. */
    private void finishOrNext() {
        stopSoundAndVibration();
        handler.removeCallbacks(timeout);
        ringingNid = -1;
        long now = System.currentTimeMillis();
        for (AlarmStore.Item other : AlarmStore.all(this)) {
            if (other.at <= now) {
                startRinging(other);
                return;
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void onTimeout() {
        AlarmStore.Item it = AlarmStore.get(this, ringingNid);
        if (it != null) {
            // Artık çalmayacak; yeniden başlatmada tekrar kurulmasın
            AlarmStore.remove(this, it.nid);
            showMissed(it);
        }
        finishOrNext();
    }

    /* ---------- Ses ve titreşim ---------- */

    private void startSound() {
        stopSound();
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // alarm ses seviyesinde, sessiz modda da
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(TAG, "Alarm sesi çalınamadı", e);
            stopSound();
        }
    }

    private void stopSound() {
        if (player == null) return;
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
        }
        player.release();
        player = null;
    }

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibrator = ((VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 800, 600};
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs);
        } else {
            vibrator.vibrate(pattern, 0, attrs);
        }
    }

    private void stopSoundAndVibration() {
        stopSound();
        if (vibrator != null) vibrator.cancel();
    }

    /* ---------- Bildirimler ---------- */

    private void ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ring = new NotificationChannel(CHANNEL_RING, "Çalan alarm",
            NotificationManager.IMPORTANCE_HIGH);
        ring.setDescription("Süresi dolan alarm çalarken gösterilir");
        ring.setSound(null, null); // sesi servis kendisi çalıyor
        ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);

        NotificationChannel missed = new NotificationChannel(CHANNEL_MISSED, "Yanıtlanmayan alarm",
            NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(missed);
    }

    private PendingIntent openAppIntent(int nid) {
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NID, nid);
        return PendingIntent.getActivity(this, nid, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceIntent(String action, int nid) {
        Intent i = new Intent(this, AlarmService.class).setAction(action).putExtra(EXTRA_NID, nid);
        int requestCode = (action + nid).hashCode();
        return PendingIntent.getService(this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String clock(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private Notification buildRingingNotification(AlarmStore.Item it) {
        ensureChannels();
        return new NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(0xFF7C6CFF)
            .setContentTitle("⏰ " + it.label)
            .setContentText(clock(it.created) + "'de kurulan alarm çalıyor")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppIntent(it.nid))
            // Ekran kapalı/kilitliyken çalma ekranını doğrudan aç
            .setFullScreenIntent(openAppIntent(it.nid), true)
            .addAction(0, "Ertele · " + SNOOZE_MINUTES + " dk", serviceIntent(ACTION_SNOOZE, it.nid))
            .addAction(0, "Kapat", serviceIntent(ACTION_DISMISS, it.nid))
            .build();
    }

    private Notification buildPlaceholder() {
        ensureChannels();
        return new NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Süre Alarmı")
            .build();
    }

    private void showMissed(AlarmStore.Item it) {
        ensureChannels();
        Notification n = new NotificationCompat.Builder(this, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(0xFF7C6CFF)
            .setContentTitle("Yanıtlanmayan alarm: " + it.label)
            .setContentText(clock(it.at) + "'de çaldı")
            .setContentIntent(openAppIntent(it.nid))
            .setAutoCancel(true)
            .build();
        getSystemService(NotificationManager.class).notify(it.nid, n);
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_RINGING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_RINGING, n);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        stopSoundAndVibration();
        ringingNid = -1;
        super.onDestroy();
    }
}
