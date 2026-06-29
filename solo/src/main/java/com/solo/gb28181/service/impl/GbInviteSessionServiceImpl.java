package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbInviteSessionMapper;
import com.solo.gb28181.service.GbInviteSessionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 点播/回放会话服务实现
 */
@Service
public class GbInviteSessionServiceImpl extends ServiceImpl<GbInviteSessionMapper, GbInviteSession>
        implements GbInviteSessionService {

    @Override
    public GbInviteSession getByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return null;
        }
        return lambdaQuery().eq(GbInviteSession::getStreamId, streamId).one();
    }

    @Override
    public GbInviteSession getByCallId(String callId) {
        if (callId == null || callId.isBlank()) {
            return null;
        }
        return lambdaQuery().eq(GbInviteSession::getCallId, callId).one();
    }

    @Override
    public GbInviteSession getBySsrc(String ssrc) {
        if (ssrc == null || ssrc.isBlank()) {
            return null;
        }
        return lambdaQuery().eq(GbInviteSession::getSsrc, ssrc).one();
    }

    @Override
    public List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId) {
        return lambdaQuery()
                .eq(GbInviteSession::getDeviceId, deviceId)
                .eq(GbInviteSession::getChannelId, channelId)
                .in(GbInviteSession::getStatus,
                        GbInviteSession.STATUS_INVITING, GbInviteSession.STATUS_READY)
                .list();
    }

    @Override
    public List<GbInviteSession> listAllActive() {
        LambdaQueryWrapper<GbInviteSession> w = new LambdaQueryWrapper<>();
        w.in(GbInviteSession::getStatus,
                GbInviteSession.STATUS_INVITING, GbInviteSession.STATUS_READY);
        return list(w);
    }
}
