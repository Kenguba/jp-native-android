package com.yuen.nativejpocr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * No-UI bridge for external lookup links:
 * jp-native://lookup?q=candidate
 *
 * It never renders a page. It forwards the query to FloatingService, which
 * displays the real WindowManager TYPE_APPLICATION_OVERLAY lookup surface.
 */
public final class LookupLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        forward(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forward(intent);
    }

    private void forward(Intent source) {
        String query = "";

        if (source != null) {
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
        }

        if (query.isEmpty()) {
            Toast.makeText(this, "查询链接没有提供 q 参数", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent show = new Intent(this, FloatingService.class)
                .setAction(FloatingService.ACTION_SHOW_LOOKUP_OVERLAY)
                .putExtra(FloatingService.EXTRA_LOOKUP_QUERY, query)
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

        finish();
        overridePendingTransition(0, 0);
    }
}
