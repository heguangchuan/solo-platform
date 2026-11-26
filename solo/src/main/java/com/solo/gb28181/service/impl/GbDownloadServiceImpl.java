package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.DownloadStartRequest;
import com.solo.gb28181.response.DownloadResultResponse;
import com.solo.gb28181.service.GbDownloadService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.support.DownloadTask;
import com.solo.gb28181.support.DownloadTaskRegistry;
import com.solo.gb28181.support.RecordFileLinkSigner;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadPoolExecutor;

// start 发完 INVITE 就返回，流就绪后开 MP4 录制，on_record_mp4 直接签发 ZLM 本地文件访问链接。
// 一段录像可能要下几十分钟，HTTP 不能一直吊着；任务只放内存，SSRC 跟回放共用。
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDownloadServiceImpl implements GbDownloadService {

    private static final String LOG_TAG = "录像下载";
    private static final String APP = "rtp";

    private static final DateTimeFormatter UI_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int LINK_EXPIRY_SEC = 3600;

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbInviteSessionService inviteSessionService;
    private final GbInviteSessionCleanupService inviteCleanup;
    private final DownloadTaskRegistry taskRegistry;
    private final RecordFileLinkSigner linkSigner;
    private final ThreadPoolExecutor gb28181BizExecutor;


    @Override
    public DownloadResultResponse start(DownloadStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null
                || req.getStartTime() == null || req.getEndTime() == null) {
            throw new ServiceException("deviceId / channelId / startTime / endTime 不能为空");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, req.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法下载");
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, req.getDeviceId())
                        .eq(GbDeviceChannel::getChannelId, req.getChannelId()));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }

        LocalDateTime startDt = LocalDateTime.parse(req.getStartTime(), UI_FMT);
        LocalDateTime endDt = LocalDateTime.parse(req.getEndTime(), UI_FMT);
        long startEpoch = startDt.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = endDt.atZone(ZoneId.systemDefault()).toEpochSecond();
        if (endEpoch <= startEpoch) {
            throw new ServiceException("结束时间必须晚于起始时间");
        }
        if (req.getSpeed() != null && (req.getSpeed() < 1 || req.getSpeed() > 8)) {
            throw new ServiceException("下载倍速只支持 1/2/4/8");
        }

        MediaServer server = mediaServerSelector.pick();
        String transport = SdpBuilder.normalizeTransport(req.getTransport());
        String streamNumber = req.getStreamNumber();
        String streamNumberForId = (streamNumber == null || streamNumber.isBlank())
                ? null : streamNumber.trim().replace(":", "_");
        // streamId：与回放一致，加时间段 + 码流后缀；DL 前缀区分用途便于日志检索
        String streamId = "DL_" + req.getDeviceId() + "_" + req.getChannelId()
                + "_" + startDt.format(COMPACT_FMT) + "_" + endDt.format(COMPACT_FMT)
                + (streamNumberForId == null ? "" : "_" + streamNumberForId);

        DownloadTask exist = taskRegistry.get(streamId);
        if (exist != null) {
            log.info("[{}] 复用已有任务 流ID:{} 状态:{}", LOG_TAG, streamId, exist.getStatus());
            return toResult(exist);
        }

        String ssrc = ssrcFactory.acquire(server.getServerId(), true);
        int rtpPort;
        try {
            int tcpMode = transportToTcpMode(transport);
            rtpPort = inviteCleanup.openRtpServerWithRetry(server, streamId, tcpMode);
        } catch (Exception e) {
            ssrcFactory.release(server.getServerId(), ssrc);
            throw new ServiceException("申请 RTP 收流端口失败: " + e.getMessage());
        }

        CallIdHeader callIdHeader = headerProvider.newCallId(device);
        if (callIdHeader == null) {
            zlmRestClient.closeRtpServer(server, streamId);
            ssrcFactory.release(server.getServerId(), ssrc);
            throw new ServiceException("SIP Provider 未就绪");
        }
        String callId = callIdHeader.getCallId();
        String fromTag = SipUtils.newFromTag();

        GbInviteSession session = new GbInviteSession();
        session.setStreamId(streamId);
        session.setServerId(server.getServerId());
        session.setDeviceId(req.getDeviceId());
        session.setChannelId(req.getChannelId());
        session.setSsrc(ssrc);
        session.setRtpPort(rtpPort);
        session.setTransport(transport);
        session.setCallId(callId);
        session.setFromTag(fromTag);
        session.setSessionType(GbInviteSession.TYPE_DOWNLOAD);
        session.setStartTimeStr(String.valueOf(startEpoch));
        session.setEndTimeStr(String.valueOf(endEpoch));
        session.setStatus(GbInviteSession.STATUS_INVITING);
        session.setStartTime(LocalDateTime.now());
        inviteSessionService.save(session);

        DownloadTask task = new DownloadTask();
        task.setStreamId(streamId);
        task.setDeviceId(req.getDeviceId());
        task.setChannelId(req.getChannelId());
        task.setStartTime(req.getStartTime());
        task.setEndTime(req.getEndTime());
        task.setStartEpochSec(startEpoch);
        task.setEndEpochSec(endEpoch);
        task.setTransport(transport);
        task.setSpeed(req.getSpeed() == null ? 1 : req.getSpeed());
        task.setStreamNumber(streamNumber);
        task.setMediaServer(server);
        task.setRtpPort(rtpPort);
        task.setSsrc(ssrc);
        task.setStatus(DownloadResultResponse.STATUS_INVITING);
        task.setCreatedAt(LocalDateTime.now());
        taskRegistry.put(task);

        try {
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            int speed = task.getSpeed();
            String sdpBody = SdpBuilder.buildDownload(sipProperties.getId(), sdpIp, rtpPort, ssrc,
                    transport, startEpoch, endEpoch, speed);
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getPlaybackTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, req.getChannelId(), sdpBody, ssrc, rtpPort, callIdHeader, fromTag,
                    okEv -> {
                        log.info("[{}] INVITE 2xx 到达 流ID:{}", LOG_TAG, streamId);
                    },
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        String reason = (errEv == null || errEv.getResponse() == null)
                                ? "" : errEv.getResponse().getReasonPhrase();
                        String detail = reason == null || reason.isBlank() ? "" : " " + reason;
                        log.warn("[{}] INVITE 拒绝 流ID:{} 状态码:{}{}", LOG_TAG, streamId, code, detail);
                        String failReason = "INVITE 失败 status=" + code + detail;
                        DownloadTask t = taskRegistry.get(streamId);
                        if (t != null) {
                            taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_FAILED, failReason);
                        }
                        inviteSessionService.removeByStreamId(streamId);
                        try {
                            zlmRestClient.closeRtpServer(server, streamId);
                        } catch (Exception ignore) { /* 已经在错误路径，吞 */ }
                        ssrcFactory.release(server.getServerId(), ssrc);
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_FAILED, "INVITE 异常: " + e.getMessage());
            inviteCleanup.failSession(session, LOG_TAG, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        log.info("[{}] 已发起下载 流ID:{} 倍速:{}x 时段:{} ~ {}",
                LOG_TAG, streamId, task.getSpeed(), startDt, endDt);
        return toResult(task);
    }

    @Override
    public DownloadResultResponse getStatus(String streamId) {
        DownloadTask t = taskRegistry.get(streamId);
        if (t == null) {
            DownloadResultResponse miss = new DownloadResultResponse();
            miss.setStreamId(streamId);
            miss.setStatus("NOT_FOUND");
            miss.setErrorMsg("任务不存在或已过期清理");
            return miss;
        }
        return toResult(t);
    }

    @Override
    public void stop(String streamId) {
        if (streamId == null) return;
        DownloadTask t = taskRegistry.get(streamId);
        if (t == null) {
            // 任务表在进程内，重启就丢；流可能还在，兜底关一下
            if (inviteCleanup.closeOrphanStream(streamId)) {
                log.warn("[{}] stop 流ID:{} 无任务但流还在，已兜底关掉", LOG_TAG, streamId);
            } else {
                log.info("[{}] stop 流ID:{} 无对应任务 原因:已结束或过期", LOG_TAG, streamId);
            }
            return;
        }
        // 优先 BYE（如果对端有 Dialog）
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session != null) {
            GbDevice device = deviceMapper.selectOne(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
            if (device != null && session.getCallId() != null && session.getFromTag() != null
                    && session.getToTag() != null) {
                sipCommander.sendBye(device, session);
            }
        }
        taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_FAILED, "用户主动停止");
        if (session != null) {
            inviteCleanup.cleanupZlm(session);
            inviteSessionService.removeByStreamId(streamId);
        } else if (t.getMediaServer() != null) {
            zlmRestClient.closeRtpServer(t.getMediaServer(), streamId);
            if (t.getSsrc() != null) {
                ssrcFactory.release(t.getMediaServer().getServerId(), t.getSsrc());
            }
        }
        log.info("[{}] stop 结果:成功 流ID:{}", LOG_TAG, streamId);
    }


    @Override
    public void onStreamReady(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) return;
        DownloadTask t = taskRegistry.get(stream);
        if (t == null) {
            log.warn("[{}] on_stream_changed 找不到任务 流ID:{}", LOG_TAG, stream);
            return;
        }
        if (DownloadResultResponse.STATUS_RECORDING.equals(t.getStatus())) {
            return; // 已开过录制
        }
        // 触发 ZLM MP4 录制：period = 起始时间的 yyyy-MM-dd；max_second = 时段长 + 60s 余量
        Long startEpoch = t.getStartEpochSec();
        Long endEpoch = t.getEndEpochSec();
        long durationSec = (startEpoch != null && endEpoch != null)
                ? (endEpoch - startEpoch) : 0L;
        int maxSecond = (int) Math.min(durationSec + 60, Integer.MAX_VALUE);
        // startEpoch 理论必填（start() 入口已写），但保留 null 兜底：取 today 避免 NPE
        LocalDate startDay = (startEpoch != null)
                ? Instant.ofEpochSecond(startEpoch).atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        String period = startDay.format(DateTimeFormatter.ISO_LOCAL_DATE);
        boolean ok = zlmRestClient.startRecord(t.getMediaServer(), APP, stream, period, maxSecond);
        if (!ok) {
            log.warn("[{}] ZLM startRecord 失败 流ID:{} 周期:{}", LOG_TAG, stream, period);
            taskRegistry.markStatus(stream, DownloadResultResponse.STATUS_FAILED, "ZLM 启动录制失败");
            cleanupForTask(t, false);
            return;
        }
        taskRegistry.markStatus(stream, DownloadResultResponse.STATUS_RECORDING, null);
        log.info("[{}] 流就绪，已开 MP4 录制 流ID:{} 周期:{} 最长:{}s",
                LOG_TAG, stream, period, maxSecond);
    }

    @Override
    public void onStreamStopped(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) return;
        DownloadTask t = taskRegistry.get(stream);
        if (t == null) return;
        // 流已断：通常是设备发完时间段 BYE 引起，ZLM 会完成本段 MP4 切片并触发 on_record_mp4 钩子
        // 这里只观察，不主动标 FAILED；让 onRecordMp4 自然走 READY；超时由 registry 兜底
        log.info("[{}] 流断开 流ID:{} 等待 on_record_mp4 回调", LOG_TAG, stream);
    }

    @Override
    public void onRecordMp4(String app, String stream, String url, long fileSize, double duration) {
        if (!APP.equals(app)) return;
        DownloadTask t = taskRegistry.get(stream);
        if (t == null) {
            log.warn("[{}] on_record_mp4 找不到任务 流ID:{} URL:{}", LOG_TAG, stream, url);
            return;
        }
        if (url == null || url.isBlank()) {
            log.warn("[{}] on_record_mp4 无 URL 流ID:{} 文件大小:{}B 时长:{}s",
                    LOG_TAG, stream, fileSize, duration);
            taskRegistry.markStatus(stream, DownloadResultResponse.STATUS_FAILED, "ZLM 未提供文件 URL");
            cleanupForTask(t, true);
            return;
        }
        if (DownloadResultResponse.STATUS_READY.equals(t.getStatus())) {
            return; // 已就绪
        }
        // 异步执行 HTTP 下载 + MinIO 上传，不阻塞 ZLM 回调线程（避免 ZLM 重试）
        gb28181BizExecutor.execute(() -> prepareDownload(t, url));
        log.info("[{}] on_record_mp4 触发下载 流ID:{} URL:{} 大小:{}B 时长:{}s",
                LOG_TAG, stream, url, fileSize, duration);
    }

    @Override
    public void onRtpServerTimeout(String streamId) {
        if (streamId == null || streamId.isBlank()) return;
        DownloadTask t = taskRegistry.get(streamId);
        if (t == null) return;
        log.warn("[{}] RTP 端口超时 流ID:{}", LOG_TAG, streamId);
        taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_FAILED, "RTP 端口长时间无数据");
        cleanupForTask(t, true);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) return;
        DownloadTask t = taskRegistry.get(session.getStreamId());
        if (t == null) return;
        // 设备主动 BYE：流已断，等待 ZLM 落 MP4 + on_record_mp4 钩子
        // 不立刻标 FAILED；只有超时或显式 stop 才标
        log.info("[{}] 设备 BYE 流ID:{} 等待 MP4 切片完成", LOG_TAG, session.getStreamId());
    }

    // 用事件让 Registry 通知这里收资源，免得 Service 和 Registry 互相注入。
    @org.springframework.context.event.EventListener
    public void onDownloadTimeout(com.solo.gb28181.support.DownloadTimeoutEvent event) {
        String streamId = event.getStreamId();
        if (streamId == null) return;
        DownloadTask t = taskRegistry.get(streamId);
        if (t == null) return;
        log.warn("[{}] 任务超时清扫 流ID:{}", LOG_TAG, streamId);
        // 资源已在 markStatus 时序里被标记；这里只做 ZLM + SSRC + 会话清理
        cleanupForTask(t, true);
    }


    private void prepareDownload(DownloadTask t, String zlmUrl) {
        String streamId = t.getStreamId();
        try {
            String serverId = t.getMediaServer() == null ? null : t.getMediaServer().getServerId();
            String signed = linkSigner.sign(serverId, zlmUrl, LINK_EXPIRY_SEC);
            t.setObjectName(zlmUrl);
            t.setDownloadUrl(signed);
            taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_READY, null);
            log.info("[{}] MP4 已就绪 流ID:{} 文件:{} 链接有效期:{}s",
                    LOG_TAG, streamId, zlmUrl, LINK_EXPIRY_SEC);
            cleanupForTask(t, true);
        } catch (Exception e) {
            log.error("[{}] 生成下载链接失败 流ID:{} URL:{} 错误:{}",
                    LOG_TAG, streamId, zlmUrl, e.getMessage(), e);
            taskRegistry.markStatus(streamId, DownloadResultResponse.STATUS_FAILED,
                    "生成下载链接失败: " + e.getMessage());
            cleanupForTask(t, true);
        }
    }

    private void cleanupForTask(DownloadTask t, boolean byeIfPossible) {
        if (t == null || t.getStreamId() == null) return;
        String streamId = t.getStreamId();
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null) {
            // 会话记录已无，直接按 task 残留信息清
            if (t.getMediaServer() != null) {
                zlmRestClient.closeRtpServer(t.getMediaServer(), streamId);
            }
            if (t.getSsrc() != null && t.getMediaServer() != null) {
                ssrcFactory.release(t.getMediaServer().getServerId(), t.getSsrc());
            }
            return;
        }
        if (byeIfPossible) {
            GbDevice device = deviceMapper.selectOne(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
            if (device != null && session.getCallId() != null && session.getToTag() != null
                    && session.getFromTag() != null) {
                sipCommander.sendBye(device, session);
            }
        }
        // 顺序：先关 ZLM 端口，再释放 SSRC，最后删会话
        inviteCleanup.cleanupZlm(session);
        inviteSessionService.removeByStreamId(streamId);
    }

    private static int transportToTcpMode(String transport) {
        if (SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport)) return 1;
        if (SdpBuilder.TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(transport)) return 2;
        return 0;
    }

    private static DownloadResultResponse toResult(DownloadTask t) {
        DownloadResultResponse vo = new DownloadResultResponse();
        vo.setStreamId(t.getStreamId());
        vo.setStatus(t.getStatus());
        vo.setStartEpochSec(t.getStartEpochSec());
        vo.setEndEpochSec(t.getEndEpochSec());
        if (t.getStartEpochSec() != null && t.getEndEpochSec() != null) {
            vo.setDurationSec(t.getEndEpochSec() - t.getStartEpochSec());
        }
        vo.setDownloadUrl(t.getDownloadUrl());
        vo.setErrorMsg(t.getErrorMsg());
        return vo;
    }
}
