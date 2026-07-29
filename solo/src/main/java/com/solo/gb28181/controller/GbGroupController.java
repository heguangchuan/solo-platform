package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.request.GbGroupPageRequest;
import com.solo.gb28181.response.GbGroupResponse;
import com.solo.gb28181.service.GbGroupService;
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
 * 国标资源目录分组管理
 * <p>平台侧自建目录树（业务分组 215 / 虚拟组织 216），用于级联时组织向上级共享的通道目录。</p>
 */
@RestController
@RequestMapping("/api/gb28181/group")
@RequiredArgsConstructor
public class GbGroupController {

    private final GbGroupService groupService;

    /** 分页查询分组 */
    @PostMapping("/page")
    @RequirePermission({"gb28181:group:list"})
    public Result<PageResult<GbGroupResponse>> page(@RequestBody GbGroupPageRequest request) {
        Page<GbGroupResponse> page = groupService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /** 分组树（列表页展示用，parentId=0 为根） */
    @GetMapping("/tree")
    @RequirePermission({"gb28181:group:list"})
    public Result<List<GbGroupResponse>> tree() {
        return Result.success(groupService.tree());
    }

    /** 列出全部分组（下拉选择父级用），按 sort 升序 */
    @GetMapping("/list")
    @RequirePermission({"gb28181:group:list"})
    public Result<List<GbGroup>> list() {
        List<GbGroup> list = groupService.lambdaQuery()
                .orderByAsc(GbGroup::getSort)
                .orderByAsc(GbGroup::getId)
                .list();
        return Result.success(list);
    }

    /** 新增分组 */
    @PostMapping("/save")
    @RequirePermission({"gb28181:group:add"})
    public Result<Void> save(@RequestBody GbGroup group) {
        groupService.createGroup(group);
        return Result.success();
    }

    /** 编辑分组资料（groupId / type 不可改） */
    @PostMapping("/update")
    @RequirePermission({"gb28181:group:edit"})
    public Result<Void> update(@RequestBody GbGroup group) {
        if (group.getId() == null) {
            throw new ServiceException("分组ID不能为空");
        }
        groupService.updateGroup(group);
        return Result.success();
    }

    /** 删除分组（默认分组不可删；通道回落默认分组） */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:group:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        groupService.deleteById(id);
        return Result.success();
    }
}
