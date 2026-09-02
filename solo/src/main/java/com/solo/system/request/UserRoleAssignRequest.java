package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户授权（分配角色）请求
 *
 * <p>对应接口 {@code POST /api/user/r2u}，全量替换该用户已有角色集：
 * 传入的 {@code roleIds} 即为最新全量，不在其中的会被取消。</p>
 */
@Schema(description = "用户授权（分配角色）请求")
@Data
public class UserRoleAssignRequest {

    /** 用户 ID（必填，主键） */
    @Schema(description = "用户 ID")
    private Long userId;

    /** 角色 ID 列表（必填；空数组表示清空） */
    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;
}
