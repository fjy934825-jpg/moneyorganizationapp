package com.moneyorganization.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class BudgetNotifier {
    public static final double MONTHLY_LIMIT = 1500.0;
    private static final String CHANNEL_ID = "budget_alerts";
    private static final int NOTIFICATION_ID = 1500;

    private BudgetNotifier() {
    }

    public static void maybeNotify(Context context, ExpenseStore store, ExpenseRecord newest) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(newest.timeMillis);
        List<ExpenseRecord> monthRecords = store.recordsForMonth(date.get(Calendar.YEAR), date.get(Calendar.MONTH));
        double total = store.sum(monthRecords);
        if (total < MONTHLY_LIMIT) return;

        String key = "budget_alert_" + date.get(Calendar.YEAR) + "_" + date.get(Calendar.MONTH);
        if (context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE).getBoolean(key, false)) return;

        notify(context, total);
        context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, true)
                .apply();
    }

    private static void notify(Context context, double total) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "支出红线提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("当月支出超过预算红线时提醒");
            manager.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("本月支出已超过红线")
                .setContentText(String.format(Locale.CHINA, "当前已支出 ¥%.2f，超过 ¥%.2f", total, MONTHLY_LIMIT))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(NOTIFICATION_ID, notification);
    }
}
