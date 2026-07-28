package com.solo.gb28181.transmit.cmd;

/**
 * 级联注册事务信息：一次 REGISTER/UNREGISTER 事务属于哪个上级、是注册还是注销。
 * <p>供 {@link PlatformRegisterState} 按 Call-Id 关联，收到响应时回查。</p>
 */
public record PlatformRegisterTx(String serverGbId, boolean register) {
}
