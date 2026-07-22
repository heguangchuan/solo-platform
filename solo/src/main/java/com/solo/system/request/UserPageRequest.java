package com.solo.system.request;

import lombok.Data;

/**
 * 用户分页查询请求参数
 */
@Data
public class UserPageRequest {
    private Long page = 1L;
    private Long pageSize = 10L;
    private String username;
    private String nickname;
    private Integer status;
}
