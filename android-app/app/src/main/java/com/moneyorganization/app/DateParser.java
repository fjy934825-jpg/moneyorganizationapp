package com.moneyorganization.app;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class DateParser {
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy年MM月dd日 HH:mm:ss",
            "yyyy年M月d日 HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年M月d日 HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy年MM月dd日",
            "yyyy年M月d日"
    };

    private DateParser() {
    }

    public static long parse(String value) {
        String normalized = value == null ? "" : value.trim();
        for (String pattern : PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CHINA);
                format.setLenient(false);
                return format.parse(normalized).getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }
}
