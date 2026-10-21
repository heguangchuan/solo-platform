package com.solo.gb28181.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;

import java.util.List;

/**
 * <p>ADR-023：本平台作为下级向上级共享通道的编排与持久化。S6.b 的 Catalog 查询响应 / 变更 Notify 以此处
 * 保存的共享关系为数据源。</p>
 *
 * <p>{@code gb_platform_share} <b>只存通道</b>（{@code gb_device_channel} 中 parental=0 的行）。
 * 分组是本平台内部的目录组织，当前不推送给上级（Catalog XML 只发通道 Item）。</p>
 *
 * <p>share 与推送分组（{@code gb_platform_group}）关联：UI 强制先在弹窗左侧选中分组才能挂载。
 * 同一通道在同一平台下只能挂载一次（{@code uk_platform_resource}），换组要先卸载旧组。
 * Catalog 响应 / INVITE 反查走全量（不分组）路径。</p>
 *
 * <p>撞码检测是<b>平台级</b>的：上级看到扁平 DeviceID 空间，跨组同码也会让上级 INVITE 认错通道，
 * 故撞码集合按 {@code platform_id} 全量判定（不限定 group_id）。</p>
 */
public interface CatalogShareService {

    List<GbShareTreeNodeResponse> shareTree();

    GbPlatformSharedResponse getShared(Long platformId);

    @Deprecated
    void saveShared(Long platformId, GbPlatformShareSaveRequest request);

    List<GbDeviceChannel> loadSharedCatalogItems(Long platformId);

    List<GbDeviceChannel> loadSharedChannels(Long platformId);

    IPage<GbDeviceChannel> pageSharedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword);

    IPage<GbDeviceChannel> pageUnmountedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword);

    int addShares(Long platformId, Long groupId, List<Long> resourceIds);

    boolean removeShare(Long platformId, Long groupId, Long resourceId);

    int clearShares(Long platformId, Long groupId);

    int addAllUnmounted(Long platformId, Long groupId);

    int clearAllSharesByPlatform(Long platformId);

    void pushCatalog(Long platformId);
}
