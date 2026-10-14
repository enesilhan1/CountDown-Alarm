package com.enesilhan.countdownalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.enesilhan.countdownalarm.widget.PresetWidgetProvider;

/** Alarm zamanı gelince çalma servisini başlatır; açılışta alarmları yeniden kurar. */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.enesilhan.countdownalarm.ALARM_FIRE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_FIRE.equals(action)) {
            Intent ring = new Intent(context, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(AlarmService.EXTRA_NID, intent.getIntExtra(AlarmService.EXTRA_NID, -1));
            ContextCompat.startForegroundService(context, ring);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AlarmScheduler.rescheduleAll(context);
            // Güncelleme/yeniden başlatma sonrası widget'lar varsayılan görünümde kalmasın.
            // Ana ekran uygulaması bu sırada eski APK'yı okumaya çalışıp ilk güncellemeyi
            // kaçırabiliyor; birkaç saniye sonra bir kez daha yenile.
            PresetWidgetProvider.updateAll(context);
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                PresetWidgetProvider.updateAll(app);
                pending.finish();
            }, 4000);
        }
    }
}
