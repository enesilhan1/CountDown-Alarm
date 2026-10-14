package com.enesilhan.countdownalarm.alarm;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.enesilhan.countdownalarm.MainActivity;
import com.enesilhan.countdownalarm.widget.PresetWidgetProvider;
import com.enesilhan.countdownalarm.widget.WidgetStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
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

    private static final String TAG = "AlarmPlugin";
    private static final long PREVIEW_MAX_MS = 15_000;

    private static WeakReference<AlarmPlugin> instance = new WeakReference<>(null);

    /** Ses seçerken "Dinle" ile çalınan önizleme. */
    private static MediaPlayer preview;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable previewTimeout = AlarmPlugin::stopPreviewIfPlaying;

    @Override
    public void load() {
        instance = new WeakReference<>(this);
    }

    /** Bildirimden Ertele/Kapat yapıldı; uygulama açıksa web tarafı hemen senkronlansın. */
    public static void emitAction() {
        AlarmPlugin p = instance.get();
        if (p == null) return;
        p.releaseLockScreen();
        p.notifyListeners("alarmAction", new JSObject());
    }

    private void releaseLockScreen() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).releaseLockScreen();
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
        it.sound = call.getString("sound", "");
        it.soundName = call.getString("soundName", "");
        it.presetId = call.getString("presetId", "");
        it.repeat = Boolean.TRUE.equals(call.getBoolean("repeat", false));
        it.durationMin = call.getInt("duration", 0);
        it.slot = call.getLong("slot", at);
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
            releaseLockScreen();
        }
        call.resolve();
    }

    /** Android 14+ tam ekran bildirim izni (kilit ekranında çalma ekranını açmak için). */
    @PluginMethod
    public void canUseFullScreen(PluginCall call) {
        boolean granted = Build.VERSION.SDK_INT < 34
            || getContext().getSystemService(NotificationManager.class).canUseFullScreenIntent();
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void openFullScreenSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 34) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:" + getContext().getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        call.resolve();
    }

    /** Kayıtlı hazır süreler; widget'lar bunları gösterir. */
    @PluginMethod
    public void syncPresets(PluginCall call) {
        JSArray presets = call.getArray("presets", new JSArray());
        WidgetStore.setPresets(getContext(), presets.toString());
        PresetWidgetProvider.updateAll(getContext());
        call.resolve();
    }

    /* ---------- Alarm sesi seçimi ---------- */

    /** Telefonun dosya seçicisini yalnızca ses dosyalarıyla açar. */
    @PluginMethod
    public void pickSound(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "pickSoundResult");
    }

    @ActivityCallback
    private void pickSoundResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            JSObject ret = new JSObject();
            ret.put("cancelled", true);
            call.resolve(ret);
            return;
        }
        Uri uri = data.getData();
        // Kalıcı okuma izni: uygulama/telefon yeniden başlasa da alarm bu dosyayı çalabilsin
        try {
            getContext().getContentResolver()
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.w(TAG, "Kalıcı izin alınamadı", e);
        }
        if (!isPlayable(uri)) {
            call.reject("Bu dosya çalınamıyor. Başka bir ses dosyası seçin.");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        ret.put("name", displayName(uri));
        call.resolve(ret);
    }

    private boolean isPlayable(Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(getContext(), uri);
            return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                || r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContext().getContentResolver()
            .query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null) return name.replaceFirst("\\.[^.]+$", ""); // uzantıyı at
            }
        } catch (Exception ignored) {
        }
        return "Seçilen ses";
    }

    /** Sesi alarm ses seviyesinde en fazla 15 sn çalar. uri boşsa varsayılan alarm sesi. */
    @PluginMethod
    public void previewSound(PluginCall call) {
        stopPreviewIfPlaying();
        String s = call.getString("uri", "");
        Uri uri = s == null || s.isEmpty() ? AlarmService.defaultAlarmUri(getContext()) : Uri.parse(s);
        try {
            preview = AlarmService.createAlarmPlayer(getContext(), uri);
            preview.setOnCompletionListener(mp -> stopPreviewIfPlaying());
            preview.start();
            handler.postDelayed(previewTimeout, PREVIEW_MAX_MS);
            call.resolve();
        } catch (Exception e) {
            stopPreviewIfPlaying();
            call.reject("Ses çalınamadı");
        }
    }

    @PluginMethod
    public void stopPreview(PluginCall call) {
        stopPreviewIfPlaying();
        call.resolve();
    }

    static void stopPreviewIfPlaying() {
        handler.removeCallbacks(previewTimeout);
        if (preview == null) return;
        try {
            preview.stop();
        } catch (IllegalStateException ignored) {
        }
        preview.release();
        preview = null;
        AlarmPlugin p = instance.get();
        if (p != null) p.notifyListeners("previewEnded", new JSObject());
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
