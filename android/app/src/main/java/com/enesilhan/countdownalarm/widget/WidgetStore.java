package com.enesilhan.countdownalarm.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget'ların ihtiyaç duyduğu bilgiler: uygulamadaki kayıtlı hazır sürelerin
 * kopyası (web tarafı her değişiklikte gönderir) ve hangi widget'ın hangi
 * hazır süreye bağlı olduğu.
 */
public final class WidgetStore {

    private static final String PREFS = "countdown_widgets";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_WIDGET = "widget_";

    private WidgetStore() {}

    public static final class Preset {
        public String id;
        public int minutes;
        public String label;
        public String sound;
        public String soundName;
        public boolean repeat;

        /** Widget başlığı: etiket, yoksa süre. */
        public String title() {
            return label.isEmpty() ? durationText(minutes) : label;
        }
    }

    public static String durationText(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h > 0 && m > 0) return h + " sa " + m + " dk";
        if (h > 0) return h + " sa";
        return m + " dk";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void setPresets(Context c, String json) {
        prefs(c).edit().putString(KEY_PRESETS, json).apply();
    }

    public static synchronized List<Preset> presets(Context c) {
        List<Preset> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_PRESETS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optInt("minutes") <= 0) continue;
                Preset p = new Preset();
                p.id = o.optString("id");
                p.minutes = o.optInt("minutes");
                p.label = o.optString("label", "");
                p.sound = o.optString("sound", "");
                p.soundName = o.optString("soundName", "");
                p.repeat = o.optBoolean("repeat", false);
                list.add(p);
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static Preset preset(Context c, String id) {
        if (id == null) return null;
        for (Preset p : presets(c)) if (id.equals(p.id)) return p;
        return null;
    }

    public static void bind(Context c, int widgetId, String presetId) {
        prefs(c).edit().putString(KEY_WIDGET + widgetId, presetId).apply();
    }

    public static String presetIdFor(Context c, int widgetId) {
        return prefs(c).getString(KEY_WIDGET + widgetId, null);
    }

    public static void unbind(Context c, int widgetId) {
        prefs(c).edit().remove(KEY_WIDGET + widgetId).apply();
    }
}
