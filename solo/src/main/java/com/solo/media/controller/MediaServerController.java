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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 */
@Tag(name = "流媒体节点管理", description = "流媒体节点管理")
@RestController
@RequestMapping("/api/media-server")
@RequiredArgsConstructor
public class MediaServerController {

    private final MediaServerService mediaServerService;

    /**
     * 节点分页查询
     */
    @Operation(summary = "节点分页查询")
    @PostMapping("/page")
    @RequirePermission({"media:server:list"})
    public Result<PageResult<MediaServer>> page(@RequestBody GbMediaServerPageRequest request) {
        Page<MediaServer> page = mediaServerService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 节点流统计 KPI 概要
     *
     * @param serverId 节点 serverId
     */
    @Operation(summary = "节点流统计 KPI 概要", description = "按 Tab 懒加载，弹窗首屏只发 summary")
    @Parameter(name = "serverId", description = "节点 serverId", in = ParameterIn.PATH)
    @GetMapping("/{serverId}/stream-stats/summary")
    @RequirePermission({"media:server:list"})
    public Result<StreamStatsSummaryResponse> streamStatsSummary(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsSummary(serverId));
    }

    /**
     * 国标点播 / 回放会话明细
     *
     * @param serverId 节点 serverId
     */
    @Operation(summary = "国标点播 / 回放会话明细", description = "按 Tab 懒加载，弹窗首屏只发 summary")
    @Parameter(name = "serverId", description = "节点 serverId", in = ParameterIn.PATH)
    @GetMapping("/{serverId}/stream-stats/gb")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsGbItem>> streamStatsGb(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsGbDetail(serverId));
    }

    /**
     * 第三方推流明细
     *
     * @param serverId 节点 serverId
     */
    @Operation(summary = "第三方推流明细", description = "按 Tab 懒加载，弹窗首屏只发 summary")
    @Parameter(name = "serverId", description = "节点 serverId", in = ParameterIn.PATH)
    @GetMapping("/{serverId}/stream-stats/push")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsPushItem>> streamStatsPush(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsPushDetail(serverId));
    }

    /**
     * 拉流代理明细
     *
     * @param serverId 节点 serverId
     */
    @Operation(summary = "拉流代理明细", description = "按 Tab 懒加载，弹窗首屏只发 summary")
    @Parameter(name = "serverId", description = "节点 serverId", in = ParameterIn.PATH)
    @GetMapping("/{serverId}/stream-stats/proxy")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsProxyItem>> streamStatsProxy(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsProxyDetail(serverId));
    }

    /**
     * ONVIF 实时点播会话明细
     *
     * @param serverId 节点 serverId
     */
    @Operation(summary = "ONVIF 实时点播会话明细", description = "按 Tab 懒加载，弹窗首屏只发 summary")
    @Parameter(name = "serverId", description = "节点 serverId", in = ParameterIn.PATH)
    @GetMapping("/{serverId}/stream-stats/onvif")
    @RequirePermission({"media:server:list"})
    public Result<List<StreamStatsOnvifItem>> streamStatsOnvif(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsOnvifDetail(serverId));
    }

    /**
     * 节点列表
     */
    @Operation(summary = "节点列表")
    @GetMapping("/list")
    @RequirePermission({"media:server:list"})
    public Result<List<MediaServer>> list() {
        return Result.success(mediaServerService.list());
    }

    /**
     * 节点详情
     *
     * @param id 主键 ID
     */
    @Operation(summary = "节点详情")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
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
     */
    @Operation(summary = "新增 / 更新节点")
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
     *
     * @param id 主键 ID
     */
    @Operation(summary = "删除节点")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"media:server:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        mediaServerService.removeById(id);
        return Result.success();
    }
}
