package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Invisible Back host for external lookup links.
 *
 * The Activity never renders dictionary UI. It stays transparent behind the
 * WindowManager overlay so Android's real system Back / predictive Back gesture
 * has an Activity dispatcher to target. FloatingService owns all visible UI.
 */
public final class LookupLinkActivity extends Activity {
    private android.window.OnBackInvokedCallback backCallback;
    private boolean finishReceiverRegistered;

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (FloatingService.ACTION_LOOKUP_HOST_FINISH.equals(intent.getAction())) {
                finishBridge();
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        suppressWindowTransition();
        registerFinishReceiver();
        registerSystemBack();
        forward(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        suppressWindowTransition();
        forward(intent);
    }

    @Override public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) {
            requestLookupBack();
        } else {
            // API 33+ uses OnBackInvokedDispatcher below.
            requestLookupBack();
        }
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Throwable ignored) {}
            backCallback = null;
        }

        if (finishReceiverRegistered) {
            try {
                unregisterReceiver(finishReceiver);
            } catch (Throwable ignored) {}
            finishReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void registerSystemBack() {
        if (Build.VERSION.SDK_INT < 33) return;

        backCallback = this::requestLookupBack;
        try {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback);
        } catch (Throwable ignored) {}
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

    private void suppressWindowTransition() {
        try {
            Window w = getWindow();
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Let taps continue to be handled by the WindowManager overlay
            // or the original app beneath it. This host only owns system Back.
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {}
    }

    private void requestLookupBack() {
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
            // IMPORTANT: do not finish here. Staying alive, fully transparent
            // and non-touchable is what makes system Back reliably target us.
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
            finish();
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {}
    }
}
