package com.yuen.nativejpocr;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Native View implementation of the floating search home.
 * Owns only UI state; lookup, OCR, account settings and history remain in FloatingService.
 * No WebView or extra Activity is introduced.
 */
final class GrokSearchView extends FrameLayout {
    interface Actions {
        void onSearch(String query);
        void onSettings();
        void onClose();
        void onUnavailable(String feature);
        void onPrivateModeChanged(boolean enabled);
    }

    private static final int INK = 0xff18191b;
    private static final int MUTED = 0xff85888e;
    private static final int SOFT = 0xfff3f3f4;
    private final Actions actions;
    private final List<String> history;
    private final LinearLayout column;
    private final FrameLayout center;
    private final LinearLayout bottom;
    private final EditText input;
    private final TextView modeButton;
    private final TextView talkButton;
    private final TextView privacyButton;
    private TextView attachmentButton;
    private String selectedMode = "快速";
    private View chips;
    private View popup;
    private View drawer;
    private boolean privateMode;
    private boolean keyboardVisible;
    private float touchX, touchY;
    private boolean draggingSidebar;

    GrokSearchView(Context context, String initialText, boolean isPrivate,
                   List<String> previousQueries, Actions callbacks) {
        super(context);
        actions = callbacks;
        history = new ArrayList<>(previousQueries);
        privateMode = isPrivate;
        setBackgroundColor(0xfffcfcfc);
        setClipChildren(false);
        setFocusableInTouchMode(true);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(3), dp(10), dp(3));
        column.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView menu = roundButton("☰", 24, SOFT, 44);
        menu.setContentDescription("打开侧边栏");
        menu.setOnClickListener(v -> openDrawer());
        header.addView(menu);
        TextView ask = label("提问", 17, INK, true);
        LinearLayout.LayoutParams askParams = new LinearLayout.LayoutParams(-2, -2);
        askParams.leftMargin = dp(17);
        header.addView(ask, askParams);
        View underline = new View(context);
        underline.setBackground(shape(0xff56585c, 2));
        // Keep the active tab's underline within a small vertical stack.
        header.removeView(ask);
        LinearLayout active = new LinearLayout(context);
        active.setGravity(Gravity.CENTER_HORIZONTAL);
        active.setOrientation(LinearLayout.VERTICAL);
        active.addView(ask);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(dp(20), dp(2));
        ul.topMargin = dp(3);
        active.addView(underline, ul);
        header.addView(active, askParams);

        TextView imagine = label("Imagine", 14, MUTED, false);
        LinearLayout.LayoutParams tab = new LinearLayout.LayoutParams(-2, -2);
        tab.leftMargin = dp(13);
        header.addView(imagine, tab);
        imagine.setOnClickListener(v -> actions.onUnavailable("Imagine 图像功能尚未接入"));
        TextView build = label("构建", 14, MUTED, false);
        LinearLayout.LayoutParams buildP = new LinearLayout.LayoutParams(-2, -2);
        buildP.leftMargin = dp(13);
        header.addView(build, buildP);
        build.setOnClickListener(v -> actions.onUnavailable("构建功能尚未接入"));
        View spacer = new View(context);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        privacyButton = roundButton("◉", 21, SOFT, 43);
        privacyButton.setContentDescription("切换私密查询模式");
        privacyButton.setOnClickListener(v -> {
            privateMode = !privateMode;
            actions.onPrivateModeChanged(privateMode);
            closePopup();
            refreshMode();
        });
        header.addView(privacyButton);

        center = new FrameLayout(context);
        column.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(11), 0, dp(11), dp(12));
        column.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView shortcutScroll = new HorizontalScrollView(context);
        shortcutScroll.setHorizontalScrollBarEnabled(false);
        shortcutScroll.setFillViewport(false);
        LinearLayout quick = new LinearLayout(context);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(chip("✦  SuperGrok 优惠", 0xffdcf5ff, 0xff197ec0,
                () -> actions.onUnavailable("SuperGrok 订阅不属于当前应用")), chipParams());
        quick.addView(chip("⚒  构建应用和网站", SOFT, INK,
                () -> actions.onUnavailable("构建功能尚未接入")), chipParams());
        quick.addView(chip("◷  最近搜索", SOFT, INK, this::openDrawer), chipParams());
        shortcutScroll.addView(quick);
        chips = shortcutScroll;
        LinearLayout.LayoutParams chipSpace = new LinearLayout.LayoutParams(-1, dp(51));
        chipSpace.bottomMargin = dp(8);
        bottom.addView(chips, chipSpace);

        LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(11), dp(7), dp(10), dp(9));
        composer.setBackground(stroked(0xfffdfdfd, 24, 0xffe9e9e9));
        bottom.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        input = new EditText(context);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(0xff92959a);
        input.setHint("随便问点什么");
        input.setPadding(dp(2), dp(1), dp(2), dp(7));
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        composer.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(tools, new LinearLayout.LayoutParams(-1, dp(37)));

        TextView add = roundButton("+", 27, SOFT, 36);
        attachmentButton = add;
        add.setContentDescription("附件菜单");
        add.setOnClickListener(v -> showAttachmentMenu(add));
        tools.addView(add);
        modeButton = label("ϟ  快速  ⌄", 13, INK, true);
        modeButton.setGravity(Gravity.CENTER);
        modeButton.setBackground(shape(SOFT, 22));
        LinearLayout.LayoutParams modeP = new LinearLayout.LayoutParams(dp(92), dp(35));
        modeP.leftMargin = dp(7);
        tools.addView(modeButton, modeP);
        modeButton.setOnClickListener(v -> showModelMenu(modeButton));

        View space = new View(context);
        tools.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView mic = roundButton("♩", 24, SOFT, 36);
        mic.setContentDescription("语音输入");
        mic.setOnClickListener(v -> actions.onUnavailable("语音输入尚未接入"));
        tools.addView(mic);
        talkButton = label("▥  开始说话", 13, Color.WHITE, true);
        talkButton.setGravity(Gravity.CENTER);
        talkButton.setBackground(shape(INK, 24));
        LinearLayout.LayoutParams talkP = new LinearLayout.LayoutParams(dp(105), dp(36));
        talkP.leftMargin = dp(7);
        tools.addView(talkButton, talkP);

        talkButton.setOnClickListener(v -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) submit();
            else actions.onUnavailable("实时语音对话尚未接入");
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int n, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                refreshSend();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        input.setText(initialText == null ? "" : initialText);
        input.clearFocus();
        refreshMode();
    }

    EditText getInput() { return input; }

    void setKeyboardVisible(boolean visible) {
        keyboardVisible = visible;
        if (visible) closePopup();
    }

    boolean handleBack() {
        if (popup != null) {
            closePopup();
            return true;
        }
        if (drawer != null) {
            closeDrawer();
            return true;
        }
        if (keyboardVisible) {
            keyboardVisible = false;
            InputMethodManager imm = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
            return true;
        }
        return false;
    }

    private void submit() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) return;
        closePopup();
        actions.onSearch(query);
    }

    private void refreshSend() {
        boolean filled = input.length() > 0 && !input.getText().toString().trim().isEmpty();
        talkButton.setText(filled ? "↑  搜索" : (privateMode ? "↑" : "▥  开始说话"));
        talkButton.setBackground(shape(filled ? INK : (privateMode ? SOFT : INK), 24));
        talkButton.setTextColor(filled || !privateMode ? Color.WHITE : MUTED);
        talkButton.setContentDescription(filled ? "搜索输入内容" : "开始语音会话（未接入）");
    }

    private void refreshMode() {
        privacyButton.setBackground(shape(privateMode ? 0xffdedee0 : SOFT, 50));
        privacyButton.setText(privateMode ? "◉" : "◎");
        input.setHint(privateMode ? "临时对话" : "随便问点什么");
        chips.setVisibility(privateMode ? GONE : VISIBLE);
        center.removeAllViews();
        LinearLayout centerContent = new LinearLayout(getContext());
        centerContent.setOrientation(LinearLayout.VERTICAL);
        centerContent.setGravity(Gravity.CENTER);
        TextView mark = label(privateMode ? "◉" : "✧", 48,
                privateMode ? 0xff333538 : 0xffdadbdd, true);
        mark.setGravity(Gravity.CENTER);
        centerContent.addView(mark);
        if (privateMode) {
            TextView title = label("私密聊天", 20, INK, true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(15);
            centerContent.addView(title, titleLp);
            TextView note = label("本次查词不会保存到本机查询历史。\n如果启用 AI，内容仍会发送给配置的 AI 服务商。",
                    13, MUTED, false);
            note.setGravity(Gravity.CENTER);
            note.setPadding(dp(28), dp(9), dp(28), 0);
            centerContent.addView(note);
        }
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        center.addView(centerContent, cp);
        refreshSend();
    }

    private void showModelMenu(View anchor) {
        if (popup != null) { closePopup(); return; }
        LinearLayout menu = menuContainer();
        addMenuLine(menu, "SuperGrok  ·  节省 67%", "原版参考样式，非当前应用订阅", false, () ->
                actions.onUnavailable("SuperGrok 订阅不属于当前应用"));
        addMenuLine(menu, "Build Beta", "构建应用和网站 · 未接入", false, () ->
                actions.onUnavailable("Build Beta 尚未接入"));
        addMenuLine(menu, "重型", "专家团队 · 未接入", false, () ->
                actions.onUnavailable("重型模式尚未接入"));
        addMenuLine(menu, "专家", "深度思考 · 未接入", false, () ->
                actions.onUnavailable("专家模式尚未接入"));
        addMenuLine(menu, ("快速".equals(selectedMode) ? "✓  " : "") + "快速",
                "当前使用词典及配置的 Groq AI", true, () -> {
                    selectedMode = "快速";
                    modeButton.setText("ϟ  快速  ⌄");
                    closePopup();
                });
        addMenuLine(menu, ("自动".equals(selectedMode) ? "✓  " : "") + "自动",
                "当前仅支持同一查词后端", true, () -> {
                    selectedMode = "自动";
                    modeButton.setText("ϟ  自动  ⌄");
                    closePopup();
                });
        showPopup(menu, dp(290), dp(420), Gravity.LEFT);
    }

    private void showAttachmentMenu(View anchor) {
        if (popup != null) { closePopup(); return; }
        LinearLayout menu = menuContainer();
        addMenuLine(menu, "▣  摄像头", "", true, () -> unavailableAttachment("摄像头"));
        addMenuLine(menu, "▧  图库", "", true, () -> unavailableAttachment("图库"));
        addMenuLine(menu, "▤  文件", "", true, () -> unavailableAttachment("文件"));
        View divider = new View(getContext());
        divider.setBackgroundColor(0xffededed);
        menu.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        addMenuLine(menu, "✧  技能", "", true, () -> unavailableAttachment("技能"));
        addMenuLine(menu, "⚭  连接器", "", true, () -> unavailableAttachment("连接器"));
        showPopup(menu, dp(225), dp(310), Gravity.LEFT);
        attachmentButton.setText("×");
    }

    private void unavailableAttachment(String feature) {
        closePopup();
        actions.onUnavailable(feature + "附件解析尚未接入，当前仅支持文字查词");
    }

    private void showPopup(LinearLayout menu, int width, int height, int gravity) {
        closeDrawer();
        FrameLayout layer = new FrameLayout(getContext());
        layer.setOnClickListener(v -> closePopup());
        addView(layer, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(shape(Color.WHITE, 22));
        scroll.setElevation(dp(12));
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.min(width, getResources().getDisplayMetrics().widthPixels - dp(28)),
                Math.min(height, Math.max(dp(150), getHeight() - dp(140))),
                Gravity.BOTTOM | gravity);
        lp.leftMargin = dp(14);
        lp.bottomMargin = dp(120);
        layer.addView(scroll, lp);
        scroll.setOnClickListener(v -> {});
        popup = layer;
    }

    private LinearLayout menuContainer() {
        LinearLayout menu = new LinearLayout(getContext());
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(7), dp(9), dp(7), dp(9));
        return menu;
    }

    private void addMenuLine(LinearLayout parent, String title, String detail,
                             boolean enabled, Runnable onTap) {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.VERTICAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(13), dp(9), dp(8), dp(9));
        TextView main = label(title, 15, enabled ? INK : MUTED, true);
        line.addView(main);
        if (!detail.isEmpty()) {
            TextView sub = label(detail, 12, MUTED, false);
            sub.setPadding(0, dp(2), 0, 0);
            line.addView(sub);
        }
        parent.addView(line, new LinearLayout.LayoutParams(-1, detail.isEmpty() ? dp(50) : dp(62)));
        line.setOnClickListener(v -> onTap.run());
    }

    private void openDrawer() {
        closePopup();
        if (drawer != null) return;
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        keyboardVisible = false;
        input.clearFocus();
        FrameLayout layer = new FrameLayout(getContext());
        View scrim = new View(getContext());
        scrim.setBackgroundColor(0x26000000);
        layer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        scrim.setOnClickListener(v -> closeDrawer());

        int drawerWidth = Math.min(dp(350),
                Math.round(getResources().getDisplayMetrics().widthPixels * .9f));
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(12), dp(15), dp(17));
        panel.setBackgroundColor(0xfff4f4f5);
        layer.addView(panel, new FrameLayout.LayoutParams(drawerWidth, -1, Gravity.LEFT));

        LinearLayout user = new LinearLayout(getContext());
        user.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(user, new LinearLayout.LayoutParams(-1, dp(65)));
        TextView avatar = roundButton("あ", 20, Color.WHITE, 43);
        user.addView(avatar);
        TextView username = label("JP Native", 19, INK, true);
        LinearLayout.LayoutParams userLp = new LinearLayout.LayoutParams(0, -2, 1);
        userLp.leftMargin = dp(13);
        user.addView(username, userLp);
        TextView back = roundButton("‹", 29, Color.WHITE, 40);
        user.addView(back);
        back.setOnClickListener(v -> closeDrawer());

        LinearLayout navigation = menuContainer();
        navigation.setBackground(shape(Color.WHITE, 16));
        panel.addView(navigation);
        addMenuLine(navigation, "◷  自动化任务", "", true, () -> actions.onUnavailable("自动化任务尚未接入"));
        addMenuLine(navigation, "▧  照片库", "", true, () -> actions.onUnavailable("照片库尚未接入"));
        addMenuLine(navigation, "▤  项目", "", true, () -> actions.onUnavailable("项目功能尚未接入"));
        addMenuLine(navigation, "✧  Groq AI 设置", "", true, () -> { closeDrawer(); actions.onSettings(); });

        TextView promo = label("✦  SuperGrok 优惠（界面参考）", 14, Color.WHITE, true);
        promo.setGravity(Gravity.CENTER);
        promo.setBackground(shape(0xff2454e5, 15));
        LinearLayout.LayoutParams promoLp = new LinearLayout.LayoutParams(-1, dp(57));
        promoLp.topMargin = dp(13);
        panel.addView(promo, promoLp);
        promo.setOnClickListener(v -> actions.onUnavailable("当前应用不提供 SuperGrok 订阅"));

        TextView historyTitle = label("对话  ⌄", 17, INK, true);
        historyTitle.setPadding(dp(6), dp(18), 0, dp(10));
        panel.addView(historyTitle);
        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout historyList = menuContainer();
        scroll.addView(historyList);
        if (history.isEmpty()) {
            addMenuLine(historyList, "暂无搜索历史", "", false, () -> {});
        } else {
            int count = 0;
            for (String h : history) {
                if (++count > 20) break;
                addMenuLine(historyList, "◷  " + h, "", true, () -> {
                    closeDrawer();
                    input.setText(h);
                    input.setSelection(input.length());
                });
            }
        }

        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(footer);
        addMenuLine(footer, "⌕  搜索", "", true, () -> { closeDrawer(); input.requestFocus(); });
        addMenuLine(footer, "⚙  设置", "", true, () -> { closeDrawer(); actions.onSettings(); });
        addMenuLine(footer, "+  新建聊天", "", true, () -> {
            input.setText("");
            privateMode = false;
            actions.onPrivateModeChanged(false);
            closeDrawer();
            refreshMode();
        });

        addView(layer, new FrameLayout.LayoutParams(-1, -1));
        drawer = layer;
        panel.setTranslationX(-drawerWidth);
        panel.animate().translationX(0).setDuration(230).start();
    }

    private void closeDrawer() {
        if (drawer == null) return;
        View old = drawer;
        drawer = null;
        removeView(old);
    }

    private void closePopup() {
        if (popup == null) return;
        View old = popup;
        popup = null;
        attachmentButton.setText("+");
        removeView(old);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchX = e.getX();
            touchY = e.getY();
            draggingSidebar = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - touchX, dy = e.getY() - touchY;
            if (Math.abs(dx) > dp(28) && Math.abs(dx) > Math.abs(dy) * 1.25f &&
                    ((drawer == null && touchX < dp(38) && dx > 0) ||
                     (drawer != null && dx < 0))) {
                draggingSidebar = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (draggingSidebar) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - touchX;
                if (dx > dp(55)) openDrawer();
                else if (dx < -dp(55)) closeDrawer();
                draggingSidebar = false;
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) draggingSidebar = false;
            return true;
        }
        return super.onTouchEvent(e);
    }

    private TextView chip(String text, int background, int foreground, Runnable tap) {
        TextView v = label(text, 13, foreground, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(15), 0, dp(15), 0);
        v.setBackground(shape(background, 22));
        v.setOnClickListener(w -> tap.run());
        return v;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(43));
        lp.rightMargin = dp(8);
        return lp;
    }

    private TextView roundButton(String content, int size, int color, int height) {
        TextView v = label(content, size, INK, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(shape(color, height / 2));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(height), dp(height)));
        return v;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable stroked(int color, int radius, int stroke) {
        GradientDrawable d = shape(color, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
