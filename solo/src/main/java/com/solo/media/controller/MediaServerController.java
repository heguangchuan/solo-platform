package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
import com.solo.media.request.GbMediaServerPageRequest;
import com.solo.media.response.StreamStatsGbItem;
import com.solo.media.response.StreamStatsOnvifItem;
import com.solo.media.response.StreamStatsProxyItem;
import com.solo.media.response.StreamStatsPushItem;
import com.solo.media.response.StreamStatsSummaryResponse;
import com.solo.media.service.MediaServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流媒体节点管理
 *
 * <p>面向管理台"流媒体节点"页与"节点流统计"弹窗的 CRUD + 状态查询接口。
 * 节点实际是 ZLMediaKit 流媒体服务器，本 Controller 只暴露管理视图所需的接口。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 节点分页查询（按 serverId 模糊 + status 过滤），节点列表页用</li>
 *   <li>{@code GET /{serverId}/stream-stats/summary} — 节点流统计 KPI 概要（弹窗首屏用）；
 *       只返回节点身份 + 4 类会话 count + 流媒体服务器维护的 total，响应体小</li>
 *   <li>{@code GET /{serverId}/stream-stats/gb} — 国标点播 / 回放会话明细（按 Tab 懒加载）</li>
 *   <li>{@code GET /{serverId}/stream-stats/push} — 第三方推流明细（按 Tab 懒加载）</li>
 *   <li>{@code GET /{serverId}/stream-stats/proxy} — 拉流代理明细（仅 status=1，按 Tab 懒加载）</li>
 *   <li>{@code GET /{serverId}/stream-stats/onvif} — ONVIF 实时点播会话明细（按 Tab 懒加载）</li>
 *   <li>{@code GET /list} — 节点列表（不分页，下拉框用）</li>
 *   <li>{@code GET /{id}} — 节点详情（按主键）</li>
 *   <li>{@code POST /save} — 新增 / 更新节点（按 serverId 唯一）</li>
 *   <li>{@code DELETE /{id}} — 删除节点</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/media-server")
@RequiredArgsConstructor
public class MediaServerController {

    private final MediaServerService mediaServerService;

    /**
     * 节点分页查询
     * <p>支持按 serverId 模糊匹配 + status 精确过滤，结果按更新时间倒序。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"media:server:list"})
    public Result<PageResult<MediaServer>> page(@RequestBody GbMediaServerPageRequest request) {
        Page<MediaServer> page = mediaServerService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 节点流统计 KPI 概要
     * <p>弹窗首屏用，仅返回节点身份 + 4 类会话的 count + 流媒体服务器维护的 total，
     * 响应体小，Tab 标签上的数字能立刻就位；
     * 4 个 Tab 的明细走 {@code /stream-stats/{gb|push|proxy|onvif}} 按需加载。</p>
     */
    @GetMapping("/{serverId}/stream-stats/summary")
    @RequirePermission({"media:server:list"})
    public Result<StreamStatsSummaryResponse> streamStatsSummary(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsSummary(serverId));
    }

    /**
     * 国标点播 / 回放会话明细
     * <p>管理台弹窗"国标"Tab 切到时请求；可能为空列表（无在线会话时）。</p>
     */
    @GetMapping("/{serverId}/stream-stats/gb")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsGbItem>> streamStatsGb(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsGbDetail(serverId));
    }

    /**
     * 第三方推流明细
     * <p>管理台弹窗"推流"Tab 切到时请求；行存在即代表正在推流。
     * 可能为空列表（无推流时）。</p>
     */
    @GetMapping("/{serverId}/stream-stats/push")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsPushItem>> streamStatsPush(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsPushDetail(serverId));
    }

    /**
     * 拉流代理明细
     * <p>管理台弹窗"拉流代理"Tab 切到时请求；只返回已启动的代理行，未启动的不会出现在列表里。
     * 可能为空列表。</p>
     */
    @GetMapping("/{serverId}/stream-stats/proxy")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsProxyItem>> streamStatsProxy(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsProxyDetail(serverId));
    }

    /**
     * ONVIF 实时点播会话明细
     * <p>管理台弹窗"ONVIF"Tab 切到时请求；可能为空列表。</p>
     */
    @GetMapping("/{serverId}/stream-stats/onvif")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsOnvifItem>> streamStatsOnvif(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsOnvifDetail(serverId));
    }

    /**
     * 节点列表（不分页）
     * <p>下拉框 / 选择器专用，按更新时间倒序。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"media:server:list"})
    public Result<List<MediaServer>> list() {
        return Result.success(mediaServerService.list());
    }

    /**
     * 节点详情（按主键）
     * <p>主键 ID 不存在时返回业务异常，列表页"查看详情"按钮用。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"media:server:list"})
    public Result<MediaServer> detail(@PathVariable Long id) {
        MediaServer server = mediaServerService.getById(id);
        if (server == null) {
            throw new ServiceException("节点不存在");
        }
        return Result.success(server);
    }

    /**
     * 新增 / 更新节点
     * <p>按 serverId 唯一：已存在则覆盖更新（保留主键 ID），不存在则新增。
     * serverId / hostIp 必填校验在 Controller 层兜底。</p>
     */
    @PostMapping("/save")
    @RequirePermission({"media:server:save"})
    public Result<Void> save(@RequestBody MediaServer server) {
        if (server.getServerId() == null || server.getServerId().isBlank()) {
            throw new ServiceException("serverId 不能为空");
        }
        if (server.getHostIp() == null || server.getHostIp().isBlank()) {
            throw new ServiceException("hostIp 不能为空");
        }
        mediaServerService.saveOrUpdateByServerId(server);
        return Result.success();
    }

    /**
     * 删除节点
     * <p>按主键 ID 物理删除；不会影响节点上正在进行的流，但其他业务按 serverId 找不到该节点。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"media:server:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        mediaServerService.removeById(id);
        return Result.success();
    }
}
