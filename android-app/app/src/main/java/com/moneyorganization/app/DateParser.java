package com.moneyorganization.app;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class DateParser {
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
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
