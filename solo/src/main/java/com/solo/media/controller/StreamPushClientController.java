package com.solo.media.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.request.StreamPushClientPageRequest;
import com.solo.media.service.StreamPushClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "推流方鉴权管理", description = "推流方鉴权管理")
@RestController
@RequestMapping("/api/stream-push-client")
@RequiredArgsConstructor
public class StreamPushClientController {

    private final StreamPushClientService service;

    @Operation(summary = "推流方分页查询")
    @PostMapping("/page")
    @RequirePermission({"media:stream-push-client:list"})
    public Result<PageResult<StreamPushClient>> page(@RequestBody StreamPushClientPageRequest request) {
        Page<StreamPushClient> page = service.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "新增推流方")
    @PostMapping("/save")
    @RequirePermission({"media:stream-push-client:add"})
    public Result<StreamPushClient> save(@RequestBody StreamPushClient input) {
        return Result.success(service.createClient(input));
    }

    @Operation(summary = "更新推流方")
    @PostMapping("/update")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<Void> update(@RequestBody StreamPushClient input) {
        service.updateClient(input);
        return Result.success();
    }

    @Operation(summary = "重置 token")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/reset-token")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<StreamPushClient> resetToken(@PathVariable Long id) {
        return Result.success(service.resetToken(id));
    }

    @Operation(summary = "删除推流方")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-push-client:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }
}
