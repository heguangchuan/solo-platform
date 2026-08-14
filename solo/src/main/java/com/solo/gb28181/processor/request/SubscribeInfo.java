package com.solo.gb28181.processor.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SUBSCRIBE 订阅记录（ADR-029）。
 * <p>本平台作为下级收到上级 SUBSCRIBE 后保存的订阅上下文——后续 catalog / mobilePosition
 * 变更 NOTIFY 推送（ADR-030 待启动）需要从这里取订阅方地址、过期时间。</p>
 * <p>当前仅入内存（{@link SubscribeHolder}），不持久化：GB/T 28181 没有"订阅持久化"要求，
 * 重启后上级按自身重传机制会重发 SUBSCRIBE，无需本平台兜底。</p>
 *
 * @see SubscribeHolder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeInfo {

    /** 上级平台国标 ID（{@code fromUser}），与 {@code gb_parent_platform.server_gb_id} 对应 */
    private String platformId;

    /** 订阅事件类型：Catalog / MobilePosition / 其他 */
    private String cmdType;

    /** 报文里带的 DeviceID（上级关心的下级设备/虚拟组织 ID；空串表示订阅本平台全部） */
    private String deviceId;

    /** 报文里带的 SN——后续 NOTIFY 响应里回带，方便上级对账 */
    private String sn;

    /** 订阅有效期（秒），0 = 退订 */
    private int expires;

    /** 接受时间，便于排查"订阅存活多久" */
    private LocalDateTime acceptedAt;
}
