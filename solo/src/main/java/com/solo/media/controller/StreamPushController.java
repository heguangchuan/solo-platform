package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.media.entity.StreamPush;
import com.solo.media.request.StreamPushBatchDeleteRequest;
import com.solo.media.request.StreamPushPageRequest;
import com.solo.media.response.StreamPushPlayUrlResponse;
import com.solo.media.service.StreamPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推流管理（只读视角 + 强制中止）
 * <p>面向管理台"推流列表"页。推流行由流媒体服务器自动维护（推流上行时新增、下线时移除），
 * 用户只能查看、强制中止、以及清理已下线的残留行。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 推流分页查询（按 app 精确 + stream 模糊 + 节点 serverId 过滤）</li>
 *   <li>{@code GET /{id}} — 推流详情（按主键）</li>
 *   <li>{@code GET /{id}/play-urls} — 播放地址（按推流的 app/stream 组装）</li>
 *   <li>{@code POST /{id}/stop} — 强制中止（让流媒体服务器断开推流连接）</li>
 *   <li>{@code DELETE /{id}} — 单条删除（仅可用于已中止的流）</li>
 *   <li>{@code POST /batch/delete} — 批量删除（一次清多条离线推流行）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/stream-push")
@RequiredArgsConstructor
public class StreamPushController {

    private final StreamPushService streamPushService;

    /**
     * 推流分页查询
     * <p>支持按 app 精确匹配、stream 模糊匹配、流媒体节点 serverId 精确过滤；
     * 结果按 pushTime（开始推流时间）倒序，最近推的在前。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"media:stream-push:list"})
    public Result<PageResult<StreamPush>> page(@RequestBody StreamPushPageRequest request) {
        Page<StreamPush> page = streamPushService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 推流详情（按主键）
     * <p>主键 ID 不存在时返回业务异常。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"media:stream-push:list"})
    public Result<StreamPush> detail(@PathVariable Long id) {
        StreamPush p = streamPushService.getById(id);
        if (p == null) {
            throw new ServiceException("推流不存在");
        }
        return Result.success(p);
    }

    /**
     * 播放地址
     * <p>按推流记录的 app/stream + 节点 serverId 组装 HTTP-FLV / WS-FLV / HLS / WebRTC 四种 URL。
     * 推流在线时返回完整 URL，已停止时所有 URL 字段为空字符串。</p>
     */
    @GetMapping("/{id}/play-urls")
    public Result<StreamPushPlayUrlResponse> playUrls(@PathVariable Long id) {
        return Result.success(streamPushService.getPlayUrls(id));
    }

    /**
     * 强制中止
     * <p>通知流媒体服务器断开推流；调用后行记录会由流媒体服务器自动移除（前端下次刷新就不在了）。
     * 接口本身只触发关闭、不直接删行，避免与流媒体服务器侧竞争。</p>
     */
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        streamPushService.forceStop(id);
        return Result.success();
    }

    /**
     * 单条删除
     * <p>仅用于清理已下线的推流残留行；推流在线时拒绝删除（避免与流媒体服务器状态不一致）。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-push:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        streamPushService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除
     * <p>一次清多条已下线的推流残留行。</p>
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"media:stream-push:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody StreamPushBatchDeleteRequest request) {
        streamPushService.deleteBatch(request.getIds());
        return Result.success();
    }
}
