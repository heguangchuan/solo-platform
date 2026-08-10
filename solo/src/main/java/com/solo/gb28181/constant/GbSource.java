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
    /**
     * 级联接入（下级级联平台通过 REGISTER 接入，视为设备）。
     *
     * @deprecated 设备与下级平台不再区分——{@code gb_platform} 已从白名单翻转为黑名单，
     *             没有"哪些 ID 是下级平台"的登记，注册流程一律写 {@link #REGISTER}。
     *             保留常量仅为兼容历史数据里可能存在的 4；新代码不要再写入。
     */
    @Deprecated
    public static final int CASCADE = 4;

    /** 设备默认状态：未注册（系统新增/导入后、真正 REGISTER 上来前） */
    public static final int DEVICE_STATUS_UNREGISTERED = 2;
    /** 通道默认状态：未注册 */
    public static final String CHANNEL_STATUS_UNREGISTERED = "UNREGISTERED";
}
