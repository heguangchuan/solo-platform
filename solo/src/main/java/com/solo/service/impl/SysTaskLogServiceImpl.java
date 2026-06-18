package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.SysTaskLog;
import com.solo.mapper.SysTaskLogMapper;
import com.solo.request.TaskLogPageRequest;
import com.solo.service.SysTaskLogService;
import org.springframework.stereotype.Service;

@Service
public class SysTaskLogServiceImpl extends ServiceImpl<SysTaskLogMapper, SysTaskLog> implements SysTaskLogService {

    @Override
    public Page<SysTaskLog> pageQuery(TaskLogPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysTaskLog> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysTaskLog> wrapper = new LambdaQueryWrapper<>();
        if (req.getTaskId() != null) {
            wrapper.eq(SysTaskLog::getTaskId, req.getTaskId());
        }
        if (req.getTaskName() != null && !req.getTaskName().isBlank()) {
            wrapper.like(SysTaskLog::getTaskName, req.getTaskName());
        }
        if (req.getTaskCode() != null && !req.getTaskCode().isBlank()) {
            wrapper.like(SysTaskLog::getTaskCode, req.getTaskCode());
        }
        if (req.getResultStatus() != null) {
            wrapper.eq(SysTaskLog::getResultStatus, req.getResultStatus());
        }
        wrapper.orderByDesc(SysTaskLog::getExecutionTime);
        return page(pageParam, wrapper);
    }
}
