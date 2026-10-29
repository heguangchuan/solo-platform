package com.solo.gb28181.task;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动时接管 Redis 里残留的会话：流还在的保留，其余清掉。
 *
 * 不能直接全删——删了就发不出 BYE，设备那头 dialog 一直挂着；ZLM 上的流也没人停得掉；
 * SsrcFactory 启动时已经按这些会话占了 SSRC，不 release 会漏号；on_record_mp4 还得靠会话反查设备。
 *
 * READY 的问一下 ZLM 流在不在，探不通就保留，宁可留着也别误杀；INVITING、DOWNLOAD、终态的直接清。
 * 本进程启动后新建的不动。排在 SipLayer 后面跑，保证 BYE 发得出去。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class InviteSessionTakeoverRunner implements ApplicationRunner {

    private static final String LOG_TAG = "会话接管";
    private static final String APP = "rtp";

    private final GbInviteSessionService inviteSessionService;
    private final GbInviteSessionCleanupService inviteCleanup;
    private final ZlmRestClient zlmRestClient;
    private final GbMediaServerMapper mediaServerMapper;

    /** 启动时刻，早于它的会话才归这次接管处理 */
    private final LocalDateTime bootTime = LocalDateTime.now();

    @Override
    public void run(ApplicationArguments args) {
        try {
            takeover();
        } catch (Exception e) {
            log.error("[{}] 启动接管出错，残留会话等下次启动再说", LOG_TAG, e);
        }
    }

    void takeover() {
        List<GbInviteSession> active = inviteSessionService.listAllActive();
        if (active.isEmpty()) {
            return;
        }
        int kept = 0;
        int cleaned = 0;
        int skipped = 0;
        // 节点一次查完，几百条会话没必要逐条查库
        Map<String, MediaServer> servers = new HashMap<>();
        for (MediaServer server : mediaServerMapper.selectList(null)) {
            servers.put(server.getServerId(), server);
        }
        // 探不通的节点记下来，后面的会话就不用再挨个等超时
        Set<String> unreachableServers = new HashSet<>();
        for (GbInviteSession session : active) {
            if (createdByThisProcess(session)) {
                skipped++;
                continue;
            }
            try {
                Decision decision = decide(session, servers, unreachableServers);
                if (decision.adopt()) {
                    kept++;
                    log.info("[{}] 接管会话 流ID:{} 类型:{}", LOG_TAG, session.getStreamId(), session.getSessionType());
                } else {
                    cleanup(session, decision.reason());
                    cleaned++;
                }
            } catch (Exception e) {
                log.warn("[{}] 处理会话出错，先留着 流ID:{} 原因:{}",
                        LOG_TAG, session.getStreamId(), e.getMessage());
            }
        }
        log.info("[{}] 完成 保留={} 清扫={} 跳过(本进程新建)={}", LOG_TAG, kept, cleaned, skipped);
    }

    private boolean createdByThisProcess(GbInviteSession session) {
        return session.getStartTime() != null && !session.getStartTime().isBefore(bootTime);
    }

    private Decision decide(GbInviteSession session, Map<String, MediaServer> servers,
                            Set<String> unreachableServers) {
        String status = session.getStatus();
        if (isTerminal(status)) {
            return Decision.clean("会话已结束");
        }
        // 下载任务表在进程内，重启就没了，留着会话也续不上
        if (GbInviteSession.TYPE_DOWNLOAD.equals(session.getSessionType())) {
            return Decision.clean("下载任务已丢");
        }
        // 邀请中的不接管：没 dialog 的发不了 BYE，有 dialog 的流也还没起来
        if (GbInviteSession.STATUS_INVITING.equals(status)) {
            return Decision.clean(session.getToTag() == null
                    ? "dialog 未建立" : "流未就绪");
        }
        MediaServer server = session.getServerId() == null ? null : servers.get(session.getServerId());
        if (server == null) {
            return Decision.clean("承载节点已不存在");
        }
        String serverId = server.getServerId();
        // 节点离线或探不通，判断不了流在不在，先留着
        if (!Integer.valueOf(1).equals(server.getStatus()) || unreachableServers.contains(serverId)) {
            if (unreachableServers.add(serverId)) {
                log.warn("[{}] 节点离线或探不通，其会话先保留 节点:{}", LOG_TAG, serverId);
            }
            return Decision.keep();
        }
        Boolean online = zlmRestClient.isMediaOnlineOrNull(server, APP, session.getStreamId());
        if (online == null) {
            unreachableServers.add(serverId);
            log.warn("[{}] 节点探不通，其会话先保留 节点:{} 流ID:{}",
                    LOG_TAG, serverId, session.getStreamId());
            return Decision.keep();
        }
        return online ? Decision.keep() : Decision.clean("流已不在");
    }

    private boolean isTerminal(String status) {
        return GbInviteSession.STATUS_STOPPED.equals(status)
                || GbInviteSession.STATUS_TIMEOUT.equals(status)
                || GbInviteSession.STATUS_FAILED.equals(status);
    }

    private void cleanup(GbInviteSession session, String reason) {
        if (session.getToTag() != null) {
            inviteCleanup.failAndBye(session, LOG_TAG, reason);
        } else {
            inviteCleanup.failSession(session, LOG_TAG, reason);
        }
    }

    private record Decision(boolean adopt, String reason) {

        static Decision keep() {
            return new Decision(true, null);
        }

        static Decision clean(String reason) {
            return new Decision(false, reason);
        }
    }
}
