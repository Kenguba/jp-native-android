package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuickLookupActivity extends Activity {
    private static final ExecutorService AI_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, String> AI_CACHE = new ConcurrentHashMap<>();

    private TextView aiBody;
    private ProgressBar aiProgress;
    private Button aiKeyButton;
    private String query;
    private boolean closing;
    private boolean popupMode;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        popupMode = getIntent() != null &&
                getIntent().getBooleanExtra(FloatingService.EXTRA_POPUP_MODE, false);
        if (popupMode) setTheme(R.style.Theme_JpQuickLookup_Popup);

        super.onCreate(savedInstanceState);

        String incoming = readQuery(getIntent());
        query = incoming == null ? "" : incoming.trim();
        saveHistory(query);

        configureWindow();
        installBackHandler();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        if (popupMode) {
            panel.setBackground(rounded(Color.WHITE, 18));
        } else {
            panel.setBackgroundColor(Color.WHITE);
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(12), dp(12));

        TextView title = text(query.isEmpty() ? "快速查词" : query, 30, 0xff202124);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(62), 1));

        String[] headerIcons = popupMode
                ? new String[]{"×"}
                : new String[]{"☆", "◉", "☷", "✚"};
        for (String icon : headerIcons) {
            TextView iv = text(icon, popupMode ? 32 : 29, 0xff202124);
            iv.setGravity(Gravity.CENTER);
            if (popupMode) iv.setOnClickListener(v -> closeLookupTask());
            header.addView(iv, new LinearLayout.LayoutParams(dp(54), dp(54)));
        }
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(76)));

        View divider = new View(this);
        divider.setBackgroundColor(0xffe6e8eb);
        panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        String[] local = findLocal(query);
        if (local != null) {
            LinearLayout pronunciation = new LinearLayout(this);
            pronunciation.setOrientation(LinearLayout.HORIZONTAL);
            pronunciation.setGravity(Gravity.CENTER_VERTICAL);
            pronunciation.setPadding(dp(20), dp(12), dp(20), dp(12));

            TextView sound = text("🔊", 24, 0xff009cf0);
            pronunciation.addView(sound, new LinearLayout.LayoutParams(dp(42), dp(44)));

            TextView reading = text(local[0] + "   /" + local[1] + "/   " + local[2], 17, 0xff62676d);
            pronunciation.addView(reading, new LinearLayout.LayoutParams(0, dp(44), 1));
            panel.addView(pronunciation);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        addSectionHeader(content, "日语单词总汇");

        LinearLayout localCard = new LinearLayout(this);
        localCard.setOrientation(LinearLayout.VERTICAL);
        localCard.setPadding(dp(18), dp(16), dp(18), dp(18));
        localCard.setBackground(rounded(0xffe8f8ff, 14));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = dp(16);
        cardLp.rightMargin = dp(16);
        cardLp.topMargin = dp(12);
        cardLp.bottomMargin = dp(14);

        if (local != null) {
            TextView localWord = text(query, 26, 0xff00a6a6);
            localWord.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            localCard.addView(localWord);

            TextView localMeta = text(local[0] + " · " + local[1] + " · " + local[2], 16, 0xff596168);
            localMeta.setPadding(0, dp(8), 0, dp(10));
            localCard.addView(localMeta);

            TextView localMeaning = text(local[3], 19, 0xff17191c);
            localMeaning.setLineSpacing(dp(3), 1f);
            localCard.addView(localMeaning);
        } else {
            TextView missing = text(
                    query.isEmpty()
                            ? "没有收到要查询的内容。"
                            : "本地词库暂未收录「" + query + "」。\n下面会自动使用 Groq AI 查询。",
                    18, 0xff34383d);
            missing.setLineSpacing(dp(4), 1f);
            localCard.addView(missing);
        }
        content.addView(localCard, cardLp);

        addSectionHeader(content, "Groq AI");

        LinearLayout aiCard = new LinearLayout(this);
        aiCard.setOrientation(LinearLayout.VERTICAL);
        aiCard.setPadding(dp(18), dp(16), dp(18), dp(20));
        aiCard.setBackground(rounded(0xfff7f8fa, 14));

        aiProgress = new ProgressBar(this);
        aiProgress.setIndeterminate(true);
        aiCard.addView(aiProgress, new LinearLayout.LayoutParams(dp(32), dp(32)));

        aiBody = text("正在查询 AI…", 18, 0xff202327);
        aiBody.setPadding(0, dp(10), 0, 0);
        aiBody.setLineSpacing(dp(5), 1f);
        aiBody.setTextIsSelectable(true);
        aiCard.addView(aiBody, new LinearLayout.LayoutParams(-1, -2));

        aiKeyButton = new Button(this);
        aiKeyButton.setAllCaps(false);
        aiKeyButton.setText(GroqKeyStore.hasKey(this) ? "更换 Groq Key" : "配置 Groq Key");
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, dp(48));
        keyLp.topMargin = dp(12);
        aiCard.addView(aiKeyButton, keyLp);

        aiKeyButton.setOnClickListener(v ->
                GroqKeyStore.showEditor(this, () -> {
                    aiKeyButton.setText(GroqKeyStore.hasKey(this)
                            ? "更换 Groq Key"
                            : "配置 Groq Key");
                    AI_CACHE.clear();

                    if (query == null || query.trim().isEmpty()) {
                        aiProgress.setVisibility(View.GONE);
                        aiBody.setText("没有查询内容。");
                        return;
                    }

                    if (GroqKeyStore.hasKey(this)) {
                        aiProgress.setVisibility(View.VISIBLE);
                        aiBody.setText("正在查询 AI…");
                        loadAi(query);
                    } else {
                        showAi("Groq AI 尚未配置。\n\n点击下面的“配置 Groq Key”，Key 会加密保存在本机。");
                    }
                }));

        content.addView(aiCard, cardLp);

        TextView footer = text("JP Native Android · 本地词典 + Groq AI", 14, 0xff777d84);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(18), dp(12), dp(6));
        content.addView(footer);

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(panel);

        if (query.isEmpty()) {
            aiProgress.setVisibility(View.GONE);
            aiBody.setText("没有查询内容。");
        } else {
            loadAi(query);
        }
    }

    private void installBackHandler() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::closeLookupTask);
        }
    }

    private void closeLookupTask() {
        if (closing) return;
        closing = true;

        Intent source = getIntent();
        if (source != null &&
                source.getBooleanExtra(FloatingService.EXTRA_RETURN_TO_SEARCH, false)) {
            String restoreQuery = source.getStringExtra(FloatingService.EXTRA_SEARCH_QUERY);
            if (restoreQuery == null || restoreQuery.trim().isEmpty()) {
                restoreQuery = query == null ? "" : query.trim();
            }

            Intent restore = new Intent(this, FloatingService.class)
                    .setAction(FloatingService.ACTION_SHOW_SEARCH)
                    .putExtra(FloatingService.EXTRA_SEARCH_QUERY, restoreQuery);
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(restore);
                } else {
                    startService(restore);
                }
            } catch (Throwable ignored) {}
        }

        try {
            finishAndRemoveTask();
        } catch (Throwable ignored) {
            finish();
        }
    }

    @Override public void onBackPressed() {
        closeLookupTask();
    }

    private void configureWindow() {
        Window w = getWindow();

        if (popupMode) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = Math.min(dm.widthPixels - dp(24), dp(540));
            lp.height = Math.max(dp(360), Math.round(dm.heightPixels * 0.78f));
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = 0.35f;
            w.setAttributes(lp);

            setFinishOnTouchOutside(true);
            return;
        }

        w.setStatusBarColor(0xffeaf7ff);
        w.setNavigationBarColor(Color.WHITE);
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackgroundColor(0xfff2f3f5);

        TextView t = text(title, 18, 0xff30343a);
        row.addView(t, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView arrow = text("⌃", 22, 0xffaeb3b9);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(42)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
    }

    private String[] findLocal(String q) {
        if (q == null || q.trim().isEmpty()) return null;

        String clean = q.trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        for (Map.Entry<String,String[]> e : MainActivity.WORDS.entrySet()) {
            String[] d = e.getValue();
            if (e.getKey().equals(clean) ||
                    e.getKey().contains(clean) ||
                    d[0].equals(clean) ||
                    d[0].contains(clean) ||
                    d[1].toLowerCase(Locale.ROOT).contains(lower)) {
                return d;
            }
        }
        return null;
    }

    private void loadAi(String q) {
        String cached = AI_CACHE.get(q);
        if (cached != null) {
            showAi(cached);
            return;
        }

        String apiKey = GroqKeyStore.load(this);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showAi("Groq AI 尚未配置。\n\n点击下面的“配置 Groq Key”，Key 会使用 Android Keystore 加密后保存在本机。");
            return;
        }

        final String requestKey = apiKey.trim();
        AI_EXECUTOR.execute(() -> {
            try {
                String answer = callGroq(q, requestKey);
                AI_CACHE.put(q, answer);
                runOnUiThread(() -> showAi(answer));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (msg.contains("HTTP 401") || msg.contains("HTTP 403")) {
                        showAi("Groq Key 无效或没有权限。\n\n请点击“更换 Groq Key”重新输入。");
                    } else {
                        showAi("AI 查询失败：\n" + msg);
                    }
                });
            }
        });
    }

    private void showAi(String value) {
        if (isFinishing() || isDestroyed()) return;
        aiProgress.setVisibility(View.GONE);
        aiBody.setText(value);
    }

    private String callGroq(String q, String apiKey) throws Exception {
        URL url = new URL(BuildConfig.GROQ_BASE_URL + "/chat/completions");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(60000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        String systemPrompt =
                "你是日语词典和翻译助手。回答必须简洁、准确、适合词典页面。"
                + "如果查询是日语，给出读音、词性、核心中文义、常用语感和一个自然例句；"
                + "如果查询是中文或英文，先给出自然的日语对应，再解释。不要输出思考过程。";

        JSONObject body = new JSONObject();
        body.put("model", BuildConfig.GROQ_MODEL);
        body.put("temperature", 0.35);
        body.put("max_tokens", 1000);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", "查询：" + q));
        body.put("messages", messages);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }

        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String response = readAll(stream);

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + (response.isEmpty() ? "" : " · " + response));
        }

        JSONObject json = new JSONObject(response);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("Groq 返回内容为空");
        }

        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String answer = message == null ? "" : message.optString("content", "").trim();
        if (answer.isEmpty()) throw new IOException("Groq 返回内容为空");
        return answer;
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (b.length() > 0) b.append('\n');
                b.append(line);
            }
            return b.toString();
        }
    }

    private void saveHistory(String raw) {
        if (raw == null) return;
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty()) return;
        if (value.length() > 80) value = value.substring(0, 80);

        String saved = getSharedPreferences("lookup_history", MODE_PRIVATE)
                .getString("items", "");

        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(value);

        if (saved != null && !saved.isEmpty()) {
            for (String line : saved.split("\\n")) {
                String item = line.trim();
                if (!item.isEmpty()) ordered.add(item);
                if (ordered.size() >= 20) break;
            }
        }

        StringBuilder out = new StringBuilder();
        for (String item : ordered) {
            if (out.length() > 0) out.append('\n');
            out.append(item);
            if (out.toString().split("\\n").length >= 20) break;
        }

        getSharedPreferences("lookup_history", MODE_PRIVATE)
                .edit()
                .putString("items", out.toString())
                .apply();
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
}
