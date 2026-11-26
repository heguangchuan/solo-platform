package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.media.entity.StreamPush;
import com.solo.media.request.StreamPushBatchDeleteRequest;
import com.solo.media.request.StreamPushPageRequest;
import com.solo.media.response.StreamPushPlayUrlResponse;
import com.solo.media.service.StreamPushService;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "推流管理", description = "推流管理")
@RestController
@RequestMapping("/api/stream-push")
@RequiredArgsConstructor
public class StreamPushController {

    private final StreamPushService streamPushService;
    private final ViewTokenSigner viewTokenSigner;

    @Operation(summary = "推流分页查询")
    @PostMapping("/page")
    @RequirePermission({"media:stream-push:list"})
    public Result<PageResult<StreamPush>> page(@RequestBody StreamPushPageRequest request) {
        Page<StreamPush> page = streamPushService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "推流详情")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}")
    @RequirePermission({"media:stream-push:list"})
    public Result<StreamPush> detail(@PathVariable Long id) {
        StreamPush p = streamPushService.getById(id);
        if (p == null) {
            throw new ServiceException("推流不存在");
        }
        return Result.success(p);
    }

    @Operation(summary = "播放地址")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}/play-urls")
    public Result<StreamPushPlayUrlResponse> playUrls(@PathVariable Long id) {
        StreamPushPlayUrlResponse result = streamPushService.getPlayUrls(id);
        String serverId = result.getMediaServerId();
        String app = result.getApp();
        String stream = result.getStream();
        ViewActor actor = viewTokenSigner.requireCurrentUserActor();
        result.setFlvUrl(viewTokenSigner.appendToken(result.getFlvUrl(), actor, PlayTarget.httpFlv(serverId, app, stream)));
        result.setWsFlvUrl(viewTokenSigner.appendToken(result.getWsFlvUrl(), actor, PlayTarget.wsFlv(serverId, app, stream)));
        result.setHlsUrl(viewTokenSigner.appendToken(result.getHlsUrl(), actor, PlayTarget.hls(serverId, app, stream)));
        result.setRtcUrl(viewTokenSigner.appendToken(result.getRtcUrl(), actor, PlayTarget.rtc(serverId, app, stream)));
        return Result.success(result);
    }

    @Operation(summary = "强制中止")
    @OperationLog(module = "推流管理", type = BusinessType.OTHER, description = "强制中止推流")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        streamPushService.forceStop(id);
        return Result.success();
    }

    @Operation(summary = "单条删除")
    @OperationLog(module = "推流管理", type = BusinessType.DELETE, description = "删除推流")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-push:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        streamPushService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "批量删除")
    @OperationLog(module = "推流管理", type = BusinessType.DELETE, description = "批量删除推流")
    @PostMapping("/batch/delete")
    @RequirePermission({"media:stream-push:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody StreamPushBatchDeleteRequest request) {
        streamPushService.deleteBatch(request.getIds());
        return Result.success();
    }
}
