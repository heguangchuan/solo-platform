package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "观看记录分页查询请求")
@Data
public class GbViewRecordPageRequest {

    private Integer page = 1;

    private Integer size = 20;

    @Schema(description = "观看者类型 1平台用户 2开放API应用")
    private Integer actorType;

    @Schema(description = "观看者 ID")
    private Long actorId;

    private String deviceId;

    private String channelId;

    @Schema(description = "状态 0观看中 1正常结束 2兜底关闭")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime start;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime end;
}
