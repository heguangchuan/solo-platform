package com.solo.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.Result;
import com.solo.response.OnlineUserVO;
import com.solo.service.SysOnlineService;
import com.solo.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户管理：列出当前在线会话、强制下线
 */
@RestController
@RequestMapping("/api/online")
@RequiredArgsConstructor
public class OnlineController {

    private final SysOnlineService sysOnlineService;

    /**
     * 在线用户列表
     */
    @GetMapping("/list")
    @RequirePermission({"system:online:list"})
    public Result<List<OnlineUserVO>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname) {
        return Result.success(sysOnlineService.list(username, nickname));
    }

    /**
     * 强制指定用户下线
     */
    @DeleteMapping("/kick/{username}")
    @RequirePermission({"system:online:kick"})
    @OperationLog(module = "在线用户", type = BusinessType.DELETE, description = "强制下线")
    public Result<Void> kick(@PathVariable String username) {
        // 防止把自己踢下线
        String current = UserHolderUtils.getCurrentUsername();
        if (current != null && current.equals(username)) {
            return Result.error(400, "不能强制下线当前登录账号");
        }
        sysOnlineService.kick(username);
        return Result.success();
    }
}
