package com.moneyorganization.app;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExpenseParser {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:支出|付款|消费|支付|扣款|向.*?付款|成功付款)?\\s*(?:¥|￥|人民币)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*元?");
    private static final Pattern MERCHANT_PATTERN = Pattern.compile("(?:向|给|商户|收款方|付款给)\\s*([^，,。\\n\\r]+)");

    private ExpenseParser() {
    }

    public static ExpenseRecord parse(String packageName, String title, String text, long postTime) {
        String source = sourceName(packageName);
        if (source == null) return null;

        String joined = ((title == null ? "" : title) + "\n" + (text == null ? "" : text)).trim();
        if (joined.isEmpty()) return null;
        if (isIgnored(joined)) return null;
        if (!looksLikePayment(joined)) return null;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(joined);
        if (!amountMatcher.find()) return null;

        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1));
        } catch (NumberFormatException error) {
            return null;
        }
        if (amount <= 0) return null;

        String merchant = extractMerchant(joined);
        String category = CategoryRules.classify(joined);
        String id = stableId(source + "|" + postTime + "|" + amount + "|" + merchant + "|" + joined);
        return new ExpenseRecord(id, postTime, merchant, joined, category, source, amount);
    }

    private static boolean isIgnored(String text) {
        return text.contains("退款")
                || text.contains("退回")
                || text.contains("收款到账")
                || text.contains("已收款")
                || text.contains("收入")
                || text.contains("到账")
                || text.contains("余额宝收益")
                || text.contains("零钱通收益");
    }

    private static boolean looksLikePayment(String text) {
        return text.contains("微信支付")
                || text.contains("支付宝")
                || text.contains("支付成功")
                || text.contains("付款成功")
                || text.contains("成功付款")
                || text.contains("支出")
                || text.contains("消费")
                || text.contains("扣款")
                || text.contains("收款方")
                || text.contains("向")
                && text.contains("付款");
    }

    private static String extractMerchant(String text) {
        Matcher merchantMatcher = MERCHANT_PATTERN.matcher(text);
        if (merchantMatcher.find()) {
            return cleanupMerchant(merchantMatcher.group(1));
        }

        String cleaned = text
                .replaceAll("(微信支付|支付宝|支付成功|付款成功|消费|支出|扣款|人民币|¥|￥|\\d+(?:\\.\\d{1,2})?元?)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 18) {
            cleaned = cleaned.substring(0, 18);
        }
        return cleaned.isEmpty() ? "通知支出" : cleanupMerchant(cleaned);
    }

    private static String cleanupMerchant(String merchant) {
        return merchant
                .replaceAll("(成功|付款|支付|支出|消费|¥|￥|\\d+(?:\\.\\d{1,2})?元?).*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String sourceName(String packageName) {
        if ("com.tencent.mm".equals(packageName)) return "微信";
        if ("com.eg.android.AlipayGphone".equals(packageName)) return "支付宝";
        return null;
    }

    private static String stableId(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes());
            StringBuilder builder = new StringBuilder("n_");
            for (int index = 0; index < 8; index++) {
                builder.append(String.format(Locale.US, "%02x", bytes[index]));
            }
            return builder.toString();
        } catch (Exception error) {
            return "n_" + Math.abs(input.hashCode());
        }
    }
}
