package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamPush;
import com.solo.media.request.StreamPushPageRequest;
import com.solo.media.response.StreamPushPlayUrlResponse;

import java.util.List;
import java.util.Map;

public interface StreamPushService extends IService<StreamPush> {

    Page<StreamPush> pageQuery(StreamPushPageRequest request);

    StreamPushPlayUrlResponse getPlayUrls(Long id);

    void forceStop(Long id);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    void onStreamRegister(String app, String stream, String mediaServerId, Map<String, Object> body);

    void onStreamCancel(String app, String stream, String mediaServerId);

    void reconcileWithZlm(String mediaServerId);
}
