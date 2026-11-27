package com.solo.gb28181.service.impl;

import com.solo.gb28181.entity.GbRecordFile;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbRecordFileMapper;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.support.RecordFileLinkSigner;
import com.solo.gb28181.support.RecordStreamRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbRecordFileServiceImplTest {

    @Mock private GbRecordFileMapper recordFileMapper;
    @Mock private GbDeviceMapper deviceMapper;
    @Mock private GbDeviceChannelMapper channelMapper;
    @Mock private GbInviteSessionService inviteSessionService;
    @Mock private RecordFileLinkSigner linkSigner;
    @Mock private RecordStreamRegistry recordStreamRegistry;

    @InjectMocks
    private GbRecordFileServiceImpl service;

    @Test
    void recordHookUsesRetiredOwnershipWithoutInviteSession() {
        String stream = "REC_device_channel";
        when(recordStreamRegistry.get(stream)).thenReturn(
                new RecordStreamRegistry.Context("device", "channel", "server-1"));

        service.onRecordMp4("rtp", stream, "record/rtp/tail.mp4", "/zlm/record/rtp/tail.mp4",
                1_764_269_746L, 43_037L, 0.601);

        ArgumentCaptor<GbRecordFile> captor = ArgumentCaptor.forClass(GbRecordFile.class);
        verify(recordFileMapper).insert(captor.capture());
        GbRecordFile record = captor.getValue();
        assertThat(record.getDeviceId()).isEqualTo("device");
        assertThat(record.getChannelId()).isEqualTo("channel");
        assertThat(record.getServerId()).isEqualTo("server-1");
        assertThat(record.getStreamId()).isEqualTo(stream);
        assertThat(record.getObjectName()).isEqualTo("record/rtp/tail.mp4");
        verifyNoInteractions(inviteSessionService);
    }

    @Test
    void recordHookWithoutOwnershipIsIgnored() {
        service.onRecordMp4("rtp", "unknown", "record/rtp/a.mp4", "/zlm/record/rtp/a.mp4",
                null, 100L, 1);

        verifyNoInteractions(recordFileMapper);
    }
}
