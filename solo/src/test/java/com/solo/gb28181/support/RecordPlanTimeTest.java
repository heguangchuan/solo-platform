package com.solo.gb28181.support;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 周计划时段匹配的边界用例。这套逻辑看着简单，跨午夜那块最容易写反，
 * 出了问题就是"凌晨的录像莫名其妙缺一段"，所以边界都单独列出来。
 */
class RecordPlanTimeTest {

    private final LocalTime t09 = LocalTime.of(9, 0);
    private final LocalTime t10 = LocalTime.of(10, 0);
    private final LocalTime t18 = LocalTime.of(18, 0);

    @Test
    void matchesWithinPeriod() {
        // 周一 09:00-18:00，周一 10 点在里面
        boolean hit = RecordPlanTime.contains(1, t10, 1, t09, t18);
        assertThat(hit).isTrue();
    }

    @Test
    void differentDayOfWeekNoMatch() {
        boolean hit = RecordPlanTime.contains(2, t10, 1, t09, t18);
        assertThat(hit).isFalse();
    }

    @Test
    void halfOpenRangeStartInclusiveEndExclusive() {
        // 18:00 整已经属于下一段，不能再算命中，否则整点会被两条时段各算一次
        assertThat(RecordPlanTime.contains(1, t09, 1, t09, t18)).isTrue();
        assertThat(RecordPlanTime.contains(1, t18, 1, t09, t18)).isFalse();
    }

    @Test
    void crossMidnightMatchesOnStartDayEvening() {
        // 周一 23:00 到周二 06:00，周一 23:30 应命中
        boolean hit = RecordPlanTime.contains(1, LocalTime.of(23, 30), 1,
                LocalTime.of(23, 0), LocalTime.of(6, 0));
        assertThat(hit).isTrue();
    }

    @Test
    void crossMidnightMatchesOnEndDayDawn() {
        // 周二凌晨 00:30，要算到周一晚开始的那条上
        boolean hit = RecordPlanTime.contains(2, LocalTime.of(0, 30), 1,
                LocalTime.of(23, 0), LocalTime.of(6, 0));
        assertThat(hit).isTrue();
    }

    @Test
    void crossMidnightAfterEndNoMatch() {
        // 周二 07:00，超过 06:00 就不该再命中
        boolean hit = RecordPlanTime.contains(2, LocalTime.of(7, 0), 1,
                LocalTime.of(23, 0), LocalTime.of(6, 0));
        assertThat(hit).isFalse();
    }

    @Test
    void crossMidnightBeforeStartNoMatch() {
        // 周一 22:00，还没到 23:00
        boolean hit = RecordPlanTime.contains(1, LocalTime.of(22, 0), 1,
                LocalTime.of(23, 0), LocalTime.of(6, 0));
        assertThat(hit).isFalse();
    }

    @Test
    void equalStartEndTreatedAsCrossMidnight() {
        // end <= start 即视为跨午夜：开始日 >=start 命中，结束日凌晨 <start 也命中
        boolean hitAtStart = RecordPlanTime.contains(3, t09, 3, t09, t09);
        assertThat(hitAtStart).isTrue();
        boolean hitNextDawn = RecordPlanTime.contains(4, LocalTime.of(0, 1), 3, t09, t09);
        assertThat(hitNextDawn).isTrue();
    }

    @Test
    void sundayToMondayPrevDayWrapsToSunday() {
        // 周日 23 点开始跨到周一凌晨：prevDay(周一) 必须是 7 而不是 0
        boolean mondayDawn = RecordPlanTime.contains(1, LocalTime.of(1, 0), 7,
                LocalTime.of(23, 0), LocalTime.of(6, 0));
        assertThat(mondayDawn).isTrue();
        assertThat(RecordPlanTime.prevDay(1)).isEqualTo(7);
        assertThat(RecordPlanTime.prevDay(7)).isEqualTo(6);
    }

    @Test
    void parseHmTrimsAndParses() {
        assertThat(RecordPlanTime.parseHm(" 08:05 ")).isEqualTo(LocalTime.of(8, 5));
    }

    @Test
    void parseHmRejectsBadFormatWithOriginalValue() {
        // 报错信息带原值，对账时能直接看出是哪条时段配坏了
        assertThatThrownBy(() -> RecordPlanTime.parseHm("8点"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8点");
    }

    @Test
    void parseHmRejectsBlank() {
        assertThatThrownBy(() -> RecordPlanTime.parseHm(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
