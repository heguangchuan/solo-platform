package com.solo.system.request;

import lombok.Data;

import java.util.List;

/**
 * 文件管理分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 10（文件列表偏大，默认更小）。
 * 对应接口 {@code POST /api/file/page}。</p>
 */
@Data
public class FilePageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 10 */
    private Long pageSize = 10L;

    /** 文件名模糊（可选） */
    private String originalName;

    /** 上传人账号（可选） */
    private String uploaderName;

    /** 创建时间起（{@code yyyy-MM-dd HH:mm:ss} 字符串，可选） */
    private String startTime;

    /** 创建时间止（{@code yyyy-MM-dd HH:mm:ss} 字符串，可选） */
    private String endTime;

    /** 批量删除场景透传的 ID 集合（可选；本接口分页查询一般不用） */
    private List<Long> ids;
}
