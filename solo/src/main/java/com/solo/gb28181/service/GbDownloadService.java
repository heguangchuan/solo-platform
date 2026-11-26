package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.DownloadStartRequest;
import com.solo.gb28181.response.DownloadResultResponse;

// start 立即返 INVITING，前端轮询到 READY 再拿 MP4 地址。
// 任务只在内存，SSRC 跟历史回放共用一个池。
public interface GbDownloadService {

    DownloadResultResponse start(DownloadStartRequest req);

    DownloadResultResponse getStatus(String streamId);

    void stop(String streamId);


    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRecordMp4(String app, String stream, String url, long fileSize, double duration);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);
}
