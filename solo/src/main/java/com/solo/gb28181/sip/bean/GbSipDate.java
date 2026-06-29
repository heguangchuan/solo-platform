package com.solo.gb28181.sip.bean;

import gov.nist.javax.sip.header.SIPDate;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 重写 JAIN-SIP SIPDate 以适配国标时间格式
 * <p>JAIN-SIP 默认 Date 头格式：<code>Mon, 29 Jun 2026 23:30:00 GMT</code>（HTTP 格式）</p>
 * <p>国标要求：<code>2026-06-29T23:30:00.000</code>（ISO8601 本地时间）</p>
 * <p>部分设备依据此头校时，缺失或格式错误会导致设备时间错乱。</p>
 */
public class GbSipDate extends SIPDate {

    private final Calendar javaCal;

    public GbSipDate(long timeMillis) {
        this.javaCal = new GregorianCalendar(TimeZone.getDefault(), Locale.getDefault());
        this.javaCal.setTime(new Date(timeMillis));
        this.year = javaCal.get(Calendar.YEAR);
        this.month = javaCal.get(Calendar.MONTH);
        this.day = javaCal.get(Calendar.DAY_OF_MONTH);
        this.hour = javaCal.get(Calendar.HOUR_OF_DAY);
        this.minute = javaCal.get(Calendar.MINUTE);
        this.second = javaCal.get(Calendar.SECOND);
        this.wkday = javaCal.get(Calendar.DAY_OF_WEEK);
        this.sipWkDay = switch (wkday) {
            case Calendar.SUNDAY -> "Sun";
            case Calendar.MONDAY -> "Mon";
            case Calendar.TUESDAY -> "Tue";
            case Calendar.WEDNESDAY -> "Wed";
            case Calendar.THURSDAY -> "Thu";
            case Calendar.FRIDAY -> "Fri";
            case Calendar.SATURDAY -> "Sat";
            default -> "Sun";
        };
        // Calendar.MONTH 0-11；这里 sipMonth 与 month 同步，按 0-based 索引取
        this.sipMonth = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"}[month];
    }

    @Override
    public StringBuilder encode(StringBuilder out) {
        int millis = javaCal.get(Calendar.MILLISECOND);
        return out.append(year).append('-')
                .append(pad2(month + 1)).append('-')
                .append(pad2(day)).append('T')
                .append(pad2(hour)).append(':')
                .append(pad2(minute)).append(':')
                .append(pad2(second)).append('.')
                .append(pad3(millis));
    }

    private static String pad2(int v) { return v < 10 ? "0" + v : String.valueOf(v); }
    private static String pad3(int v) { return v < 10 ? "00" + v : (v < 100 ? "0" + v : String.valueOf(v)); }
}
