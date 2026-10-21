package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.request.StreamPushClientPageRequest;

public interface StreamPushClientService extends IService<StreamPushClient> {

    Page<StreamPushClient> pageQuery(StreamPushClientPageRequest req);

    StreamPushClient createClient(StreamPushClient input);

    void updateClient(StreamPushClient input);

    StreamPushClient resetToken(Long id);

    String authenticate(String token);

    String clientIdOfToken(String token);
}
