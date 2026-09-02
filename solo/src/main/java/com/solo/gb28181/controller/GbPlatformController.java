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
 * 国标接入禁用名单管理（黑名单）
 * <p>设备与下级平台默认自由注册，只有在此登记且 {@code enabled=0} 的国标 ID 会被
 * SIP 入口（REGISTER + MESSAGE）静默拒绝。鉴权与本表无关，走全局国标密码。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 分页查询禁用名单，关联设备表回填在线状态</li>
 *   <li>{@code POST /save} — 新增禁用项</li>
 *   <li>{@code POST /update} — 编辑禁用项（备注名 / 原因 / 拦截开关；platformId 不可改）</li>
 *   <li>{@code DELETE /{id}} — 移出禁用名单（不影响已注册的设备行）</li>
 *   <li>{@code POST /batch/delete} — 批量移出禁用名单</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/platform")
@RequiredArgsConstructor
public class GbPlatformController {

    private final GbPlatformService gbPlatformService;

    /**
     * 分页查询禁用名单，关联设备表回填在线状态（便于确认"拉黑后是否真的断了"）
     * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
     * 响应结构为 {@code { total, rows }}，前端表格可直接接。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:platform:list"})
    public Result<PageResult<GbPlatformVO>> page(@RequestBody GbPlatformPageRequest request) {
        Page<GbPlatformVO> page = gbPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增禁用项
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:platform:add"})
    public Result<Void> save(@RequestBody GbPlatform platform) {
        gbPlatformService.createByManual(platform);
        return Result.success();
    }

    /**
     * 编辑禁用项（备注名 / 原因 / 拦截开关；platformId 不可改）
     * <p>入参 {@code id} 必填，否则返回 400。</p>
     */
    @PostMapping("/update")
    @RequirePermission({"gb28181:platform:edit"})
    public Result<Void> update(@RequestBody GbPlatform platform) {
        if (platform.getId() == null) {
            throw new ServiceException("记录ID不能为空");
        }
        gbPlatformService.updateByManual(platform);
        return Result.success();
    }

    /**
     * 移出禁用名单（不影响已注册的设备行；解禁后设备重新 REGISTER 即可接入）
     * @param id 禁用项主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:platform:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        gbPlatformService.deleteBatch(java.util.List.of(id));
        return Result.success();
    }

    /**
     * 批量移出禁用名单
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:platform:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbPlatformBatchDeleteRequest request) {
        gbPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }
}