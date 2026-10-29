package com.solo.gb28181.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbRecordPlan;
import com.solo.gb28181.entity.GbRecordPlanPeriod;
import com.solo.gb28181.mapper.GbRecordPlanMapper;
import com.solo.gb28181.mapper.GbRecordPlanPeriodMapper;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.support.RecordPlanTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 录像计划对账：每分钟把"此刻应该在录的计划"和"REC 前缀的在册录制会话"对一遍。
 *
 * 不依赖状态事件，只靠期望集收敛，所以计划增删改、断流后重新到点、服务重启这些情况都能兜住：
 * 接管下来的会话直接复用，被扫掉的当缺失重拉。每分钟一跳，整点边界最多差 1 分钟，录像无所谓。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordPlanReconciler {

    private static final String LOG_TAG = "录像计划对账";

    private final GbRecordPlanMapper planMapper;
    private final GbRecordPlanPeriodMapper periodMapper;
    private final GbInviteSessionService inviteSessionService;
    private final GbCloudRecordService cloudRecordService;
    private final GbInviteSessionCleanupService inviteCleanup;

    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        try {
            doReconcile(now);
        } catch (Exception e) {
            // 单条计划的异常在循环里已兜，这里只防查询级故障刷爆调度器
            log.error("[{}] 对账异常: {}", LOG_TAG, e.getMessage(), e);
        }
    }

    void doReconcile(LocalDateTime now) {
        List<GbRecordPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<GbRecordPlan>().eq(GbRecordPlan::getEnabled, 1));
        if (plans.isEmpty()) {
            return;
        }

        List<GbRecordPlanPeriod> allPeriods = periodMapper.selectList(null);
        Map<Long, List<GbRecordPlanPeriod>> periodsByPlan = new HashMap<>();
        for (GbRecordPlanPeriod p : allPeriods) {
            periodsByPlan.computeIfAbsent(p.getPlanId(), k -> new java.util.ArrayList<>()).add(p);
        }

        List<GbInviteSession> active = inviteSessionService.listAllActive().stream()
                .filter(s -> GbInviteSession.TYPE_RECORD.equals(s.getSessionType()))
                .filter(s -> s.getStreamId() != null
                        && s.getStreamId().startsWith(GbCloudRecordService.REC_PREFIX))
                .toList();
        Map<String, GbInviteSession> sessions = new HashMap<>();
        for (GbInviteSession s : active) {
            sessions.put(s.getStreamId(), s);
        }

        int started = 0;
        int stopped = 0;
        int swept = 0;
        int recovered = 0;
        LocalTime time = now.toLocalTime();
        int dayOfWeek = now.getDayOfWeek().getValue();

        for (GbRecordPlan plan : plans) {
            try {
                String streamId = GbCloudRecordService.planStreamId(
                        plan.getDeviceId(), plan.getChannelId(), plan.getStreamNumber());
                boolean shouldRecord = shouldRecordNow(periodsByPlan.get(plan.getId()), dayOfWeek, time);
                GbInviteSession session = sessions.get(streamId);

                if (shouldRecord) {
                    if (session == null) {
                        cloudRecordService.startPlan(plan.getDeviceId(), plan.getChannelId(),
                                plan.getTransport(), plan.getStreamNumber());
                        started++;
                    } else if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())) {
                        // 流断了但还在计划时段内：先把延迟清扫的旧会话收掉再重拉，
                        // 不然 openRtpServer 可能撞旧端口、SSRC 也占着
                        inviteCleanup.cleanupZlm(session);
                        inviteSessionService.removeByStreamId(streamId);
                        cloudRecordService.startPlan(plan.getDeviceId(), plan.getChannelId(),
                                plan.getTransport(), plan.getStreamNumber());
                        swept++;
                        started++;
                    } else if (GbInviteSession.STATUS_READY.equals(session.getStatus())) {
                        // READY 只代表平台侧开通过录制；外部手动 stopRecord / ZLM 丢录制态都无事件，
                        // 这里按 5 分钟节流查 ZLM 真实录制态，流在但没录就补发 startRecord
                        if (cloudRecordService.ensureRecording(session)) {
                            recovered++;
                        }
                    }
                    // INVITING：等流就绪 hook 起录，不动
                } else if (session != null) {
                    // 过了时段就停。STOPPED 的残留也顺手删掉，不必等延迟任务
                    cloudRecordService.stop(streamId);
                    stopped++;
                }
            } catch (Exception e) {
                // 设备离线、SIP 没起来等单条失败不影响其他计划，下分钟再试
                log.warn("[{}] 计划 {} 对账失败: {}", LOG_TAG, plan.getId(), e.getMessage());
            }
        }

        if (started + stopped + swept + recovered > 0) {
            log.info("[{}] 本轮完成 拉起={} 停止={} 断流重拉={} 录制补发={} 启用计划数={}",
                    LOG_TAG, started, stopped, swept, recovered, plans.size());
        }
    }

    /**
     * 此刻是否命中该计划的任一时段。periods 为空 = 计划没有时段明细，永不在录（而不是全天录），
     * 避免误配的空计划把通道 7x24 拉起来。
     */
    static boolean shouldRecordNow(List<GbRecordPlanPeriod> periods, int dayOfWeek, LocalTime now) {
        if (periods == null || periods.isEmpty()) {
            return false;
        }
        for (GbRecordPlanPeriod p : periods) {
            LocalTime start = RecordPlanTime.parseHm(p.getStartTime());
            LocalTime end = RecordPlanTime.parseHm(p.getEndTime());
            if (RecordPlanTime.contains(dayOfWeek, now, p.getDayOfWeek(), start, end)) {
                return true;
            }
        }
        return false;
    }
}
