package com.enesilhan.countdownalarm;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.enesilhan.countdownalarm.alarm.AlarmPlugin;
import com.enesilhan.countdownalarm.alarm.AlarmService;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /** Alarm açıldığında telefon kilitli miydi? Kapatınca kilit ekranına dönmek için. */
    private boolean openedOverLockScreen = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AlarmPlugin.class);
        super.onCreate(savedInstanceState);
        handleAlarmIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAlarmIntent(intent);
    }

    /** Çalan alarmdan açıldıysak kilit ekranının üstünde göster ve ekranı aç. */
    private void handleAlarmIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(AlarmService.EXTRA_NID)) return;
        KeyguardManager km = getSystemService(KeyguardManager.class);
        openedOverLockScreen = AlarmService.lockedWhenRang || (km != null && km.isKeyguardLocked());
        setShowOverLockScreen(true);
    }

    private void setShowOverLockScreen(boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(show);
            setTurnScreenOn(show);
        } else {
            int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            if (show) getWindow().addFlags(flags);
            else getWindow().clearFlags(flags);
        }
    }

    /**
     * Alarm kapatıldı/ertelendi: kilit ekranı izni geri alınır. Alarm kilit
     * ekranının üstünde açıldıysa uygulama arkaya alınır ve kullanıcı kilit
     * ekranına (ya da önceki ekranına) döner.
     */
    public void releaseLockScreen() {
        runOnUiThread(() -> {
            if (openedOverLockScreen) moveTaskToBack(true);
            openedOverLockScreen = false;
            setShowOverLockScreen(false);
            getIntent().removeExtra(AlarmService.EXTRA_NID);
        });
    }
}
