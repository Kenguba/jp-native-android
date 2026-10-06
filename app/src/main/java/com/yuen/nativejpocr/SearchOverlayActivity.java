package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.Locale;
import java.util.Map;

public final class SearchOverlayActivity extends Activity {
    public static final String ACTION_CLOSE = "com.yuen.nativejpocr.CLOSE_SEARCH_OVERLAY";
    public static volatile boolean VISIBLE = false;

    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_CLOSE.equals(intent.getAction())) finish();
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (strokeWidth > 0) g.setStroke(dp(strokeWidth), stroke);
        return g;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(closeReceiver, new IntentFilter(ACTION_CLOSE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(closeReceiver, new IntentFilter(ACTION_CLOSE));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), 0);
        root.setBackgroundColor(0xd91d1f24);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), 0, dp(6), 0);
        bar.setBackground(rounded(0xff1a1b1f, 15, 0xff1293ff, 2));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xff8e949e);
        input.setHint("请输入需要查找的内容");
        input.setTextSize(22);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(0, 0, dp(6), 0);

        TextView mic = new TextView(this);
        mic.setText("♩");
        mic.setTextColor(0xff1293ff);
        mic.setTextSize(29);
        mic.setGravity(Gravity.CENTER);

        bar.addView(input, new LinearLayout.LayoutParams(0, dp(64), 1));
        bar.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(64)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(0, dp(16), 0, dp(36));
        scroll.addView(suggestions, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        updateSuggestions(suggestions, "");

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSuggestions(suggestions, s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                openLookup(input.getText().toString());
                return true;
            }
            return false;
        });

        mic.setOnClickListener(v ->
                Toast.makeText(this, "语音入口已预留", Toast.LENGTH_SHORT).show());

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(0xff171717);
        w.setNavigationBarColor(0xff1d1f24);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        View decor = w.getDecorView();
        int vis = decor.getSystemUiVisibility();
        vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(vis);
    }

    private void updateSuggestions(LinearLayout suggestions, String raw) {
        suggestions.removeAllViews();

        String q = raw == null ? "" : raw.trim();
        String lower = q.toLowerCase(Locale.ROOT);
        int count = 0;

        for (Map.Entry<String, String[]> e : MainActivity.WORDS.entrySet()) {
            String word = e.getKey();
            String[] d = e.getValue();

            boolean match = q.isEmpty() ||
                    word.contains(q) ||
                    d[0].contains(q) ||
                    d[1].toLowerCase(Locale.ROOT).contains(lower) ||
                    d[3].contains(q);

            if (!match) continue;
            suggestions.addView(row(word, d[2] + ". " + d[3] + "  ·  " + d[0]));
            count++;
            if (count >= 15) break;
        }

        if (!q.isEmpty() && count == 0) {
            suggestions.addView(row(q, "本地词库暂无结果 · 点击后使用 AI / 在线查询"));
        }

        if (q.isEmpty() && count == 0) {
            TextView empty = new TextView(this);
            empty.setText("输入日语、中文或英文进行查询");
            empty.setTextColor(0xff9299a3);
            empty.setTextSize(16);
            empty.setPadding(dp(14), dp(28), dp(14), dp(20));
            suggestions.addView(empty);
        }
    }

    private View row(String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(6), dp(10), dp(4), dp(10));

        TextView history = new TextView(this);
        history.setText("◷");
        history.setTextColor(0xffb9bec7);
        history.setTextSize(25);
        history.setGravity(Gravity.CENTER);
        row.addView(history, new LinearLayout.LayoutParams(dp(42), dp(48)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xfff1f2f4);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

        TextView sub = new TextView(this);
        sub.setText(subtitleText);
        sub.setTextColor(0xff9ba1aa);
        sub.setTextSize(15);
        sub.setMaxLines(2);
        sub.setPadding(0, dp(2), 0, 0);

        body.addView(title);
        body.addView(sub);
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnClickListener(v -> openLookup(titleText));
        return row;
    }

    private void openLookup(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return;

        try {
            startActivity(new Intent(this, QuickLookupActivity.class)
                    .putExtra("query", q));
        } finally {
            finish();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        VISIBLE = true;
    }

    @Override protected void onStop() {
        VISIBLE = false;
        super.onStop();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(closeReceiver); } catch (Exception ignored) {}
        VISIBLE = false;
        super.onDestroy();
    }
}
