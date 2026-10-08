package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Invisible Back host for external lookup links.
 *
 * Visible dictionary UI remains a WindowManager TYPE_APPLICATION_OVERLAY.
 * This transparent Activity exists only so Android / OriginOS has a real
 * application window to target for system Back gestures.
 */
public final class LookupLinkActivity extends Activity {
    private android.window.OnBackInvokedCallback backCallback;
    private boolean finishReceiverRegistered;
    private String currentMode = FloatingService.LOOKUP_MODE_FULLSCREEN;

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (FloatingService.ACTION_LOOKUP_HOST_FINISH.equals(intent.getAction())) {
                markHostAction("HOST_FINISH_BROADCAST");
                finishBridge();
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        currentMode = extractMode(getIntent());
        configureTransparentHost(currentMode);
        registerFinishReceiver();
        forward(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        currentMode = extractMode(intent);
        configureTransparentHost(currentMode);
        forward(intent);
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        // Register only after the Activity is truly resumed. Some OriginOS
        // versions ignore predictive/system Back callbacks registered too early.
        registerSystemBack();
        markHostAction("HOST_RESUMED:" + currentMode);
    }

    @Override public void onBackPressed() {
        markHostAction("HOST_ON_BACK_PRESSED");
        requestLookupBack();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    event.getRepeatCount() == 0) {
                markHostAction("HOST_KEY_BACK");
                requestLookupBack();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        clearSystemBack();
        if (finishReceiverRegistered) {
            try {
                unregisterReceiver(finishReceiver);
            } catch (Throwable ignored) {}
            finishReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void clearSystemBack() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Throwable ignored) {}
        }
        backCallback = null;
    }

    private void registerSystemBack() {
        if (Build.VERSION.SDK_INT < 33) return;

        clearSystemBack();
        backCallback = () -> {
            markHostAction("HOST_ON_BACK_INVOKED");
            requestLookupBack();
        };

        try {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    backCallback);
        } catch (Throwable first) {
            // PRIORITY_OVERLAY is preferred on OEM gesture navigation; fall
            // back to DEFAULT if the vendor implementation rejects it.
            try {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        backCallback);
            } catch (Throwable ignored) {}
        }
    }

    private void registerFinishReceiver() {
        IntentFilter filter = new IntentFilter(FloatingService.ACTION_LOOKUP_HOST_FINISH);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(finishReceiver, filter);
            }
            finishReceiverRegistered = true;
        } catch (Throwable ignored) {}
    }

    private void configureTransparentHost(String mode) {
        try {
            Window w = getWindow();
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            if (FloatingService.LOOKUP_MODE_FULLSCREEN.equals(mode)) {
                // Important for vivo/OriginOS: a FLAG_NOT_TOUCHABLE Activity
                // can be skipped by the vendor edge-back target resolver.
                // The real full-screen overlay sits above this host and still
                // receives ordinary taps, so keeping the host touchable does
                // not expose an extra visible page.
                w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                // Let the WindowManager mask remain visible behind both
                // system bars. The transparent Activity is still only a Back
                // host; the caller's content and the overlay supply the pixels
                // underneath the status/navigation icons.
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                w.setStatusBarColor(Color.TRANSPARENT);
                w.setNavigationBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= 30) {
                    w.setDecorFitsSystemWindows(false);
                }
                w.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                // Small floating lookup must let taps outside the overlay
                // continue through to the original application.
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                w.getDecorView().setSystemUiVisibility(0);
                if (Build.VERSION.SDK_INT >= 30) {
                    w.setDecorFitsSystemWindows(true);
                }
                w.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }

            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {}
    }

    private void requestLookupBack() {
        markHostAction("HOST_SEND_LOOKUP_BACK");
        Intent back = new Intent(this, FloatingService.class)
                .setAction(FloatingService.ACTION_LOOKUP_BACK);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(back);
            } else {
                startService(back);
            }
        } catch (Throwable ignored) {
            finishBridge();
        }
    }

    private void markHostAction(String action) {
        try {
            getSharedPreferences("crash_log", MODE_PRIVATE)
                    .edit()
                    .putString("last_lookup_host_action", action)
                    .putLong("last_lookup_host_action_time", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {}
    }

    private String extractQuery(Intent source) {
        String query = "";
        if (source == null) return query;

        Uri data = source.getData();
        if (data != null) {
            String value = data.getQueryParameter("q");
            if (value == null || value.trim().isEmpty()) {
                value = data.getQueryParameter("query");
            }
            if (value != null) query = value.trim();
        }

        if (query.isEmpty()) {
            String extra = source.getStringExtra(FloatingService.EXTRA_LOOKUP_QUERY);
            if (extra != null) query = extra.trim();
        }

        if (query.isEmpty()) {
            CharSequence text = source.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) query = text.toString().trim();
        }

        if (query.startsWith("intent://") || query.startsWith("jp-native://")) {
            try {
                Uri nested = Uri.parse(query);
                String nestedQuery = nested.getQueryParameter("q");
                if (nestedQuery == null || nestedQuery.trim().isEmpty()) {
                    nestedQuery = nested.getQueryParameter("query");
                }
                if (nestedQuery != null && !nestedQuery.trim().isEmpty()) {
                    query = nestedQuery.trim();
                }
            } catch (Throwable ignored) {}
        }

        return query;
    }

    private String extractMode(Intent source) {
        String mode = "";
        if (source != null) {
            Uri data = source.getData();
            if (data != null) {
                String value = data.getQueryParameter("mode");
                if (value != null) mode = value.trim();
            }
            if (mode.isEmpty()) {
                String extra = source.getStringExtra(FloatingService.EXTRA_LOOKUP_MODE);
                if (extra != null) mode = extra.trim();
            }
        }

        return FloatingService.LOOKUP_MODE_FLOAT.equalsIgnoreCase(mode)
                ? FloatingService.LOOKUP_MODE_FLOAT
                : FloatingService.LOOKUP_MODE_FULLSCREEN;
    }

    private void forward(Intent source) {
        String query = extractQuery(source);
        String mode = extractMode(source);

        if (query.isEmpty()) {
            Toast.makeText(this, "查询链接没有提供 q 参数", Toast.LENGTH_SHORT).show();
            finishBridge();
            return;
        }

        Intent show = new Intent(this, FloatingService.class)
                .setAction(FloatingService.ACTION_SHOW_LOOKUP_OVERLAY)
                .putExtra(FloatingService.EXTRA_LOOKUP_QUERY, query)
                .putExtra(FloatingService.EXTRA_LOOKUP_MODE, mode)
                .putExtra(FloatingService.EXTRA_RETURN_TO_SEARCH, false);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(show);
            } else {
                startService(show);
            }
            markHostAction("HOST_FORWARD_LOOKUP:" + mode);
        } catch (Throwable t) {
            Toast.makeText(
                    this,
                    "查词浮层启动失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            finishBridge();
        }
    }

    private void finishBridge() {
        try {
            clearSystemBack();
            finish();
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {}
    }
}
