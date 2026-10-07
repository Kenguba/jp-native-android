package com.yuen.nativejpocr;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FloatingService extends Service {
    private static final String CH = "floating_lookup";
    private static final int NOTIFY_ID = 51;
    public static final String ACTION_SHOW_SEARCH = "com.yuen.nativejpocr.SHOW_FLOATING_SEARCH";

    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams bubbleLp;

    private TextView targetBox;
    private WindowManager.LayoutParams targetLp;

    private View searchPanel;
    private View resultCard;
    private boolean projectionRequestInFlight = false;

    private final Handler main = new Handler(Looper.getMainLooper());

    private void markAction(String action) {
        try {
            getSharedPreferences("crash_log", MODE_PRIVATE)
                    .edit()
                    .putString("last_action", action)
                    .putLong("last_action_time", System.currentTimeMillis())
                    .commit();
        } catch (Throwable ignored) {}
    }

    private void recordHandledCrash(String where, Throwable t) {
        try {
            StringBuilder out = new StringBuilder();
            out.append(where).append("\n")
                    .append(t.getClass().getName()).append(": ")
                    .append(t.getMessage() == null ? "" : t.getMessage()).append("\n");
            StackTraceElement[] trace = t.getStackTrace();
            int max = Math.min(trace.length, 32);
            for (int i = 0; i < max; i++) {
                out.append("  at ").append(trace[i].toString()).append("\n");
            }
            getSharedPreferences("crash_log", MODE_PRIVATE)
                    .edit()
                    .putString("last_handled", out.toString())
                    .commit();
        } catch (Throwable ignored) {}
    }

    private void safeToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {}
    }

    private final BroadcastReceiver ocrReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!MainActivity.ScreenCaptureService.ACTION_RESULT.equals(intent.getAction())) return;

            String text = intent.getStringExtra("text");
            String error = intent.getStringExtra("error");

            if (bubble != null) bubble.setVisibility(View.VISIBLE);

            if (error != null && !error.isEmpty()) {
                showResultCard("取词失败", error);
                return;
            }

            if (text == null || text.trim().isEmpty()) {
                showResultCard("没有识别到文字", "把取词框对准日语单词后再松手。");
                return;
            }

            openQuickLookup(text.trim());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startAsForeground();

        IntentFilter filter = new IntentFilter(MainActivity.ScreenCaptureService.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(ocrReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(ocrReceiver, filter);
        }

        if (Settings.canDrawOverlays(this)) showBubble();
        else stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CH, "日语浮动取词", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("保持桌面可拖拽的日语取词浮标");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent p = PendingIntent.getActivity(
                this, 51, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("日语浮动取词已开启")
                .setContentText("轻点搜索；拖动并松手可 OCR 取词")
                .setContentIntent(p)
                .setOngoing(true)
                .build();
    }

    private void startAsForeground() {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable outline(int strokeColor, int strokeDp, int fillColor, int radiusDp) {
        GradientDrawable g = bg(fillColor, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void showBubble() {
        if (bubble != null) return;

        wm = (WindowManager)getSystemService(WINDOW_SERVICE);

        FrameLayout bubbleView = new FrameLayout(this);
        bubbleView.setBackgroundResource(R.drawable.floating_bubble_bg);
        bubbleView.setElevation(dp(12));

        TextView kana = new TextView(this);
        kana.setText("あ");
        kana.setTextColor(Color.WHITE);
        kana.setTextSize(22);
        kana.setTypeface(Typeface.DEFAULT_BOLD);
        kana.setGravity(Gravity.CENTER);
        bubbleView.addView(kana, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView searchMark = new ImageView(this);
        searchMark.setImageResource(android.R.drawable.ic_menu_search);
        searchMark.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams searchMarkLp = new FrameLayout.LayoutParams(dp(18), dp(18));
        searchMarkLp.gravity = Gravity.END | Gravity.BOTTOM;
        searchMarkLp.rightMargin = dp(4);
        searchMarkLp.bottomMargin = dp(4);
        bubbleView.addView(searchMark, searchMarkLp);

        bubble = bubbleView;

        bubbleLp = new WindowManager.LayoutParams(
                dp(54), dp(54), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences sp = getSharedPreferences("float_pos", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubbleLp.x = sp.getInt("x", Math.max(0, dm.widthPixels - dp(74)));
        bubbleLp.y = sp.getInt("y", Math.max(dp(120), dm.heightPixels / 2));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            long downTime;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                try {
                    switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        projectionRequestInFlight = false;
                        removeResultCard();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int nx = startX + Math.round(e.getRawX() - downRawX);
                        int ny = startY + Math.round(e.getRawY() - downRawY);

                        if (Math.abs(e.getRawX() - downRawX) > dp(5) ||
                                Math.abs(e.getRawY() - downRawY) > dp(5)) {
                            if (!moved) {
                                moved = true;
                                removeSearchPanel();
                                if (MainActivity.ScreenCaptureService.READY) {
                                    showTargetBox();
                                } else {
                                    requestProjectionPermissionNow();
                                }
                            }
                        }

                        DisplayMetrics d = getResources().getDisplayMetrics();
                        nx = Math.max(0, Math.min(nx, d.widthPixels - dp(54)));
                        ny = Math.max(0, Math.min(ny, d.heightPixels - dp(54)));

                        bubbleLp.x = nx;
                        bubbleLp.y = ny;

                        try {
                            wm.updateViewLayout(bubble, bubbleLp);
                        } catch (Exception ignored) {}

                        if (moved) updateTargetBox();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        getSharedPreferences("float_pos", MODE_PRIVATE).edit()
                                .putInt("x", bubbleLp.x)
                                .putInt("y", bubbleLp.y)
                                .apply();

                        if (!moved && System.currentTimeMillis() - downTime < 650) {
                            markAction("BUBBLE_TAP_SHOW_SEARCH");
                            hideTargetBox();
                            toggleSearchPanel();
                        } else if (moved) {
                            if (MainActivity.ScreenCaptureService.READY && targetLp != null) {
                                triggerPickup();
                            } else {
                                hideTargetBox();
                            }
                        }
                        return true;
                    }
                    return false;
                } catch (Throwable t) {
                    recordHandledCrash("FloatingService.onTouch", t);
                    safeToast("悬浮球操作异常：" + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : " · " + t.getMessage()));
                    hideTargetBox();
                    return true;
                }
            }
        });

        wm.addView(bubble, bubbleLp);
    }

    private void showTargetBox() {
        if (targetBox != null) return;

        targetBox = new TextView(this);
        targetBox.setText("  对准单词 · 松手取词  ");
        targetBox.setTextColor(Color.WHITE);
        targetBox.setTextSize(13);
        targetBox.setGravity(Gravity.CENTER);
        targetBox.setBackground(outline(0xff29a8ff, 2, 0x77090d14, 8));

        targetLp = new WindowManager.LayoutParams(
                dp(190), dp(58), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        targetLp.gravity = Gravity.TOP | Gravity.START;

        updateTargetCoordinates();
        wm.addView(targetBox, targetLp);
    }

    private void updateTargetBox() {
        if (targetBox == null || targetLp == null) return;
        updateTargetCoordinates();
        try {
            wm.updateViewLayout(targetBox, targetLp);
        } catch (Exception ignored) {}
    }

    private void updateTargetCoordinates() {
        DisplayMetrics d = getResources().getDisplayMetrics();

        int targetWidth = dp(190);
        int targetHeight = dp(58);

        int centerX = bubbleLp.x + dp(27);
        int x = centerX - targetWidth / 2;
        int y = bubbleLp.y - targetHeight - dp(18);

        x = Math.max(0, Math.min(x, d.widthPixels - targetWidth));
        y = Math.max(dp(4), Math.min(y, d.heightPixels - targetHeight - dp(4)));

        targetLp.x = x;
        targetLp.y = y;
    }

    private void hideTargetBox() {
        if (targetBox != null && wm != null) {
            try {
                wm.removeView(targetBox);
            } catch (Exception ignored) {}
        }

        targetBox = null;
        targetLp = null;
    }

    private void triggerPickup() {
        if (targetLp == null) return;

        final int x = targetLp.x;
        final int y = targetLp.y;
        final int width = targetLp.width;
        final int height = targetLp.height;

        hideTargetBox();

        if (!MainActivity.ScreenCaptureService.READY) {
            requestProjectionPermissionNow();
            return;
        }

        if (bubble != null) bubble.setVisibility(View.INVISIBLE);

        Intent i = new Intent(this, MainActivity.ScreenCaptureService.class)
                .setAction(MainActivity.ScreenCaptureService.ACT_OCR_REGION)
                .putExtra("x", x)
                .putExtra("y", y)
                .putExtra("w", width)
                .putExtra("h", height);

        startService(i);

        main.postDelayed(() -> {
            if (bubble != null && bubble.getVisibility() != View.VISIBLE) {
                bubble.setVisibility(View.VISIBLE);
            }
        }, 1200);
    }

    private void toggleSearchPanel() {
        try {
            if (searchPanel != null) {
                markAction("BUBBLE_TAP_CLOSE_SEARCH");
                removeSearchPanel();
                return;
            }
            markAction("SHOW_SEARCH_PANEL_BEGIN");
            showSearchPanel();
            markAction("SHOW_SEARCH_PANEL_OK");
        } catch (Throwable t) {
            searchPanel = null;
            recordHandledCrash("toggleSearchPanel", t);
            safeToast("搜索页异常：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " · " + t.getMessage()));
        }
    }

    private void requestProjectionPermissionNow() {
        if (MainActivity.ScreenCaptureService.READY || projectionRequestInFlight) return;
        projectionRequestInFlight = true;
        markAction("OPEN_MEDIA_PROJECTION_PERMISSION");

        Intent i = new Intent(this, MainActivity.OcrActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (Exception e) {
            projectionRequestInFlight = false;
            showResultCard("授权页打开失败",
                    e.getMessage() == null ? "未知错误" : e.getMessage());
        }
    }

    private void showSearchPanel() {
        if (!Settings.canDrawOverlays(this) || wm == null) return;

        removeResultCard();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x8a101820);
        root.setFocusableInTouchMode(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        contentLp.leftMargin = dp(16);
        contentLp.rightMargin = dp(16);
        contentLp.topMargin = dp(28);
        root.addView(content, contentLp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), 0, dp(7), 0);
        bar.setBackground(outline(0xff079bff, 2, 0xed18191d, 15));
        bar.setElevation(dp(6));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(0xfff4f5f7);
        input.setHintTextColor(0xff969ca6);
        input.setHint("请输入需要查找的内容");
        input.setTextSize(22);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setSelectAllOnFocus(false);
        input.setPadding(0, 0, dp(8), 0);

        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_search_mic);
        mic.setColorFilter(0xff079bff);
        mic.setPadding(dp(10), dp(10), dp(10), dp(10));

        bar.addView(input, new LinearLayout.LayoutParams(0, dp(64), 1));
        bar.addView(mic, new LinearLayout.LayoutParams(dp(50), dp(64)));

        content.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(0, dp(12), 0, dp(72));
        scroll.addView(suggestions, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollLp.topMargin = dp(4);
        content.addView(scroll, scrollLp);

        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;

        searchPanel = root;
        markAction("SHOW_SEARCH_PANEL_ADD_VIEW");
        try {
            wm.addView(searchPanel, lp);
        } catch (Throwable firstError) {
            searchPanel = null;
            showResultCard(
                    "搜索悬浮层打开失败",
                    firstError.getClass().getSimpleName() + ": "
                            + (firstError.getMessage() == null ? "未知窗口错误" : firstError.getMessage()));
            return;
        }

        markAction("SHOW_SEARCH_PANEL_UPDATE_SUGGESTIONS");
        updateSuggestions(suggestions, "");

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSuggestions(suggestions, s == null ? "" : s.toString());
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = input.getText().toString().trim();
                if (!q.isEmpty()) {
                    removeSearchPanel();
                    openQuickLookup(q);
                }
                return true;
            }
            return false;
        });

        input.setOnClickListener(v -> {
            input.requestFocus();
            InputMethodManager imm =
                    (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });

        mic.setOnClickListener(v ->
                Toast.makeText(this, "语音搜索入口已预留", Toast.LENGTH_SHORT).show());

        markAction("SHOW_SEARCH_PANEL_FINISH");
        root.requestFocus();
        bringBubbleToFront();
    }

    private void updateSuggestions(LinearLayout suggestions, String raw) {
        suggestions.removeAllViews();

        String q = raw == null ? "" : raw.trim();
        String lower = q.toLowerCase(Locale.ROOT);
        Set<String> shown = new LinkedHashSet<>();
        int count = 0;

        List<String> history = recentHistory();

        if (q.isEmpty()) {
            for (String item : history) {
                if (item.isEmpty() || !shown.add(item)) continue;
                suggestions.addView(createSuggestionRow(
                        item,
                        lookupSubtitle(item),
                        true));
                count++;
                if (count >= 12) break;
            }

            if (count < 12) {
                for (Map.Entry<String, String[]> e : MainActivity.WORDS.entrySet()) {
                    String word = e.getKey();
                    if (!shown.add(word)) continue;
                    suggestions.addView(createSuggestionRow(
                            word,
                            formatSubtitle(e.getValue()),
                            true));
                    count++;
                    if (count >= 12) break;
                }
            }
        } else {
            for (String item : history) {
                if (!item.toLowerCase(Locale.ROOT).contains(lower)) continue;
                if (!shown.add(item)) continue;
                suggestions.addView(createSuggestionRow(
                        item,
                        lookupSubtitle(item),
                        true));
                count++;
                if (count >= 10) break;
            }

            for (Map.Entry<String, String[]> e : MainActivity.WORDS.entrySet()) {
                String word = e.getKey();
                String[] d = e.getValue();

                boolean match =
                        word.contains(q) ||
                        d[0].contains(q) ||
                        d[1].toLowerCase(Locale.ROOT).contains(lower) ||
                        d[3].contains(q);

                if (!match || !shown.add(word)) continue;

                suggestions.addView(createSuggestionRow(
                        word,
                        formatSubtitle(d),
                        true));
                count++;
                if (count >= 10) break;
            }

            if (!shown.contains(q)) {
                suggestions.addView(createSuggestionRow(
                        q,
                        "搜索「" + q + "」",
                        false));
                count++;
            }
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("输入日语、中文或英文进行查询");
            empty.setTextColor(0xffa4aab3);
            empty.setTextSize(16);
            empty.setPadding(dp(56), dp(28), dp(12), dp(20));
            suggestions.addView(empty);
        }
    }

    private String formatSubtitle(String[] data) {
        if (data == null || data.length < 4) return "最近查询";
        return data[2] + ". " + data[3] + "  ·  " + data[0];
    }

    private String lookupSubtitle(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> e : MainActivity.WORDS.entrySet()) {
            String[] d = e.getValue();
            if (e.getKey().equals(query) ||
                    d[0].equals(query) ||
                    d[1].toLowerCase(Locale.ROOT).equals(lower)) {
                return formatSubtitle(d);
            }
        }
        return "最近查询";
    }

    private List<String> recentHistory() {
        String saved = getSharedPreferences("lookup_history", MODE_PRIVATE)
                .getString("items", "");
        List<String> out = new ArrayList<>();
        if (saved == null || saved.isEmpty()) return out;

        for (String line : saved.split("\\n")) {
            String item = line.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    private View createSuggestionRow(String word, String subtitle, boolean historyStyle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setMinimumHeight(dp(72));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_history_clock);
        icon.setColorFilter(0xffc4c9d0);
        icon.setPadding(dp(7), dp(8), dp(7), dp(8));
        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(dp(44), dp(48));
        row.addView(icon, iconLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(2), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(word);
        title.setTextColor(0xfff0f2f4);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView meaning = new TextView(this);
        meaning.setText(subtitle == null ? "" : subtitle);
        meaning.setTextColor(0xff9ea5ae);
        meaning.setTextSize(15);
        meaning.setMaxLines(2);
        meaning.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meaning.setPadding(0, dp(2), 0, 0);

        body.addView(title);
        if (subtitle != null && !subtitle.isEmpty()) body.addView(meaning);

        row.addView(body, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            removeSearchPanel();
            openQuickLookup(word);
        });

        return row;
    }

    private void bringBubbleToFront() {
        if (bubble == null || wm == null) return;

        try {
            wm.removeView(bubble);
            wm.addView(bubble, bubbleLp);
        } catch (Exception ignored) {}
    }

    private void openQuickLookup(String q) {
        markAction("OPEN_QUICK_LOOKUP:" + q);
        Intent i = new Intent(this, QuickLookupActivity.class)
                .putExtra("query", q)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(i);
        } catch (Throwable e) {
            recordHandledCrash("openQuickLookup", e);
            safeToast("查词页异常：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " · " + e.getMessage()));
        }
    }

    private void showResultCard(String title, String body) {
        removeResultCard();
        removeSearchPanel();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackground(outline(0x33ffffff, 1, 0xf2222222, 14));
        card.setElevation(dp(16));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(t);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(0xffeeeeee);
        b.setTextSize(16);
        b.setPadding(0, dp(6), 0, 0);
        card.addView(b);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.min(
                        getResources().getDisplayMetrics().widthPixels - dp(24),
                        dp(330)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics d = getResources().getDisplayMetrics();
        lp.x = Math.max(
                dp(12),
                Math.min(
                        bubbleLp.x - dp(230),
                        d.widthPixels - lp.width - dp(12)));
        lp.y = Math.max(
                dp(40),
                Math.min(
                        bubbleLp.y - dp(30),
                        d.heightPixels - dp(180)));

        resultCard = card;
        wm.addView(resultCard, lp);

        card.setOnClickListener(v -> removeResultCard());

        main.postDelayed(() -> {
            if (resultCard == card) removeResultCard();
        }, 7000);
    }

    private void removeSearchPanel() {
        if (searchPanel != null && wm != null) {
            try {
                InputMethodManager imm =
                        (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchPanel.getWindowToken(), 0);
            } catch (Exception ignored) {}

            try {
                wm.removeView(searchPanel);
            } catch (Exception ignored) {}

            searchPanel = null;
        }
    }

    private void removeResultCard() {
        if (resultCard != null && wm != null) {
            try {
                wm.removeView(resultCard);
            } catch (Exception ignored) {}
            resultCard = null;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (bubble == null && Settings.canDrawOverlays(this)) showBubble();

        if (intent != null && ACTION_SHOW_SEARCH.equals(intent.getAction())) {
            main.post(() -> {
                if (!Settings.canDrawOverlays(this)) return;
                if (searchPanel == null) showSearchPanel();
            });
        }

        return START_STICKY;
    }

    @Override public void onDestroy() {
        try {
            unregisterReceiver(ocrReceiver);
        } catch (Exception ignored) {}

        hideTargetBox();
        removeSearchPanel();
        removeResultCard();

        if (bubble != null && wm != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {}
            bubble = null;
        }

        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
