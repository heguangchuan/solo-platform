package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.media.request.GbViewRecordPageRequest;
import com.solo.media.response.GbViewRecordVO;
import com.solo.media.service.GbViewRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "观看记录", description = "谁看了哪路流、看了多久、耗了多少流量")
@RestController
@RequestMapping("/api/gb28181/view-record")
@RequiredArgsConstructor
public class ViewRecordController {

    private final GbViewRecordService viewRecordService;

    @Operation(summary = "分页查询观看记录")
    @GetMapping("/page")
    @RequirePermission("gb28181:view-record:list")
    public Result<PageResult<GbViewRecordVO>> page(GbViewRecordPageRequest request) {
        return Result.success(viewRecordService.page(request));
    }
}
