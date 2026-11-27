package com.solo.gb28181.service.impl;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.support.RecordStreamRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbCloudRecordServiceImplTest {

    @Mock
    private GbInviteSessionService inviteSessionService;
    @Mock
    private GbInviteSessionCleanupService inviteCleanup;
    @Mock
    private RecordStreamRegistry recordStreamRegistry;

    @InjectMocks
    private GbCloudRecordServiceImpl service;

    @Test
    void recordStreamIdsDoNotCollideWithPlayStreamIds() {
        assertThat(GbCloudRecordService.manualStreamId("device", "channel", null))
                .isEqualTo("REC_device_channel");
        assertThat(GbCloudRecordService.manualStreamId("device", "channel", "streamnumber:1"))
                .isEqualTo("REC_device_channel_streamnumber_1");
    }

    @Test
    void stopIgnoresNonRecordingSession() {
        GbInviteSession play = new GbInviteSession();
        play.setStreamId("device_channel");
        play.setSessionType(GbInviteSession.TYPE_PLAY);
        when(inviteSessionService.getByStreamId(play.getStreamId())).thenReturn(play);

        service.stop(play.getStreamId());

        verifyNoInteractions(inviteCleanup);
    }

    @Test
    void stopRetiresRecordOwnershipBeforeDeletingInviteSession() {
        GbInviteSession recording = new GbInviteSession();
        recording.setStreamId("REC_device_channel");
        recording.setSessionType(GbInviteSession.TYPE_RECORD);
        when(inviteSessionService.getByStreamId(recording.getStreamId())).thenReturn(recording);

        service.stop(recording.getStreamId());

        var order = inOrder(recordStreamRegistry, inviteCleanup, inviteSessionService);
        order.verify(recordStreamRegistry).retire(recording);
        order.verify(inviteCleanup).cleanupZlm(recording);
        order.verify(inviteSessionService).removeByStreamId(recording.getStreamId());
    }
}
