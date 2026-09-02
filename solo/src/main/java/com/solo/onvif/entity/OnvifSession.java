package com.solo.onvif.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * ONVIF 实时点播会话（Redis 主存，纯运行时状态）
 *
 * <p>与 {@code com.solo.gb28181.entity.GbInviteSession} 对应——都是"点播进行中"的会话快照，
 * 不持久化。Redis Key = {@code onvif:play:<streamId>}，Hash 字段：serverId / proxyKey /
 * deviceId / startTime（毫秒时间戳）。</p>
 *
 * <p>{@link #streamId} 即 ZLM app=stream，本系统固定 {@code onvif_<deviceId>}；{@link #startTime}
 * 启动时写入，停止时整 Key 删（参见 {@code OnvifPlayServiceImpl#stop}）。</p>
 */
@Schema(description = "ONVIF 实时点播会话")
@Data
public class OnvifSession {

    /** 业务主键：{@code onvif_<deviceId>} */
    @Schema(description = "业务流 ID（onvif_<deviceId>）")
    private String streamId;

    /** 选中承载拉流的 ZLM 节点 serverId */
    @Schema(description = "承载流节点 serverId")
    private String serverId;

    /** ONVIF 设备主键（关联 {@link OnvifDevice#getId()}） */
    @Schema(description = "ONVIF 设备主键")
    private Long deviceId;

    /** ZLM addStreamProxy 返回的 key，用于 delStreamProxy */
    @Schema(description = "ZLM addStreamProxy 返回的 key")
    private String proxyKey;

    @Schema(description = "会话开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
