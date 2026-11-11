package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.media.entity.GbViewRecord;
import com.solo.media.mapper.GbViewRecordMapper;
import com.solo.media.request.GbViewRecordPageRequest;
import com.solo.media.response.GbViewRecordVO;
import com.solo.media.service.GbViewRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 观看记录落库与闭合。
 *
 * 幂等：ZLM 可能重推 on_play，以播放会话 id 为唯一键，先查后插 + 唯一索引双保险。
 * 时长：优先用 on_flow_report 给的 duration，并与服务端按 start_time 估算的值对拍，偏差大记日志；
 * 兜底闭合没有 duration，就用估算值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbViewRecordServiceImpl implements GbViewRecordService {

    private static final String LOG_TAG = "观看记录";
    /** 清扫单轮上限，同一条流的观看者通常远不到这个量，主要防异常积压一轮刷太多 */
    private static final int SWEEP_BATCH = 200;
    /** ZLM duration 与服务端估算偏差超过该秒数时记 warn，便于发现时钟/回调异常 */
    private static final double DURATION_DRIFT_SECONDS = 30;

    private final GbViewRecordMapper viewRecordMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;

    @Override
    public void openRecord(GbViewRecord record) {
        if (record == null || isBlank(record.getSessionId()) || isBlank(record.getStreamId())) {
            return;
        }
        Long exists = viewRecordMapper.selectCount(new LambdaQueryWrapper<GbViewRecord>()
                .eq(GbViewRecord::getSessionId, record.getSessionId()));
        if (exists != null && exists > 0) {
            log.info("[{}] 会话已登记，忽略重复 on_play sessionId={}", LOG_TAG, record.getSessionId());
            return;
        }
        record.setId(null);
        record.setStatus(GbViewRecord.STATUS_OPEN);
        if (record.getStartTime() == null) {
            record.setStartTime(LocalDateTime.now());
        }
        try {
            viewRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发重推时查重窗口撞车，按幂等命中处理
            log.info("[{}] 会话唯一索引冲突，忽略 sessionId={}", LOG_TAG, record.getSessionId());
            return;
        }
        log.info("[{}] 开始记录 sessionId={} 流ID:{} 观看者:{}#{}",
                LOG_TAG, record.getSessionId(), record.getStreamId(),
                record.getActorType(), record.getActorId());
    }

    @Override
    public void closeBySessionId(String sessionId, Double durationSec, Long totalBytes, String reason) {
        if (isBlank(sessionId)) {
            return;
        }
        GbViewRecord record = viewRecordMapper.selectOne(new LambdaQueryWrapper<GbViewRecord>()
                .eq(GbViewRecord::getSessionId, sessionId));
        if (record == null) {
            // 没落到 on_play（重启丢 hook / 推流侧事件 / 非本平台签发的播放），无记录可关
            log.debug("[{}] 闭合时找不到会话 sessionId={} reason={}", LOG_TAG, sessionId, reason);
            return;
        }
        if (!Integer.valueOf(GbViewRecord.STATUS_OPEN).equals(record.getStatus())) {
            log.info("[{}] 会话已闭合，忽略重复回调 sessionId={} status={}",
                    LOG_TAG, sessionId, record.getStatus());
            return;
        }
        doClose(record, durationSec, totalBytes, reason);
    }

    @Override
    public int closeOpenByStreamId(String streamId, String reason) {
        if (isBlank(streamId)) {
            return 0;
        }
        List<GbViewRecord> openList = viewRecordMapper.selectList(new LambdaQueryWrapper<GbViewRecord>()
                .eq(GbViewRecord::getStreamId, streamId)
                .eq(GbViewRecord::getStatus, GbViewRecord.STATUS_OPEN));
        for (GbViewRecord record : openList) {
            doClose(record, null, null, reason);
        }
        if (!openList.isEmpty()) {
            log.info("[{}] 流级兜底闭合 流ID:{} 条数:{} reason={}", LOG_TAG, streamId, openList.size(), reason);
        }
        return openList.size();
    }

    @Override
    public int sweepOpenRecords(int staleMinutes) {
        if (staleMinutes <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleMinutes);
        List<GbViewRecord> stale = viewRecordMapper.selectList(new LambdaQueryWrapper<GbViewRecord>()
                .eq(GbViewRecord::getStatus, GbViewRecord.STATUS_OPEN)
                .lt(GbViewRecord::getStartTime, threshold)
                .last("limit " + SWEEP_BATCH));
        for (GbViewRecord record : stale) {
            doClose(record, null, null, REASON_SWEEP);
        }
        if (!stale.isEmpty()) {
            log.warn("[{}] 清扫僵死观看记录 条数:{} staleMinutes={}", LOG_TAG, stale.size(), staleMinutes);
        }
        return stale.size();
    }

    private void doClose(GbViewRecord record, Double zlmDuration, Long totalBytes, String reason) {
        LocalDateTime end = LocalDateTime.now();
        double estimated = Duration.between(record.getStartTime(), end).toMillis() / 1000.0;
        double duration = zlmDuration != null && zlmDuration > 0 ? zlmDuration : Math.max(0, estimated);
        if (zlmDuration != null && zlmDuration > 0 && Math.abs(estimated - zlmDuration) > DURATION_DRIFT_SECONDS) {
            log.warn("[{}] 时长偏差较大 sessionId={} zlm={}s 估算={}s",
                    LOG_TAG, record.getSessionId(), zlmDuration, estimated);
        }

        GbViewRecord update = new GbViewRecord();
        update.setId(record.getId());
        update.setEndTime(end);
        update.setDurationSec(duration);
        update.setTotalBytes(totalBytes);
        update.setCloseReason(reason);
        // 只有 flow_report 算正常结束，其余都是没等到回调的兜底
        update.setStatus(REASON_FLOW_REPORT.equals(reason)
                ? GbViewRecord.STATUS_CLOSED : GbViewRecord.STATUS_FALLBACK_CLOSED);
        viewRecordMapper.updateById(update);
        log.info("[{}] 闭合记录 sessionId={} 流ID:{} 时长:{}s reason={}",
                LOG_TAG, record.getSessionId(), record.getStreamId(), duration, reason);
    }

    @Override
    public PageResult<GbViewRecordVO> page(GbViewRecordPageRequest q) {
        int pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        int size = q.getSize() == null || q.getSize() < 1 ? 20 : Math.min(q.getSize(), 200);
        LambdaQueryWrapper<GbViewRecord> w = new LambdaQueryWrapper<>();
        if (q.getActorType() != null) {
            w.eq(GbViewRecord::getActorType, q.getActorType());
        }
        if (q.getActorId() != null) {
            w.eq(GbViewRecord::getActorId, q.getActorId());
        }
        if (!isBlank(q.getDeviceId())) {
            w.eq(GbViewRecord::getDeviceId, q.getDeviceId());
        }
        if (!isBlank(q.getChannelId())) {
            w.eq(GbViewRecord::getChannelId, q.getChannelId());
        }
        if (q.getStatus() != null) {
            w.eq(GbViewRecord::getStatus, q.getStatus());
        }
        if (q.getStart() != null) {
            w.ge(GbViewRecord::getStartTime, q.getStart());
        }
        if (q.getEnd() != null) {
            w.le(GbViewRecord::getStartTime, q.getEnd());
        }
        w.orderByDesc(GbViewRecord::getStartTime);
        IPage<GbViewRecord> result = viewRecordMapper.selectPage(new Page<>(pageNo, size), w);
        List<GbViewRecord> records = result.getRecords();

        Map<String, String> deviceNameMap = Map.of();
        Map<String, String> channelNameMap = Map.of();
        if (!records.isEmpty()) {
            Set<String> deviceIds = records.stream()
                    .map(GbViewRecord::getDeviceId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> channelIds = records.stream()
                    .map(GbViewRecord::getChannelId).filter(Objects::nonNull).collect(Collectors.toSet());
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
        List<GbViewRecordVO> rows = records.stream()
                .map(r -> toVo(r, finalDeviceNameMap, finalChannelNameMap))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), rows);
    }

    private GbViewRecordVO toVo(GbViewRecord r, Map<String, String> deviceNameMap,
                                Map<String, String> channelNameMap) {
        GbViewRecordVO vo = new GbViewRecordVO();
        vo.setId(r.getId());
        vo.setSessionId(r.getSessionId());
        vo.setActorType(r.getActorType());
        vo.setActorId(r.getActorId());
        vo.setActorName(r.getActorName());
        vo.setDeviceId(r.getDeviceId());
        vo.setDeviceName(deviceNameMap.get(r.getDeviceId()));
        vo.setChannelId(r.getChannelId());
        vo.setChannelName(channelNameMap.get(r.getDeviceId() + "|" + r.getChannelId()));
        vo.setStreamId(r.getStreamId());
        vo.setApp(r.getApp());
        vo.setStreamSchema(r.getStreamSchema());
        vo.setClientIp(r.getClientIp());
        vo.setMediaServerId(r.getMediaServerId());
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setDurationSec(r.getDurationSec());
        vo.setTotalBytes(r.getTotalBytes());
        vo.setStatus(r.getStatus());
        vo.setCloseReason(r.getCloseReason());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
