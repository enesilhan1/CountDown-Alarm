package com.enesilhan.countdownalarm.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.enesilhan.countdownalarm.MainActivity;

/** Alarmları AlarmManager'a kurar ve iptal eder. */
public final class AlarmScheduler {

    private AlarmScheduler() {}

    private static PendingIntent firePendingIntent(Context c, int nid) {
        Intent i = new Intent(c, AlarmReceiver.class)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmService.EXTRA_NID, nid);
        return PendingIntent.getBroadcast(c, nid, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, AlarmStore.Item it) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent fire = firePendingIntent(c, it.nid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Tam zamanlı alarm izni yoksa en azından yaklaşık zamanda çalsın
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.at, fire);
            return;
        }
        // Saat uygulamalarının kullandığı yöntem: güç tasarrufunda ertelenmez,
        // durum çubuğunda alarm simgesi gösterilir
        PendingIntent show = PendingIntent.getActivity(c, it.nid,
            new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(it.at, show), fire);
    }

    public static void cancel(Context c, int nid) {
        c.getSystemService(AlarmManager.class).cancel(firePendingIntent(c, nid));
    }

    /** Telefon yeniden başlayınca AlarmManager temizlenir; kayıtlı alarmları tekrar kur. */
    public static void rescheduleAll(Context c) {
        for (AlarmStore.Item it : AlarmStore.all(c)) schedule(c, it);
    }
}
