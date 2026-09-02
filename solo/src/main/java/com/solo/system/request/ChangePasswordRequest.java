package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改当前用户密码请求体
 *
 * <p>对应接口 {@code POST /api/auth/password}。首次登录强制改密也走本接口。
 * 失败常见返回：400（旧密码错误 / 新密码与旧密码相同 / 新密码不满足强密码规则）。</p>
 *
 * <h3>新密码规则</h3>
 * <ul>
 *   <li>长度 8-32 位</li>
 *   <li>至少包含大写字母、小写字母、数字、特殊字符 4 类中的 3 类</li>
 *   <li>不允许包含空白字符</li>
 * </ul>
 */
@Schema(description = "修改当前用户密码请求体")
@Data
public class ChangePasswordRequest {

    /** 旧密码（必填，明文；与服务端存的 MD5 比对） */
    @Schema(description = "旧密码")
    private String oldPassword;

    /** 新密码（必填；需满足强密码规则，见类注释） */
    @Schema(description = "新密码")
    private String newPassword;
}
