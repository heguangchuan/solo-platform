package com.solo.gb28181.support;

import com.solo.gb28181.response.PlayResultResponse;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;

public final class GbPlayUrlSigner {

    private GbPlayUrlSigner() {
    }

    public static void sign(PlayResultResponse result, ViewActor actor, ViewTokenSigner signer) {
        String serverId = result.getMediaServerId();
        String app = result.getApp();
        String streamId = result.getStreamId();
        result.setFlvUrl(signer.appendToken(result.getFlvUrl(), actor, PlayTarget.httpFlv(serverId, app, streamId)));
        result.setWsFlvUrl(signer.appendToken(result.getWsFlvUrl(), actor, PlayTarget.wsFlv(serverId, app, streamId)));
        result.setHlsUrl(signer.appendToken(result.getHlsUrl(), actor, PlayTarget.hls(serverId, app, streamId)));
        result.setRtcUrl(signer.appendToken(result.getRtcUrl(), actor, PlayTarget.rtc(serverId, app, streamId)));
    }
}
