package com.moneyorganization.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BillImporter {
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}[-/年]\\d{1,2}[-/月]\\d{1,2}(?:日)?(?:\\s+\\d{1,2}:\\d{1,2}(?::\\d{1,2})?)?)");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}:\\d{1,2}(?::\\d{1,2})?$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[-－]?(?:(?:¥|￥)\\s*\\d+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})\\s*元|\\d{1,7}\\.\\d{1,2})");

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
        if (headerIndex < 0) return parsePdfBlocks(lines, source);

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

        if (records.isEmpty()) return parsePdfBlocks(lines, source);
        return records;
    }

    private static ExpenseRecord normalizeRow(Map<String, String> row, String source) {
        String dateText = pick(row, "交易时间", "交易创建时间", "付款时间", "时间", "账单时间", "发生时间", "记账时间");
        String merchant = pick(row, "交易对方", "商户名称", "商品名称", "商品", "商品说明", "交易说明", "收/付款方", "对方", "对方名称", "商家", "付款方", "收款方");
        String note = pick(row, "商品名称", "商品", "商品说明", "交易说明", "备注", "类型", "分类");
        String type = pick(row, "收/支", "收支", "收支类型", "资金流向", "交易类型", "类型");
        String status = pick(row, "交易状态", "当前状态", "状态", "资金状态");
        String amountText = pick(row, "金额(元)", "金额（元）", "金额", "支出金额", "交易金额", "收入/支出金额(元)", "收入/支出金额（元）", "收/支金额", "收支金额");

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
        if (joined.matches(".*(退款|已退款|收入|转入|已退|还款成功|零钱提现|转账收款).*")) return false;
        if (joined.matches(".*(支出|付款|消费|交易成功|支付成功|扫二维码付款|商户消费|转账付款).*")) return true;
        String amount = amountText.trim();
        return amount.startsWith("-") || amount.startsWith("－");
    }

    private static List<ExpenseRecord> parseLooseLines(List<String> lines, String source) {
        List<ExpenseRecord> records = new ArrayList<>();
        for (String line : lines) {
            if (!couldBeRecord(line)) continue;

            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            Matcher amountMatcher = AMOUNT_PATTERN.matcher(line);
            if (!dateMatcher.find()) continue;

            String amountText = "";
            while (amountMatcher.find()) {
                amountText = amountMatcher.group();
            }
            if (amountText.isEmpty() || !isExpense(line, amountText, "", line)) continue;

            long timeMillis = DateParser.parse(dateMatcher.group(1));
            double amount = parseAmount(amountText);
            if (timeMillis <= 0 || amount <= 0) continue;

            String merchant = looseMerchant(line, dateMatcher.group(1), amountText);
            String category = CategoryRules.classify(merchant + " " + line);
            String id = "h_" + Math.abs((timeMillis + "|" + amount + "|" + merchant + "|" + source).hashCode());
            records.add(new ExpenseRecord(id, timeMillis, merchant, line, category, source, amount));
        }
        return records;
    }

    private static List<ExpenseRecord> parsePdfBlocks(List<String> lines, String source) {
        List<ExpenseRecord> records = parseLooseLines(lines, source);
        if (!records.isEmpty()) return records;

        for (int index = 0; index < lines.size(); index++) {
            DateMatch date = dateAt(lines, index);
            if (date == null) continue;

            StringBuilder block = new StringBuilder(date.text);
            int nextIndex = index + date.consumedLines;
            while (nextIndex < lines.size() && dateAt(lines, nextIndex) == null) {
                block.append(' ').append(lines.get(nextIndex));
                nextIndex++;
            }

            ExpenseRecord record = parseBlock(block.toString(), date.text, source);
            if (record != null) records.add(record);
            index = Math.max(index, nextIndex - 1);
        }
        return records;
    }

    private static ExpenseRecord parseBlock(String block, String dateText, String source) {
        String amountText = lastAmount(block);
        if (amountText.isEmpty() || !isExpense(block, amountText, "", block)) return null;

        long timeMillis = DateParser.parse(dateText);
        double amount = parseAmount(amountText);
        if (timeMillis <= 0 || amount <= 0) return null;

        String merchant = looseMerchant(block, dateText, amountText);
        String category = CategoryRules.classify(merchant + " " + block);
        String id = "h_" + Math.abs((timeMillis + "|" + amount + "|" + merchant + "|" + source).hashCode());
        return new ExpenseRecord(id, timeMillis, merchant, block, category, source, amount);
    }

    private static DateMatch dateAt(List<String> lines, int index) {
        if (index + 1 < lines.size()) {
            String nextLine = lines.get(index + 1).trim();
            if (TIME_PATTERN.matcher(nextLine).matches()) {
                String combined = lines.get(index).trim() + " " + nextLine;
                Matcher combinedDate = DATE_PATTERN.matcher(combined);
                if (combinedDate.find()) return new DateMatch(combinedDate.group(1), 2);
            }
        }

        Matcher fullDate = DATE_PATTERN.matcher(lines.get(index));
        if (fullDate.find()) return new DateMatch(fullDate.group(1), 1);
        return null;
    }

    private static String lastAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        String amount = "";
        while (matcher.find()) amount = matcher.group();
        return amount;
    }

    private static boolean couldBeRecord(String line) {
        if (line.contains("交易时间") || line.contains("导出") || line.contains("微信支付账单")) return false;
        return line.matches(".*20\\d{2}[-/年]\\d{1,2}[-/月]\\d{1,2}.*") && line.matches(".*\\d+(?:\\.\\d{1,2})?.*");
    }

    private static String looseMerchant(String line, String dateText, String amountText) {
        String cleaned = line
                .replace(dateText, " ")
                .replace(amountText, " ")
                .replaceAll("(支出|收入|交易成功|支付成功|付款|消费|零钱|余额|¥|￥|,|\\t)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) return "微信账单";
        String[] parts = cleaned.split(" ");
        for (String part : parts) {
            if (!part.matches(".*(成功|完成|支出|收入|支付|付款|微信|账单).*")) return part;
        }
        return parts[0];
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

    private static final class DateMatch {
        final String text;
        final int consumedLines;

        DateMatch(String text, int consumedLines) {
            this.text = text;
            this.consumedLines = consumedLines;
        }
    }
}
