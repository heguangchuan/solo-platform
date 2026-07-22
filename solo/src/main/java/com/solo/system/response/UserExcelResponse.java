package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class UserExcelResponse {
    @ExcelProperty("用户ID")
    private Long id;

    @ExcelProperty("用户账号")
    private String username;

    @ExcelProperty("用户昵称")
    private String nickname;

    @ExcelProperty("邮箱地址")
    private String email;

    @ExcelProperty("手机号码")
    private String phone;

    @ExcelProperty("用户状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;
}
