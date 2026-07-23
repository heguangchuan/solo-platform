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
 * <p>token 明文返回给列表，权限收紧到 {@code media:stream-push-client:list}。</p>
 */
@RestController
@RequestMapping("/api/stream-push-client")
@RequiredArgsConstructor
public class StreamPushClientController {

    private final StreamPushClientService service;

    @PostMapping("/page")
    @RequirePermission({"media:stream-push-client:list"})
    public Result<PageResult<StreamPushClient>> page(@RequestBody StreamPushClientPageRequest request) {
        Page<StreamPushClient> page = service.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/save")
    @RequirePermission({"media:stream-push-client:add"})
    public Result<StreamPushClient> save(@RequestBody StreamPushClient input) {
        return Result.success(service.createClient(input));
    }

    @PostMapping("/update")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<Void> update(@RequestBody StreamPushClient input) {
        service.updateClient(input);
        return Result.success();
    }

    /** 重置 token（安全事件时用） */
    @PostMapping("/{id}/reset-token")
    @RequirePermission({"media:stream-push-client:edit"})
    public Result<StreamPushClient> resetToken(@PathVariable Long id) {
        return Result.success(service.resetToken(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-push-client:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }
}
