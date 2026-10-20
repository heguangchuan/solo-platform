package com.solo.gb28181.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;

import java.util.List;

/**
 * 目录共享服务（ADR-023）。
 * <p>本平台作为下级向上级共享通道的编排与持久化。S6.b 的 Catalog 查询响应 / 变更 Notify 以此处
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

    /**
     * 本平台可共享的 catalog item 树：parental=1 分组作枝干，parental=0 通道按 parentId 挂分组下。
     * <p>仅供旧勾选 UI 展示；share 表实际只存通道。</p>
     */
    List<GbShareTreeNodeResponse> shareTree();

    /**
     * 查询某上级平台已共享的通道节点 id（回填前端勾选态；全量，不分组）。
     */
    GbPlatformSharedResponse getShared(Long platformId);

    /**
     * @deprecated 旧 catalog 树"全量覆盖勾选"入口。{@code group_id} 已改为 NOT NULL（必须挂在某个推送
     *             分组下），本方法没有 groupId 参数无法正确入库，调用直接抛 ServiceException。
     *             请改用按组接口：{@link #addShares} / {@link #removeShare} / {@link #clearShares}。
     */
    @Deprecated
    void saveShared(Long platformId, GbPlatformShareSaveRequest request);

    /**
     * 查询某上级平台已共享的目录项（分组 + 通道，ADR-028）。
     * <p>结果含：① {@code gb_platform_share} 里的通道（{@code parental=0}），带
     *   {@code exposedCode} 透传；② 这些通道的所有祖先分组（{@code parental=1}），
     *   沿 {@code parent_id} 链递归上溯得到。分组在前、通道在后，按 {@code channelId}
     *   二级排序——分片时跨片顺序稳定，上级按 {@code <ParentID>} 引用关系总能解析到。</p>
     * <p>供 S6.b-2 向上级回 Catalog Response 时构造 Item 使用——既要通道也要它们所在的分组，
     * 才能向上级表达正确的目录层级。</p>
     */
    List<GbDeviceChannel> loadSharedCatalogItems(Long platformId);

    /**
     * 查询某上级平台已共享的所有通道明细（<b>仅通道</b>，全量）。
     * <p>供上级 INVITE 点播反查真实通道用。{@link #loadSharedCatalogItems} 含分组，
     * 本方法只返通道——INVITE 反查按 {@code channelId} 精确匹配，混入分组会误匹配。</p>
     */
    List<GbDeviceChannel> loadSharedChannels(Long platformId);

    /**
     * 分页查询某上级平台某分组下已挂载的通道（推送目录弹窗"已挂载"列表）。
     */
    IPage<GbDeviceChannel> pageSharedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword);

    /**
     * 分页查询某上级平台"未挂载"的通道（推送目录弹窗"添加"picker）。
     * <p>未挂载 = 不在该平台任何 share 里（任意分组都算已挂载）——同一通道在本平台只能挂一次。</p>
     */
    IPage<GbDeviceChannel> pageUnmountedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword);

    /**
     * 批量挂载通道到某分组。每个 resourceId 来自 {@code gb_device_channel.id}（parental=0）。
     * <ul>
     *   <li>exposedCode：撞码时按 {@code (deviceId, channelId)} 哈希派生（ADR-026 决策 1 / 决策 2）；
     *       优先复用该 resourceId 已有的旧码（跨平台稳定，ADR-026 决策 6"已分码不收回"）</li>
     *   <li>撞码判定按 {@code platform_id} 全量（不限定 group_id）：本批按 channelId 分组 size>1 /
     *       与该平台已有 share 的 channelId 相交 → 撞码</li>
     * </ul>
     * 已挂本组的幂等跳过；已挂<b>其他组</b>的抛 ServiceException（提示先卸载）。
     *
     * @param platformId  上级平台主键
     * @param groupId     目标分组主键（必须属于该 platformId）
     * @param resourceIds 待挂载通道 id 列表
     * @return 新增条数
     */
    int addShares(Long platformId, Long groupId, List<Long> resourceIds);

    /**
     * 单条卸载（按 (platform_id, group_id, resource_id) 定位删除）。
     * @return true = 删了一行；false = 该挂载关系不存在
     */
    boolean removeShare(Long platformId, Long groupId, Long resourceId);

    /**
     * 清除某上级平台某分组下的全部挂载关系。管理台树节点"清除本组"按钮用。
     * @return 删除条数
     */
    int clearShares(Long platformId, Long groupId);

    /**
     * 一键添加所有"未挂载"的通道到本组（不在该平台任何 share 里的）。
     * <p>管理台"一键添加"按钮：跳过 picker 多选，把当前所有未挂载通道全部入库本组。
     * 走 {@link #addShares} 同一套撞码检测逻辑。</p>
     * @return 新增条数
     */
    int addAllUnmounted(Long platformId, Long groupId);

    /**
     * 清除某上级平台下全部挂载关系（不分组，按 platform_id 全删）。
     * 管理台右侧"一键清除"按钮用——区别于 {@link #clearShares}（按组清）。
     * @return 删除条数
     */
    int clearAllSharesByPlatform(Long platformId);

    /**
     * 主动推送目录给上级平台（不依赖订阅活跃状态，手动触发一次 Catalog NOTIFY）。
     */
    void pushCatalog(Long platformId);
}
