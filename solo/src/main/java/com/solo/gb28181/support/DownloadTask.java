package com.solo.gb28181.support;

import com.solo.media.entity.MediaServer;
import com.solo.gb28181.response.DownloadResultResponse;
import lombok.Data;

import java.time.LocalDateTime;

// 进程内存里的临时任务，重启后查不到就是没了。
@Data
public class DownloadTask {

    private String streamId;

    private String deviceId;

    private String channelId;

    private String startTime;

    private String endTime;

    private Long startEpochSec;

    private Long endEpochSec;

    private String transport;

    private Integer speed;

    private String streamNumber;

    private MediaServer mediaServer;

    private Integer rtpPort;

    private String ssrc;

    private String status;

    private String errorMsg;

    private String objectName;

    private String downloadUrl;

    private LocalDateTime createdAt;

    private LocalDateTime inviteAt;

    private LocalDateTime readyAt;
}
