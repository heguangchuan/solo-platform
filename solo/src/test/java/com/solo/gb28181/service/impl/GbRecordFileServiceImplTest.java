package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.common.storage.FileStorage;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbRecordFile;
import com.solo.gb28181.mapper.GbRecordFileMapper;
import com.solo.gb28181.service.GbInviteSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 录像文件服务的纯 Mockito 用例。下载/上传那条真实 HTTP 链路不测（本机没有 ZLM），
 * 重点守住三件事：回调幂等、状态机别乱翻、重试/清理的领取条件。
 * properties 直接用真实对象，默认 maxRetry=3、僵死 30 分钟、保留 30 天。
 */
@ExtendWith(MockitoExtension.class)
class GbRecordFileServiceImplTest {

    @Mock
    private GbRecordFileMapper recordFileMapper;
    @Mock
    private GbInviteSessionService inviteSessionService;
    @Mock
    private FileStorage fileStorage;
    // execute 不打桩，默认空转：只验证任务被提交，不真的去下载
    @Mock
    private ThreadPoolExecutor bizExecutor;

    private GbRecordFileServiceImpl recordFileService;

    private static final String STREAM = "REC_34020000001320000001_34020000001310000001";
    private static final String DEVICE = "34020000001320000001";
    private static final String CHANNEL = "34020000001310000001";

    @BeforeEach
    void setUp() {
        // properties 用真实默认值，所以不走 @InjectMocks，手动 new
        recordFileService = new GbRecordFileServiceImpl(
                recordFileMapper, inviteSessionService, fileStorage,
                bizExecutor, new Gb28181Properties());
    }

    private GbInviteSession recordSession() {
        GbInviteSession s = new GbInviteSession();
        s.setStreamId(STREAM);
        s.setDeviceId(DEVICE);
        s.setChannelId(CHANNEL);
        s.setServerId("zlm-node-1");
        s.setSessionType(GbInviteSession.TYPE_RECORD);
        return s;
    }

    private void stubRecordSession() {
        when(inviteSessionService.getByStreamId(STREAM)).thenReturn(recordSession());
    }

    // ---------- onRecordMp4 ----------

    @Test
    void ignoresHookWhenAppIsNotRtp() {
        recordFileService.onRecordMp4("proxy", STREAM, "http://x/a.mp4", "/a.mp4", null, 100, 10);

        verifyNoInteractions(inviteSessionService, recordFileMapper, bizExecutor);
    }

    @Test
    void ignoresHookWhenStreamMissing() {
        recordFileService.onRecordMp4("rtp", null, "http://x/a.mp4", "/a.mp4", null, 100, 10);

        verifyNoInteractions(inviteSessionService, recordFileMapper, bizExecutor);
    }

    @Test
    void ignoresHookWhenSessionNotFound() {
        when(inviteSessionService.getByStreamId(STREAM)).thenReturn(null);

        recordFileService.onRecordMp4("rtp", STREAM, "http://x/a.mp4", "/a.mp4", null, 100, 10);

        verify(recordFileMapper, never()).insert(any(GbRecordFile.class));
        verify(bizExecutor, never()).execute(any());
    }

    @Test
    void ignoresHookWhenSessionIsNotRecordType() {
        GbInviteSession play = recordSession();
        play.setSessionType(GbInviteSession.TYPE_PLAY);
        when(inviteSessionService.getByStreamId(STREAM)).thenReturn(play);

        recordFileService.onRecordMp4("rtp", STREAM, "http://x/a.mp4", "/a.mp4", null, 100, 10);

        verify(recordFileMapper, never()).insert(any(GbRecordFile.class));
    }

    @Test
    void skipsDuplicateHookForSameFilePath() {
        stubRecordSession();
        when(recordFileMapper.selectCount(any())).thenReturn(1L);

        recordFileService.onRecordMp4("rtp", STREAM, "http://x/a.mp4", "/record/a.mp4", null, 100, 10);

        verify(recordFileMapper, never()).insert(any(GbRecordFile.class));
        verify(bizExecutor, never()).execute(any());
    }

    @Test
    void treatsUniqueKeyViolationAsDuplicateHook() {
        stubRecordSession();
        when(recordFileMapper.selectCount(any())).thenReturn(0L);
        when(recordFileMapper.insert(any(GbRecordFile.class)))
                .thenThrow(new DuplicateKeyException("uk_hook_key"));

        recordFileService.onRecordMp4("rtp", STREAM, "http://x/a.mp4", "/record/a.mp4", null, 100, 10);

        verify(bizExecutor, never()).execute(any());
    }

    @Test
    void insertsUploadingRowAndSubmitsUploadTask() {
        stubRecordSession();
        when(recordFileMapper.selectCount(any())).thenReturn(0L);

        // 1761067800 = 2025-10-22 01:30:00 +08
        recordFileService.onRecordMp4("rtp", STREAM, "http://zlm/a.mp4", "/record/a.mp4",
                1761067800L, 2048L, 60);

        ArgumentCaptor<GbRecordFile> row = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper).insert(row.capture());
        GbRecordFile saved = row.getValue();
        assertThat(saved.getStatus()).isEqualTo(GbRecordFile.STATUS_UPLOADING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getHookKey()).isEqualTo("/record/a.mp4");
        assertThat(saved.getFileSize()).isEqualTo(2048L);
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE);
        assertThat(saved.getStartTime()).isEqualTo(LocalDateTime.of(2025, 10, 22, 1, 30));
        verify(bizExecutor).execute(any(Runnable.class));
    }

    @Test
    void estimatesStartTimeFromDurationWhenAbsent() {
        stubRecordSession();
        // 没传 path：老版本 ZLM 退化路径，压根不查幂等表，别给 selectCount 打桩

        recordFileService.onRecordMp4("rtp", STREAM, "http://zlm/a.mp4", null, null, 0, 90);

        ArgumentCaptor<GbRecordFile> row = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper).insert(row.capture());
        GbRecordFile saved = row.getValue();
        // 回调到 insert 在同一秒里，估出来的开始时间至少不能晚于结束时间
        assertThat(saved.getStartTime()).isNotNull();
        assertThat(saved.getEndTime()).isNotNull();
        assertThat(saved.getStartTime()).isBeforeOrEqualTo(saved.getEndTime());
        // fileSize<=0 存 null，别存个 0 让前端展示成 0B
        assertThat(saved.getFileSize()).isNull();
    }

    @Test
    void marksFailedWithoutTaskWhenUrlMissing() {
        stubRecordSession();
        when(recordFileMapper.selectCount(any())).thenReturn(0L);

        recordFileService.onRecordMp4("rtp", STREAM, "  ", "/record/a.mp4", null, 100, 10);

        ArgumentCaptor<GbRecordFile> mark = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper).updateById(mark.capture());
        assertThat(mark.getValue().getStatus()).isEqualTo(GbRecordFile.STATUS_FAILED);
        assertThat(mark.getValue().getRetryCount()).isZero();
        verify(bizExecutor, never()).execute(any());
    }

    // ---------- retryFailed ----------

    private GbRecordFile storedRow(long id, int status, int retryCount) {
        GbRecordFile r = new GbRecordFile();
        r.setId(id);
        r.setStreamId(STREAM);
        r.setSourceUrl("http://zlm/" + id + ".mp4");
        r.setStatus(status);
        r.setRetryCount(retryCount);
        r.setCreateTime(LocalDateTime.now());
        return r;
    }

    @Test
    void claimsFailedRowsUnderRetryLimitAndBumpsCount() {
        GbRecordFile first = storedRow(1, GbRecordFile.STATUS_FAILED, 1);
        GbRecordFile second = storedRow(2, GbRecordFile.STATUS_FAILED, 2);
        when(recordFileMapper.selectList(any())).thenReturn(List.of(first, second));

        int picked = recordFileService.retryFailed();

        assertThat(picked).isEqualTo(2);
        // 领取即把 retryCount +1 落库，抢在异步任务前占坑
        ArgumentCaptor<GbRecordFile> claims = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper, org.mockito.Mockito.times(2)).updateById(claims.capture());
        assertThat(claims.getAllValues()).allSatisfy(c ->
                assertThat(c.getStatus()).isEqualTo(GbRecordFile.STATUS_UPLOADING));
        assertThat(claims.getAllValues().get(0).getRetryCount()).isEqualTo(2);
        assertThat(claims.getAllValues().get(1).getRetryCount()).isEqualTo(3);
        verify(bizExecutor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void returnsZeroWithoutTouchingPoolWhenNothingPending() {
        when(recordFileMapper.selectList(any())).thenReturn(List.of());

        assertThat(recordFileService.retryFailed()).isZero();
        verify(recordFileMapper, never()).updateById(any(GbRecordFile.class));
        verify(bizExecutor, never()).execute(any());
    }

    @Test
    void rollsBackToFailedWhenTaskSubmissionThrows() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_FAILED, 0);
        when(recordFileMapper.selectList(any())).thenReturn(List.of(r));
        // 第一次是领取置 UPLOADING（retryCount 1），紧接着提交失败，再标回 FAILED（retryCount 回退 0）
        doThrow(new RuntimeException("线程池满")).when(bizExecutor).execute(any(Runnable.class));

        int picked = recordFileService.retryFailed();

        assertThat(picked).isEqualTo(1);
        ArgumentCaptor<GbRecordFile> writes = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper, org.mockito.Mockito.times(2)).updateById(writes.capture());
        GbRecordFile rollback = writes.getAllValues().get(1);
        assertThat(rollback.getStatus()).isEqualTo(GbRecordFile.STATUS_FAILED);
        assertThat(rollback.getRetryCount()).isZero();
    }

    // ---------- cleanExpired ----------

    @Test
    void skipsCleanupWhenRetentionDisabled() {
        assertThat(recordFileService.cleanExpired(0)).isZero();
        verify(recordFileMapper, never()).selectList(any());
    }

    @Test
    void deletesStorageBeforeMetadataForExpiredRow() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_SUCCESS, 0);
        r.setObjectName("gb28181/record/a.mp4");
        r.setCreateTime(LocalDateTime.now().minusDays(60));
        // 第一批 1 条（<200），删完下一批查到空，循环退出
        when(recordFileMapper.selectList(any()))
                .thenReturn(List.of(r))
                .thenReturn(List.of());

        int deleted = recordFileService.cleanExpired(30);

        assertThat(deleted).isEqualTo(1);
        verify(fileStorage).delete("gb28181/record/a.mp4");
        verify(recordFileMapper).deleteById(1L);
    }

    @Test
    void deletesNothingWhenNoExpiredRow() {
        when(recordFileMapper.selectList(any())).thenReturn(List.of());

        assertThat(recordFileService.cleanExpired(30)).isZero();
        verify(fileStorage, never()).delete(anyString());
        verify(recordFileMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    // ---------- delete ----------

    @Test
    void deleteReturnsSilentlyWhenRowMissing() {
        when(recordFileMapper.selectById(9L)).thenReturn(null);

        recordFileService.delete(9L);

        verify(fileStorage, never()).delete(anyString());
        verify(recordFileMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteRemovesObjectThenRow() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_SUCCESS, 0);
        r.setObjectName("gb28181/record/a.mp4");
        when(recordFileMapper.selectById(1L)).thenReturn(r);

        recordFileService.delete(1L);

        verify(fileStorage).delete("gb28181/record/a.mp4");
        verify(recordFileMapper).deleteById(1L);
    }

    @Test
    void deleteSkipsObjectRemovalWhenNeverUploaded() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_FAILED, 0);
        when(recordFileMapper.selectById(1L)).thenReturn(r);

        recordFileService.delete(1L);

        verify(fileStorage, never()).delete(anyString());
        verify(recordFileMapper).deleteById(1L);
    }

    @Test
    void deleteStillRemovesRowWhenObjectDeleteFails() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_SUCCESS, 0);
        r.setObjectName("gb28181/record/a.mp4");
        when(recordFileMapper.selectById(1L)).thenReturn(r);
        doThrow(new RuntimeException("S3 500")).when(fileStorage).delete(anyString());

        recordFileService.delete(1L);

        // 对象没删掉靠存储侧生命周期兜底，这行元数据不能一直删不掉
        verify(recordFileMapper).deleteById(1L);
    }

    // ---------- presignedUrl ----------

    @Test
    void presignedUrlThrowsWhenRowMissing() {
        when(recordFileMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> recordFileService.presignedUrl(9L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在");
        verify(fileStorage, never()).presignedUrl(anyString(), anyInt());
    }

    @Test
    void presignedUrlThrowsWhenNotUploaded() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_UPLOADING, 0);
        // objectName 有值才走状态判断，否则先撞"未就绪"分支
        r.setObjectName("gb28181/record/a.mp4");
        when(recordFileMapper.selectById(1L)).thenReturn(r);

        assertThatThrownBy(() -> recordFileService.presignedUrl(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未上传成功");
    }

    @Test
    void presignedUrlSignsOneHourUrlForSuccessRow() {
        GbRecordFile r = storedRow(1, GbRecordFile.STATUS_SUCCESS, 0);
        r.setObjectName("gb28181/record/a.mp4");
        when(recordFileMapper.selectById(1L)).thenReturn(r);
        when(fileStorage.presignedUrl(eq("gb28181/record/a.mp4"), eq(3600)))
                .thenReturn("http://signed/a.mp4");

        assertThat(recordFileService.presignedUrl(1L)).isEqualTo("http://signed/a.mp4");
    }
}
