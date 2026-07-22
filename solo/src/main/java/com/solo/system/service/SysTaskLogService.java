package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysTaskLog;
import com.solo.system.request.TaskLogPageRequest;

public interface SysTaskLogService extends IService<SysTaskLog> {
    Page<SysTaskLog> pageQuery(TaskLogPageRequest req);
}
