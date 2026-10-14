package com.enesilhan.countdownalarm.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kurulu alarmların yerel kopyası. Uygulama kapalıyken (çalma, erteleme,
 * yeniden başlatma sonrası kurma) bu bilgiye ihtiyaç var.
 * Bildirimden yapılan Ertele/Kapat işlemleri de burada sıraya alınır;
 * web tarafı açılınca bunları alıp kendi listesine uygular.
 */
public final class AlarmStore {

    private static final String PREFS = "countdown_alarms";
    private static final String KEY_ALARMS = "alarms";
    private static final String KEY_ACTIONS = "pending_actions";

    private AlarmStore() {}

    public static final class Item {
        public int nid;
        public String alarmId;
        public String label;
        public long at;
        public long created;
        /** Seçilen ses dosyasının content:// adresi; boşsa telefonun alarm sesi. */
        public String sound = "";

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                .put("nid", nid)
                .put("alarmId", alarmId)
                .put("label", label)
                .put("at", at)
                .put("created", created)
                .put("sound", sound);
        }

        static Item fromJson(JSONObject o) {
            Item it = new Item();
            it.nid = o.optInt("nid");
            it.alarmId = o.optString("alarmId");
            it.label = o.optString("label", "Alarm");
            it.at = o.optLong("at");
            it.created = o.optLong("created");
            it.sound = o.optString("sound", "");
            return it;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject readAlarms(Context c) {
        try {
            return new JSONObject(prefs(c).getString(KEY_ALARMS, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static synchronized void put(Context c, Item it) {
        JSONObject all = readAlarms(c);
        try {
            all.put(String.valueOf(it.nid), it.toJson());
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString(KEY_ALARMS, all.toString()).apply();
    }

    public static synchronized Item get(Context c, int nid) {
        JSONObject o = readAlarms(c).optJSONObject(String.valueOf(nid));
        return o == null ? null : Item.fromJson(o);
    }

    public static synchronized void remove(Context c, int nid) {
        JSONObject all = readAlarms(c);
        all.remove(String.valueOf(nid));
        prefs(c).edit().putString(KEY_ALARMS, all.toString()).apply();
    }

    public static synchronized List<Item> all(Context c) {
        JSONObject all = readAlarms(c);
        List<Item> list = new ArrayList<>();
        for (Iterator<String> keys = all.keys(); keys.hasNext(); ) {
            JSONObject o = all.optJSONObject(keys.next());
            if (o != null) list.add(Item.fromJson(o));
        }
        return list;
    }

    /** Bildirimden yapılan bir işlemi web tarafı için sıraya al. */
    public static synchronized void addAction(Context c, String type, Item it, long newAt) {
        JSONArray actions;
        try {
            actions = new JSONArray(prefs(c).getString(KEY_ACTIONS, "[]"));
            actions.put(new JSONObject()
                .put("type", type)
                .put("alarmId", it.alarmId)
                .put("time", System.currentTimeMillis())
                .put("at", newAt));
        } catch (JSONException e) {
            return;
        }
        prefs(c).edit().putString(KEY_ACTIONS, actions.toString()).apply();
    }

    public static synchronized JSONArray takeActions(Context c) {
        JSONArray actions;
        try {
            actions = new JSONArray(prefs(c).getString(KEY_ACTIONS, "[]"));
        } catch (JSONException e) {
            actions = new JSONArray();
        }
        prefs(c).edit().remove(KEY_ACTIONS).apply();
        return actions;
    }
}
