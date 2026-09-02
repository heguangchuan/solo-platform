package com.solo.system.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.Result;
import com.solo.system.response.OnlineUserVO;
import com.solo.system.service.SysOnlineService;
import com.solo.common.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户管理接口
 *
 * <p>列出当前所有登录会话、强制下线。
 * "在线" 通过 access token 的会话周期判定，access 过期（30 分钟无活动）后自动从列表消失。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET    /api/online/list} — 在线用户列表（支持账号/昵称模糊）</li>
 *   <li>{@code DELETE /api/online/kick/{username}} — 强制指定用户下线</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/online")
@RequiredArgsConstructor
public class OnlineController {

    private final SysOnlineService sysOnlineService;

    /**
     * 在线用户列表
     *
     * <p>{@code username} / {@code nickname} 均为可选模糊匹配，传入则按包含关系过滤。
     * 同一用户多次登录会出现多条记录（不同 token / IP）。</p>
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
     *
     * <p>{@code {username}} 是登录账号；后端会让该账号所有 access token 立即失效。
     * <b>不能强制下线当前登录账号自身</b>，否则返回 400。</p>
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
