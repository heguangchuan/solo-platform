package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysTaskLog;
import com.solo.request.TaskLogPageRequest;

public interface SysTaskLogService extends IService<SysTaskLog> {
    Page<SysTaskLog> pageQuery(TaskLogPageRequest req);
}
