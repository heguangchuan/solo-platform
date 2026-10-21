package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamProxy;
import com.solo.media.request.StreamProxyPageRequest;
import com.solo.media.response.StreamProxyPlayUrlResponse;

import java.util.List;

public interface StreamProxyService extends IService<StreamProxy> {

    Page<StreamProxy> pageQuery(StreamProxyPageRequest request);

    void saveOrUpdateProxy(StreamProxy proxy);

    void enable(Long id);

    void disable(Long id);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    StreamProxyPlayUrlResponse getPlayUrls(Long id);

    void reconcileWithZlm(String mediaServerId);
}
