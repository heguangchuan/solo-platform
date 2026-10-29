package com.solo.gb28181.service.impl;

import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.ssrc.SsrcFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 无会话兜底关流：会话没了但流还在，得把流停掉。
 */
@ExtendWith(MockitoExtension.class)
class GbInviteSessionCleanupServiceImplTest {

    private static final String STREAM = "REC_34020000001320000001_34020000001310000001";

    @Mock
    private GbDeviceMapper deviceMapper;
    @Mock
    private GbMediaServerMapper mediaServerMapper;
    @Mock
    private GbInviteSessionService inviteSessionService;
    @Mock
    private SsrcFactory ssrcFactory;
    @Mock
    private ZlmRestClient zlmRestClient;
    @Mock
    private SipCommander sipCommander;

    @InjectMocks
    private GbInviteSessionCleanupServiceImpl service;

    private MediaServer server(String id) {
        MediaServer s = new MediaServer();
        s.setServerId(id);
        return s;
    }

    @Test
    void closesStreamOnTheNodeThatHasIt() {
        MediaServer first = server("zlm-1");
        MediaServer second = server("zlm-2");
        when(mediaServerMapper.selectList(any())).thenReturn(List.of(first, second));
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM))).thenReturn(false, true);

        assertThat(service.closeOrphanStream(STREAM)).isTrue();

        verify(zlmRestClient, never()).closeRtpServer(eq(first), anyString());
        verify(zlmRestClient).closeRtpServer(eq(second), eq(STREAM));
    }

    @Test
    void returnsFalseWhenNoNodeHasTheStream() {
        when(mediaServerMapper.selectList(any())).thenReturn(List.of(server("zlm-1")));
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM))).thenReturn(false);

        assertThat(service.closeOrphanStream(STREAM)).isFalse();

        verify(zlmRestClient, never()).closeRtpServer(any(), anyString());
    }

    @Test
    void ignoresBlankStreamId() {
        assertThat(service.closeOrphanStream(" ")).isFalse();

        verify(mediaServerMapper, never()).selectList(any());
        verify(zlmRestClient, never()).closeRtpServer(any(), anyString());
    }
}
