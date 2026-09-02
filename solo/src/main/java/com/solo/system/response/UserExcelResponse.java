package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用户 Excel 导出响应
 *
 * <p>对应接口 {@code POST /api/user/export}，响应为 {@code .xlsx} 二进制流。
 * <b>不会</b>导出密码字段；{@link #status} 在 Excel 中以"启用/禁用"文本呈现；{@link #createTime} 为 {@code yyyy-MM-dd HH:mm:ss} 字符串。</p>
 */
@Data
public class UserExcelResponse {

    /** 用户 ID */
    @ExcelProperty("用户ID")
    private Long id;

    /** 用户账号 */
    @ExcelProperty("用户账号")
    private String username;

    /** 用户昵称 */
    @ExcelProperty("用户昵称")
    private String nickname;

    /** 邮箱地址 */
    @ExcelProperty("邮箱地址")
    private String email;

    /** 手机号码 */
    @ExcelProperty("手机号码")
    private String phone;

    /** 用户状态（{@code 启用/禁用} 文本） */
    @ExcelProperty("用户状态")
    private String status;

    /** 创建时间（{@code yyyy-MM-dd HH:mm:ss}） */
    @ExcelProperty("创建时间")
    private String createTime;
}
