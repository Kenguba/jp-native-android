package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;

public class QuickLookupActivity extends Activity {

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String incoming = readQuery(getIntent());
        if (incoming == null) incoming = "";
        final String q = incoming.trim();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        root.setBackgroundColor(Color.WHITE);

        TextView word = new TextView(this);
        word.setText(q.isEmpty() ? "快速查词" : q);
        word.setTextSize(27);
        word.setTextColor(0xff111111);
        word.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(word, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = new TextView(this);
        detail.setText(formatLookup(q));
        detail.setTextSize(17);
        detail.setTextColor(0xff333333);
        detail.setPadding(0, dp(10), 0, dp(14));
        root.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button full = new Button(this);
        full.setText("完整搜索");
        full.setAllCaps(false);
        full.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.SearchActivity.class)
                    .putExtra("query", q);
            startActivity(i);
            finish();
        });

        Button close = new Button(this);
        close.setText("关闭");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());

        actions.addView(full);
        actions.addView(close);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        setFinishOnTouchOutside(true);

        Window w = getWindow();
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(420));
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.22f;
        w.setAttributes(lp);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private String readQuery(Intent i) {
        if (i == null) return null;

        CharSequence p = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (p != null) return p.toString();

        String shared = i.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null) return shared;

        String q = i.getStringExtra("query");
        if (q != null) return q;

        return null;
    }

    private String formatLookup(String q) {
        if (q == null || q.trim().isEmpty()) return "没有收到要查询的单词。";

        String clean = q.trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        for (Map.Entry<String,String[]> e : MainActivity.WORDS.entrySet()) {
            String[] d = e.getValue();
            if (e.getKey().equals(clean) ||
                    e.getKey().contains(clean) ||
                    d[0].equals(clean) ||
                    d[0].contains(clean) ||
                    d[1].toLowerCase(Locale.ROOT).contains(lower)) {
                return d[0] + " · " + d[1] + " · " + d[2] + "\n\n" + d[3];
            }
        }

        return "本地示例词库暂未收录：\n" + clean;
    }
}
