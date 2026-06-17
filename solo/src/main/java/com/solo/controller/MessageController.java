package com.solo.controller;

import com.solo.common.Result;
import com.solo.entity.SysMessage;
import com.solo.service.impl.SysMessageService;
import com.solo.utils.UserHolderUtils;
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
    public Result<Void> sendMessage(
            @RequestParam String username,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "SYSTEM") String type,
            @RequestParam(defaultValue = "INFO") String level) {
        messageService.sendMessage(username, title, content, type, level, null);
        return Result.success();
    }

    /**
     * 后台：广播消息给所有在线用户（需管理员权限）
     */
    @PostMapping("/broadcast")
    public Result<Integer> broadcast(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "INFO") String level) {
        int count = messageService.broadcast(title, content, level);
        return Result.success(count);
    }
}
