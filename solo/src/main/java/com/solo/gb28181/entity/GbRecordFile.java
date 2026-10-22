package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "云端录像文件（ZLM 切片落 S3 的元数据）")
@Data
@TableName("gb_record_file")
public class GbRecordFile {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "ZLM 流 ID（GbInviteSession.streamId）")
    private String streamId;

    @Schema(description = "ZLM app 名（通常 rtp）")
    private String app;

    @Schema(description = "承载流节点 ID（ZLM 节点 ID）")
    private String serverId;

    @Schema(description = "录像段开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "录像段结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "时长（秒）")
    private Double duration;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "S3 对象名（gb28181/record/...mp4）")
    private String objectName;

    @Schema(description = "缩略图 S3 对象名（截图抓拍，预留）")
    private String thumbObjectName;

    // 0上传中 1成功 2失败；ZLM 回调先落一行 UPLOADING，传完再翻 SUCCESS
    public static final int STATUS_UPLOADING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    @Schema(description = "状态 0上传中 1成功 2失败")
    private Integer status;

    @Schema(description = "最近一次失败原因")
    private String failReason;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "ZLM 回调的 MP4 下载地址（失败重试要用）")
    private String sourceUrl;

    @Schema(description = "幂等键（ZLM 回调 path），同一切片只落一次")
    private String hookKey;

    @Schema(description = "入库时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
