package com.solo.gb28181.support;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 录像计划周时段的时间匹配，单独抽成纯静态逻辑，对账调度和单元测试都走这里。
 *
 * <p>跨午夜语义：endTime &lt;= startTime 表示跨天，如 23:00-06:00 表示周一 23:00 一直录到周二 06:00。
 * 跨天时段在"结束日"（周二凌晨）也要命中，所以判断时除了当天起始的时段，
 * 还要看前一天有没有跨到今天的时段。</p>
 */
public final class RecordPlanTime {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private RecordPlanTime() {
    }

    /** HH:mm 解析，非法格式抛 IllegalArgumentException，带原值方便定位是哪条时段 */
    public static LocalTime parseHm(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        try {
            return LocalTime.parse(value.trim(), HM);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式必须是 HH:mm: " + value);
        }
    }

    /**
     * 判断给定时刻是否落在时段内。
     *
     * @param dayOfWeek  时刻的星期（{@link DayOfWeek#getValue()}，1周一 7周日）
     * @param now        时刻
     * @param periodDay  时段配置的星期
     * @param startTime  时段开始
     * @param endTime    时段结束
     */
    public static boolean contains(int dayOfWeek, LocalTime now, int periodDay,
                                   LocalTime startTime, LocalTime endTime) {
        boolean crossMidnight = !endTime.isAfter(startTime);
        if (!crossMidnight) {
            // 普通时段：同一天内 [start, end)
            return periodDay == dayOfWeek
                    && !now.isBefore(startTime) && now.isBefore(endTime);
        }
        // 跨天：开始日 [start, 24:00)，结束日 [00:00, end)
        if (periodDay == dayOfWeek) {
            return !now.isBefore(startTime);
        }
        return periodDay == prevDay(dayOfWeek) && now.isBefore(endTime);
    }

    /** 周一的前一天是周日（7），其余往前减 1 */
    public static int prevDay(int dayOfWeek) {
        return dayOfWeek == 1 ? 7 : dayOfWeek - 1;
    }
}
