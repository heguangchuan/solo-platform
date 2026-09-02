package com.solo.system.request;

import lombok.Data;

/**
 * 用户分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 10（用户列表偏大，默认更小）。
 * 对应接口 {@code POST /api/user/page}。</p>
 */
@Data
public class UserPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 10 */
    private Long pageSize = 10L;

    /** 登录账号（可选，精确匹配） */
    private String username;

    /** 用户昵称（可选，精确匹配） */
    private String nickname;

    /** 状态过滤（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
