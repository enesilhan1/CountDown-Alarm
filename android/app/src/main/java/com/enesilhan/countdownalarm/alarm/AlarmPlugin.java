package com.enesilhan.countdownalarm.alarm;

import android.Manifest;
import android.content.Intent;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import org.json.JSONException;

import java.lang.ref.WeakReference;

/**
 * Web tarafının (www/app.js) Android alarmlarını yönettiği köprü.
 * JS'de: window.Capacitor.Plugins.Alarm
 */
@CapacitorPlugin(
    name = "Alarm",
    permissions = @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
)
public class AlarmPlugin extends Plugin {

    private static WeakReference<AlarmPlugin> instance = new WeakReference<>(null);

    @Override
    public void load() {
        instance = new WeakReference<>(this);
    }

    /** Bildirimden Ertele/Kapat yapıldı; uygulama açıksa web tarafı hemen senkronlansın. */
    static void emitAction() {
        AlarmPlugin p = instance.get();
        if (p != null) p.notifyListeners("alarmAction", new JSObject());
    }

    @PluginMethod
    public void schedule(PluginCall call) {
        Integer nid = call.getInt("nid");
        Long at = call.getLong("at");
        if (nid == null || at == null) {
            call.reject("nid ve at gerekli");
            return;
        }
        AlarmStore.Item it = new AlarmStore.Item();
        it.nid = nid;
        it.at = at;
        it.alarmId = call.getString("alarmId", "");
        it.label = call.getString("label", "Alarm");
        it.created = call.getLong("created", System.currentTimeMillis());
        AlarmStore.put(getContext(), it);
        AlarmScheduler.schedule(getContext(), it);
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Integer nid = call.getInt("nid");
        if (nid == null) {
            call.reject("nid gerekli");
            return;
        }
        AlarmScheduler.cancel(getContext(), nid);
        AlarmStore.remove(getContext(), nid);
        if (AlarmService.ringingNid == nid) {
            Intent stop = new Intent(getContext(), AlarmService.class)
                .setAction(AlarmService.ACTION_STOP)
                .putExtra(AlarmService.EXTRA_NID, nid);
            getContext().startService(stop);
        }
        call.resolve();
    }

    /** Uygulama kapalıyken bildirimden yapılan işlemleri döndürür ve sırayı temizler. */
    @PluginMethod
    public void takePendingActions(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            ret.put("actions", new JSArray(AlarmStore.takeActions(getContext()).toString()));
        } catch (JSONException e) {
            ret.put("actions", new JSArray());
        }
        call.resolve(ret);
    }
}
