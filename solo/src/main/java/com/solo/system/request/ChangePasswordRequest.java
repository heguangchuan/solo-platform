package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新密码规则：
 * <ul>
 *   <li>长度 8-32 位</li>
 *   <li>至少包含大写字母、小写字母、数字、特殊字符 4 类中的 3 类</li>
 *   <li>不允许包含空白字符</li>
 * </ul>
 */
@Schema(description = "修改当前用户密码请求体")
@Data
public class ChangePasswordRequest {

    @Schema(description = "旧密码")
    private String oldPassword;

    @Schema(description = "新密码")
    private String newPassword;
}
