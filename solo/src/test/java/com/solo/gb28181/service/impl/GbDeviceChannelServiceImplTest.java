package com.solo.gb28181.service.impl;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.service.GbInviteSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbDeviceChannelServiceImplTest {

    @Mock
    private GbDeviceMapper deviceMapper;
    @Mock
    private Gb28181Properties properties;
    @Mock
    private GbInviteSessionService inviteSessionService;

    @InjectMocks
    private GbDeviceChannelServiceImpl service;

    @Test
    void fillsOnlyActiveMainStreamRecordingState() {
        GbDeviceChannelResponse recording = channel("device-1", "channel-1");
        GbDeviceChannelResponse playing = channel("device-2", "channel-2");
        List<GbDeviceChannelResponse> rows = List.of(recording, playing);
        when(inviteSessionService.listAllActive()).thenReturn(List.of(
                session("device-1", "channel-1", GbInviteSession.TYPE_RECORD,
                        GbInviteSession.STATUS_READY, null, "REC_main"),
                session("device-1", "channel-1", GbInviteSession.TYPE_RECORD,
                        GbInviteSession.STATUS_READY, "streamnumber_1", "REC_sub"),
                session("device-1", "channel-1", GbInviteSession.TYPE_RECORD,
                        GbInviteSession.STATUS_STOPPED, null, "REC_stopped"),
                session("device-2", "channel-2", GbInviteSession.TYPE_PLAY,
                        GbInviteSession.STATUS_READY, null, "play")));

        service.fillRecordingState(rows);

        assertThat(recording.getRecordStreamId()).isEqualTo("REC_main");
        assertThat(recording.getRecordStatus()).isEqualTo(GbInviteSession.STATUS_READY);
        assertThat(playing.getRecordStreamId()).isNull();
        assertThat(playing.getRecordStatus()).isNull();
    }

    private static GbDeviceChannelResponse channel(String deviceId, String channelId) {
        GbDeviceChannelResponse row = new GbDeviceChannelResponse();
        row.setDeviceId(deviceId);
        row.setChannelId(channelId);
        return row;
    }

    private static GbInviteSession session(String deviceId, String channelId, String type,
                                           String status, String streamNumber, String streamId) {
        GbInviteSession session = new GbInviteSession();
        session.setDeviceId(deviceId);
        session.setChannelId(channelId);
        session.setSessionType(type);
        session.setStatus(status);
        session.setStreamNumber(streamNumber);
        session.setStreamId(streamId);
        return session;
    }
}
