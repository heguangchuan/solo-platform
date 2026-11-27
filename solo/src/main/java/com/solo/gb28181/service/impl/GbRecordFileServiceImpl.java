package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.common.ServiceException;
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
import com.solo.gb28181.support.RecordFileLinkSigner;
import com.solo.gb28181.support.RecordStreamRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 切片由 ZLM 本地落盘，平台只登记文件位置并签发限时访问链接。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbRecordFileServiceImpl implements GbRecordFileService {

    private static final String LOG_TAG = "云端录像";
    private static final String APP = "rtp";
    private static final int LINK_EXPIRY_SEC = 3600;
    private static final int CLEAN_BATCH = 200;

    private final GbRecordFileMapper gbRecordFileMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbInviteSessionService inviteSessionService;
    private final RecordFileLinkSigner linkSigner;
    private final RecordStreamRegistry recordStreamRegistry;

    @Override
    public void onRecordMp4(String app, String stream, String url, String path, Long startTime,
                            long fileSize, double duration) {
        if (!APP.equals(app) || stream == null) {
            return;
        }
        RecordStreamRegistry.Context context = recordStreamRegistry.get(stream);
        if (context == null) {
            // 兼容升级前已经在录的流：它们还没写独立档案，活跃会话仍能补齐归属
            GbInviteSession session = inviteSessionService.getByStreamId(stream);
            if (session != null && GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
                context = new RecordStreamRegistry.Context(
                        session.getDeviceId(), session.getChannelId(), session.getServerId());
            }
        }
        if (context == null) {
            log.info("[{}] on_record_mp4 无录像档案，跳过 stream={}", LOG_TAG, stream);
            return;
        }

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
        boolean ready = url != null && !url.isBlank();
        GbRecordFile record = new GbRecordFile();
        record.setDeviceId(context.deviceId());
        record.setChannelId(context.channelId());
        record.setStreamId(stream);
        record.setApp(app);
        record.setServerId(context.serverId());
        record.setDuration(duration);
        record.setFileSize(fileSize > 0 ? fileSize : null);
        record.setStartTime(start);
        record.setEndTime(end);
        record.setStatus(ready ? GbRecordFile.STATUS_SUCCESS : GbRecordFile.STATUS_FAILED);
        record.setFailReason(ready ? null : "ZLM 回调缺少文件 URL");
        record.setRetryCount(0);
        // objectName 保留字段语义：这里存 ZLM 侧相对路径，用于签名访问
        record.setObjectName(url);
        record.setSourceUrl(url);
        record.setHookKey(path);
        record.setCreateTime(end);
        try {
            gbRecordFileMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("[{}] hook_key 唯一索引冲突，忽略 stream={} path={}", LOG_TAG, stream, path);
            return;
        }
        log.info("[{}] 登记切片 流ID:{} 设备:{} 通道:{} 时长:{}s 文件:{}",
                LOG_TAG, stream, context.deviceId(), context.channelId(), duration, url);
    }

    private LocalDateTime resolveStart(Long startUnixSeconds, double duration, LocalDateTime fallbackEnd) {
        if (startUnixSeconds != null && startUnixSeconds > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(startUnixSeconds), ZoneId.systemDefault());
        }
        return fallbackEnd.minusSeconds((long) duration);
    }

    @Override
    public int retryFailed() {
        // 不再有上传副本流程；失败切片只保留记录供排查，不自动重试。
        return 0;
    }

    @Override
    public int cleanExpired(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = 0;
        while (true) {
            List<GbRecordFile> batch = gbRecordFileMapper.selectList(new LambdaQueryWrapper<GbRecordFile>()
                    .eq(GbRecordFile::getStatus, GbRecordFile.STATUS_SUCCESS)
                    .lt(GbRecordFile::getCreateTime, threshold)
                    .last("limit " + CLEAN_BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (GbRecordFile r : batch) {
                deleteLocalQuietly(r);
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
        deleteLocalQuietly(r);
        gbRecordFileMapper.deleteById(id);
        log.info("[{}] 删除录像 id={} 文件={}", LOG_TAG, id, r.getObjectName());
    }

    /** ZLM 本地文件删除尽力而为：多节点部署时平台主机未必挂载了该文件。 */
    private void deleteLocalQuietly(GbRecordFile r) {
        String localPath = r.getHookKey();
        if (localPath == null || localPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(localPath));
        } catch (Exception e) {
            log.warn("[{}] ZLM 本地文件删除失败 id={} path={} err={}",
                    LOG_TAG, r.getId(), localPath, e.getMessage());
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
            throw new ServiceException("录像文件未就绪");
        }
        return "/api/record-file/" + linkSigner.sign(r.getServerId(), r.getObjectName(), LINK_EXPIRY_SEC);
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
        return vo;
    }
}
