package com.moneyorganization.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.app.Activity;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private ExpenseStore store;
    private LinearLayout content;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
    private final Calendar selectedMonth = Calendar.getInstance();
    private int animatedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ExpenseStore(this);
        selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildLayout();
        requestPostNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void buildLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF5F7F8);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(28), dp(18), dp(28));
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private void render() {
        content.removeAllViews();
        animatedIndex = 0;

        TextView eyebrow = text("微信 / 支付宝通知自动记账", 13, 0xFF1F8A64, Typeface.BOLD);
        content.addView(eyebrow);

        TextView title = text("支出管家", 34, 0xFF15201D, Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(16));
        content.addView(title);

        if (!isNotificationListenerEnabled()) {
            content.addView(permissionCard());
        } else {
            TextView enabled = text("通知识别已开启。付款后如果微信/支付宝发出支付通知，会自动记录。", 14, 0xFF63706C, Typeface.NORMAL);
            enabled.setPadding(0, 0, 0, dp(14));
            content.addView(enabled);
        }

        int year = selectedMonth.get(Calendar.YEAR);
        int month = selectedMonth.get(Calendar.MONTH);
        List<ExpenseRecord> monthRecords = store.recordsForMonth(year, month);
        List<ExpenseRecord> yearRecords = store.recordsForYear(year);

        content.addView(monthSwitcher());
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(summaryCard(monthRecords));
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(budgetCard(monthRecords));
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(yearCard(year, yearRecords));
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(categoryCard(monthRecords));
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(recordsCard(monthRecords));
        animateIn(content.getChildAt(content.getChildCount() - 1));
        content.addView(clearButton());
    }

    private View monthSwitcher() {
        LinearLayout card = card(0xFFEFF7F3, 0x00000000);
        LinearLayout row = row();
        Button previous = chip("上个月");
        Button next = chip("下个月");
        TextView label = text(monthLabel(), 20, 0xFF15201D, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);

        previous.setOnClickListener(v -> {
            selectedMonth.add(Calendar.MONTH, -1);
            render();
        });
        next.setOnClickListener(v -> {
            selectedMonth.add(Calendar.MONTH, 1);
            render();
        });

        row.addView(previous);
        row.addView(label, weightParams(1));
        row.addView(next);
        card.addView(row);
        return card;
    }

    private View permissionCard() {
        LinearLayout card = card(0xFFFFFFFF, 0x221F8A64);
        TextView heading = text("先开启通知使用权", 19, 0xFF15201D, Typeface.BOLD);
        TextView body = text("打开后，本应用只能读取通知栏文本，用来识别微信和支付宝的支出通知。", 14, 0xFF63706C, Typeface.NORMAL);
        body.setPadding(0, dp(8), 0, dp(14));
        Button button = button("去开启");
        button.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        card.addView(heading);
        card.addView(body);
        card.addView(button);
        return card;
    }

    private View summaryCard(List<ExpenseRecord> records) {
        LinearLayout card = card(0xFFFFFFFF, 0x221F8A64);
        TextView label = text(monthLabel() + "支出", 14, 0xFF63706C, Typeface.BOLD);
        TextView total = text(money(store.sum(records)), 32, 0xFF15201D, Typeface.BOLD);
        TextView count = text(records.size() + " 笔支出", 14, 0xFF63706C, Typeface.NORMAL);
        card.addView(label);
        card.addView(total);
        card.addView(count);
        return card;
    }

    private View budgetCard(List<ExpenseRecord> records) {
        LinearLayout card = card(0xFF15201D, 0x00000000);
        double total = store.sum(records);
        double progress = Math.min(total / BudgetNotifier.MONTHLY_LIMIT, 1.0);
        int barColor = total >= BudgetNotifier.MONTHLY_LIMIT ? 0xFFCF4B45 : 0xFF1F8A64;

        TextView label = text("每月红线", 14, 0xBBFFFFFF, Typeface.BOLD);
        TextView amount = text(money(total) + " / " + money(BudgetNotifier.MONTHLY_LIMIT), 24, 0xFFFFFFFF, Typeface.BOLD);
        TextView status = text(total >= BudgetNotifier.MONTHLY_LIMIT ? "已超过红线，会发送系统通知提醒。" : "还在预算范围内。", 14, 0xCCFFFFFF, Typeface.NORMAL);
        status.setPadding(0, dp(8), 0, dp(12));

        LinearLayout track = new LinearLayout(this);
        track.setBackground(roundDrawable(0x33FFFFFF, 999, 0));
        track.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12));

        View fill = new View(this);
        fill.setBackground(roundDrawable(barColor, 999, 0));
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) progress));
        View spacer = new View(this);
        track.addView(spacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) (1 - progress)));

        card.addView(label);
        card.addView(amount);
        card.addView(status);
        card.addView(track, trackParams);
        return card;
    }

    private View yearCard(int year, List<ExpenseRecord> records) {
        LinearLayout card = card(0xFFFFFFFF, 0x223867C8);
        card.addView(text(year + " 年度账单", 19, 0xFF15201D, Typeface.BOLD));
        TextView total = text("全年支出 " + money(store.sum(records)), 18, 0xFF3867C8, Typeface.BOLD);
        total.setPadding(0, dp(8), 0, dp(8));
        card.addView(total);

        Map<Integer, Double> monthlyTotals = store.monthlyTotalsForYear(year);
        for (Map.Entry<Integer, Double> entry : monthlyTotals.entrySet()) {
            if (entry.getValue() <= 0) continue;
            LinearLayout row = row();
            row.addView(text((entry.getKey() + 1) + " 月", 14, 0xFF15201D, Typeface.BOLD), weightParams(1));
            TextView amount = text(money(entry.getValue()), 14, 0xFF63706C, Typeface.NORMAL);
            amount.setGravity(Gravity.END);
            row.addView(amount, weightParams(1));
            card.addView(row);
        }
        if (records.isEmpty()) {
            TextView empty = text("这一年还没有支出记录。", 14, 0xFF63706C, Typeface.NORMAL);
            empty.setPadding(0, dp(10), 0, 0);
            card.addView(empty);
        }
        return card;
    }

    private View categoryCard(List<ExpenseRecord> records) {
        LinearLayout card = card(0xFFFFFFFF, 0x00000000);
        card.addView(text("分类概览", 19, 0xFF15201D, Typeface.BOLD));
        Map<String, Double> totals = store.categoryTotals(records);
        if (totals.isEmpty()) {
            TextView empty = text("还没有自动识别到支出。", 14, 0xFF63706C, Typeface.NORMAL);
            empty.setPadding(0, dp(10), 0, 0);
            card.addView(empty);
            return card;
        }

        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            LinearLayout row = row();
            row.addView(text(entry.getKey(), 15, 0xFF15201D, Typeface.BOLD), weightParams(1));
            TextView amount = text(money(entry.getValue()), 15, 0xFF63706C, Typeface.NORMAL);
            amount.setGravity(Gravity.END);
            row.addView(amount, weightParams(1));
            card.addView(row);
        }
        return card;
    }

    private View recordsCard(List<ExpenseRecord> records) {
        LinearLayout card = card(0xFFFFFFFF, 0x00000000);
        card.addView(text("最近记录", 19, 0xFF15201D, Typeface.BOLD));
        if (records.isEmpty()) {
            TextView empty = text("开启通知使用权后，微信/支付宝付款通知会出现在这里。", 14, 0xFF63706C, Typeface.NORMAL);
            empty.setPadding(0, dp(10), 0, 0);
            card.addView(empty);
            return card;
        }

        int limit = Math.min(records.size(), 20);
        for (int index = 0; index < limit; index++) {
            ExpenseRecord record = records.get(index);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(12), 0, dp(10));

            LinearLayout top = row();
            top.addView(text(record.merchant, 15, 0xFF15201D, Typeface.BOLD), weightParams(1));
            TextView amount = text(money(record.amount), 15, 0xFF15201D, Typeface.BOLD);
            amount.setGravity(Gravity.END);
            top.addView(amount, weightParams(1));

            TextView meta = text(record.category + " · " + record.source + " · " + dateFormat.format(record.timeMillis), 13, 0xFF63706C, Typeface.NORMAL);
            item.addView(top);
            item.addView(meta);
            card.addView(item);
        }
        return card;
    }

    private View clearButton() {
        Button button = button("清空本地记录");
        button.setTextColor(0xFFCF4B45);
        button.setBackgroundColor(0xFFEFE7E5);
        button.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清空记录")
                .setMessage("确定清空本机保存的全部支出记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    store.clear();
                    render();
                })
                .show());
        return button;
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName componentName = new ComponentName(this, NotificationExpenseListener.class);
        return flat.contains(componentName.flattenToString());
    }

    private LinearLayout card(int fillColor, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundDrawable(fillColor, 22, strokeColor));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        return row;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setLineSpacing(0, 1.12f);
        return textView;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(0xFF15201D);
        return button;
    }

    private Button chip(String value) {
        Button button = button(value);
        button.setTextColor(0xFF15201D);
        button.setTextSize(13);
        button.setBackground(roundDrawable(0xFFFFFFFF, 999, 0xFFE1EAE6));
        return button;
    }

    private GradientDrawable roundDrawable(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(10));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(animatedIndex * 45L)
                .setDuration(280L)
                .start();
        animatedIndex++;
    }

    private String monthLabel() {
        return selectedMonth.get(Calendar.YEAR) + " 年 " + (selectedMonth.get(Calendar.MONTH) + 1) + " 月";
    }

    private void requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private LinearLayout.LayoutParams weightParams(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    private String money(double value) {
        return String.format(Locale.CHINA, "¥%.2f", value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
