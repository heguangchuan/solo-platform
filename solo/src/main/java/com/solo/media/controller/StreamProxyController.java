package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.media.entity.StreamProxy;
import com.solo.media.request.StreamProxyBatchDeleteRequest;
import com.solo.media.request.StreamProxyPageRequest;
import com.solo.media.response.StreamProxyPlayUrlResponse;
import com.solo.media.service.StreamProxyService;
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
 * 拉流代理管理
 * <p>面向管理台"拉流代理"页的 CRUD + 启停 + 播放地址查询。用于将外部 RTSP / RTMP / HTTP-FLV / HLS
 * 源拉入流媒体服务器，供前端用统一协议播放。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 拉流代理分页查询（按 app 精确 + stream 模糊 + status 过滤）</li>
 *   <li>{@code GET /{id}} — 代理详情（按主键）</li>
 *   <li>{@code POST /save} — 新增代理；id 留空，新增成功后流媒体服务器侧开始拉流</li>
 *   <li>{@code POST /update} — 编辑代理（按 id 必填；已启动的代理会同步更新拉流配置）</li>
 *   <li>{@code POST /{id}/enable} — 启用（重新推流到流媒体服务器）</li>
 *   <li>{@code POST /{id}/disable} — 停用（摘流，代理回到未启动状态）</li>
 *   <li>{@code GET /{id}/play-urls} — 播放地址（仅当已启动时返回完整 URL）</li>
 *   <li>{@code DELETE /{id}} — 删除代理（按主键）</li>
 *   <li>{@code POST /batch/delete} — 批量删除（一次清多条）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/stream-proxy")
@RequiredArgsConstructor
public class StreamProxyController {

    private final StreamProxyService streamProxyService;

    /**
     * 拉流代理分页查询
     * <p>支持按 app 精确匹配、stream 模糊匹配、status 精确过滤；结果按更新时间倒序。
     * 返回 {@code { total, rows }} 形式的分页结果。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"media:stream-proxy:list"})
    public Result<PageResult<StreamProxy>> page(@RequestBody StreamProxyPageRequest request) {
        Page<StreamProxy> page = streamProxyService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 代理详情（按主键）
     * <p>主键 ID 不存在时返回业务异常。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"media:stream-proxy:list"})
    public Result<StreamProxy> detail(@PathVariable Long id) {
        StreamProxy p = streamProxyService.getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在");
        }
        return Result.success(p);
    }

    /**
     * 新增代理
     * <p>id 强制置空，强制走"新增"路径；保存后自动触发一次"启用"以让流媒体服务器开始拉流。
     * 启动失败时回写 lastError 字段（前端可读），不抛异常中断流程。</p>
     */
    @PostMapping("/save")
    @RequirePermission({"media:stream-proxy:add"})
    public Result<Void> save(@RequestBody StreamProxy proxy) {
        proxy.setId(null);
        streamProxyService.saveOrUpdateProxy(proxy);
        return Result.success();
    }

    /**
     * 编辑代理
     * <p>id 必填；更新后若代理已在运行（status=1）会自动同步流媒体服务器侧的配置。</p>
     */
    @PostMapping("/update")
    @RequirePermission({"media:stream-proxy:edit"})
    public Result<Void> update(@RequestBody StreamProxy proxy) {
        if (proxy.getId() == null) {
            throw new ServiceException("id 不能为空");
        }
        streamProxyService.saveOrUpdateProxy(proxy);
        return Result.success();
    }

    /**
     * 启用（重新推流）
     * <p>已停用的代理调此接口重新推流；成功后 status 变为 1，play-urls 才有值。</p>
     */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        streamProxyService.enable(id);
        return Result.success();
    }

    /**
     * 停用（摘流）
     * <p>通知流媒体服务器停止拉流；成功后 status 变为 0，play-urls 返回空字符串。</p>
     */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        streamProxyService.disable(id);
        return Result.success();
    }

    /**
     * 播放地址
     * <p>仅当代理"已启动"时返回完整 HTTP-FLV / WS-FLV / HLS / WebRTC 四种 URL，否则所有 URL 字段为空。
     * 前端拿到后按播放器支持的协议选取使用。</p>
     */
    @GetMapping("/{id}/play-urls")
    public Result<StreamProxyPlayUrlResponse> playUrls(@PathVariable Long id) {
        return Result.success(streamProxyService.getPlayUrls(id));
    }

    /**
     * 删除代理
     * <p>先停流再删行；删后无法恢复。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-proxy:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        streamProxyService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除
     * <p>一次清多条拉流代理；过程中单条失败不影响其它。</p>
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"media:stream-proxy:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody StreamProxyBatchDeleteRequest request) {
        streamProxyService.deleteBatch(request.getIds());
        return Result.success();
    }
}
