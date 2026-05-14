package org.leng.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 基础时间单位转换方法
    public static long secondsToMillis(long seconds) {
        return seconds * 1000L;
    }

    public static long minutesToMillis(long minutes) {
        return minutes * 60L * 1000;
    }

    public static long hoursToMillis(long hours) {
        return hours * 60L * 60 * 1000;
    }

    public static long daysToMillis(long days) {
        return days * 24L * 60 * 60 * 1000;
    }

    public static long weeksToMillis(long weeks) {
        return weeks * 7L * 24 * 60 * 60 * 1000;
    }

    public static long monthsToMillis(long months) {
        return months * 30L * 24 * 60 * 60 * 1000;
    }

    public static long yearsToMillis(long years) {
        return years * 365L * 24 * 60 * 60 * 1000;
    }

    // 时间字符串解析（兼容旧版和新版）
    public static long parseTime(String timeStr) {
        return parseDurationToMillis(timeStr);
    }

    public static boolean isValidTime(String timeStr) {
        return isValidTimeFormat(timeStr);
    }

    // 增强版时间字符串解析
    public static long parseDurationToMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1L;
        }

        if (timeStr.equalsIgnoreCase("forever") || timeStr.equalsIgnoreCase("perm") || timeStr.equalsIgnoreCase("permanent")) {
            return Long.MAX_VALUE;
        }

        try {
            char unit = timeStr.charAt(timeStr.length() - 1);
            long amount = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            
            switch (Character.toLowerCase(unit)) {
                case 's': return secondsToMillis(amount);
                case 'm': return minutesToMillis(amount);
                case 'h': return hoursToMillis(amount);
                case 'd': return daysToMillis(amount);
                case 'w': return weeksToMillis(amount);
                case 'M': return monthsToMillis(amount);
                case 'y': return yearsToMillis(amount);
                default: return -1L;
            }
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    // 格式化时间为可读字符串
    public static String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) return "永久";
        if (millis <= 0) return "0秒";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds < 60) return seconds + "秒";

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return minutes + "分钟";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return hours + "小时";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days < 7) return days + "天";

        long weeks = days / 7;
        if (weeks < 4) return weeks + "周";

        long months = days / 30;
        if (months < 12) return months + "个月";

        long years = days / 365;
        return years + "年";
    }

    // 时间戳转可读日期
    public static String timestampToReadable(long timestamp) {
        if (timestamp == Long.MAX_VALUE) return "永久";
        return DATE_FORMAT.format(new Date(timestamp));
    }

    // 计算剩余时间
    public static String getRemainingTime(long endTime) {
        if (endTime == Long.MAX_VALUE) return "永久";
        
        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return "已过期";

        return formatDuration(remaining);
    }

    // 验证时间格式
    public static boolean isValidTimeFormat(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return false;
        
        // 永久封禁关键词
        if (timeStr.equalsIgnoreCase("forever") || 
            timeStr.equalsIgnoreCase("perm") || 
            timeStr.equalsIgnoreCase("permanent")) {
            return true;
        }

        // 数字+单位格式
        return timeStr.matches("^\\d+[smhdwMy]$");
    }

    // 获取当前时间戳（毫秒）
    public static long currentTime() {
        return System.currentTimeMillis();
    }

    // 计算封禁结束时间
    public static long calculateEndTime(long durationMillis) {
        if (durationMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() + durationMillis;
    }
}