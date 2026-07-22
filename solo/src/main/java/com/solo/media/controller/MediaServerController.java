package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
import com.solo.gb28181.request.GbMediaServerPageRequest;
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
 * 流媒体节点管理（ZLMediaKit）
 */
@RestController
@RequestMapping("/api/media-server")
@RequiredArgsConstructor
public class MediaServerController {

    private final MediaServerService mediaServerService;

    /**
     * 分页查询节点
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:media-server:list"})
    public Result<PageResult<MediaServer>> page(@RequestBody GbMediaServerPageRequest request) {
        Page<MediaServer> page = mediaServerService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 节点列表（不分页，下拉框用）
     */
    @GetMapping("/list")
    @RequirePermission({"gb28181:media-server:list"})
    public Result<List<MediaServer>> list() {
        return Result.success(mediaServerService.list());
    }

    /**
     * 详情（按主键）
     */
    @GetMapping("/{id}")
    @RequirePermission({"gb28181:media-server:list"})
    public Result<MediaServer> detail(@PathVariable Long id) {
        MediaServer server = mediaServerService.getById(id);
        if (server == null) {
            throw new ServiceException("节点不存在");
        }
        return Result.success(server);
    }

    /**
     * 新增/更新节点（按 serverId 唯一）
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:media-server:save"})
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
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:media-server:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        mediaServerService.removeById(id);
        return Result.success();
    }
}
