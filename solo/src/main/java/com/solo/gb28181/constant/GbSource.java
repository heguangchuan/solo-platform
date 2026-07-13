package com.solo.gb28181.constant;

/**
 * 设备 / 通道数据来源常量，以及"系统新增/导入"记录的默认状态常量。
 * <p>状态字段只能由 SIP 交互改变，手动新增/导入的记录一律落默认态：
 * 设备 {@link #DEVICE_STATUS_UNREGISTERED}，通道 {@link #CHANNEL_STATUS_UNREGISTERED}。</p>
 */
public final class GbSource {

    private GbSource() {
    }

    /** 系统新增（页面手动录入） */
    public static final int SYSTEM = 1;
    /** 表格导入 */
    public static final int IMPORT = 2;
    /** 设备注册（SIP REGISTER / Catalog 自动入库） */
    public static final int REGISTER = 3;

    /** 设备默认状态：未注册（系统新增/导入后、真正 REGISTER 上来前） */
    public static final int DEVICE_STATUS_UNREGISTERED = 2;
    /** 通道默认状态：未注册 */
    public static final String CHANNEL_STATUS_UNREGISTERED = "UNREGISTERED";
}
