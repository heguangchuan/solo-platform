package com.solo.system.request;

import lombok.Data;

import java.util.List;

/**
 * 文件管理分页查询请求参数
 */
@Data
public class FilePageRequest {

    private Long page = 1L;
    private Long pageSize = 10L;

    /** 文件名模糊 */
    private String originalName;

    /** 上传人账号 */
    private String uploaderName;

    /** 创建时间起 */
    private String startTime;

    /** 创建时间止 */
    private String endTime;

    /** 批量删除场景透传 */
    private List<Long> ids;
}
