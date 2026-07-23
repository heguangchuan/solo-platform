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
 * <p>面向管理台的 CRUD + 启停 + 播放地址查询。ZLM 侧的 addStreamProxy/delStreamProxy 由 Service 触发。</p>
 */
@RestController
@RequestMapping("/api/stream-proxy")
@RequiredArgsConstructor
public class StreamProxyController {

    private final StreamProxyService streamProxyService;

    /** 分页 */
    @PostMapping("/page")
    @RequirePermission({"media:stream-proxy:list"})
    public Result<PageResult<StreamProxy>> page(@RequestBody StreamProxyPageRequest request) {
        Page<StreamProxy> page = streamProxyService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /** 详情 */
    @GetMapping("/{id}")
    @RequirePermission({"media:stream-proxy:list"})
    public Result<StreamProxy> detail(@PathVariable Long id) {
        StreamProxy p = streamProxyService.getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在");
        }
        return Result.success(p);
    }

    /** 新增 */
    @PostMapping("/save")
    @RequirePermission({"media:stream-proxy:add"})
    public Result<Void> save(@RequestBody StreamProxy proxy) {
        proxy.setId(null);
        streamProxyService.saveOrUpdateProxy(proxy);
        return Result.success();
    }

    /** 编辑 */
    @PostMapping("/update")
    @RequirePermission({"media:stream-proxy:edit"})
    public Result<Void> update(@RequestBody StreamProxy proxy) {
        if (proxy.getId() == null) {
            throw new ServiceException("id 不能为空");
        }
        streamProxyService.saveOrUpdateProxy(proxy);
        return Result.success();
    }

    /** 启用（推流） */
    @PostMapping("/{id}/enable")
    @RequirePermission({"media:stream-proxy:toggle"})
    public Result<Void> enable(@PathVariable Long id) {
        streamProxyService.enable(id);
        return Result.success();
    }

    /** 停用（摘流） */
    @PostMapping("/{id}/disable")
    @RequirePermission({"media:stream-proxy:toggle"})
    public Result<Void> disable(@PathVariable Long id) {
        streamProxyService.disable(id);
        return Result.success();
    }

    /** 播放地址 */
    @GetMapping("/{id}/play-urls")
    @RequirePermission({"media:stream-proxy:list"})
    public Result<StreamProxyPlayUrlResponse> playUrls(@PathVariable Long id) {
        return Result.success(streamProxyService.getPlayUrls(id));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    @RequirePermission({"media:stream-proxy:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        streamProxyService.deleteById(id);
        return Result.success();
    }

    /** 批量删除 */
    @PostMapping("/batch/delete")
    @RequirePermission({"media:stream-proxy:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody StreamProxyBatchDeleteRequest request) {
        streamProxyService.deleteBatch(request.getIds());
        return Result.success();
    }
}
