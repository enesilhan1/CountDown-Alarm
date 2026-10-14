package com.enesilhan.countdownalarm.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.enesilhan.countdownalarm.R;
import com.enesilhan.countdownalarm.alarm.AlarmPlugin;
import com.enesilhan.countdownalarm.alarm.AlarmScheduler;
import com.enesilhan.countdownalarm.alarm.AlarmService;
import com.enesilhan.countdownalarm.alarm.AlarmStore;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Hazır süre widget'ı: bağlı olduğu kayıtlı hazır süreyi tek dokunuşla başlatır.
 * Alarm kuruluyken çalma saatini ve geri sayımı gösterir, dokununca iptal eder;
 * çalarken dokununca kapatır.
 *
 * Boyuta göre iki görünüm: geniş widget ayrıntılı (etiket, çalma saati, saniyeli
 * geri sayım, buton); 1x1 widget yalnızca süreyi ("8s", "42dk") gösterir.
 * Renk duruma göre değişir: boşta koyu, kuruluyken mor, çalarken kırmızı.
 */
public class PresetWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "PresetWidget";
    private static final String ACTION_START = "com.enesilhan.countdownalarm.WIDGET_START";
    private static final String ACTION_STOP = "com.enesilhan.countdownalarm.WIDGET_STOP";
    /** 1x1 widget'larda gösterilen yuvarlanmış süre değişince yenileme. */
    private static final String ACTION_TICK = "com.enesilhan.countdownalarm.WIDGET_TICK";

    private static final int COLOR_MUTED = 0xFF8F95B2;
    private static final int COLOR_ON_FILL = 0xE6FFFFFF;

    /** Bu genişliğin (dp) altındaki widget'lar 1x1 görünümünü kullanır. */
    private static final int SMALL_MAX_WIDTH_DP = 110;

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private enum State { IDLE, RUNNING, RINGING }

    /** Alarmlar ya da hazır süreler değişince tüm widget'ları yenile. */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        for (int id : allWidgetIds(c, m)) update(c, m, id);
        scheduleTick(c, m);
    }

    private static int[] allWidgetIds(Context c, AppWidgetManager m) {
        int[] big = m.getAppWidgetIds(new ComponentName(c, PresetWidgetProvider.class));
        int[] small = m.getAppWidgetIds(new ComponentName(c, PresetWidgetSmallProvider.class));
        int[] all = new int[big.length + small.length];
        System.arraycopy(big, 0, all, 0, big.length);
        System.arraycopy(small, 0, all, big.length, small.length);
        return all;
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle newOptions) {
        // Boyut değişti: 1x1 ↔ geniş görünüm
        updateAll(c);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetStore.unbind(c, id);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)) {
            updateAll(c);
            return;
        }
        if (!ACTION_START.equals(action) && !ACTION_STOP.equals(action)) return;
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        WidgetStore.Preset p = WidgetStore.preset(c, WidgetStore.presetIdFor(c, widgetId));
        if (p == null) return;
        if (ACTION_START.equals(action)) start(c, p);
        else stop(c, p);
    }

    /* ---------- Görünüm ---------- */

    private static boolean isSmall(Context c, AppWidgetManager m, int widgetId) {
        Bundle opts = m.getAppWidgetOptions(widgetId);
        int minWidth = opts == null ? 0 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (minWidth > 0) return minWidth < SMALL_MAX_WIDTH_DP;
        // Ana ekran boyutu henüz bildirmediyse widget türüne göre karar ver
        android.appwidget.AppWidgetProviderInfo info = m.getAppWidgetInfo(widgetId);
        return info != null && info.provider.getClassName().equals(PresetWidgetSmallProvider.class.getName());
    }

    static void update(Context c, AppWidgetManager m, int widgetId) {
        boolean small = isSmall(c, m, widgetId);
        RemoteViews v = new RemoteViews(c.getPackageName(),
            small ? R.layout.widget_preset_small : R.layout.widget_preset);
        String presetId = WidgetStore.presetIdFor(c, widgetId);
        WidgetStore.Preset p = WidgetStore.preset(c, presetId);

        if (p == null) {
            // Henüz bağlanmamış ya da hazır süresi uygulamada silinmiş
            v.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg);
            if (small) {
                v.setTextViewText(R.id.widget_small_text, "Seç");
            } else {
                v.setViewVisibility(R.id.widget_timer, View.GONE);
                v.setTextViewText(R.id.widget_label, presetId == null ? "Hazır süre seç" : "Hazır süre silindi");
                v.setTextViewText(R.id.widget_status, "Dokun ve yeniden seç");
                v.setTextColor(R.id.widget_status, COLOR_MUTED);
                setAction(v, "Seç", R.drawable.widget_button_neutral);
            }
            Intent cfg = new Intent(c, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, widgetId, cfg,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);
            m.updateAppWidget(widgetId, v);
            return;
        }

        AlarmStore.Item active = soonestFor(c, p.id);
        long now = System.currentTimeMillis();
        State state = active == null ? State.IDLE : (active.at > now ? State.RUNNING : State.RINGING);

        v.setInt(R.id.widget_root, "setBackgroundResource",
            state == State.IDLE ? R.drawable.widget_bg
                : state == State.RUNNING ? R.drawable.widget_bg_running
                : R.drawable.widget_bg_ringing);

        if (small) {
            String text = state == State.IDLE ? compactDuration(p.minutes)
                : state == State.RUNNING ? roundedRemaining(active.at - now)
                : "⏰";
            v.setTextViewText(R.id.widget_small_text, text);
            v.setContentDescription(R.id.widget_root, p.title() + ", " + text);
        } else {
            renderLarge(v, p, active, state, now);
        }

        Intent i = new Intent(c, PresetWidgetProvider.class)
            .setAction(state == State.IDLE ? ACTION_START : ACTION_STOP)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent pi = PendingIntent.getBroadcast(c, widgetId, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        m.updateAppWidget(widgetId, v);
    }

    private static void renderLarge(RemoteViews v, WidgetStore.Preset p, AlarmStore.Item active, State state, long now) {
        v.setTextViewText(R.id.widget_label, p.title());
        v.setViewVisibility(R.id.widget_timer, View.GONE);
        if (state == State.IDLE) {
            String status = p.label.isEmpty() ? "Dokun, başlasın" : WidgetStore.durationText(p.minutes);
            if (p.repeat) status += "  ·  🔁";
            if (!p.soundName.isEmpty()) status += "  ·  🎵";
            v.setTextViewText(R.id.widget_status, status);
            v.setTextColor(R.id.widget_status, COLOR_MUTED);
            setAction(v, "Başlat", R.drawable.widget_button_start);
        } else if (state == State.RUNNING) {
            v.setTextViewText(R.id.widget_status, "Çalacak " + clock(active.at) + "  ·");
            v.setTextColor(R.id.widget_status, COLOR_ON_FILL);
            // Canlı geri sayım: her saniye widget güncellemeye gerek kalmadan
            v.setViewVisibility(R.id.widget_timer, View.VISIBLE);
            v.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + (active.at - now), null, true);
            v.setChronometerCountDown(R.id.widget_timer, true);
            v.setTextColor(R.id.widget_timer, Color.WHITE);
            setAction(v, "İptal", R.drawable.widget_button_onfill);
        } else {
            v.setTextViewText(R.id.widget_status, "Çalıyor!");
            v.setTextColor(R.id.widget_status, Color.WHITE);
            setAction(v, "Kapat", R.drawable.widget_button_onfill);
        }
    }

    private static void setAction(RemoteViews v, String text, int background) {
        v.setTextViewText(R.id.widget_action, text);
        v.setInt(R.id.widget_action, "setBackgroundResource", background);
        v.setTextColor(R.id.widget_action, Color.WHITE);
    }

    /** Boştaki 1x1 widget: hazır sürenin tam değeri ("15dk", "8s", "1s30dk"). */
    static String compactDuration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h > 0 && m > 0) return h + "s" + m + "dk";
        if (h > 0) return h + "s";
        return m + "dk";
    }

    /**
     * Geri sayan 1x1 widget: 1 saatten fazla kaldıysa en yakın tam saat ("8s", "7s"),
     * azsa yalnızca dakika ("42dk"). Saniye ve küsurat gösterilmez.
     */
    static String roundedRemaining(long remaining) {
        if (remaining >= HOUR) return Math.round((double) remaining / HOUR) + "s";
        long minutes = Math.round((double) remaining / MINUTE);
        return Math.max(1, Math.min(59, minutes)) + "dk";
    }

    /** Gösterilen yuvarlanmış değerin bir sonraki değişeceği kalan süre. */
    private static long nextChangeAt(long remaining) {
        if (remaining >= HOUR) {
            long hours = Math.round((double) remaining / HOUR);
            // 7s → 6s geçişi 6,5 saatte; 1 saatin altına inince dakikaya geçilir
            return Math.max((long) ((hours - 0.5) * HOUR), HOUR - 1);
        }
        long minutes = Math.round((double) remaining / MINUTE);
        return Math.max(0, (long) ((minutes - 0.5) * MINUTE));
    }

    /**
     * Geri sayan 1x1 widget'lar için bir sonraki değişim anına tek bir yenileme kur.
     * Cihazı uyandırmaz (RTC): ekran kapalıyken pil harcamaz, açılınca hemen yenilenir.
     */
    private static void scheduleTick(Context c, AppWidgetManager m) {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (int id : allWidgetIds(c, m)) {
            if (!isSmall(c, m, id)) continue;
            WidgetStore.Preset p = WidgetStore.preset(c, WidgetStore.presetIdFor(c, id));
            if (p == null) continue;
            AlarmStore.Item active = soonestFor(c, p.id);
            if (active == null || active.at <= now) continue;
            long remaining = active.at - now;
            next = Math.min(next, now + (remaining - nextChangeAt(remaining)) + 500);
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0,
            new Intent(c, PresetWidgetProvider.class).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (next == Long.MAX_VALUE) {
            am.cancel(pi);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExact(AlarmManager.RTC, next, pi);
        } else {
            am.set(AlarmManager.RTC, next, pi);
        }
    }

    /** Bir süre sonra tüm widget'ları bir kez daha yenile (cihazı uyandırmaz). */
    public static void scheduleRefresh(Context c, int slot, long delayMs) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 100 + slot,
            new Intent(c, PresetWidgetProvider.class).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pi);
    }

    /** Bu hazır süreden kurulmuş en yakın alarm. */
    private static AlarmStore.Item soonestFor(Context c, String presetId) {
        AlarmStore.Item best = null;
        for (AlarmStore.Item it : AlarmStore.all(c)) {
            if (presetId.equals(it.presetId) && (best == null || it.at < best.at)) best = it;
        }
        return best;
    }

    private static String clock(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /* ---------- İşlemler ---------- */

    private static void start(Context c, WidgetStore.Preset p) {
        long now = System.currentTimeMillis();
        AlarmStore.Item it = new AlarmStore.Item();
        it.nid = new SecureRandom().nextInt(Integer.MAX_VALUE);
        it.alarmId = UUID.randomUUID().toString();
        it.label = p.label.isEmpty() ? "Alarm" : p.label;
        it.created = now;
        it.at = now + p.minutes * 60_000L;
        it.sound = p.sound;
        it.soundName = p.soundName;
        it.presetId = p.id;
        it.repeat = p.repeat;
        it.durationMin = p.minutes;
        it.slot = it.at;
        AlarmStore.put(c, it);
        AlarmScheduler.schedule(c, it);
        // Uygulama açılınca (ya da açıksa hemen) listesine eklesin
        AlarmStore.addAction(c, "create", it, it.at);
        AlarmPlugin.emitAction();
        Toast.makeText(c, it.label + " kuruldu · " + clock(it.at) + "'de çalacak", Toast.LENGTH_SHORT).show();
    }

    private static void stop(Context c, WidgetStore.Preset p) {
        long now = System.currentTimeMillis();
        boolean any = false;
        for (AlarmStore.Item it : AlarmStore.all(c)) {
            if (!p.id.equals(it.presetId)) continue;
            any = true;
            if (it.at <= now || AlarmService.isRinging(it.nid)) {
                // Çalıyor: servis kapatsın (sesi durdurur, kaydı siler, uygulamaya bildirir)
                Intent dismiss = new Intent(c, AlarmService.class)
                    .setAction(AlarmService.ACTION_DISMISS)
                    .putExtra(AlarmService.EXTRA_NID, it.nid);
                try {
                    c.startService(dismiss);
                    continue;
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Servis başlatılamadı, alarm doğrudan kaldırılıyor", e);
                }
            }
            AlarmScheduler.cancel(c, it.nid);
            AlarmStore.remove(c, it.nid);
            AlarmStore.addAction(c, "dismiss", it, 0);
        }
        if (!any) return;
        AlarmPlugin.emitAction();
        Toast.makeText(c, p.title() + " alarmı kapatıldı", Toast.LENGTH_SHORT).show();
    }
}
