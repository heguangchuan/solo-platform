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
 * 系统消息控制器
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final SysMessageService messageService;

    /**
     * 获取当前用户消息列表
     */
    @GetMapping("/list")
    public Result<List<SysMessage>> list(@RequestParam(defaultValue = "50") int limit) {
        String username = UserHolderUtils.getCurrentUsername();
        List<SysMessage> messages = messageService.getUserMessages(username, limit);
        return Result.success(messages);
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        String username = UserHolderUtils.getCurrentUsername();
        long count = messageService.getUnreadCount(username);
        return Result.success(count);
    }

    /**
     * 标记单条消息为已读
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        String username = UserHolderUtils.getCurrentUsername();
        messageService.markRead(id, username);
        return Result.success();
    }

    /**
     * 标记所有消息为已读
     */
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        String username = UserHolderUtils.getCurrentUsername();
        messageService.markAllRead(username);
        return Result.success();
    }

    /**
     * 后台：发送消息给指定用户（需管理员权限）
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
     * 后台：广播消息给所有在线用户（需管理员权限）
     */
    @PostMapping("/broadcast")
    public Result<Integer> broadcast(@RequestBody BroadcastMessageRequest request) {
        int count = messageService.broadcast(request.getTitle(), request.getContent(), request.getLevel());
        return Result.success(count);
    }

    /**
     * 获取 WebSocket 当前在线用户数
     */
    @GetMapping("/online-count")
    public Result<Integer> onlineCount() {
        return Result.success(SystemMessageWebSocket.getOnlineCount());
    }
}
