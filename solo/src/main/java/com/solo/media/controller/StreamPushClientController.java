package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.request.StreamPushClientPageRequest;
import com.solo.media.service.StreamPushClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推流方鉴权管理
 * <p>管理可向本平台推 RTMP / RTSP / WebRTC 的"推流方账号"：每个推流方有自己的 clientId 和 token，
 * 推流时需带上 token 才被接受。token 在创建 / 重置时一次性返回给前端，请及时展示给用户。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 推流方分页查询（按 clientId 模糊 + enabled 过滤）</li>
 *   <li>{@code POST /save} — 新增推流方（生成 token 一次性返回，UI 必须立刻展示给用户）</li>
 *   <li>{@code POST /update} — 更新推流方（按 id 修改 clientId / enabled / remark）</li>
 *   <li>{@code POST /{id}/reset-token} — 重置 token（安全事件时用，旧的立即失效）</li>
 *   <li>{@code DELETE /{id}} — 删除推流方</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/stream-push-client")
@RequiredArgsConstructor
public class StreamPushClientController {

    private final StreamPushClientService service;

    /**
     * 推流方分页查询
     * <p>支持按 clientId 模糊匹配、enabled 精确过滤；结果按更新时间倒序。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"media:stream-push-client:list"})
    public Result<PageResult<StreamPushClient>> page(@RequestBody StreamPushClientPageRequest request) {
        Page<StreamPushClient> page = service.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增推流方
     * <p>服务端为新推流方生成一个高强度随机 token（256 位熵），仅本次响应返回；
     * 列表分页接口不再返回 token，所以前端必须把响应里的 token 立刻展示给用户并提示复制保存。</p>
     */
    @PostMapping("/save")
    @RequirePermission({"media:stream-push-client:add"})
    public Result<StreamPushClient> save(@RequestBody StreamPushClient input) {
        return Result.success(service.createClient(input));
    }

    /**
     * 更新推流方
     * <p>按主键 id 修改 clientId / enabled / remark；请求里的 token 字段被忽略，
     * 重置 token 必须调专门的 {@code /{id}/reset-token} 接口。</p>
     */
    @PostMapping("/update")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<Void> update(@RequestBody StreamPushClient input) {
        service.updateClient(input);
        return Result.success();
    }

    /**
     * 重置 token
     * <p>安全事件（token 泄露、员工离职等）时用；重新生成 token 一次性返回，
     * 旧的立即失效——正在用旧 token 推的连接会在下一次鉴权时被拒绝。
     * 前端拿到新 token 后必须再次展示给用户并提示复制保存。</p>
     */
    @PostMapping("/{id}/reset-token")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<StreamPushClient> resetToken(@PathVariable Long id) {
        return Result.success(service.resetToken(id));
    }

    /**
     * 删除推流方
     * <p>物理删除；删除后该 clientId 不能继续推流。但已经推上来的流（stream_push 行）不会被自动关停，
     * 直到流媒体服务器端正常超时断开。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-push-client:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }
}
