package com.solo.gb28181.service;

import com.solo.common.PageResult;
import com.solo.gb28181.response.GbRecordFileVO;

import java.time.LocalDateTime;

public interface GbRecordFileService {

    /**
     * ZLM on_record_mp4 回调入口：登记切片元数据并异步把 MP4 落到对象存储。
     *
     * @param app       ZLM app 名
     * @param stream    ZLM stream 名（即 GbInviteSession.streamId）
     * @param url       ZLM 提供的 MP4 HTTP 下载地址（失败重试靠它）
     * @param path      ZLM 切片文件在节点上的绝对路径，作为幂等键（ZLM 重推同一切片时 path 不变）
     * @param startTime 切片真实开始时间（ZLM start_time，unix 秒）；传 null 时退回"回调时刻-时长"估算
     * @param fileSize  文件大小（字节，ZLM 回调值，可能不准，以实际上传为准）
     * @param duration  时长（秒）
     */
    void onRecordMp4(String app, String stream, String url, String path, Long startTime,
                     long fileSize, double duration);

    /**
     * 分页查询云端录像文件（按设备/通道/时间范围），列表不签 URL，需要播放时再调 {@link #presignedUrl}。
     */
    PageResult<GbRecordFileVO> page(String deviceId, String channelId,
                                    LocalDateTime start, LocalDateTime end, int page, int size);

    /**
     * 取某条录像的播放/下载预签名 URL（有效期 1h）。
     */
    String presignedUrl(Long id);

    /**
     * 重试落存储：FAILED 未超重试上限的，以及 UPLOADING 卡太久的（进程崩在异步任务里）重新拉一遍。
     * 返回本轮领取的任务数。
     */
    int retryFailed();

    /**
     * 按保留天数清理成功切片：删存储对象 + 删元数据行。返回删除条数。
     */
    int cleanExpired(int retentionDays);

    /**
     * 删除一条录像（同时删存储对象）。
     */
    void delete(Long id);
}
