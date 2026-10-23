package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.common.ServiceException;
import com.solo.common.storage.FileStorage;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbRecordFile;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbRecordFileMapper;
import com.solo.gb28181.response.GbRecordFileVO;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbRecordFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 云端录像中心：on_record_mp4 回调进来后登记切片元数据，异步把 MP4 拉到对象存储。
 *
 * <h3>状态机</h3>
 * <p>落库即 UPLOADING；异步任务传完翻 SUCCESS，失败翻 FAILED 并累计 retryCount。
 * FAILED / 僵死的 UPLOADING 由定时任务 {@link #retryFailed} 重新领取，超过配置次数不再重试。</p>
 *
 * <h3>幂等</h3>
 * <p>ZLM 可能因收不到 hook 响应重推同一个切片，以回调里的文件 path 作为 hook_key，
 * 落库前查一次 + 数据库 uk_hook_key 双保险。path 为空（老版本 ZLM）就退化为不幂等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbRecordFileServiceImpl implements GbRecordFileService {

    private static final String LOG_TAG = "云端录像";
    private static final String APP = "rtp";
    private static final String OBJECT_PREFIX = "gb28181/record/";
    private static final int PRESIGNED_EXPIRY_SEC = 3600;
    /** 重试任务一轮最多领多少条，避免单轮把业务线程池占满 */
    private static final int RETRY_BATCH = 50;
    /** 清理任务一轮删多少条 */
    private static final int CLEAN_BATCH = 200;

    private final GbRecordFileMapper gbRecordFileMapper;
    private final GbMediaServerMapper mediaServerMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbInviteSessionService inviteSessionService;
    private final FileStorage fileStorage;
    private final ThreadPoolExecutor gb28181BizExecutor;
    private final Gb28181Properties gb28181Properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onRecordMp4(String app, String stream, String url, String path, Long startTime,
                            long fileSize, double duration) {
        if (!APP.equals(app) || stream == null) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null || !GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            log.info("[{}] on_record_mp4 非录制流/无 session，跳过 stream={}", LOG_TAG, stream);
            return;
        }

        // hook 幂等：ZLM 重推同一切片时 path 不变
        if (path != null && !path.isBlank()) {
            Long exists = gbRecordFileMapper.selectCount(new LambdaQueryWrapper<GbRecordFile>()
                    .eq(GbRecordFile::getHookKey, path));
            if (exists != null && exists > 0) {
                log.info("[{}] 切片已登记，忽略重复回调 stream={} path={}", LOG_TAG, stream, path);
                return;
            }
        }

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStart(startTime, duration, end);
        GbRecordFile record = new GbRecordFile();
        record.setDeviceId(session.getDeviceId());
        record.setChannelId(session.getChannelId());
        record.setStreamId(stream);
        record.setApp(app);
        record.setServerId(session.getServerId());
        record.setDuration(duration);
        record.setFileSize(fileSize > 0 ? fileSize : null);
        record.setStartTime(start);
        record.setEndTime(end);
        record.setStatus(GbRecordFile.STATUS_UPLOADING);
        record.setRetryCount(0);
        record.setSourceUrl(url);
        record.setHookKey(path);
        record.setCreateTime(end);
        try {
            gbRecordFileMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重推/查重的时间窗内撞唯一索引，当作幂等命中
            log.info("[{}] hook_key 唯一索引冲突，忽略 stream={} path={}", LOG_TAG, stream, path);
            return;
        }
        log.info("[{}] 登记切片 流ID:{} 设备:{} 通道:{} 时长:{}s 起:{}",
                LOG_TAG, stream, session.getDeviceId(), session.getChannelId(), duration, start);

        if (url == null || url.isBlank()) {
            markFailed(record.getId(), 0, "ZLM 回调缺少文件 URL");
            return;
        }
        // 异步下载+上传，不阻塞 ZLM 回调线程（慢了会被 ZLM 当失败重推）
        String serverId = session.getServerId();
        gb28181BizExecutor.execute(() -> downloadAndUpload(record.getId(), stream, serverId, url, 0));
    }

    private LocalDateTime resolveStart(Long startUnixSeconds, double duration, LocalDateTime fallbackEnd) {
        if (startUnixSeconds != null && startUnixSeconds > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(startUnixSeconds), ZoneId.systemDefault());
        }
        // 老版本 ZLM 不带 start_time，退回"回调时刻 - 时长"估算，误差就是回调延迟
        return fallbackEnd.minusSeconds((long) duration);
    }

    private void downloadAndUpload(Long id, String streamId, String serverId, String zlmUrl, int attemptRetryCount) {
        Path tmp = null;
        try {
            // hook 给的 url 是相对路径，按产生该切片的节点补成绝对地址；库里存相对路径，重试时重新解析
            MediaServer server = mediaServerMapper.selectOne(new LambdaQueryWrapper<MediaServer>()
                    .eq(MediaServer::getServerId, serverId));
            if (server == null) {
                throw new IllegalStateException("ZLM 节点未登记: " + serverId);
            }
            String absoluteUrl = ZlmRestClient.resolveDownloadUrl(server, zlmUrl);
            HttpRequest req = HttpRequest.newBuilder(URI.create(absoluteUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("ZLM 文件下载状态码 " + resp.statusCode());
            }
            tmp = Files.createTempFile("gb-rec-", ".mp4");
            try (InputStream in = resp.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            long realSize = Files.size(tmp);
            String objectName = OBJECT_PREFIX + streamId + "_" + System.currentTimeMillis() + ".mp4";
            try (InputStream in = Files.newInputStream(tmp)) {
                fileStorage.upload(in, realSize, "video/mp4", objectName);
            }

            GbRecordFile update = new GbRecordFile();
            update.setId(id);
            update.setObjectName(objectName);
            update.setFileSize(realSize);
            update.setStatus(GbRecordFile.STATUS_SUCCESS);
            update.setFailReason(null);
            gbRecordFileMapper.updateById(update);
            log.info("[{}] MP4 已落存储 流ID:{} 对象:{} 大小:{}B", LOG_TAG, streamId, objectName, realSize);
        } catch (Exception e) {
            log.warn("[{}] 下载/上传失败 流ID:{} URL:{} 错误:{}", LOG_TAG, streamId, zlmUrl, e.getMessage());
            markFailed(id, attemptRetryCount, e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignore) {
                    // best effort
                }
            }
        }
    }

    private void markFailed(Long id, int retryCount, String reason) {
        GbRecordFile update = new GbRecordFile();
        update.setId(id);
        update.setStatus(GbRecordFile.STATUS_FAILED);
        update.setRetryCount(retryCount);
        update.setFailReason(truncate(reason, 500));
        gbRecordFileMapper.updateById(update);
    }

    @Override
    public int retryFailed() {
        int maxRetry = gb28181Properties.getRecord().getMaxRetry();
        int stuckMinutes = gb28181Properties.getRecord().getUploadingStuckMinutes();
        LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(stuckMinutes);

        // FAILED 且没超重试上限，或 UPLOADING 卡太久（进程崩在异步任务里，状态没来得及翻）
        List<GbRecordFile> pending = gbRecordFileMapper.selectList(new LambdaQueryWrapper<GbRecordFile>()
                .and(w -> w.eq(GbRecordFile::getStatus, GbRecordFile.STATUS_FAILED)
                        .lt(GbRecordFile::getRetryCount, maxRetry)
                        .or(o -> o.eq(GbRecordFile::getStatus, GbRecordFile.STATUS_UPLOADING)
                                .lt(GbRecordFile::getCreateTime, stuckBefore)))
                .isNotNull(GbRecordFile::getSourceUrl)
                .last("limit " + RETRY_BATCH));

        for (GbRecordFile r : pending) {
            // 领取时先把 retryCount 加一：多实例部署时两个实例不会把同一条领重（乐观意义有限，
            // 主要防本实例上一轮的重试任务和下一轮撞车）
            int nextRetry = (r.getRetryCount() == null ? 0 : r.getRetryCount()) + 1;
            GbRecordFile claim = new GbRecordFile();
            claim.setId(r.getId());
            claim.setStatus(GbRecordFile.STATUS_UPLOADING);
            claim.setRetryCount(nextRetry);
            gbRecordFileMapper.updateById(claim);
            try {
                gb28181BizExecutor.execute(
                        () -> downloadAndUpload(r.getId(), r.getStreamId(), r.getServerId(), r.getSourceUrl(), nextRetry));
            } catch (Exception e) {
                // 线程池满（CallerRunsPolicy 下其实抛不出来）：回滚状态下轮再领
                markFailed(r.getId(), nextRetry - 1, "重试任务提交失败: " + e.getMessage());
            }
        }
        return pending.size();
    }

    @Override
    public int cleanExpired(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = 0;
        // 只清成功切片；FAILED/UPLOADING 可能还要重试或排查，保留期任务不动它们
        while (true) {
            List<GbRecordFile> batch = gbRecordFileMapper.selectList(new LambdaQueryWrapper<GbRecordFile>()
                    .eq(GbRecordFile::getStatus, GbRecordFile.STATUS_SUCCESS)
                    .lt(GbRecordFile::getCreateTime, threshold)
                    .last("limit " + CLEAN_BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (GbRecordFile r : batch) {
                deleteStorageQuietly(r);
                gbRecordFileMapper.deleteById(r.getId());
                deleted++;
            }
            if (batch.size() < CLEAN_BATCH) {
                break;
            }
        }
        return deleted;
    }

    @Override
    public void delete(Long id) {
        GbRecordFile r = gbRecordFileMapper.selectById(id);
        if (r == null) {
            return;
        }
        if (r.getObjectName() != null) {
            try {
                fileStorage.delete(r.getObjectName());
            } catch (Exception e) {
                // S3 没删掉就保留元数据，否则记录没了对象成了无法清理的孤儿；用户可重新点删除
                log.warn("[{}] 存储对象删除失败，保留录像记录 id={} 对象={} err={}",
                        LOG_TAG, id, r.getObjectName(), e.getMessage());
                throw new ServiceException("存储对象删除失败，录像记录已保留，请重试");
            }
        }
        gbRecordFileMapper.deleteById(id);
        log.info("[{}] 删除录像 id={} 对象={}", LOG_TAG, id, r.getObjectName());
    }

    private void deleteStorageQuietly(GbRecordFile r) {
        if (r.getObjectName() == null) {
            return;
        }
        try {
            fileStorage.delete(r.getObjectName());
        } catch (Exception e) {
            // 对象没删掉不拦元数据删除，否则这行会一直删不掉；丢对象的脏数据靠存储侧生命周期兜底
            log.warn("[{}] 存储对象删除失败 id={} 对象={} err={}",
                    LOG_TAG, r.getId(), r.getObjectName(), e.getMessage());
        }
    }

    @Override
    public PageResult<GbRecordFileVO> page(String deviceId, String channelId,
                                           LocalDateTime start, LocalDateTime end, Integer status,
                                           int page, int size) {
        Page<GbRecordFile> p = new Page<>(page, size);
        LambdaQueryWrapper<GbRecordFile> w = new LambdaQueryWrapper<>();
        if (deviceId != null && !deviceId.isBlank()) {
            w.eq(GbRecordFile::getDeviceId, deviceId);
        }
        if (channelId != null && !channelId.isBlank()) {
            w.eq(GbRecordFile::getChannelId, channelId);
        }
        if (status != null) {
            w.eq(GbRecordFile::getStatus, status);
        }
        if (start != null) {
            w.ge(GbRecordFile::getStartTime, start);
        }
        if (end != null) {
            w.le(GbRecordFile::getStartTime, end);
        }
        w.orderByDesc(GbRecordFile::getStartTime);
        IPage<GbRecordFile> result = gbRecordFileMapper.selectPage(p, w);
        Map<String, String> deviceNameMap = Map.of();
        Map<String, String> channelNameMap = Map.of();
        List<GbRecordFile> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> deviceIds = records.stream()
                    .map(GbRecordFile::getDeviceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> channelIds = records.stream()
                    .map(GbRecordFile::getChannelId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!deviceIds.isEmpty()) {
                deviceNameMap = deviceMapper
                        .selectList(new LambdaQueryWrapper<GbDevice>().in(GbDevice::getDeviceId, deviceIds))
                        .stream()
                        .collect(Collectors.toMap(GbDevice::getDeviceId, GbDevice::getName, (a, b) -> a));
            }
            if (!deviceIds.isEmpty() && !channelIds.isEmpty()) {
                channelNameMap = channelMapper
                        .selectList(new LambdaQueryWrapper<GbDeviceChannel>()
                                .in(GbDeviceChannel::getDeviceId, deviceIds)
                                .in(GbDeviceChannel::getChannelId, channelIds))
                        .stream()
                        .collect(Collectors.toMap(
                                c -> c.getDeviceId() + "|" + c.getChannelId(),
                                GbDeviceChannel::getName, (a, b) -> a));
            }
        }
        Map<String, String> finalDeviceNameMap = deviceNameMap;
        Map<String, String> finalChannelNameMap = channelNameMap;
        List<GbRecordFileVO> rows = records.stream()
                .map(r -> toVo(r, finalDeviceNameMap, finalChannelNameMap))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), rows);
    }

    @Override
    public String presignedUrl(Long id) {
        GbRecordFile r = gbRecordFileMapper.selectById(id);
        if (r == null || r.getObjectName() == null) {
            throw new ServiceException("录像不存在或文件未就绪");
        }
        if (!Integer.valueOf(GbRecordFile.STATUS_SUCCESS).equals(r.getStatus())) {
            throw new ServiceException("录像文件未上传成功");
        }
        return fileStorage.presignedUrl(r.getObjectName(), PRESIGNED_EXPIRY_SEC);
    }

    private GbRecordFileVO toVo(GbRecordFile r, Map<String, String> deviceNameMap,
                                Map<String, String> channelNameMap) {
        GbRecordFileVO vo = new GbRecordFileVO();
        vo.setId(r.getId());
        vo.setDeviceId(r.getDeviceId());
        vo.setDeviceName(deviceNameMap.get(r.getDeviceId()));
        vo.setChannelId(r.getChannelId());
        vo.setChannelName(channelNameMap.get(r.getDeviceId() + "|" + r.getChannelId()));
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setDuration(r.getDuration());
        vo.setFileSize(r.getFileSize());
        vo.setStatus(r.getStatus());
        vo.setCreateTime(r.getCreateTime());
        // URL 按需签：列表一页 20 行每行签一次是纯浪费，播放时走 /{id}/url
        return vo;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
