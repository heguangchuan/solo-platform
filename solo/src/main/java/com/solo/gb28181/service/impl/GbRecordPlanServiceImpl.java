package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbRecordPlan;
import com.solo.gb28181.entity.GbRecordPlanPeriod;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbRecordPlanMapper;
import com.solo.gb28181.mapper.GbRecordPlanPeriodMapper;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbRecordPlanService;
import com.solo.gb28181.support.RecordPlanTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbRecordPlanServiceImpl extends ServiceImpl<GbRecordPlanMapper, GbRecordPlan>
        implements GbRecordPlanService {

    private final GbRecordPlanPeriodMapper periodMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbCloudRecordService cloudRecordService;

    @Override
    public List<GbRecordPlan> listPlans(String deviceId) {
        LambdaQueryWrapper<GbRecordPlan> w = new LambdaQueryWrapper<>();
        if (deviceId != null && !deviceId.isBlank()) {
            w.eq(GbRecordPlan::getDeviceId, deviceId);
        }
        w.orderByDesc(GbRecordPlan::getCreateTime);
        return this.list(w);
    }

    @Override
    public GbRecordPlan getDetail(Long id) {
        GbRecordPlan plan = this.getById(id);
        if (plan == null) {
            return null;
        }
        plan.setPeriods(listPeriods(id));
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePlan(GbRecordPlan plan) {
        validate(plan);
        if (plan.getEnabled() == null) {
            plan.setEnabled(1);
        }
        if (plan.getRetentionDays() == null) {
            plan.setRetentionDays(0);
        }
        this.save(plan);
        savePeriods(plan);
        log.info("[录像计划] 新增 id={} 设备={} 通道={} 时段数={}",
                plan.getId(), plan.getDeviceId(), plan.getChannelId(),
                plan.getPeriods() == null ? 0 : plan.getPeriods().size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(GbRecordPlan plan) {
        GbRecordPlan old = this.getById(plan.getId());
        if (old == null) {
            throw new ServiceException("录像计划不存在");
        }
        validate(plan);
        this.updateById(plan);
        // 时段整体覆盖：先删后插，避免逐行 diff
        periodMapper.delete(new LambdaQueryWrapper<GbRecordPlanPeriod>()
                .eq(GbRecordPlanPeriod::getPlanId, plan.getId()));
        savePeriods(plan);

        // 被改成停用，或设备/通道/码流换了（streamId 会变）：先把旧流停掉，下一轮对账按新配置拉起
        boolean disabled = plan.getEnabled() != null && plan.getEnabled() == 0;
        boolean targetChanged = !old.getDeviceId().equals(plan.getDeviceId())
                || !old.getChannelId().equals(plan.getChannelId())
                || !eq(old.getStreamNumber(), plan.getStreamNumber());
        if (disabled || targetChanged) {
            cloudRecordService.stopPlanRecord(old.getDeviceId(), old.getChannelId(), old.getStreamNumber());
        }
        log.info("[录像计划] 编辑 id={} 停用={} 目标变更={}", plan.getId(), disabled, targetChanged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long id) {
        GbRecordPlan plan = this.getById(id);
        if (plan == null) {
            return;
        }
        periodMapper.delete(new LambdaQueryWrapper<GbRecordPlanPeriod>()
                .eq(GbRecordPlanPeriod::getPlanId, plan.getId()));
        this.removeById(id);
        cloudRecordService.stopPlanRecord(plan.getDeviceId(), plan.getChannelId(), plan.getStreamNumber());
        log.info("[录像计划] 删除 id={} 设备={} 通道={}", id, plan.getDeviceId(), plan.getChannelId());
    }

    @Override
    public void toggle(Long id, boolean enabled) {
        GbRecordPlan plan = this.getById(id);
        if (plan == null) {
            throw new ServiceException("录像计划不存在");
        }
        GbRecordPlan update = new GbRecordPlan();
        update.setId(id);
        update.setEnabled(enabled ? 1 : 0);
        this.updateById(update);
        if (!enabled) {
            cloudRecordService.stopPlanRecord(plan.getDeviceId(), plan.getChannelId(), plan.getStreamNumber());
        }
        log.info("[录像计划] {} id={}", enabled ? "启用" : "停用", id);
    }

    // ============================================================
    //  内部
    // ============================================================

    private List<GbRecordPlanPeriod> listPeriods(Long planId) {
        return periodMapper.selectList(new LambdaQueryWrapper<GbRecordPlanPeriod>()
                .eq(GbRecordPlanPeriod::getPlanId, planId)
                .orderByAsc(GbRecordPlanPeriod::getDayOfWeek)
                .orderByAsc(GbRecordPlanPeriod::getStartTime));
    }

    private void savePeriods(GbRecordPlan plan) {
        List<GbRecordPlanPeriod> periods = plan.getPeriods();
        if (periods == null || periods.isEmpty()) {
            return;
        }
        for (GbRecordPlanPeriod p : periods) {
            if (p.getDayOfWeek() == null || p.getDayOfWeek() < 1 || p.getDayOfWeek() > 7) {
                throw new ServiceException("星期取值必须在 1~7（1周一 7周日）");
            }
            // 解析一次做格式校验；跨午夜（结束早于开始）允许，全天（相等）也允许
            RecordPlanTime.parseHm(p.getStartTime());
            RecordPlanTime.parseHm(p.getEndTime());
            p.setPlanId(plan.getId());
            periodMapper.insert(p);
        }
    }

    private void validate(GbRecordPlan plan) {
        if (plan == null || plan.getDeviceId() == null || plan.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, plan.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在: " + plan.getDeviceId());
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, plan.getDeviceId())
                        .eq(GbDeviceChannel::getChannelId, plan.getChannelId()));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + plan.getChannelId());
        }
    }

    private static boolean eq(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null || b.isBlank();
        }
        return a.equals(b);
    }
}
