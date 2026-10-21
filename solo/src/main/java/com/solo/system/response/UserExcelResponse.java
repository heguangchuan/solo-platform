package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 Excel 导出响应")
@Data
public class UserExcelResponse {

    @Schema(description = "用户 ID")
    @ExcelProperty("用户ID")
    private Long id;

    @Schema(description = "用户账号")
    @ExcelProperty("用户账号")
    private String username;

    @Schema(description = "用户昵称")
    @ExcelProperty("用户昵称")
    private String nickname;

    @Schema(description = "邮箱地址")
    @ExcelProperty("邮箱地址")
    private String email;

    @Schema(description = "手机号码")
    @ExcelProperty("手机号码")
    private String phone;

    @Schema(description = "用户状态")
    @ExcelProperty("用户状态")
    private String status;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private String createTime;
}
