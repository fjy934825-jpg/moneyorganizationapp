package com.moneyorganization.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpenseStore {
    private static final String PREFS = "expense_records";
    private static final String KEY_RECORDS = "records";

    private final SharedPreferences preferences;

    public ExpenseStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean add(ExpenseRecord record) {
        List<ExpenseRecord> records = all();
        for (ExpenseRecord existing : records) {
            if (existing.id.equals(record.id)) return false;
        }
        records.add(0, record);
        save(records);
        return true;
    }

    public synchronized List<ExpenseRecord> all() {
        String raw = preferences.getString(KEY_RECORDS, "[]");
        List<ExpenseRecord> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                records.add(ExpenseRecord.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        return records;
    }

    public synchronized List<ExpenseRecord> currentMonth() {
        Calendar now = Calendar.getInstance();
        return recordsForMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH));
    }

    public synchronized List<ExpenseRecord> recordsForMonth(int year, int month) {
        List<ExpenseRecord> filtered = new ArrayList<>();
        for (ExpenseRecord record : all()) {
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(record.timeMillis);
            if (date.get(Calendar.YEAR) == year && date.get(Calendar.MONTH) == month) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    public synchronized List<ExpenseRecord> recordsForYear(int year) {
        List<ExpenseRecord> filtered = new ArrayList<>();
        for (ExpenseRecord record : all()) {
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(record.timeMillis);
            if (date.get(Calendar.YEAR) == year) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    public synchronized Map<Integer, Double> monthlyTotalsForYear(int year) {
        Map<Integer, Double> totals = new LinkedHashMap<>();
        for (int month = 0; month < 12; month++) {
            totals.put(month, 0.0);
        }
        for (ExpenseRecord record : recordsForYear(year)) {
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(record.timeMillis);
            int month = date.get(Calendar.MONTH);
            totals.put(month, totals.get(month) + record.amount);
        }
        return totals;
    }

    public synchronized double sum(List<ExpenseRecord> records) {
        double total = 0;
        for (ExpenseRecord record : records) {
            total += record.amount;
        }
        return total;
    }

    public synchronized Map<String, Double> categoryTotals(List<ExpenseRecord> records) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (ExpenseRecord record : records) {
            Double previous = totals.get(record.category);
            totals.put(record.category, (previous == null ? 0 : previous) + record.amount);
        }
        return totals;
    }

    public synchronized void clear() {
        preferences.edit().putString(KEY_RECORDS, "[]").apply();
    }

    private void save(List<ExpenseRecord> records) {
        JSONArray array = new JSONArray();
        for (ExpenseRecord record : records) {
            try {
                array.put(record.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply();
    }
}
