package com.moneyorganization.app;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationExpenseListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        String title = text(notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = text(notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = text(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String joined = bigText.isEmpty() ? text : text + "\n" + bigText;

        ExpenseRecord record = ExpenseParser.parse(sbn.getPackageName(), title, joined, sbn.getPostTime());
        if (record != null) {
            ExpenseStore store = new ExpenseStore(this);
            if (store.add(record)) {
                BudgetNotifier.maybeNotify(this, store, record);
            }
        }
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
