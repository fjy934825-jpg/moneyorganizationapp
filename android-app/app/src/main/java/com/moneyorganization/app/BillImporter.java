package com.moneyorganization.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BillImporter {
    private BillImporter() {
    }

    public static List<ExpenseRecord> parse(String content, String source) {
        List<String> lines = new ArrayList<>();
        for (String raw : content.replace("\uFEFF", "").split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) lines.add(line);
        }

        int headerIndex = -1;
        for (int index = 0; index < lines.size(); index++) {
            String normalized = lines.get(index).replaceAll("\\s", "");
            if (normalized.contains("交易时间") || normalized.contains("交易创建时间") || normalized.contains("时间")) {
                headerIndex = index;
                break;
            }
        }
        if (headerIndex < 0) return new ArrayList<>();

        String delimiter = guessDelimiter(lines.get(headerIndex));
        List<String> headers = splitLine(lines.get(headerIndex), delimiter);
        List<ExpenseRecord> records = new ArrayList<>();

        for (int index = headerIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.matches("^-{4,}$") || line.contains("共") && line.contains("笔记录")) continue;
            List<String> cells = splitLine(line, delimiter);
            if (cells.size() < 3) continue;

            Map<String, String> row = new HashMap<>();
            for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {
                row.put(headers.get(cellIndex), cellIndex < cells.size() ? cells.get(cellIndex) : "");
            }

            ExpenseRecord record = normalizeRow(row, source);
            if (record != null) records.add(record);
        }

        return records;
    }

    private static ExpenseRecord normalizeRow(Map<String, String> row, String source) {
        String dateText = pick(row, "交易时间", "交易创建时间", "付款时间", "时间", "账单时间");
        String merchant = pick(row, "交易对方", "商户名称", "商品名称", "商品", "商品说明", "交易说明", "收/付款方", "对方");
        String note = pick(row, "商品名称", "商品", "商品说明", "交易说明", "备注", "类型", "分类");
        String type = pick(row, "收/支", "收支", "资金流向", "交易类型", "类型");
        String status = pick(row, "交易状态", "当前状态", "状态", "资金状态");
        String amountText = pick(row, "金额(元)", "金额（元）", "金额", "支出金额", "交易金额", "收入/支出金额(元)", "收入/支出金额（元）");

        if (dateText.isEmpty() || amountText.isEmpty()) return null;
        if (!isExpense(type, amountText, status, note)) return null;

        double amount = parseAmount(amountText);
        if (amount <= 0) return null;

        long timeMillis = DateParser.parse(dateText);
        if (timeMillis <= 0) return null;

        String description = (merchant + " " + note).trim();
        String category = CategoryRules.classify(description);
        String safeMerchant = merchant.isEmpty() ? (note.isEmpty() ? "历史支出" : note) : merchant;
        String id = "h_" + Math.abs((timeMillis + "|" + amount + "|" + safeMerchant + "|" + source).hashCode());
        return new ExpenseRecord(id, timeMillis, safeMerchant, note, category, source, amount);
    }

    private static boolean isExpense(String type, String amountText, String status, String note) {
        String joined = type + " " + amountText + " " + status + " " + note;
        if (joined.matches(".*(退款|已退款|收入|转入|已退|还款成功).*")) return false;
        if (joined.matches(".*(支出|付款|消费|交易成功|支付成功).*")) return true;
        return amountText.trim().startsWith("-") || amountText.trim().startsWith("－");
    }

    private static String pick(Map<String, String> row, String... names) {
        for (String key : row.keySet()) {
            String normalizedKey = key.replaceAll("\\s", "");
            for (String name : names) {
                if (normalizedKey.contains(name.replaceAll("\\s", ""))) {
                    return row.get(key).trim();
                }
            }
        }
        return "";
    }

    private static double parseAmount(String value) {
        try {
            return Math.abs(Double.parseDouble(value.replaceAll("[^\\d.-]", "")));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String guessDelimiter(String line) {
        int tabs = count(line, '\t');
        int commas = count(line, ',');
        return tabs > commas ? "\t" : ",";
    }

    private static int count(String value, char target) {
        int total = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) total++;
        }
        return total;
    }

    private static List<String> splitLine(String line, String delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        char split = delimiter.charAt(0);

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            char next = index + 1 < line.length() ? line.charAt(index + 1) : '\0';
            if (current == '"' && next == '"') {
                value.append('"');
                index++;
            } else if (current == '"') {
                quoted = !quoted;
            } else if (current == split && !quoted) {
                cells.add(clean(value.toString()));
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        cells.add(clean(value.toString()));
        return cells;
    }

    private static String clean(String value) {
        return value.replaceAll("^\"+|\"+$", "").trim();
    }
}
