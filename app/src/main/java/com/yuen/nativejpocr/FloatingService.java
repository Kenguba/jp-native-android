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

import java.util.Locale;
import java.util.Map;

public class FloatingService extends Service {
    private static final String CH = "floating_lookup";
    private static final int NOTIFY_ID = 51;

    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams bubbleLp;

    private TextView targetBox;
    private WindowManager.LayoutParams targetLp;

    private View searchPanel;
    private View resultCard;
    private boolean projectionRequestInFlight = false;

    private final Handler main = new Handler(Looper.getMainLooper());

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
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        if (MainActivity.ScreenCaptureService.READY) projectionRequestInFlight = false;
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
        if (SearchOverlayActivity.VISIBLE) {
            removeSearchPanel();
            return;
        }

        removeResultCard();
        Intent i = new Intent(this, SearchOverlayActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NO_ANIMATION |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
        } catch (Exception e) {
            showResultCard("搜索页打开失败",
                    e.getMessage() == null ? "未知错误" : e.getMessage());
        }
    }

    private void requestProjectionPermissionNow() {
        if (MainActivity.ScreenCaptureService.READY || projectionRequestInFlight) return;
        projectionRequestInFlight = true;

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
        root.setBackgroundColor(0xb812171f);

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
        bar.setPadding(dp(15), 0, dp(6), 0);
        bar.setBackgroundResource(R.drawable.search_bar_bg);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xff8b96a6);
        input.setHint("搜索日语单词");
        input.setTextSize(23);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setSelectAllOnFocus(false);

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(0xff168bff);
        close.setTextSize(34);
        close.setGravity(Gravity.CENTER);
        close.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

        bar.addView(input, new LinearLayout.LayoutParams(
                0, dp(62), 1));
        bar.addView(close, new LinearLayout.LayoutParams(
                dp(54), dp(62)));

        content.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(0, dp(14), 0, dp(60));
        scroll.addView(suggestions, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollLp.topMargin = dp(8);
        content.addView(scroll, scrollLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

        searchPanel = root;
        wm.addView(searchPanel, lp);

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

        close.setOnClickListener(v -> removeSearchPanel());

        input.requestFocus();
        main.postDelayed(() -> {
            InputMethodManager imm =
                    (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);

        bringBubbleToFront();
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

            suggestions.addView(createSuggestionRow(word, d));
            count++;

            if (count >= 12) break;
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有匹配结果\n按键盘搜索键仍可直接查询输入内容");
            empty.setTextColor(0xff9ba5b3);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(20), dp(38), dp(20), dp(20));
            suggestions.addView(empty);
        }
    }

    private View createSuggestionRow(String word, String[] data) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(8), dp(13), dp(6), dp(13));
        row.setBackgroundColor(Color.TRANSPARENT);

        TextView icon = new TextView(this);
        icon.setText("▣");
        icon.setTextColor(0xffc1cad5);
        icon.setTextSize(19);
        icon.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(dp(42), dp(44));
        row.addView(icon, iconLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(word);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

        TextView meaning = new TextView(this);
        meaning.setText(data[2] + ". " + data[3] + "  ·  " + data[0]);
        meaning.setTextColor(0xff9ba4b1);
        meaning.setTextSize(16);
        meaning.setMaxLines(2);

        body.addView(title);
        body.addView(meaning);

        row.addView(body, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

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
        Intent i = new Intent(this, QuickLookupActivity.class)
                .putExtra("query", q)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(i);
        } catch (Exception e) {
            showResultCard(
                    "查词窗口打开失败",
                    e.getMessage() == null ? "未知错误" : e.getMessage());
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
        try {
            sendBroadcast(new Intent(SearchOverlayActivity.ACTION_CLOSE)
                    .setPackage(getPackageName()));
        } catch (Exception ignored) {}

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
