package com.solo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.AiAsset;
import com.solo.mapper.AiAssetMapper;
import com.solo.service.AiAssetService;
import org.springframework.stereotype.Service;

/**
 * AI素材服务实现类
 */
@Service
public class AiAssetServiceImpl extends ServiceImpl<AiAssetMapper, AiAsset> implements AiAssetService {

    @Override
    public AiAsset saveAsset(AiAsset asset) {
        save(asset);
        return asset;
    }
}
