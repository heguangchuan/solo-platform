package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysLog;
import com.solo.system.request.LogExportRequest;
import com.solo.system.request.LogPageRequest;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface SysLogService extends IService<SysLog> {

    Page<SysLog> pageQuery(LogPageRequest req);

    void deleteBatch(List<Long> ids);

    List<SysLog> queryForExport(LogExportRequest request);
}
