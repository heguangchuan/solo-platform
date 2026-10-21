package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;
import com.solo.gb28181.service.PlatformGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "上级平台推送目录分组管理", description = "上级平台推送目录分组管理")
@RestController
@RequestMapping("/api/gb28181/parent-platform")
@RequiredArgsConstructor
public class PlatformGroupController {

    private final PlatformGroupService platformGroupService;

    @Operation(summary = "拉取该上级平台的推送目录分组树")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}/share-group-tree")
    @RequirePermission({"gb28181:share-group:list"})
    public Result<List<GbPlatformGroupResponse>> shareGroupTree(@PathVariable("id") Long id) {
        return Result.success(platformGroupService.listGroupTree(id));
    }

    @Operation(summary = "新建该上级平台下的推送分组")
    @Parameter(name = "id", description = "上级平台主键 ID（路径参数）", in = ParameterIn.PATH)
    @PostMapping("/{id}/share-group")
    @RequirePermission({"gb28181:share-group:add"})
    public Result<Void> createShareGroup(
            @PathVariable("id") Long platformId,
            @Valid @RequestBody GbPlatformGroupSaveRequest req) {
        platformGroupService.createGroup(platformId, req);
        return Result.success();
    }

    @Operation(summary = "改该上级平台下的推送分组")
    @Parameter(name = "id", description = "上级平台主键 ID（路径参数）", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "分组主键 ID（路径参数）", in = ParameterIn.PATH)
    @PutMapping("/{id}/share-group/{groupId}")
    @RequirePermission({"gb28181:share-group:edit"})
    public Result<Void> updateShareGroup(
            @PathVariable("id") Long platformId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody GbPlatformGroupSaveRequest req) {
        platformGroupService.updateGroup(platformId, groupId, req);
        return Result.success();
    }

    @Operation(summary = "删除该上级平台下的推送分组")
    @Parameter(name = "id", description = "上级平台主键 ID（路径参数）", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "分组主键 ID（路径参数）", in = ParameterIn.PATH)
    @DeleteMapping("/{id}/share-group/{groupId}")
    @RequirePermission({"gb28181:share-group:delete"})
    public Result<Void> deleteShareGroup(
            @PathVariable("id") Long platformId,
            @PathVariable("groupId") Long groupId) {
        platformGroupService.deleteGroup(platformId, groupId);
        return Result.success();
    }
}
