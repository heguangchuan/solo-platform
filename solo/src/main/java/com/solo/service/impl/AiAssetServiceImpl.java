package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.entity.AiAsset;
import com.solo.mapper.AiAssetMapper;
import com.solo.request.AiAssetPageRequest;
import com.solo.request.AiAssetRequest;
import com.solo.service.AiAssetService;
import org.springframework.stereotype.Service;

/**
 * AI素材服务实现类
 */
@Service
public class AiAssetServiceImpl extends ServiceImpl<AiAssetMapper, AiAsset> implements AiAssetService {

    @Override
    public Page<AiAsset> pageQuery(AiAssetPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<AiAsset> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AiAsset> wrapper = new LambdaQueryWrapper<>();
        if (req.getFileName() != null && !req.getFileName().isBlank()) {
            wrapper.like(AiAsset::getFileName, req.getFileName());
        }
        if (req.getAssetType() != null && !req.getAssetType().isBlank()) {
            wrapper.eq(AiAsset::getAssetType, req.getAssetType());
        }
        if (req.getStatus() != null) {
            wrapper.eq(AiAsset::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(AiAsset::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createAsset(AiAssetRequest request) {
        AiAsset asset = new AiAsset();
        asset.setUserId(request.getUserId());
        asset.setTaskId(request.getTaskId());
        asset.setAssetType(request.getAssetType());
        asset.setFileName(request.getFileName());
        asset.setFileUrl(request.getFileUrl());
        asset.setCoverUrl(request.getCoverUrl());
        asset.setThumbnailUrl(request.getThumbnailUrl());
        asset.setMimeType(request.getMimeType());
        asset.setExt(request.getExt());
        asset.setSize(request.getSize());
        asset.setDuration(request.getDuration());
        asset.setWidth(request.getWidth());
        asset.setHeight(request.getHeight());
        asset.setPrompt(request.getPrompt());
        asset.setModelName(request.getModelName());
        asset.setStatus(request.getStatus());
        asset.setRemark(request.getRemark());
        save(asset);
    }

    @Override
    public void updateAsset(Long id, AiAssetRequest request) {
        AiAsset asset = getById(id);
        if (asset == null) {
            throw new ServiceException("素材不存在");
        }
        if (request.getUserId() != null) asset.setUserId(request.getUserId());
        if (request.getTaskId() != null) asset.setTaskId(request.getTaskId());
        if (request.getAssetType() != null) asset.setAssetType(request.getAssetType());
        if (request.getFileName() != null) asset.setFileName(request.getFileName());
        if (request.getFileUrl() != null) asset.setFileUrl(request.getFileUrl());
        if (request.getCoverUrl() != null) asset.setCoverUrl(request.getCoverUrl());
        if (request.getThumbnailUrl() != null) asset.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getMimeType() != null) asset.setMimeType(request.getMimeType());
        if (request.getExt() != null) asset.setExt(request.getExt());
        if (request.getSize() != null) asset.setSize(request.getSize());
        if (request.getDuration() != null) asset.setDuration(request.getDuration());
        if (request.getWidth() != null) asset.setWidth(request.getWidth());
        if (request.getHeight() != null) asset.setHeight(request.getHeight());
        if (request.getPrompt() != null) asset.setPrompt(request.getPrompt());
        if (request.getModelName() != null) asset.setModelName(request.getModelName());
        if (request.getStatus() != null) asset.setStatus(request.getStatus());
        if (request.getRemark() != null) asset.setRemark(request.getRemark());
        updateById(asset);
    }

    @Override
    public AiAsset saveAsset(AiAsset asset) {
        save(asset);
        return asset;
    }
}
