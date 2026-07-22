package com.solo.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.ai.entity.AiAsset;
import com.solo.ai.request.AiAssetPageRequest;
import com.solo.ai.request.AiAssetRequest;

/**
 * AI素材服务接口
 */
public interface AiAssetService extends IService<AiAsset> {

    /**
     * 分页查询
     */
    Page<AiAsset> pageQuery(AiAssetPageRequest req);

    /**
     * 新增素材
     */
    void createAsset(AiAssetRequest request);

    /**
     * 修改素材
     */
    void updateAsset(Long id, AiAssetRequest request);

    /**
     * 保存素材
     */
    AiAsset saveAsset(AiAsset asset);
}
