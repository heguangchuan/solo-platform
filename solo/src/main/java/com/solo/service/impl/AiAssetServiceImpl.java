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
        if (req.getSource() != null && !req.getSource().isBlank()) {
            wrapper.eq(AiAsset::getSource, req.getSource());
        }
        wrapper.orderByDesc(AiAsset::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createAsset(AiAssetRequest request) {
        AiAsset asset = new AiAsset();
        asset.setAssetType(request.getAssetType());
        asset.setSource(request.getSource() != null ? request.getSource() : "SYSTEM_UPLOAD");
        asset.setFileName(request.getFileName());
        asset.setFileUrl(request.getFileUrl());
        asset.setCoverUrl(request.getCoverUrl());
        save(asset);
    }

    @Override
    public void updateAsset(Long id, AiAssetRequest request) {
        AiAsset asset = getById(id);
        if (asset == null) {
            throw new ServiceException("素材不存在");
        }
        if (request.getAssetType() != null) asset.setAssetType(request.getAssetType());
        if (request.getSource() != null) asset.setSource(request.getSource());
        if (request.getFileName() != null) asset.setFileName(request.getFileName());
        if (request.getFileUrl() != null) asset.setFileUrl(request.getFileUrl());
        if (request.getCoverUrl() != null) asset.setCoverUrl(request.getCoverUrl());
        updateById(asset);
    }

    @Override
    public AiAsset saveAsset(AiAsset asset) {
        save(asset);
        return asset;
    }
}
