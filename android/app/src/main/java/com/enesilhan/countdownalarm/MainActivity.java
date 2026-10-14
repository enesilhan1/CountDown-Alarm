package com.enesilhan.countdownalarm;

import android.os.Bundle;

import com.enesilhan.countdownalarm.alarm.AlarmPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
