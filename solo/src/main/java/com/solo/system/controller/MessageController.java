package com.solo.system.controller;

import com.solo.common.Result;
import com.solo.system.entity.SysMessage;
import com.solo.system.request.BroadcastMessageRequest;
import com.solo.system.request.SendMessageRequest;
import com.solo.system.service.impl.SysMessageService;
import com.solo.common.utils.UserHolderUtils;
import com.solo.websocket.SystemMessageWebSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内消息接口
 *
 * <p>面向登录用户的站内消息：列表、未读数、标记已读；同时提供后台发消息 / 广播接口。
 * 实时推送走 WebSocket（连接地址见下方），离线用户消息落库，登录后由 {@code GET /list} 拉取。</p>
 *
 * <h3>WebSocket 实时推送</h3>
 * <p>前端登录后建议建立 WebSocket 连接：<br>
 * {@code ws://<host>/api/ws/message?token=<accessToken>}<br>
 * 服务端会用 type=NOTICE 等消息体主动推过来，登录后由 {@code GET /list} 补全离线消息。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET  /api/message/list} — 当前用户最近消息列表（默认 50 条）</li>
 *   <li>{@code GET  /api/message/unread-count} — 未读消息数</li>
 *   <li>{@code POST /api/message/{id}/read} — 标记单条已读</li>
 *   <li>{@code POST /api/message/read-all} — 全部已读</li>
 *   <li>{@code POST /api/message/send} — 后台：给指定用户发消息</li>
 *   <li>{@code POST /api/message/broadcast} — 后台：广播给所有在线用户</li>
 *   <li>{@code GET  /api/message/online-count} — WebSocket 当前在线用户数</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final SysMessageService messageService;

    /**
     * 获取当前用户消息列表
     *
     * <p>登录后第一次拉取会带回离线期间未读的消息；{@code limit} 默认 50，最大建议不超过 200。</p>
     */
    @GetMapping("/list")
    public Result<List<SysMessage>> list(@RequestParam(defaultValue = "50") int limit) {
        String username = UserHolderUtils.getCurrentUsername();
        List<SysMessage> messages = messageService.getUserMessages(username, limit);
        return Result.success(messages);
    }

    /**
     * 获取未读消息数
     *
     * <p>用于右上角红点徽标，建议心跳轮询（如 30s 一次）。</p>
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        String username = UserHolderUtils.getCurrentUsername();
        long count = messageService.getUnreadCount(username);
        return Result.success(count);
    }

    /**
     * 标记单条消息为已读
     *
     * <p>{@code {id}} 是消息主键 ID；不属于自己的消息会被忽略。</p>
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        String username = UserHolderUtils.getCurrentUsername();
        messageService.markRead(id, username);
        return Result.success();
    }

    /**
     * 标记所有消息为已读
     *
     * <p>一次性清零当前用户的未读徽标。</p>
     */
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        String username = UserHolderUtils.getCurrentUsername();
        messageService.markAllRead(username);
        return Result.success();
    }

    /**
     * 后台：发送消息给指定用户
     *
     * <p>在线用户走 WebSocket 实时推送，离线用户落库等下次登录拉取。
     * 字段取值见 {@link SendMessageRequest}。</p>
     */
    @PostMapping("/send")
    public Result<Void> sendMessage(@RequestBody SendMessageRequest request) {
        messageService.sendMessage(
                request.getUsername(),
                request.getTitle(),
                request.getContent(),
                request.getType(),
                request.getLevel(),
                request.getRoute()
        );
        return Result.success();
    }

    /**
     * 后台：广播消息给所有在线用户
     *
     * <p>返回实际投递到的在线用户数；离线用户不会收到离线消息（与 send 不同）。</p>
     */
    @PostMapping("/broadcast")
    public Result<Integer> broadcast(@RequestBody BroadcastMessageRequest request) {
        int count = messageService.broadcast(request.getTitle(), request.getContent(), request.getLevel());
        return Result.success(count);
    }

    /**
     * 获取 WebSocket 当前在线用户数
     *
     * <p>用于大屏 / 仪表盘展示实时在线指标。</p>
     */
    @GetMapping("/online-count")
    public Result<Integer> onlineCount() {
        return Result.success(SystemMessageWebSocket.getOnlineCount());
    }
}
