package com.solo.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.entity.OpenApiLog;
import com.solo.openapi.mapper.OpenApiLogMapper;
import com.solo.openapi.request.OpenApiLogPageRequest;
import com.solo.openapi.service.OpenApiLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class OpenApiLogServiceImpl extends ServiceImpl<OpenApiLogMapper, OpenApiLog> implements OpenApiLogService {

    @Override
    public Page<OpenApiLog> pageQuery(OpenApiLogPageRequest req) {
        long page = req.getPage() != null ? req.getPage() : 1L;
        long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        return page(new Page<>(page, pageSize), buildWrapper(req));
    }

    @Override
    public List<OpenApiLog> queryForExport(OpenApiLogPageRequest req) {
        return list(buildWrapper(req));
    }

    private LambdaQueryWrapper<OpenApiLog> buildWrapper(OpenApiLogPageRequest req) {
        LambdaQueryWrapper<OpenApiLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getAppKey())) {
            wrapper.like(OpenApiLog::getAppKey, req.getAppKey());
        }
        if (StringUtils.hasText(req.getPath())) {
            wrapper.like(OpenApiLog::getPath, req.getPath());
        }
        if (req.getSuccess() != null) {
            if (req.getSuccess()) {
                wrapper.eq(OpenApiLog::getBizCode, OpenApiErrorCode.SUCCESS.getCode());
            } else {
                wrapper.ne(OpenApiLog::getBizCode, OpenApiErrorCode.SUCCESS.getCode());
            }
        }
        if (req.getStartTime() != null) {
            wrapper.ge(OpenApiLog::getCreateTime, req.getStartTime());
        }
        if (req.getEndTime() != null) {
            wrapper.le(OpenApiLog::getCreateTime, req.getEndTime());
        }
        wrapper.orderByDesc(OpenApiLog::getCreateTime);
        return wrapper;
    }
}
