package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "本平台国标身份")
@Data
public class GbLocalPlatformInfo {

    @Schema(description = "本平台 SIP ID（20 位国标编码），上级平台白名单配置时使用")
    private String id;

    @Schema(description = "本平台 SIP 域（如行政区划编码），上级平台白名单配置时使用")
    private String domain;

    @Schema(description = "本平台 SIP 监听 IP（设备/上级发包的目标地址）")
    private String ip;

    @Schema(description = "本平台 SIP 监听端口")
    private Integer port;

    @Schema(description = "鉴权密码（上级平台把本平台加入白名单时要填的密码）")
    private String password;

    @Schema(description = "设备注册有效期（秒），仅信息展示用")
    private Integer registerExpires;
}
