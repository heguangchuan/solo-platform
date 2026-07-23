package com.solo.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 推流实体（对应 ZLM 上的第三方 RTMP / RTSP / RTC push）
 *
 * <p>数据流入路径：{@code ZlmHookService.onStreamChanged(regist=true, originType∈PUSH)} → upsert；
 * {@code regist=false} → 删除本行。{@code on_server_started} 时兜底对齐库与 ZLM。</p>
 *
 * <p><b>注意</b>：国标 INVITE（origin_type=3 RTP_PUSH）由 GbInviteSession 单独记账，不入本表；
 * 拉流代理（PULL）由 stream_proxy 表管；ONVIF 点播（PULL）由 Redis 记账。</p>
 */
@Data
@TableName("stream_push")
public class StreamPush {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String app;

    private String stream;

    private String mediaServerId;

    private String vhost;

    /** 推流协议（rtsp/rtmp/hls/ts/fmp4，通常只留 rtsp） */
    private String streamSchema;

    /** 推流类型：rtmp / rtsp / rtc（ZLM originType 整数已映射为字符串再入库） */
    private String originType;

    private String originUrl;

    /** 当前是否在推流：0已断 1推流中 */
    private Integer pushing;

    private Integer readerCount;

    private Integer aliveSecond;

    /** 首次开始推流时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pushTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
