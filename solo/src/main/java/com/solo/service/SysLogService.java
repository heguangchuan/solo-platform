package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysLog;
import com.solo.request.LogExportRequest;
import com.solo.request.LogPageRequest;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface SysLogService extends IService<SysLog> {

    Page<SysLog> pageQuery(LogPageRequest req);

    void deleteBatch(List<Long> ids);

    List<SysLog> queryForExport(LogExportRequest request);
}
