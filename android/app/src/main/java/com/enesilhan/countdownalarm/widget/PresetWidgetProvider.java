package com.enesilhan.countdownalarm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
 */
public class PresetWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "PresetWidget";
    private static final String ACTION_START = "com.enesilhan.countdownalarm.WIDGET_START";
    private static final String ACTION_STOP = "com.enesilhan.countdownalarm.WIDGET_STOP";

    private static final int COLOR_MUTED = 0xFF8F95B2;
    private static final int COLOR_ACCENT2 = 0xFF4FD1C5;
    private static final int COLOR_DANGER = 0xFFFF6B7D;

    /** Alarmlar ya da hazır süreler değişince tüm widget'ları yenile. */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, PresetWidgetProvider.class));
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetStore.unbind(c, id);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String action = intent.getAction();
        if (!ACTION_START.equals(action) && !ACTION_STOP.equals(action)) return;
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        WidgetStore.Preset p = WidgetStore.preset(c, WidgetStore.presetIdFor(c, widgetId));
        if (p == null) return;
        if (ACTION_START.equals(action)) start(c, p);
        else stop(c, p);
    }

    /* ---------- Görünüm ---------- */

    static void update(Context c, AppWidgetManager m, int widgetId) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_preset);
        String presetId = WidgetStore.presetIdFor(c, widgetId);
        WidgetStore.Preset p = WidgetStore.preset(c, presetId);
        v.setViewVisibility(R.id.widget_timer, View.GONE);

        if (p == null) {
            // Henüz bağlanmamış ya da hazır süresi uygulamada silinmiş
            v.setTextViewText(R.id.widget_label, presetId == null ? "Hazır süre seç" : "Hazır süre silindi");
            v.setTextViewText(R.id.widget_status, "Dokun ve yeniden seç");
            v.setTextColor(R.id.widget_status, COLOR_MUTED);
            setAction(v, "Seç", R.drawable.widget_button_neutral);
            Intent cfg = new Intent(c, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, widgetId, cfg,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);
            m.updateAppWidget(widgetId, v);
            return;
        }

        v.setTextViewText(R.id.widget_label, p.title());
        AlarmStore.Item active = soonestFor(c, p.id);
        long now = System.currentTimeMillis();
        String clickAction;

        if (active == null) {
            String status = p.label.isEmpty() ? "Dokun, başlasın" : WidgetStore.durationText(p.minutes);
            if (!p.soundName.isEmpty()) status += "  ·  🎵";
            v.setTextViewText(R.id.widget_status, status);
            v.setTextColor(R.id.widget_status, COLOR_MUTED);
            setAction(v, "Başlat", R.drawable.widget_button_start);
            clickAction = ACTION_START;
        } else if (active.at > now) {
            v.setTextViewText(R.id.widget_status, "Çalacak " + clock(active.at) + "  ·");
            v.setTextColor(R.id.widget_status, COLOR_MUTED);
            // Canlı geri sayım: her saniye widget güncellemeye gerek kalmadan
            v.setViewVisibility(R.id.widget_timer, View.VISIBLE);
            v.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + (active.at - now), null, true);
            v.setChronometerCountDown(R.id.widget_timer, true);
            setAction(v, "İptal", R.drawable.widget_button_neutral);
            clickAction = ACTION_STOP;
        } else {
            v.setTextViewText(R.id.widget_status, "Çalıyor!");
            v.setTextColor(R.id.widget_status, COLOR_DANGER);
            setAction(v, "Kapat", R.drawable.widget_button_danger);
            clickAction = ACTION_STOP;
        }

        Intent i = new Intent(c, PresetWidgetProvider.class)
            .setAction(clickAction)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent pi = PendingIntent.getBroadcast(c, widgetId, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        m.updateAppWidget(widgetId, v);
    }

    private static void setAction(RemoteViews v, String text, int background) {
        v.setTextViewText(R.id.widget_action, text);
        v.setInt(R.id.widget_action, "setBackgroundResource", background);
        v.setTextColor(R.id.widget_action, Color.WHITE);
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
