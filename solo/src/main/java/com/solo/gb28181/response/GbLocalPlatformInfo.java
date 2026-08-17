package com.solo.gb28181.response;

import lombok.Data;

/**
 * 本平台国标身份（级联配置时给上级平台参考用）
 * <p>对应 {@code application.yaml} 的 {@code gb28181.*} 段。按 ADR-036，本平台身份收归全局
 * 单例，per-upper 表不再冗余存储。该 DTO 只读展示，不参与任何写入。</p>
 */
@Data
public class GbLocalPlatformInfo {

    /** 本平台 SIP ID（20 位国标编码），上级平台白名单配置时使用 */
    private String id;

    /** 本平台 SIP 域（如行政区划编码），上级平台白名单配置时使用 */
    private String domain;

    /** 本平台 SIP 监听 IP（设备 / 上级发包的目标地址） */
    private String ip;

    /** 本平台 SIP 监听端口 */
    private Integer port;

    /** 鉴权密码（上级平台把本平台加入白名单时要填的密码） */
    private String password;

    /** 设备注册有效期（秒），仅信息展示用 */
    private Integer registerExpires;
}
