package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Transparent bridge for external lookup links.
 * It forwards immediately to FloatingService and renders no page of its own.
 */
public final class LookupLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        suppressWindowTransition();
        forward(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        suppressWindowTransition();
        forward(intent);
    }

    private void suppressWindowTransition() {
        try {
            Window w = getWindow();
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            overridePendingTransition(0, 0);
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

        // Some external callers incorrectly pass the complete intent URI as q.
        // Recover only its nested q/query parameter instead of looking up the URI.
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

        return FloatingService.LOOKUP_MODE_FULLSCREEN.equalsIgnoreCase(mode)
                ? FloatingService.LOOKUP_MODE_FULLSCREEN
                : FloatingService.LOOKUP_MODE_FLOAT;
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
        } catch (Throwable t) {
            Toast.makeText(
                    this,
                    "查词浮层启动失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }

        finishBridge();
    }

    private void finishBridge() {
        try {
            finish();
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {}
    }
}
