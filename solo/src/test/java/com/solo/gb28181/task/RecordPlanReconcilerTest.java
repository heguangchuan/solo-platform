package com.solo.gb28181.task;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbRecordPlan;
import com.solo.gb28181.entity.GbRecordPlanPeriod;
import com.solo.gb28181.mapper.GbRecordPlanMapper;
import com.solo.gb28181.mapper.GbRecordPlanPeriodMapper;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账调度的纯 Mockito 用例，不起 Spring 容器。
 * 关键就四件事：到点没流就拉、到点流断了就清扫重拉、过点有流就停、空时段永不动。
 */
@ExtendWith(MockitoExtension.class)
class RecordPlanReconcilerTest {

    @Mock
    private GbRecordPlanMapper planMapper;
    @Mock
    private GbRecordPlanPeriodMapper periodMapper;
    @Mock
    private GbInviteSessionService inviteSessionService;
    @Mock
    private GbCloudRecordService cloudRecordService;
    @Mock
    private GbInviteSessionCleanupService inviteCleanup;

    @InjectMocks
    private RecordPlanReconciler reconciler;

    private static final String DEVICE = "34020000001320000001";
    private static final String CHANNEL = "34020000001310000001";
    // 2026-10-22 是周四
    private static final LocalDateTime THURSDAY_10 = LocalDateTime.of(2026, 10, 22, 10, 0);

    @BeforeEach
    void setUp() {
        // "没有启用计划"用例提前 return 不查会话，严格模式下这里得用 lenient
        lenient().when(inviteSessionService.listAllActive()).thenReturn(List.of());
    }

    private GbRecordPlan plan(long id, String streamNumber) {
        GbRecordPlan p = new GbRecordPlan();
        p.setId(id);
        p.setName("测试计划" + id);
        p.setDeviceId(DEVICE);
        p.setChannelId(CHANNEL);
        p.setEnabled(1);
        p.setTransport("UDP");
        p.setStreamNumber(streamNumber);
        return p;
    }

    private GbRecordPlanPeriod period(long planId, int dayOfWeek, String start, String end) {
        GbRecordPlanPeriod p = new GbRecordPlanPeriod();
        p.setPlanId(planId);
        p.setDayOfWeek(dayOfWeek);
        p.setStartTime(start);
        p.setEndTime(end);
        return p;
    }

    private GbInviteSession recordSession(String streamId, String status) {
        GbInviteSession s = new GbInviteSession();
        s.setStreamId(streamId);
        s.setSessionType(GbInviteSession.TYPE_RECORD);
        s.setStatus(status);
        return s;
    }

    @Test
    void startsPlanWhenInScheduleButNoSession() {
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 4, "09:00", "18:00")));

        reconciler.doReconcile(THURSDAY_10);

        verify(cloudRecordService).startPlan(DEVICE, CHANNEL, "UDP", null);
        verify(cloudRecordService, never()).stop(anyString());
    }

    @Test
    void doesNothingWhenInScheduleAndSessionReady() {
        String streamId = GbCloudRecordService.planStreamId(DEVICE, CHANNEL, null);
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 4, "09:00", "18:00")));
        when(inviteSessionService.listAllActive())
                .thenReturn(List.of(recordSession(streamId, GbInviteSession.STATUS_READY)));

        reconciler.doReconcile(THURSDAY_10);

        verify(cloudRecordService, never()).startPlan(anyString(), anyString(), any(), any());
        verify(cloudRecordService, never()).stop(anyString());
        verify(inviteCleanup, never()).cleanupZlm(any());
    }

    @Test
    void sweepsAndRestartsWhenInScheduleButSessionStopped() {
        String streamId = GbCloudRecordService.planStreamId(DEVICE, CHANNEL, null);
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 4, "09:00", "18:00")));
        when(inviteSessionService.listAllActive())
                .thenReturn(List.of(recordSession(streamId, GbInviteSession.STATUS_STOPPED)));

        reconciler.doReconcile(THURSDAY_10);

        // 旧会话占着 RTP 端口和 SSRC，必须先清再拉
        verify(inviteCleanup).cleanupZlm(any(GbInviteSession.class));
        verify(inviteSessionService).removeByStreamId(streamId);
        verify(cloudRecordService).startPlan(DEVICE, CHANNEL, "UDP", null);
    }

    @Test
    void stopsPlanStreamWhenOutOfSchedule() {
        String streamId = GbCloudRecordService.planStreamId(DEVICE, CHANNEL, null);
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        // 时段是周一，今天周四，不该在录
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 1, "00:00", "23:59")));
        when(inviteSessionService.listAllActive())
                .thenReturn(List.of(recordSession(streamId, GbInviteSession.STATUS_READY)));

        reconciler.doReconcile(THURSDAY_10);

        verify(cloudRecordService).stop(streamId);
        verify(cloudRecordService, never()).startPlan(anyString(), anyString(), any(), any());
    }

    @Test
    void neverStartsWithoutAnyPeriod() {
        // 空时段如果按"全天"处理，一条误配计划就能把通道 7x24 拉起来，必须反过来：不录
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of());

        reconciler.doReconcile(THURSDAY_10);

        verify(cloudRecordService, never()).startPlan(anyString(), anyString(), any(), any());
        verify(cloudRecordService, never()).stop(anyString());
    }

    @Test
    void crossMidnightPeriodStillMatchesOnEndDayDawn() {
        String streamId = GbCloudRecordService.planStreamId(DEVICE, CHANNEL, null);
        // 周三 23 点到周四 06 点，周四凌晨 1 点应在录
        LocalDateTime thursdayDawn = LocalDateTime.of(2026, 10, 22, 1, 0);
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 3, "23:00", "06:00")));
        when(inviteSessionService.listAllActive())
                .thenReturn(List.of(recordSession(streamId, GbInviteSession.STATUS_READY)));

        reconciler.doReconcile(thursdayDawn);

        verify(cloudRecordService, never()).stop(anyString());
        verify(cloudRecordService, never()).startPlan(anyString(), anyString(), any(), any());
    }

    @Test
    void manualRecordStreamIgnoredByReconcile() {
        // 手动流没有 REC_ 前缀：计划在时段内，对账也只该拉计划流，不能把它算进期望集去停
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, null)));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 4, "09:00", "18:00")));
        GbInviteSession manual = recordSession(DEVICE + "_" + CHANNEL, GbInviteSession.STATUS_READY);
        when(inviteSessionService.listAllActive()).thenReturn(List.of(manual));

        reconciler.doReconcile(THURSDAY_10);

        verify(cloudRecordService, never()).stop(anyString());
        // 到点没计划流，该拉还是得拉
        verify(cloudRecordService, times(1)).startPlan(eq(DEVICE), eq(CHANNEL), eq("UDP"), any());
    }

    @Test
    void onePlanFailureDoesNotBlockOthers() {
        GbRecordPlan p1 = plan(1, null);
        GbRecordPlan p2 = plan(2, "sub");
        when(planMapper.selectList(any())).thenReturn(List.of(p1, p2));
        when(periodMapper.selectList(any())).thenReturn(List.of(
                period(1, 4, "09:00", "18:00"),
                period(2, 4, "09:00", "18:00")));
        // 第一个计划设备离线，SIP 那侧直接抛
        org.mockito.Mockito.doThrow(new RuntimeException("设备离线"))
                .when(cloudRecordService).startPlan(DEVICE, CHANNEL, "UDP", null);

        reconciler.doReconcile(THURSDAY_10);

        // 第二个计划带子码流 token，照常被拉起
        verify(cloudRecordService).startPlan(DEVICE, CHANNEL, "UDP", "sub");
    }

    @Test
    void returnsEarlyWhenNoEnabledPlan() {
        when(planMapper.selectList(any())).thenReturn(List.of());

        reconciler.doReconcile(THURSDAY_10);

        verify(inviteSessionService, never()).listAllActive();
        verify(cloudRecordService, never()).startPlan(anyString(), anyString(), any(), any());
    }

    @Test
    void subStreamPlanGetsTokenInStreamId() {
        when(planMapper.selectList(any())).thenReturn(List.of(plan(1, "stream:sub")));
        when(periodMapper.selectList(any())).thenReturn(List.of(period(1, 4, "09:00", "18:00")));

        reconciler.doReconcile(THURSDAY_10);

        ArgumentCaptor<String> streamUsage = ArgumentCaptor.forClass(String.class);
        // startPlan 传原始 token，流名由编排服务统一生成；这里只保证 token 原样透传
        verify(cloudRecordService).startPlan(eq(DEVICE), eq(CHANNEL), eq("UDP"), streamUsage.capture());
        assertThat(streamUsage.getValue()).isEqualTo("stream:sub");
        assertThat(GbCloudRecordService.planStreamId(DEVICE, CHANNEL, "stream:sub"))
                .isEqualTo("REC_" + DEVICE + "_" + CHANNEL + "_stream_sub");
    }
}
