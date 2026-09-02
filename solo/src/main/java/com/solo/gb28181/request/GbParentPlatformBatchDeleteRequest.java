package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量删除上级平台请求
 *
 * <p>对应 {@code POST /api/gb28181/parent-platform/batch/delete}。</p>
 */
@Schema(description = "批量删除上级平台请求")
@Data
public class GbParentPlatformBatchDeleteRequest {

    @Schema(description = "要删除的上级平台主键 ID 列表")
    private List<Long> ids;
}
