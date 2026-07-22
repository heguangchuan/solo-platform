package com.solo.system.request;

import lombok.Data;

/**
 * 修改当前用户密码请求体。
 */
@Data
public class ChangePasswordRequest {

    /**
     * 旧密码（明文）
     */
    private String oldPassword;

    /**
     * 新密码（明文，需满足强密码规则）
     */
    private String newPassword;
}
