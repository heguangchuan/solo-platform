package com.solo.onvif.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.request.OnvifDevicePageRequest;
import com.solo.onvif.service.OnvifDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ONVIF 设备管理
 *
 * <p>管理台对 ONVIF 摄像头的 CRUD + 探测触发。新增 / 编辑设备时服务端会自动调一次 ONVIF 探测，
 * 写回流地址、在线状态、最后探测时间；前端用设备详情里的 RTSP 流地址直接播放。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 设备分页查询（按 name 模糊 + host 模糊 + status 过滤）</li>
 *   <li>{@code GET /{id}} — 设备详情（按主键）</li>
 *   <li>{@code POST /save} — 新增设备（id 留空，保存后自动触发一次探测）</li>
 *   <li>{@code POST /update} — 编辑设备（id 必填，保存后自动重新探测）</li>
 *   <li>{@code POST /{id}/probe} — 主动探测一次（"刷新/探测"按钮专用）</li>
 *   <li>{@code DELETE /{id}} — 删除设备</li>
 * </ul>
 *
 * <p>前端集成 ONVIF 设备时典型流程：</p>
 * <ol>
 *   <li>用户在新增表单填入 WSDL 地址（如 {@code http://192.168.1.64/onvif/device_service}）、
 *       用户名、密码，调用 {@code /save}</li>
 *   <li>服务端自动调 ONVIF GetCapabilities / GetProfiles / GetStreamUri 等协议，
 *       把流地址写回数据库</li>
 *   <li>前端读详情时拿到 streamUri，配合 OnvifPlayController 取播放 URL</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/onvif/device")
@RequiredArgsConstructor
public class OnvifDeviceController {

    private final OnvifDeviceService onvifDeviceService;

    /**
     * 设备分页查询
     * <p>支持按 name 模糊、host 模糊、status 精确过滤；结果按更新时间倒序。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"onvif:device:list"})
    public Result<PageResult<OnvifDevice>> page(@RequestBody OnvifDevicePageRequest req) {
        Page<OnvifDevice> page = onvifDeviceService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 设备详情（按主键）
     * <p>主键 ID 不存在时返回业务异常。详情里包含设备主动探测回来的 RTSP 流地址（streamUri）。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"onvif:device:list"})
    public Result<OnvifDevice> detail(@PathVariable Long id) {
        OnvifDevice device = onvifDeviceService.getById(id);
        if (device == null) {
            throw new ServiceException("设备不存在: " + id);
        }
        return Result.success(device);
    }

    /**
     * 新增设备
     * <p>id 强制置空，强制走"新增"路径；保存后立即触发一次 ONVIF 探测，
     * 写回 RTSP 流地址、在线状态、最后探测时间。
     * 厂商 / 型号 / 固件 / 序列号只打日志，不入表，前端无需关心。</p>
     */
    @PostMapping("/save")
    @RequirePermission({"onvif:device:add"})
    public Result<Void> save(@RequestBody OnvifDevice device) {
        device.setId(null); // 强制新增
        onvifDeviceService.saveWithProbe(device);
        return Result.success();
    }

    /**
     * 编辑设备
     * <p>id 必填；更新后立即重新探测一次，刷新 RTSP 流地址、在线状态、最后探测时间。</p>
     */
    @PostMapping("/update")
    @RequirePermission({"onvif:device:edit"})
    public Result<Void> update(@RequestBody OnvifDevice device) {
        if (device.getId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        onvifDeviceService.saveWithProbe(device);
        return Result.success();
    }

    /**
     * 主动探测一次
     * <p>前端"刷新 / 探测"按钮专用，调用后会调一次 ONVIF 协议，
     * 更新 RTSP 流地址、在线状态、最后探测时间，其它字段不动。</p>
     */
    @PostMapping("/{id}/probe")
    @RequirePermission({"onvif:device:probe"})
    public Result<Void> probe(@PathVariable Long id) {
        onvifDeviceService.probe(id);
        return Result.success();
    }

    /**
     * 删除设备
     * <p>仅删除本平台上的设备表记录；不影响该设备本体的 ONVIF 服务。
     * 删除后无法再通过本平台拉该设备的流。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"onvif:device:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        onvifDeviceService.removeById(id);
        return Result.success();
    }
}
