package com.enesilhan.countdownalarm.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.enesilhan.countdownalarm.MainActivity;

import java.util.List;

/** Widget eklenirken (ya da yeniden ayarlanırken) bağlanacak hazır süreyi seçtirir. */
public class WidgetConfigActivity extends Activity {

    private static final int BG = 0xFF0F1220;
    private static final int SURFACE = 0xFF22273D;
    private static final int TEXT = 0xFFEEF0FA;
    private static final int MUTED = 0xFF8F95B2;
    private static final int ACCENT = 0xFF7C6CFF;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Kullanıcı seçmeden çıkarsa widget eklenmesin
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(28), dp(20), dp(20));

        list.addView(text("Widget için hazır süre seç", 22, TEXT, true), wrap(0));
        list.addView(text("Widget'a dokununca bu alarm uygulamayı açmadan kurulur.", 14, MUTED, false), wrap(6));

        List<WidgetStore.Preset> presets = WidgetStore.presets(this);
        if (presets.isEmpty()) {
            list.addView(text("Henüz kayıtlı hazır süren yok. Uygulamada bir süre seçip "
                + "\"＋ Kaydet\" ile kaydet, sonra widget'ı tekrar ekle.", 15, TEXT, false), wrap(24));
            TextView open = button("Uygulamayı aç", ACCENT);
            open.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
            list.addView(open, wrap(16));
        } else {
            for (WidgetStore.Preset p : presets) {
                String line = p.label.isEmpty()
                    ? WidgetStore.durationText(p.minutes)
                    : p.label + "  ·  " + WidgetStore.durationText(p.minutes);
                if (!p.soundName.isEmpty()) line += "  ·  🎵 " + p.soundName;
                TextView item = button(line, SURFACE);
                item.setOnClickListener(v -> choose(p));
                list.addView(item, wrap(12));
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        scroll.addView(list);
        setContentView(scroll);
    }

    private void choose(WidgetStore.Preset p) {
        WidgetStore.bind(this, widgetId, p.id);
        PresetWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    /* ---------- Basit görünüm yardımcıları ---------- */

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private LinearLayout.LayoutParams wrap(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, int color) {
        TextView b = text(s, 16, TEXT, false);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(56));
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }
}
