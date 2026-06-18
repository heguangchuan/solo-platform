package com.solo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiAsset;

/**
 * AI素材服务接口
 */
public interface AiAssetService extends IService<AiAsset> {

    /**
     * 保存素材
     */
    AiAsset saveAsset(AiAsset asset);
}
