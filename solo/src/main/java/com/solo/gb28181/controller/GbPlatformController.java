package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.request.GbPlatformBatchDeleteRequest;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;
import com.solo.gb28181.service.GbPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 下级级联平台管理
 * 下级平台白名单 CRUD + 注册鉴权密码配置
 */
@RestController
@RequestMapping("/api/gb28181/platform")
@RequiredArgsConstructor
public class GbPlatformController {

    private final GbPlatformService gbPlatformService;

    /**
     * 分页查询白名单，关联 gb_device 回填在线状态
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:platform:list"})
    public Result<PageResult<GbPlatformVO>> page(@RequestBody GbPlatformPageRequest request) {
        Page<GbPlatformVO> page = gbPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 手动新增白名单
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:platform:add"})
    public Result<Void> save(@RequestBody GbPlatform platform) {
        gbPlatformService.createByManual(platform);
        return Result.success();
    }

    /**
     * 手动编辑白名单
     */
    @PostMapping("/update")
    @RequirePermission({"gb28181:platform:edit"})
    public Result<Void> update(@RequestBody GbPlatform platform) {
        if (platform.getId() == null) {
            throw new ServiceException("平台ID不能为空");
        }
        gbPlatformService.updateByManual(platform);
        return Result.success();
    }

    /**
     * 删除白名单记录（不影响已注册的 gb_device 行）
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:platform:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        GbPlatform platform = gbPlatformService.getById(id);
        if (platform != null) {
            gbPlatformService.removeById(id);
        }
        return Result.success();
    }

    /**
     * 批量删除白名单记录
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:platform:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbPlatformBatchDeleteRequest request) {
        gbPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }
}