package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.SysLog;
import com.solo.mapper.SysLogMapper;
import com.solo.request.LogExportRequest;
import com.solo.request.LogPageRequest;
import com.solo.service.SysLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务实现
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public Page<SysLog> pageQuery(LogPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysLog> pageParam = new Page<>(page, pageSize);
        return page(pageParam, buildWrapper(req));
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    @Override
    public List<SysLog> queryForExport(LogExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return listByIds(request.getIds());
        }
        return list(buildWrapper(request));
    }

    private LambdaQueryWrapper<SysLog> buildWrapper(LogPageRequest req) {
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();
        if (req.getOperatorName() != null && !req.getOperatorName().isBlank()) {
            wrapper.like(SysLog::getOperatorName, req.getOperatorName());
        }
        if (req.getModule() != null && !req.getModule().isBlank()) {
            wrapper.like(SysLog::getModule, req.getModule());
        }
        if (req.getBusinessType() != null && !req.getBusinessType().isBlank()) {
            wrapper.eq(SysLog::getBusinessType, req.getBusinessType());
        }
        if (req.getResponseStatus() != null) {
            wrapper.eq(SysLog::getResponseStatus, req.getResponseStatus());
        }
        if (req.getClientSource() != null && !req.getClientSource().isBlank()) {
            wrapper.eq(SysLog::getClientSource, req.getClientSource());
        }
        if (req.getStartTime() != null) {
            wrapper.ge(SysLog::getCreateTime, req.getStartTime());
        }
        if (req.getEndTime() != null) {
            wrapper.le(SysLog::getCreateTime, req.getEndTime());
        }
        wrapper.orderByDesc(SysLog::getCreateTime);
        return wrapper;
    }
}
