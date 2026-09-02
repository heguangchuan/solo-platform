package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文件管理分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 10（文件列表偏大，默认更小）。
 * 对应接口 {@code POST /api/file/page}。</p>
 */
@Schema(description = "文件管理分页查询请求参数")
@Data
public class FilePageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    @Schema(description = "当前页码")
    private Long page = 1L;

    /** 每页条数，缺省 10 */
    @Schema(description = "每页条数")
    private Long pageSize = 10L;

    /** 文件名模糊（可选） */
    @Schema(description = "文件名")
    private String originalName;

    /** 上传人账号（可选） */
    @Schema(description = "上传人账号")
    private String uploaderName;

    /** 创建时间起（{@code yyyy-MM-dd HH:mm:ss} 字符串，可选） */
    @Schema(description = "创建时间起")
    private String startTime;

    /** 创建时间止（{@code yyyy-MM-dd HH:mm:ss} 字符串，可选） */
    @Schema(description = "创建时间止")
    private String endTime;

    /** 批量删除场景透传的 ID 集合（可选；本接口分页查询一般不用） */
    @Schema(description = "文件 ID 集合")
    private List<Long> ids;
}
