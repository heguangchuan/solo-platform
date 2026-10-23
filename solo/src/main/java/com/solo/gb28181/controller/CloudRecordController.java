package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.gb28181.response.GbRecordFileVO;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbRecordFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "云端录像中心", description = "手动录制、录像文件查询/播放/删除")
@RestController
@RequestMapping("/api/gb28181/cloud-record")
@RequiredArgsConstructor
public class CloudRecordController {

    private final GbRecordFileService recordFileService;
    private final GbCloudRecordService cloudRecordService;

    @Operation(summary = "分页查询录像文件")
    @GetMapping("/page")
    @RequirePermission("gb28181:record:list")
    public Result<PageResult<GbRecordFileVO>> page(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(recordFileService.page(deviceId, channelId, start, end, status, page, size));
    }

    @Operation(summary = "取录像播放/下载地址（预签名，限时有效）")
    @GetMapping("/{id}/url")
    @RequirePermission("gb28181:record:list")
    public Result<String> url(@PathVariable Long id) {
        return Result.success(recordFileService.presignedUrl(id));
    }

    @Operation(summary = "手动开始录制")
    @OperationLog(module = "云端录像", type = BusinessType.OTHER, description = "手动开始录制")
    @PostMapping("/start")
    @RequirePermission("gb28181:record:control")
    public Result<String> start(@RequestParam String deviceId,
                                @RequestParam String channelId,
                                @RequestParam(required = false) String transport,
                                @RequestParam(required = false) String streamNumber) {
        return Result.success(cloudRecordService.startManual(deviceId, channelId, transport, streamNumber));
    }

    @Operation(summary = "手动停止录制")
    @OperationLog(module = "云端录像", type = BusinessType.OTHER, description = "手动停止录制")
    @PostMapping("/stop")
    @RequirePermission("gb28181:record:control")
    public Result<Void> stop(@RequestParam String streamId) {
        cloudRecordService.stop(streamId);
        return Result.success();
    }

    @Operation(summary = "删除录像文件（同时删存储对象）")
    @OperationLog(module = "云端录像", type = BusinessType.DELETE, description = "删除录像文件")
    @DeleteMapping("/{id}")
    @RequirePermission("gb28181:record:delete")
    public Result<Void> delete(@PathVariable Long id) {
        recordFileService.delete(id);
        return Result.success();
    }
}
