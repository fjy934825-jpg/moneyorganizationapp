package com.moneyorganization.app;

import org.json.JSONException;
import org.json.JSONObject;

public class ExpenseRecord {
    public final String id;
    public final long timeMillis;
    public final String merchant;
    public final String note;
    public final String category;
    public final String source;
    public final double amount;

    public ExpenseRecord(String id, long timeMillis, String merchant, String note, String category, String source, double amount) {
        this.id = id;
        this.timeMillis = timeMillis;
        this.merchant = merchant;
        this.note = note;
        this.category = category;
        this.source = source;
        this.amount = amount;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("timeMillis", timeMillis);
        json.put("merchant", merchant);
        json.put("note", note);
        json.put("category", category);
        json.put("source", source);
        json.put("amount", amount);
        return json;
    }

    public static ExpenseRecord fromJson(JSONObject json) throws JSONException {
        return new ExpenseRecord(
                json.getString("id"),
                json.getLong("timeMillis"),
                json.optString("merchant", "未命名支出"),
                json.optString("note", ""),
                json.optString("category", "其他"),
                json.optString("source", "通知"),
                json.getDouble("amount")
        );
    }
}
